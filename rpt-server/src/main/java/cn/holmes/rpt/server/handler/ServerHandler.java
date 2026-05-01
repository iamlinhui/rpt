package cn.holmes.rpt.server.handler;

import cn.holmes.rpt.base.config.ProxyType;
import cn.holmes.rpt.base.executor.MessageExecutor;
import cn.holmes.rpt.base.executor.MessageExecutorFactory;
import cn.holmes.rpt.base.protocol.Message;
import cn.holmes.rpt.base.utils.Constants.Server;
import cn.holmes.rpt.server.cache.ServerChannelCache;
import cn.holmes.rpt.server.cache.TrafficStatsCache;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 处理服务器接收到的客户端连接
 */
public class ServerHandler extends SimpleChannelInboundHandler<Message> {

    private static final Logger logger = LoggerFactory.getLogger(ServerHandler.class);

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        Channel localChannel = ctx.channel().attr(Server.LOCAL).get();
        if (Objects.nonNull(localChannel)) {
            localChannel.config().setAutoRead(ctx.channel().isWritable());
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
     * 连接中断
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        String clientKey = ctx.channel().attr(Server.CLIENT_KEY).getAndSet(null);
        // 代理连接/未知连接
        if (Objects.isNull(clientKey)) {
            logger.info("服务端-客户端代理连接中断");
            Channel localChannel = ctx.channel().attr(Server.LOCAL).getAndSet(null);
            if (Objects.nonNull(localChannel) && localChannel.isActive()) {
                ProxyType proxyType = localChannel.attr(Server.PROXY_TYPE).get();
                localChannel.attr(Server.PROXY).set(null);
                if (!Objects.equals(proxyType, ProxyType.UDP)) {
                    localChannel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
                }
            }
            return;
        }
        logger.info("服务端-客户端连接中断,{}", clientKey);
        ServerChannelCache.getServerChannelMap().remove(ctx.channel().id().asLongText());
        TrafficStatsCache.remove(ctx.channel().id().asLongText());
        Optional.ofNullable(ctx.channel().attr(Server.CHANNELS).getAndSet(null)).ifPresent(this::clear);
        Optional.ofNullable(ctx.channel().attr(Server.TCP_PORT_CHANNEL_FUTURE).getAndSet(null)).ifPresent(this::close);
        Optional.ofNullable(ctx.channel().attr(Server.UDP_PORT_CHANNEL_FUTURE).getAndSet(null)).ifPresent(this::close);
        Optional.ofNullable(ctx.channel().attr(Server.DOMAIN).getAndSet(null)).ifPresent(ServerChannelCache::remove);
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
