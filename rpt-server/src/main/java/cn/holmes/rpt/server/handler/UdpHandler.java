package cn.holmes.rpt.server.handler;

import cn.holmes.rpt.base.config.ConfigHolder;
import cn.holmes.rpt.base.config.ProxyType;
import cn.holmes.rpt.base.config.RemoteConfig;
import cn.holmes.rpt.base.config.ServerConfig;
import cn.holmes.rpt.base.protocol.ChannelEvent;
import cn.holmes.rpt.base.protocol.Message;
import cn.holmes.rpt.base.protocol.MessageType;
import cn.holmes.rpt.base.protocol.Meta;
import cn.holmes.rpt.base.utils.Attributes.Server;
import cn.holmes.rpt.server.cache.TrafficStatsCache;
import cn.holmes.rpt.server.cache.UdpSessionQuota;
import cn.holmes.rpt.server.utils.IpCountryFilter;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.ipfilter.IpFilterRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 处理服务器接收到的外部UDP请求
 * <p>
 * UDP是无连接协议，每个发送者IP:Port作为一个虚拟会话。
 * 使用proxyMap维护每个会话对应的代理通道，bufferMap缓冲代理建立前的数据。
 */
public class UdpHandler extends SimpleChannelInboundHandler<DatagramPacket> {

    private static final Logger logger = LoggerFactory.getLogger(UdpHandler.class);

    private static final IpFilterRule IP_COUNTRY_FILTER = IpCountryFilter.getInstance();

    private final Channel serverChannel;

    private final RemoteConfig remoteConfig;

    private final UdpSessionQuota sessionQuota;

    private final ServerConfig serverConfig;

    /**
     * 本端口对应的UDP DatagramChannel（channelActive时赋值，按端口共享）
     */
    private Channel udpChannel;

    private final Map<String, InetSocketAddress> senderAddressMap = new ConcurrentHashMap<>();

    private final Map<String, Channel> proxyChannelMap = new ConcurrentHashMap<>();

    private final Map<String, Long> sessionCreatedMap = new ConcurrentHashMap<>();

    /**
     * channelId → 代理建立前缓冲的数据（retain的ByteBuf）
     */
    private final Map<String, PendingPackets> channelBufferMap = new ConcurrentHashMap<>();

    /**
     * 本端口所有待建立会话的缓冲字节数，只在channelBufferMap锁内访问
     */
    private long bufferedBytes;

    private ScheduledFuture<?> timeoutChecker;

    public UdpHandler(Channel serverChannel, RemoteConfig remoteConfig, UdpSessionQuota sessionQuota) {
        this.serverChannel = serverChannel;
        this.remoteConfig = remoteConfig;
        this.sessionQuota = sessionQuota;
        this.serverConfig = ConfigHolder.getServerConfig();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        udpChannel = ctx.channel();
        ctx.channel().attr(Server.PROXY_TYPE).set(ProxyType.UDP);
        ctx.channel().attr(Server.UDP_LAST_ACTIVE).set(new ConcurrentHashMap<>());
        long interval = Math.max(1000L, TimeUnit.SECONDS.toMillis(Math.min(serverConfig.getUdpSessionTimeout(), serverConfig.getUdpConnectTimeout()) / 2));
        timeoutChecker = ctx.channel().eventLoop().scheduleAtFixedRate(this::cleanIdleSessions, interval, interval, TimeUnit.MILLISECONDS);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        boolean isEvent = evt instanceof ChannelEvent;
        if (!isEvent) {
            ctx.fireUserEventTriggered(evt);
            return;
        }
        ChannelEvent channelEvent = (ChannelEvent) evt;
        MessageType messageType = channelEvent.getMessageType();
        if (messageType == MessageType.TYPE_CONNECTED) {
            // 代理通道已建立，绑定channelId和代理通道
            bindProxy(channelEvent.getChannelId(), channelEvent.getProxyChannel());
        }
        if (messageType == MessageType.TYPE_DISCONNECTED) {
            // 代理通道断开，移除会话
            removeSession(channelEvent.getChannelId());
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (timeoutChecker != null) {
            timeoutChecker.cancel(false);
        }
        // 关闭所有代理通道，使客户端能感知断开并回收资源
        for (String channelId : senderAddressMap.keySet()) {
            removeSession(channelId);
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) throws Exception {
        InetSocketAddress sender = packet.sender();
        if (ConfigHolder.getServerConfig().ipFilterEnabled() && IP_COUNTRY_FILTER.matches(sender)) {
            logger.info("UDP remote handler rejected sender: {}", sender);
            return;
        }
        String channelId = toChannelId(sender);
        ByteBuf data = packet.content().retainedDuplicate();
        if (!senderAddressMap.containsKey(channelId) && !createSession(channelId, sender)) {
            data.release();
            return;
        }
        ctx.channel().attr(Server.UDP_LAST_ACTIVE).get().put(channelId, System.currentTimeMillis());
        TrafficStatsCache.recordIn(serverChannel.id().asLongText(), data.readableBytes());
        if (!senderAddressMap.containsKey(channelId)) {
            // 缓冲数据，等待代理通道建立
            addToBuffer(channelId, data);
            // 通知客户端建立连接
            sendMessage(serverChannel, MessageType.TYPE_CONNECTED, Unpooled.EMPTY_BUFFER, channelId);
            return;
        }

        // 已有会话，检查代理通道是否已建立
        Channel proxyChannel = proxyChannelMap.get(channelId);
        if (proxyChannel != null && proxyChannel.isActive()) {
            sendMessage(proxyChannel, MessageType.TYPE_DATA, data, channelId);
        } else {
            // 代理通道尚未建立，缓冲数据
            addToBuffer(channelId, data);
        }
    }

    /**
     * 绑定隧道并刷新缓冲数据
     */
    private void bindProxy(String channelId, Channel tunnel) {
        InetSocketAddress address = senderAddressMap.get(channelId);
        if (address == null) {
            return;
        }
        // 发送者地址按会话记录在共享的UDP本地通道上
        udpChannel.attr(Server.UDP_SENDERS).setIfAbsent(new ConcurrentHashMap<>());
        Map<String, InetSocketAddress> senders = udpChannel.attr(Server.UDP_SENDERS).get();
        senders.put(channelId, address);
        proxyChannelMap.put(channelId, tunnel);
        // 刷新缓冲的数据
        PendingPackets buffer = removeBuffer(channelId);
        if (buffer != null) {
            ByteBuf data;
            while ((data = buffer.packets.poll()) != null) {
                sendMessage(tunnel, MessageType.TYPE_DATA, data, channelId);
            }
        }
    }

    /**
     * 移除会话
     */
    private void removeSession(String channelId) {
        Optional.ofNullable(serverChannel.attr(Server.CHANNELS).get()).ifPresent(channelMap -> channelMap.remove(channelId));
        sessionCreatedMap.remove(channelId);
        if (senderAddressMap.remove(channelId) != null) {
            sessionQuota.releaseSession();
        }
        Map<String, InetSocketAddress> senders = udpChannel != null ? udpChannel.attr(Server.UDP_SENDERS).get() : null;
        if (senders != null) {
            senders.remove(channelId);
        }
        // 释放缓冲区中未发送的ByteBuf
        releaseBuffer(removeBuffer(channelId));
        Optional.ofNullable(udpChannel).map(channel -> channel.attr(Server.UDP_LAST_ACTIVE).get()).ifPresent(lastActive -> lastActive.remove(channelId));
        Channel tunnel = proxyChannelMap.remove(channelId);
        // 通过隧道通知客户端断开该会话，隧道本身保持复用
        if (tunnel != null && tunnel.isActive()) {
            String serverId = serverChannel.id().asLongText();
            Optional.ofNullable(tunnel.attr(Server.STREAM_SET).get()).ifPresent(streamSet -> {
                // remove返回true才递减
                if (streamSet.remove(channelId)) {
                    TrafficStatsCache.decrementProxyChannels(serverId);
                }
            });
            sendMessage(tunnel, MessageType.TYPE_DISCONNECTED, Unpooled.EMPTY_BUFFER, channelId);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("UDP remote handler error", cause);
    }

    private void cleanIdleSessions() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Long> entry : udpChannel.attr(Server.UDP_LAST_ACTIVE).get().entrySet()) {
            String channelId = entry.getKey();
            long lastActive = entry.getValue();
            Long created = sessionCreatedMap.get(channelId);
            Channel proxyChannel = proxyChannelMap.get(channelId);
            boolean connectTimedOut = created != null && (proxyChannel == null || !proxyChannel.isActive()) && now - created > TimeUnit.SECONDS.toMillis(serverConfig.getUdpConnectTimeout());
            if (connectTimedOut || now - lastActive > TimeUnit.SECONDS.toMillis(serverConfig.getUdpSessionTimeout())) {
                logger.info("UDP session expired: {}, idle {}s", channelId, TimeUnit.MILLISECONDS.toSeconds(now - lastActive));
                removeSession(channelId);
            }
        }
    }

    private boolean createSession(String channelId, InetSocketAddress sender) {
        Map<String, Channel> localChannelMap = serverChannel.attr(Server.CHANNELS).get();
        if (localChannelMap == null || senderAddressMap.size() >= serverConfig.getUdpMaxSessionsPerPort() || !sessionQuota.acquireSession(serverConfig.getUdpMaxSessionsTotal())) {
            return false;
        }
        if (senderAddressMap.putIfAbsent(channelId, sender) != null) {
            sessionQuota.releaseSession();
            return true;
        }
        sessionCreatedMap.put(channelId, System.currentTimeMillis());
        localChannelMap.put(channelId, udpChannel);
        return true;
    }

    private void addToBuffer(String channelId, ByteBuf data) {
        int size = data.readableBytes();
        synchronized (channelBufferMap) {
            PendingPackets buffer = channelBufferMap.computeIfAbsent(channelId, k -> new PendingPackets());
            if (buffer.packets.size() >= serverConfig.getUdpMaxBufferedPackets() || buffer.bytes + size > serverConfig.getUdpMaxBufferBytesPerSession() || bufferedBytes + size > serverConfig.getUdpMaxBufferBytesPerPort() || !sessionQuota.acquireBuffer(size, serverConfig.getUdpMaxBufferBytesTotal())) {
                data.release();
                return;
            }
            buffer.packets.add(data);
            buffer.bytes += size;
            bufferedBytes += size;
        }
    }

    private PendingPackets removeBuffer(String channelId) {
        synchronized (channelBufferMap) {
            PendingPackets buffer = channelBufferMap.remove(channelId);
            if (buffer != null) {
                bufferedBytes -= buffer.bytes;
                sessionQuota.releaseBuffer(buffer.bytes);
            }
            return buffer;
        }
    }

    private void releaseBuffer(PendingPackets buffer) {
        if (buffer == null) {
            return;
        }
        ByteBuf data;
        while ((data = buffer.packets.poll()) != null) {
            data.release();
        }
    }

    private void sendMessage(Channel target, MessageType type, ByteBuf data, String channelId) {
        if (type == MessageType.TYPE_DATA && !target.isWritable()) {
            data.release();
            return;
        }
        Meta meta = new Meta(channelId, remoteConfig).setServerId(serverChannel.id().asLongText());
        target.writeAndFlush(new Message(type, meta, data));
    }

    private String toChannelId(InetSocketAddress address) {
        // 同一客户端的多个 UDP 监听端口共享控制通道的 CHANNELS 表；
        // 会话 ID 必须同时包含监听端口，否则同一发送者访问两个端口会互相覆盖。
        return remoteConfig.getRemotePort() + "|" + address.getAddress().getHostAddress() + ":" + address.getPort();
    }

    private static class PendingPackets {

        private final Queue<ByteBuf> packets = new ConcurrentLinkedQueue<>();

        private long bytes;
    }
}
