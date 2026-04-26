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
import java.util.Objects;
import java.util.Optional;

/**
 * 客户端UDP本地连接处理器
 * 接收来自本地UDP服务的响应数据并转发到服务端
 * <p>
 * 注意：channel注册和TYPE_CONNECTED发送由ConnectedExecutor的bind listener完成，
 * 因为channelActive触发时PROXY属性尚未设置。
 */
public class UdpHandler extends SimpleChannelInboundHandler<DatagramPacket> {

    private final Channel serverChannel;
    private final Meta meta;

    public UdpHandler(Channel serverChannel, Meta meta) {
        this.serverChannel = serverChannel;
        this.meta = meta;
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        Channel proxyChannel = ctx.channel().attr(Client.PROXY).get();
        if (Objects.nonNull(proxyChannel)) {
            proxyChannel.config().setAutoRead(ctx.channel().isWritable());
        }
        super.channelWritabilityChanged(ctx);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        ctx.channel().config().setAutoRead(false);
        // 注册到channel map并发送TYPE_CONNECTED ACK到服务端
        serverChannel.attr(Client.CHANNELS).get().put(meta.getChannelId(), ctx.channel());
        Channel proxyChannel = ctx.channel().attr(Client.PROXY).get();
        RemoteConfig remoteConfig = meta.getRemoteConfig();
        // 设置UDP目标地址，用于DataExecutor将数据发送到本地服务
        ctx.channel().attr(Client.UDP_TARGET).set(new InetSocketAddress(remoteConfig.getLocalIp(), remoteConfig.getLocalPort()));
        proxyChannel.writeAndFlush(new Message(MessageType.TYPE_CONNECTED, meta, Unpooled.EMPTY_BUFFER));
        ctx.channel().config().setAutoRead(true);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) throws Exception {
        Channel proxyChannel = ctx.channel().attr(Client.PROXY).get();
        if (Objects.isNull(proxyChannel)) {
            return;
        }
        proxyChannel.writeAndFlush(new Message(MessageType.TYPE_DATA, meta, packet.content().retainedDuplicate()));
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        Optional.ofNullable(serverChannel.attr(Client.CHANNELS).get()).ifPresent(channelMap -> channelMap.remove(meta.getChannelId()));
        Channel proxyChannel = ctx.channel().attr(Client.PROXY).get();
        if (Objects.nonNull(proxyChannel) && proxyChannel.isActive()) {
            proxyChannel.writeAndFlush(new Message(MessageType.TYPE_DISCONNECTED, meta, Unpooled.EMPTY_BUFFER));
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.close();
    }
}
