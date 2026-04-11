package cn.holmes.rpt.server.handler;

import cn.holmes.rpt.base.config.RemoteConfig;
import cn.holmes.rpt.base.protocol.Message;
import cn.holmes.rpt.base.protocol.MessageType;
import cn.holmes.rpt.base.protocol.Meta;
import cn.holmes.rpt.base.utils.Constants;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.*;

/**
 * 处理服务器接收到的外部UDP请求
 * <p>
 * UDP是无连接协议，每个发送者IP:Port作为一个虚拟会话。
 * 使用proxyMap维护每个会话对应的代理通道，bufferMap缓冲代理建立前的数据。
 */
public class UdpRemoteHandler extends SimpleChannelInboundHandler<DatagramPacket> {

    private static final Logger logger = LoggerFactory.getLogger(UdpRemoteHandler.class);

    private static final long SESSION_TIMEOUT_MS = 60_000;

    /**
     * 每个会话代理建立前最多缓冲的数据包数量，防止OOM
     */
    private static final int MAX_BUFFER_SIZE = 64;

    private final Channel channel;
    private final RemoteConfig remoteConfig;

    /**
     * channelId → 发送者地址
     */
    private final Map<String, InetSocketAddress> senderMap = new ConcurrentHashMap<>();

    /**
     * channelId → 对应的代理通道（代理建立后设置）
     */
    private final Map<String, Channel> proxyMap = new ConcurrentHashMap<>();

    /**
     * channelId → 代理建立前缓冲的数据
     */
    private final Map<String, Queue<byte[]>> bufferMap = new ConcurrentHashMap<>();

    /**
     * channelId → 最后活跃时间
     */
    private final Map<String, Long> lastActiveMap = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "udp-session-cleaner");
        t.setDaemon(true);
        return t;
    });

    public UdpRemoteHandler(Channel channel, RemoteConfig remoteConfig) {
        this.channel = channel;
        this.remoteConfig = remoteConfig;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        scheduler.scheduleAtFixedRate(() -> cleanIdleSessions(ctx), SESSION_TIMEOUT_MS, SESSION_TIMEOUT_MS / 2, TimeUnit.MILLISECONDS);
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        scheduler.shutdownNow();
        // 关闭所有代理通道，使客户端能感知断开并回收资源
        for (Channel proxyChannel : proxyMap.values()) {
            if (proxyChannel.isActive()) {
                proxyChannel.close();
            }
        }
        senderMap.clear();
        proxyMap.clear();
        bufferMap.clear();
        lastActiveMap.clear();
        super.channelInactive(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) throws Exception {
        InetSocketAddress sender = packet.sender();
        String channelId = toChannelId(sender);

        lastActiveMap.put(channelId, System.currentTimeMillis());

        ByteBuf content = packet.content();
        byte[] data = new byte[content.readableBytes()];
        content.readBytes(data);

        if (!senderMap.containsKey(channelId)) {
            // 新的发送者，创建虚拟会话
            senderMap.put(channelId, sender);
            Map<String, Channel> channelMap = channel.attr(Constants.CHANNELS).get();
            if (channelMap == null) {
                return;
            }
            // 将DatagramChannel存入CHANNELS map，用于ConnectedExecutor查找
            channelMap.put(channelId, ctx.channel());
            // 缓冲数据，等待代理通道建立
            addToBuffer(channelId, data);
            // 通知客户端建立连接
            sendMessage(channel, MessageType.TYPE_CONNECTED, new byte[0], channelId);
            return;
        }

        // 已有会话，检查代理通道是否已建立
        Channel proxyChannel = proxyMap.get(channelId);
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
    public void bindProxy(String channelId, Channel proxyChannel) {
        proxyMap.put(channelId, proxyChannel);
        // 刷新缓冲的数据
        Queue<byte[]> buffer = bufferMap.remove(channelId);
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
    public void removeSession(String channelId) {
        senderMap.remove(channelId);
        proxyMap.remove(channelId);
        bufferMap.remove(channelId);
        lastActiveMap.remove(channelId);
    }

    public Map<String, InetSocketAddress> getSenderMap() {
        return senderMap;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("UDP remote handler error", cause);
    }

    private void cleanIdleSessions(ChannelHandlerContext ctx) {
        long now = System.currentTimeMillis();
        lastActiveMap.forEach((channelId, lastActive) -> {
            if (now - lastActive > SESSION_TIMEOUT_MS) {
                logger.info("UDP session expired: {}", channelId);
                Channel proxyChannel = proxyMap.get(channelId);
                removeSession(channelId);
                // 从CHANNELS map移除
                Map<String, Channel> channelMap = channel.attr(Constants.CHANNELS).get();
                if (channelMap != null) {
                    channelMap.remove(channelId);
                }
                // 通过代理通道通知客户端断开，客户端会关闭本地通道并将proxyChannel归还连接池复用
                if (proxyChannel != null && proxyChannel.isActive()) {
                    proxyChannel.attr(Constants.LOCAL).set(null);
                    proxyChannel.attr(Constants.UDP_SENDER).set(null);
                    sendMessage(proxyChannel, MessageType.TYPE_DISCONNECTED, new byte[0], channelId);
                }
            }
        });
    }

    private void addToBuffer(String channelId, byte[] data) {
        Queue<byte[]> queue = bufferMap.computeIfAbsent(channelId, k -> new ConcurrentLinkedQueue<>());
        if (queue.size() < MAX_BUFFER_SIZE) {
            queue.add(data);
        }
    }

    private void sendMessage(Channel target, MessageType type, byte[] data, String channelId) {
        Meta meta = new Meta(channelId, remoteConfig).setServerId(channel.id().asLongText());
        Message message = new Message();
        message.setType(type);
        message.setMeta(meta);
        message.setData(data);
        target.writeAndFlush(message);
    }

    static String toChannelId(InetSocketAddress address) {
        return "udp-" + address.getAddress().getHostAddress() + ":" + address.getPort();
    }
}
