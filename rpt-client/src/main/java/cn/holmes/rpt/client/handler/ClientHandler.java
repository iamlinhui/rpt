package cn.holmes.rpt.client.handler;

import cn.holmes.rpt.base.executor.MessageExecutor;
import cn.holmes.rpt.base.executor.MessageExecutorFactory;
import cn.holmes.rpt.base.protocol.Message;
import cn.holmes.rpt.base.utils.Application;
import cn.holmes.rpt.base.utils.Config;
import cn.holmes.rpt.base.utils.Constants;
import cn.holmes.rpt.client.cache.ProxyChannelCache;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.internal.EmptyArrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 服务器连接处理器
 */
public class ClientHandler extends SimpleChannelInboundHandler<Message> {

    private static final Logger logger = LoggerFactory.getLogger(ClientHandler.class);

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        Channel localChannel = ctx.channel().attr(Constants.LOCAL).get();
        if (Objects.nonNull(localChannel)) {
            localChannel.config().setAutoRead(ctx.channel().isWritable());
        }
        super.channelWritabilityChanged(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext context, Message message) throws Exception {
        MessageExecutor messageExecutor = MessageExecutorFactory.getMessageExecutor(message.getType());
        if (Objects.nonNull(messageExecutor)) {
            messageExecutor.execute(context, message);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        Application<Bootstrap> application = ctx.channel().attr(Constants.Client.APPLICATION).getAndSet(null);
        if (Objects.nonNull(application)) {
            logger.info("客户端-服务端连接中断,{}:{}", Config.getClientConfig().getServerIp(), Config.getClientConfig().getServerPort());
            Optional.ofNullable(ctx.channel().attr(Constants.CHANNELS).get()).ifPresent(this::clear);
            application.start(15);
            return;
        }
        logger.info("客户端-服务端代理连接中断");
        Channel localChannel = ctx.channel().attr(Constants.LOCAL).getAndSet(null);
        if (Objects.nonNull(localChannel) && localChannel.isActive()) {
            localChannel.attr(Constants.PROXY).set(null);
            localChannel.writeAndFlush(EmptyArrays.EMPTY_BYTES).addListener(ChannelFutureListener.CLOSE);
        }
        ProxyChannelCache.delete(ctx.channel());
    }

    private void clear(Map<String, Channel> channelMap) {
        for (Channel localChannel : channelMap.values()) {
            localChannel.attr(Constants.PROXY).set(null);
            localChannel.writeAndFlush(EmptyArrays.EMPTY_BYTES).addListener(ChannelFutureListener.CLOSE);
        }
        channelMap.clear();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.close();
    }
}
