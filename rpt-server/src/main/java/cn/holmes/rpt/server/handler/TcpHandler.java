package cn.holmes.rpt.server.handler;

import cn.holmes.rpt.base.config.ProxyType;
import cn.holmes.rpt.base.config.RemoteConfig;
import cn.holmes.rpt.base.protocol.Message;
import cn.holmes.rpt.base.protocol.MessageType;
import cn.holmes.rpt.base.protocol.Meta;
import cn.holmes.rpt.base.utils.Constants.Server;
import cn.holmes.rpt.server.cache.TrafficStatsCache;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import java.util.Objects;
import java.util.Optional;

/**
 * 处理服务器接收到的外部请求
 */
public class TcpHandler extends SimpleChannelInboundHandler<ByteBuf> {

    private final Channel serverChannel;
    private final RemoteConfig remoteConfig;

    public TcpHandler(Channel serverChannel, RemoteConfig remoteConfig) {
        this.serverChannel = serverChannel;
        this.remoteConfig = remoteConfig;
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        Channel proxyChannel = ctx.channel().attr(Server.PROXY).get();
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
        ctx.channel().attr(Server.PROXY_TYPE).set(ProxyType.TCP);
        serverChannel.attr(Server.CHANNELS).get().put(ctx.channel().id().asLongText(), ctx.channel());
        ctx.channel().config().setAutoRead(false);
        send(serverChannel, MessageType.TYPE_CONNECTED, Unpooled.EMPTY_BUFFER, ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf buf) throws Exception {
        // 从外部连接接收到的数据 转发到客户端
        Channel proxyChannel = ctx.channel().attr(Server.PROXY).get();
        if (Objects.isNull(proxyChannel)) {
            ctx.close();
            return;
        }
        TrafficStatsCache.recordIn(serverChannel.id().asLongText(), buf.readableBytes());
        send(proxyChannel, MessageType.TYPE_DATA, buf.retain(), ctx);
    }

    /**
     * 连接中断
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        Optional.ofNullable(serverChannel.attr(Server.CHANNELS).get()).ifPresent(channelMap -> channelMap.remove(ctx.channel().id().asLongText()));
        Channel proxyChannel = ctx.channel().attr(Server.PROXY).getAndSet(null);
        if (Objects.nonNull(proxyChannel) && proxyChannel.isActive()) {
            proxyChannel.attr(Server.LOCAL).set(null);
            proxyChannel.config().setAutoRead(true);
            String serverId = proxyChannel.attr(Server.SERVER_ID).getAndSet(null);
            if (serverId != null) {
                TrafficStatsCache.decrementProxyChannels(serverId);
            }
            send(proxyChannel, MessageType.TYPE_DISCONNECTED, Unpooled.EMPTY_BUFFER, ctx);
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
    public void send(Channel complex, MessageType type, ByteBuf data, ChannelHandlerContext ctx) {
        Meta meta = new Meta(ctx.channel().id().asLongText(), remoteConfig).setServerId(serverChannel.id().asLongText());
        complex.writeAndFlush(new Message(type, meta, data));
    }
}
