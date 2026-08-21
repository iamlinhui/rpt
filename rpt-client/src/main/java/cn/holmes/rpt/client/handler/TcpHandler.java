package cn.holmes.rpt.client.handler;

import cn.holmes.rpt.base.protocol.Message;
import cn.holmes.rpt.base.protocol.MessageType;
import cn.holmes.rpt.base.protocol.Meta;
import cn.holmes.rpt.base.utils.Constants.Client;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端TCP本地连接处理器
 */
public class TcpHandler extends SimpleChannelInboundHandler<ByteBuf> {

    private final Channel control;
    private final Channel tunnel;
    private final Meta meta;

    public TcpHandler(Channel control, Channel tunnel, Meta meta) {
        this.control = control;
        this.tunnel = tunnel;
        this.meta = meta;
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        if (tunnel.isActive()) {
            tunnel.config().setAutoRead(ctx.channel().isWritable());
        }
        super.channelWritabilityChanged(ctx);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        ctx.channel().config().setAutoRead(false);
        ctx.channel().attr(Client.TUNNEL).set(tunnel);
        tunnel.attr(Client.STREAM_SET).setIfAbsent(ConcurrentHashMap.newKeySet());
        Set<String> streamSet = tunnel.attr(Client.STREAM_SET).get();
        streamSet.add(meta.getChannelId());
        control.attr(Client.CHANNELS).get().put(meta.getChannelId(), ctx.channel());
        send(tunnel, MessageType.TYPE_CONNECTED, Unpooled.EMPTY_BUFFER);
        ctx.channel().config().setAutoRead(true);
    }


    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf buf) throws Exception {
        if (!tunnel.isActive()) {
            ctx.close();
            return;
        }
        send(tunnel, MessageType.TYPE_DATA, buf.retain());
    }


    /**
     * 连接断开
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        Optional.ofNullable(control.attr(Client.CHANNELS).get()).ifPresent(channelMap -> channelMap.remove(meta.getChannelId()));
        Optional.ofNullable(tunnel.attr(Client.STREAM_SET).get()).ifPresent(streamSet -> streamSet.remove(meta.getChannelId()));
        if (tunnel.isActive()) {
            send(tunnel, MessageType.TYPE_DISCONNECTED, Unpooled.EMPTY_BUFFER);
        }
    }

    /**
     * 连接异常
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.close();
    }

    private void send(Channel tunnel, MessageType type, ByteBuf data) {
        tunnel.writeAndFlush(new Message(type, meta, data));
    }
}
