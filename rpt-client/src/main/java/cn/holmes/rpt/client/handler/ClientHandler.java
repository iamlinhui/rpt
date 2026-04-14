package cn.holmes.rpt.client.handler;

import cn.holmes.rpt.base.executor.MessageExecutor;
import cn.holmes.rpt.base.executor.MessageExecutorFactory;
import cn.holmes.rpt.base.protocol.Message;
import cn.holmes.rpt.base.utils.Application;
import cn.holmes.rpt.base.utils.Config;
import cn.holmes.rpt.base.utils.Constants.Client;
import cn.holmes.rpt.client.cache.ProxyChannelCache;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.internal.EmptyArrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
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
        Channel localChannel = ctx.channel().attr(Client.LOCAL).get();
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
        Application<Bootstrap> application = ctx.channel().attr(Client.APPLICATION).getAndSet(null);
        if (Objects.nonNull(application)) {
            logger.info("客户端-服务端连接中断,{}:{}", Config.getClientConfig().getServerIp(), Config.getClientConfig().getServerPort());
            Optional.ofNullable(ctx.channel().attr(Client.CHANNELS).get()).ifPresent(this::clear);
            application.start(15);
            return;
        }
        logger.info("客户端-服务端代理连接中断");
        close(ctx.channel().attr(Client.LOCAL).getAndSet(null));
        ProxyChannelCache.delete(ctx.channel());
    }

    private void close(Channel localChannel) {
        if (Objects.nonNull(localChannel) && localChannel.isActive()) {
            localChannel.attr(Client.PROXY).set(null);
            // UDP DatagramChannel不接受原始byte[]写入，直接关闭
            InetSocketAddress udpTarget = localChannel.attr(Client.UDP_TARGET).getAndSet(null);
            if (udpTarget != null) {
                localChannel.close();
            } else {
                localChannel.writeAndFlush(EmptyArrays.EMPTY_BYTES).addListener(ChannelFutureListener.CLOSE);
            }
        }
    }

    private void clear(Map<String, Channel> channelMap) {
        channelMap.values().forEach(this::close);
        channelMap.clear();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.close();
    }
}
