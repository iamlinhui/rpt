package cn.holmes.rpt.client.handler;

import cn.holmes.rpt.base.executor.MessageExecutor;
import cn.holmes.rpt.base.executor.MessageExecutorFactory;
import cn.holmes.rpt.base.protocol.Message;
import cn.holmes.rpt.base.utils.Application;
import cn.holmes.rpt.base.utils.Config;
import cn.holmes.rpt.base.utils.Constants.Client;
import cn.holmes.rpt.client.cache.TunnelPool;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 服务器连接处理器：控制通道负责注册/重连，共享数据隧道承载多路复用会话
 */
public class ClientHandler extends SimpleChannelInboundHandler<Message> {

    private static final Logger logger = LoggerFactory.getLogger(ClientHandler.class);

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        Set<String> streamSet = ctx.channel().attr(Client.STREAM_SET).get();
        Channel control = ctx.channel().attr(Client.CONTROL).get();
        Map<String, Channel> channelMap = Objects.nonNull(control) ? control.attr(Client.CHANNELS).get() : null;
        if (Objects.nonNull(streamSet) && Objects.nonNull(channelMap)) {
            boolean writable = ctx.channel().isWritable();
            for (String channelId : streamSet) {
                Channel localChannel = channelMap.get(channelId);
                if (Objects.nonNull(localChannel)) {
                    localChannel.config().setAutoRead(writable);
                }
            }
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
            // 控制通道中断：关闭所有隧道与本地连接，整体重连
            logger.info("客户端-服务端连接中断,{}:{}", Config.getClientConfig().getServerIp(), Config.getClientConfig().getServerPort());
            TunnelPool.getInstance().closeAll();
            Optional.ofNullable(ctx.channel().attr(Client.CHANNELS).get()).ifPresent(this::clear);
            application.start(3);
            return;
        }
        // 共享数据隧道中断：关闭其上承载的会话本地连接，由隧道池自动补连
        logger.info("客户端-共享数据隧道中断,当前隧道数:{}", TunnelPool.getInstance().size());
        Channel controlChannel = ctx.channel().attr(Client.CONTROL).get();
        Map<String, Channel> channelMap = Objects.nonNull(controlChannel) ? controlChannel.attr(Client.CHANNELS).get() : null;
        Set<String> streamSet = ctx.channel().attr(Client.STREAM_SET).getAndSet(null);
        if (Objects.nonNull(streamSet) && Objects.nonNull(channelMap)) {
            for (String channelId : streamSet) {
                close(channelMap.remove(channelId));
            }
        }
        TunnelPool.getInstance().onTunnelClosed(ctx.channel());
    }

    private void close(Channel localChannel) {
        if (Objects.nonNull(localChannel) && localChannel.isActive()) {
            localChannel.attr(Client.TUNNEL).set(null);
            // UDP DatagramChannel不接受原始byte[]写入，直接关闭
            InetSocketAddress udpTarget = localChannel.attr(Client.UDP_TARGET).getAndSet(null);
            if (udpTarget != null) {
                localChannel.close();
            } else {
                localChannel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
            }
        }
    }

    private void clear(Map<String, Channel> channelMap) {
        channelMap.values().forEach(this::close);
        channelMap.clear();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.info("客户端-服务端连接异常,{}", cause.getMessage());
        ctx.close();
    }
}
