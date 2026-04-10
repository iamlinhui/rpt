package cn.holmes.rpt.client.handler;

import cn.holmes.rpt.base.protocol.Message;
import cn.holmes.rpt.base.protocol.MessageType;
import cn.holmes.rpt.base.protocol.Meta;
import cn.holmes.rpt.base.utils.Constants;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.internal.EmptyArrays;

import java.util.Objects;
import java.util.Optional;

/**
 * 客户端UDP本地连接处理器
 * 接收来自本地UDP服务的响应数据并转发到服务端
 * <p>
 * 注意：channel注册和TYPE_CONNECTED发送由ConnectedExecutor的bind listener完成，
 * 因为channelActive触发时PROXY属性尚未设置。
 */
public class UdpLocalHandler extends SimpleChannelInboundHandler<DatagramPacket> {

    private final Channel channel;
    private final Meta meta;

    public UdpLocalHandler(Channel channel, Meta meta) {
        this.channel = channel;
        this.meta = meta;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) throws Exception {
        Channel proxyChannel = ctx.channel().attr(Constants.PROXY).get();
        if (Objects.isNull(proxyChannel)) {
            return;
        }
        ByteBuf content = packet.content();
        byte[] data = new byte[content.readableBytes()];
        content.readBytes(data);
        proxyChannel.writeAndFlush(new Message(MessageType.TYPE_DATA, meta, data));
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        Optional.ofNullable(channel.attr(Constants.CHANNELS).get()).ifPresent(channelMap -> channelMap.remove(meta.getChannelId()));
        Channel proxyChannel = ctx.channel().attr(Constants.PROXY).get();
        if (Objects.nonNull(proxyChannel) && proxyChannel.isActive()) {
            proxyChannel.writeAndFlush(new Message(MessageType.TYPE_DISCONNECTED, meta, EmptyArrays.EMPTY_BYTES));
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.close();
    }
}
