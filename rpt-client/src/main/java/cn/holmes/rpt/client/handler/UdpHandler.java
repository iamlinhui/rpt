package cn.holmes.rpt.client.handler;

import cn.holmes.rpt.base.config.RemoteConfig;
import cn.holmes.rpt.base.protocol.Message;
import cn.holmes.rpt.base.protocol.MessageType;
import cn.holmes.rpt.base.protocol.Meta;
import cn.holmes.rpt.base.utils.Constants.Client;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;

import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端UDP本地连接处理器
 */
public class UdpHandler extends SimpleChannelInboundHandler<DatagramPacket> {

    private final Channel control;
    private final Channel tunnel;
    private final Meta meta;

    public UdpHandler(Channel control, Channel tunnel, Meta meta) {
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
        RemoteConfig remoteConfig = meta.getRemoteConfig();
        // 设置UDP目标地址，用于DataExecutor将数据发送到本地服务
        ctx.channel().attr(Client.UDP_TARGET).set(new InetSocketAddress(remoteConfig.getLocalIp(), remoteConfig.getLocalPort()));
        tunnel.writeAndFlush(new Message(MessageType.TYPE_CONNECTED, meta, Unpooled.EMPTY_BUFFER));
        ctx.channel().config().setAutoRead(true);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) throws Exception {
        if (!tunnel.isActive()) {
            return;
        }
        tunnel.writeAndFlush(new Message(MessageType.TYPE_DATA, meta, packet.content().retainedDuplicate()));
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        Optional.ofNullable(control.attr(Client.CHANNELS).get()).ifPresent(channelMap -> channelMap.remove(meta.getChannelId()));
        Optional.ofNullable(tunnel.attr(Client.STREAM_SET).get()).ifPresent(streamSet -> streamSet.remove(meta.getChannelId()));
        if (tunnel.isActive()) {
            tunnel.writeAndFlush(new Message(MessageType.TYPE_DISCONNECTED, meta, Unpooled.EMPTY_BUFFER));
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.close();
    }
}
