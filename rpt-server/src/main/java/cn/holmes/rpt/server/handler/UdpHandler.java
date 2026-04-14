package cn.holmes.rpt.server.handler;

import cn.holmes.rpt.base.config.ProxyType;
import cn.holmes.rpt.base.config.RemoteConfig;
import cn.holmes.rpt.base.protocol.Message;
import cn.holmes.rpt.base.protocol.MessageType;
import cn.holmes.rpt.base.protocol.Meta;
import cn.holmes.rpt.base.utils.Config;
import cn.holmes.rpt.base.utils.Constants.Server;
import cn.holmes.rpt.base.utils.FireEvent;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.ipfilter.IpFilterRule;
import io.netty.util.internal.EmptyArrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.*;

/**
 * 处理服务器接收到的外部UDP请求
 * <p>
 * UDP是无连接协议，每个发送者IP:Port作为一个虚拟会话。
 * 使用proxyMap维护每个会话对应的代理通道，bufferMap缓冲代理建立前的数据。
 */
public class UdpHandler extends SimpleChannelInboundHandler<DatagramPacket> {

    private static final Logger logger = LoggerFactory.getLogger(UdpHandler.class);

    private static final IpFilterRule IP_FILTER_RULE_HANDLER = new IpFilterRuleHandler();

    private static final long SESSION_TIMEOUT_MS = 60_000;

    private static final int MAX_BUFFER_SIZE = 64;

    private final Channel serverChannel;

    private final RemoteConfig remoteConfig;

    /**
     * channelId → 发送者地址
     */
    private final Map<String, InetSocketAddress> senderAddressMap = new ConcurrentHashMap<>();

    /**
     * channelId → 对应的代理通道（代理建立后设置）
     */
    private final Map<String, Channel> proxyChannelMap = new ConcurrentHashMap<>();

    /**
     * channelId → 代理建立前缓冲的数据
     */
    private final Map<String, Queue<byte[]>> channelBufferMap = new ConcurrentHashMap<>();

    /**
     * channelId → 最后活跃时间
     */
    private final Map<String, Long> lastActiveMap = new ConcurrentHashMap<>();

    private final ScheduledExecutorService checkScheduler = Executors.newSingleThreadScheduledExecutor();

    public UdpHandler(Channel serverChannel, RemoteConfig remoteConfig) {
        this.serverChannel = serverChannel;
        this.remoteConfig = remoteConfig;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        ctx.channel().attr(Server.PROXY_TYPE).set(ProxyType.UDP);
        checkScheduler.scheduleAtFixedRate(this::cleanIdleSessions, SESSION_TIMEOUT_MS, SESSION_TIMEOUT_MS / 2, TimeUnit.MILLISECONDS);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        boolean isEvent = evt instanceof FireEvent;
        if (!isEvent) {
            ctx.fireUserEventTriggered(evt);
            return;
        }
        FireEvent fireEvent = (FireEvent) evt;
        MessageType messageType = fireEvent.getMessageType();
        if (messageType == MessageType.TYPE_CONNECTED) {
            // 代理通道已建立，绑定channelId和代理通道
            bindProxy(fireEvent.getChannelId(), fireEvent.getProxyChannel());
        }
        if (messageType == MessageType.TYPE_DISCONNECTED) {
            // 代理通道断开，移除会话
            removeSession(fireEvent.getChannelId());
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        checkScheduler.shutdownNow();
        // 关闭所有代理通道，使客户端能感知断开并回收资源
        for (String channelId : proxyChannelMap.keySet()) {
            removeSession(channelId);
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) throws Exception {
        InetSocketAddress sender = packet.sender();
        if (Config.getServerConfig().ipFilter() && IP_FILTER_RULE_HANDLER.matches(sender)) {
            logger.info("UDP remote handler rejected sender: {}", sender);
            return;
        }
        String channelId = toChannelId(sender);

        lastActiveMap.put(channelId, System.currentTimeMillis());

        ByteBuf content = packet.content();
        byte[] data = new byte[content.readableBytes()];
        content.readBytes(data);

        if (!senderAddressMap.containsKey(channelId)) {
            // 新的发送者，创建虚拟会话
            senderAddressMap.put(channelId, sender);
            Map<String, Channel> channelMap = serverChannel.attr(Server.CHANNELS).get();
            if (channelMap == null) {
                return;
            }
            // 将DatagramChannel存入CHANNELS map，用于ConnectedExecutor查找
            channelMap.put(channelId, ctx.channel());
            // 缓冲数据，等待代理通道建立
            addToBuffer(channelId, data);
            // 通知客户端建立连接
            sendMessage(serverChannel, MessageType.TYPE_CONNECTED, new byte[0], channelId);
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
     * 绑定代理通道并刷新缓冲数据。
     * 由服务端ConnectedExecutor在代理通道建立后调用。
     */
    private void bindProxy(String channelId, Channel proxyChannel) {
        InetSocketAddress address = senderAddressMap.get(channelId);
        if (address == null) {
            return;
        }
        proxyChannel.attr(Server.UDP_SENDER).set(address);
        proxyChannelMap.put(channelId, proxyChannel);
        // 刷新缓冲的数据
        Queue<byte[]> buffer = channelBufferMap.remove(channelId);
        if (buffer != null) {
            byte[] data;
            while ((data = buffer.poll()) != null) {
                sendMessage(proxyChannel, MessageType.TYPE_DATA, data, channelId);
            }
        }
    }

    /**
     * 移除会话
     */
    private void removeSession(String channelId) {
        Optional.ofNullable(serverChannel.attr(Server.CHANNELS).get()).ifPresent(channelMap -> channelMap.remove(channelId));
        senderAddressMap.remove(channelId);
        channelBufferMap.remove(channelId);
        lastActiveMap.remove(channelId);
        Channel proxyChannel = proxyChannelMap.remove(channelId);
        // 通过代理通道通知客户端断开，客户端会关闭本地通道并将proxyChannel归还连接池复用
        if (proxyChannel != null && proxyChannel.isActive()) {
            proxyChannel.attr(Server.LOCAL).set(null);
            proxyChannel.attr(Server.UDP_SENDER).set(null);
            sendMessage(proxyChannel, MessageType.TYPE_DISCONNECTED, EmptyArrays.EMPTY_BYTES, channelId);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("UDP remote handler error", cause);
    }

    private void cleanIdleSessions() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Long> entry : lastActiveMap.entrySet()) {
            String channelId = entry.getKey();
            long lastActive = entry.getValue();
            if (now - lastActive > SESSION_TIMEOUT_MS) {
                logger.info("UDP session expired: {}", channelId);
                removeSession(channelId);
            }
        }
    }

    private void addToBuffer(String channelId, byte[] data) {
        Queue<byte[]> queue = channelBufferMap.computeIfAbsent(channelId, k -> new ConcurrentLinkedQueue<>());
        if (queue.size() < MAX_BUFFER_SIZE) {
            queue.add(data);
        }
    }

    private void sendMessage(Channel target, MessageType type, byte[] data, String channelId) {
        Meta meta = new Meta(channelId, remoteConfig).setServerId(serverChannel.id().asLongText());
        Message message = new Message();
        message.setType(type);
        message.setMeta(meta);
        message.setData(data);
        target.writeAndFlush(message);
    }

    private String toChannelId(InetSocketAddress address) {
        return String.format("%s:%s", address.getAddress().getHostAddress(), address.getPort());
    }
}
