package cn.holmes.rpt.server.handler;

import cn.holmes.rpt.base.config.ProxyType;
import cn.holmes.rpt.base.executor.MessageExecutor;
import cn.holmes.rpt.base.executor.MessageExecutorFactory;
import cn.holmes.rpt.base.protocol.Message;
import cn.holmes.rpt.base.protocol.MessageType;
import cn.holmes.rpt.base.utils.Constants.Server;
import cn.holmes.rpt.base.utils.FireEvent;
import cn.holmes.rpt.server.cache.ServerChannelCache;
import cn.holmes.rpt.server.cache.TrafficStatsCache;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 处理服务器接收到的客户端连接（控制通道 + 共享数据隧道）
 */
public class ServerHandler extends SimpleChannelInboundHandler<Message> {

    private static final Logger logger = LoggerFactory.getLogger(ServerHandler.class);

    /**
     * 隧道级背压：隧道写缓冲区满时暂停其上所有通道的外部连接读取。
     * <p>
     * 恢复时跳过被通道级TYPE_PAUSE暂停的通道，否则会顶掉更细粒度的暂停。
     */
    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        Set<String> streamSet = ctx.channel().attr(Server.STREAM_SET).get();
        if (Objects.nonNull(streamSet)) {
            Channel serverChannel = ServerChannelCache.getServerChannelMap().get(ctx.channel().attr(Server.SERVER_ID).get());
            Map<String, Channel> channelMap = Objects.nonNull(serverChannel) ? serverChannel.attr(Server.CHANNELS).get() : null;
            if (Objects.nonNull(channelMap)) {
                boolean writable = ctx.channel().isWritable();
                for (String channelId : streamSet) {
                    Channel localChannel = channelMap.get(channelId);
                    if (Objects.isNull(localChannel)) {
                        continue;
                    }
                    if (writable && Boolean.TRUE.equals(localChannel.attr(Server.PAUSED).get())) {
                        continue;
                    }
                    localChannel.config().setAutoRead(writable);
                }
            }
        }
        super.channelWritabilityChanged(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext context, Message message) throws Exception {
        MessageExecutor messageExecutor = MessageExecutorFactory.getMessageExecutor(message.getType());
        if (Objects.isNull(messageExecutor)) {
            return;
        }
        messageExecutor.execute(context, message);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.info("服务端-客户端连接异常,{}", cause.getMessage());
        ctx.close();
    }

    /**
     * 连接中断：区分控制通道与共享数据隧道
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        String clientKey = ctx.channel().attr(Server.CLIENT_KEY).getAndSet(null);
        if (Objects.isNull(clientKey)) {
            // 共享数据隧道断开：关闭其上承载的所有会话
            closeTunnelStreams(ctx.channel());
            return;
        }
        logger.info("服务端-客户端连接中断,{}", clientKey);
        String serverId = ctx.channel().id().asLongText();
        ServerChannelCache.getServerChannelMap().remove(serverId);
        TrafficStatsCache.remove(serverId);
        Optional.ofNullable(ctx.channel().attr(Server.TUNNEL_SET).getAndSet(null)).ifPresent(tunnelSet -> tunnelSet.forEach(Channel::close));
        Optional.ofNullable(ctx.channel().attr(Server.CHANNELS).getAndSet(null)).ifPresent(this::clear);
        Optional.ofNullable(ctx.channel().attr(Server.TCP_PORT_CHANNEL_FUTURE).getAndSet(null)).ifPresent(this::close);
        Optional.ofNullable(ctx.channel().attr(Server.UDP_PORT_CHANNEL_FUTURE).getAndSet(null)).ifPresent(this::close);
        Optional.ofNullable(ctx.channel().attr(Server.DOMAIN).getAndSet(null)).ifPresent(ServerChannelCache::remove);
    }

    /**
     * 隧道断开：按反向索引关闭其上承载的外部连接并递减计数
     */
    private void closeTunnelStreams(Channel tunnel) {
        String serverId = tunnel.attr(Server.SERVER_ID).getAndSet(null);
        Set<String> streamSet = tunnel.attr(Server.STREAM_SET).getAndSet(null);
        Channel serverChannel = Objects.nonNull(serverId) ? ServerChannelCache.getServerChannelMap().get(serverId) : null;
        Optional.ofNullable(serverChannel).map(ch -> ch.attr(Server.TUNNEL_SET).get()).ifPresent(tunnelSet -> tunnelSet.remove(tunnel));
        Map<String, Channel> channelMap = Objects.nonNull(serverChannel) ? serverChannel.attr(Server.CHANNELS).get() : null;
        if (Objects.isNull(streamSet)) {
            return;
        }
        logger.info("服务端-数据隧道中断,serverId:{},承载会话数:{}", serverId, streamSet.size());
        for (String channelId : streamSet) {
            if (!streamSet.remove(channelId)) {
                continue;
            }
            if (Objects.nonNull(channelMap)) {
                Channel localChannel = channelMap.remove(channelId);
                if (Objects.nonNull(localChannel)) {
                    if (Objects.equals(localChannel.attr(Server.PROXY_TYPE).get(), ProxyType.UDP)) {
                        // UDP本地通道按端口共享，通知UdpHandler清理该会话状态
                        localChannel.pipeline().fireUserEventTriggered(new FireEvent(channelId, tunnel, MessageType.TYPE_DISCONNECTED));
                    } else {
                        localChannel.close();
                    }
                }
            }
            if (Objects.nonNull(serverId)) {
                TrafficStatsCache.decrementProxyChannels(serverId);
            }
        }
    }

    private void clear(Map<String, Channel> channelMap) {
        for (Channel localChannel : channelMap.values()) {
            ProxyType proxyType = localChannel.attr(Server.PROXY_TYPE).get();
            // UDP DatagramChannel是共享的，由PORT_CHANNEL_FUTURE.close()统一关闭，此处跳过
            if (Objects.equals(proxyType, ProxyType.UDP)) {
                continue;
            }
            localChannel.close();
        }
    }

    private void close(Map<Integer, ChannelFuture> channelFutureMap) {
        for (ChannelFuture future : channelFutureMap.values()) {
            future.channel().close();
        }
    }

}
