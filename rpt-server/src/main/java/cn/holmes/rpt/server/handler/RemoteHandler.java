package cn.holmes.rpt.server.handler;

import cn.holmes.rpt.base.config.ProxyType;
import cn.holmes.rpt.base.config.RemoteConfig;
import cn.holmes.rpt.base.protocol.Message;
import cn.holmes.rpt.base.protocol.MessageType;
import cn.holmes.rpt.base.protocol.Meta;
import cn.holmes.rpt.base.utils.Constants;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.internal.EmptyArrays;

import java.util.Objects;
import java.util.Optional;

/**
 * 处理服务器接收到的外部请求
 */
public class RemoteHandler extends SimpleChannelInboundHandler<byte[]> {

    private final Channel channel;
    private final RemoteConfig remoteConfig;

    public RemoteHandler(Channel channel, RemoteConfig remoteConfig) {
        this.channel = channel;
        this.remoteConfig = remoteConfig;
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        Channel proxyChannel = ctx.channel().attr(Constants.PROXY).get();
        if (Objects.nonNull(proxyChannel)) {
            proxyChannel.config().setAutoRead(ctx.channel().isWritable());
        }
        super.channelWritabilityChanged(ctx);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (!Objects.equals(ProxyType.TCP, evt)) {
            ctx.fireUserEventTriggered(evt);
            return;
        }
        ctx.channel().config().setAutoRead(true);
    }

    /**
     * 连接初始化，建立连接
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        channel.attr(Constants.CHANNELS).get().put(ctx.channel().id().asLongText(), ctx.channel());
        ctx.channel().config().setAutoRead(false);
        send(channel, MessageType.TYPE_CONNECTED, EmptyArrays.EMPTY_BYTES, ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, byte[] bytes) throws Exception {
        // 从外部连接接收到的数据 转发到客户端
        Channel proxyChannel = ctx.channel().attr(Constants.PROXY).get();
        if (Objects.isNull(proxyChannel)) {
            ctx.close();
            return;
        }
        send(proxyChannel, MessageType.TYPE_DATA, bytes, ctx);
    }

    /**
     * 连接中断
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        Optional.ofNullable(channel.attr(Constants.CHANNELS).get()).ifPresent(channelMap -> channelMap.remove(ctx.channel().id().asLongText()));
        Channel proxyChannel = ctx.channel().attr(Constants.PROXY).getAndSet(null);
        if (Objects.nonNull(proxyChannel) && proxyChannel.isActive()) {
            proxyChannel.attr(Constants.LOCAL).set(null);
            proxyChannel.config().setAutoRead(true);
            send(proxyChannel, MessageType.TYPE_DISCONNECTED, EmptyArrays.EMPTY_BYTES, ctx);
        }
    }

    /**
     * 连接异常
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.close();
    }

    /**
     * 发送数据到内网客户端流程封装
     **/
    public void send(Channel complex, MessageType type, byte[] data, ChannelHandlerContext ctx) {
        Meta meta = new Meta(ctx.channel().id().asLongText(), remoteConfig).setServerId(channel.id().asLongText());
        Message message = new Message();
        message.setType(type);
        message.setMeta(meta);
        message.setData(data);
        complex.writeAndFlush(message);
    }
}
