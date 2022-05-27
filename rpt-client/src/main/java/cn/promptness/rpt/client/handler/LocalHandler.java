package cn.promptness.rpt.client.handler;

import cn.promptness.rpt.base.protocol.Message;
import cn.promptness.rpt.base.protocol.MessageType;
import cn.promptness.rpt.base.protocol.Meta;
import cn.promptness.rpt.base.utils.Constants;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.internal.EmptyArrays;

import java.util.Objects;
import java.util.Optional;

/**
 * 实际内网连接处理器
 */
public class LocalHandler extends SimpleChannelInboundHandler<byte[]> {

    private final Channel channel;
    private final Meta meta;

    public LocalHandler(Channel channel, Meta meta) {
        this.channel = channel;
        this.meta = meta;
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
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        ctx.channel().config().setAutoRead(false);
        Channel proxyChannel = ctx.channel().attr(Constants.PROXY).get();
        channel.attr(Constants.CHANNELS).get().put(meta.getChannelId(), ctx.channel());
        send(proxyChannel, MessageType.TYPE_CONNECTED, EmptyArrays.EMPTY_BYTES);
        ctx.channel().config().setAutoRead(true);
    }


    @Override
    protected void channelRead0(ChannelHandlerContext ctx, byte[] bytes) throws Exception {
        Channel proxyChannel = ctx.channel().attr(Constants.PROXY).get();
        if (Objects.isNull(proxyChannel)) {
            ctx.close();
            return;
        }
        send(proxyChannel, MessageType.TYPE_DATA, bytes);
    }


    /**
     * 连接断开
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        Optional.ofNullable(channel.attr(Constants.CHANNELS).get()).ifPresent(channelMap -> channelMap.remove(meta.getChannelId()));
        Channel proxyChannel = ctx.channel().attr(Constants.PROXY).get();
        if (Objects.nonNull(proxyChannel) && proxyChannel.isActive()) {
            send(proxyChannel, MessageType.TYPE_DISCONNECTED, EmptyArrays.EMPTY_BYTES);
        }
    }

    /**
     * 连接异常
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.close();
    }

    private void send(Channel proxyChannel, MessageType type, byte[] data) {
        proxyChannel.writeAndFlush(new Message(type, meta, data));
    }
}
