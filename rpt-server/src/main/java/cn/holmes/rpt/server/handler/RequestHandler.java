package cn.holmes.rpt.server.handler;

import cn.holmes.rpt.base.config.ProxyType;
import cn.holmes.rpt.base.config.RemoteConfig;
import cn.holmes.rpt.base.protocol.Message;
import cn.holmes.rpt.base.protocol.MessageType;
import cn.holmes.rpt.base.protocol.Meta;
import cn.holmes.rpt.base.utils.Constants.Server;
import cn.holmes.rpt.base.utils.StringUtils;
import cn.holmes.rpt.server.cache.ServerChannelCache;
import cn.holmes.rpt.server.coder.HttpEncoder;
import cn.holmes.rpt.server.page.StaticDispatcher;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.util.ReferenceCountUtil;

import java.util.*;
import java.util.regex.Pattern;

public class RequestHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static final Pattern COLON = Pattern.compile(":");

    private final Queue<FullHttpRequest> requestMessage = new LinkedList<>();

    private final HttpEncoder.RequestEncoder requestEncoder = new HttpEncoder.RequestEncoder();

    private String domain;

    private boolean connecting;

    private boolean connected;

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        ctx.channel().attr(Server.PROXY_TYPE).set(ProxyType.HTTP);
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
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.close();
    }

    /**
     * 连接中断
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        ctx.channel().config().setAutoRead(true);
        FullHttpRequest request;
        while ((request = requestMessage.poll()) != null) {
            ReferenceCountUtil.release(request);
        }
        if (domain == null) {
            return;
        }
        Channel serverChannel = ServerChannelCache.getServerDomainChannelMap().get(domain);
        if (serverChannel == null) {
            return;
        }
        Optional.ofNullable(serverChannel.attr(Server.CHANNELS).get()).ifPresent(channelMap -> channelMap.remove(ctx.channel().id().asLongText()));
        Channel proxyChannel = ctx.channel().attr(Server.PROXY).getAndSet(null);
        if (Objects.nonNull(proxyChannel) && proxyChannel.isActive()) {
            proxyChannel.attr(Server.LOCAL).set(null);
            proxyChannel.config().setAutoRead(true);
            send(proxyChannel, ctx, domain, MessageType.TYPE_DISCONNECTED, Unpooled.EMPTY_BUFFER);
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (!Objects.equals(ProxyType.HTTP, evt)) {
            ctx.fireUserEventTriggered(evt);
            return;
        }
        Channel proxyChannel = ctx.channel().attr(Server.PROXY).get();
        ctx.pipeline().remove(HttpServerCodec.class);
        ctx.pipeline().remove(HttpObjectAggregator.class);
        connected = true;
        FullHttpRequest request;
        while ((request = requestMessage.poll()) != null) {
            handle(proxyChannel, ctx, request);
        }
        ctx.channel().config().setAutoRead(true);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof FullHttpRequest) {
            super.channelRead(ctx, msg);
            return;
        }
        if (msg instanceof ByteBuf) {
            ByteBuf buf = (ByteBuf) msg;
            Channel proxyChannel = ctx.channel().attr(Server.PROXY).get();
            if (Objects.isNull(proxyChannel)) {
                buf.release();
                ctx.close();
                return;
            }
            send(proxyChannel, ctx, domain, MessageType.TYPE_DATA, buf);
            return;
        }
        ctx.fireChannelRead(msg);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest fullHttpRequest) throws Exception {
        domain = Optional.ofNullable(domain).orElse(COLON.split(fullHttpRequest.headers().get(HttpHeaderNames.HOST))[0]);
        if (!StringUtils.hasText(domain)) {
            StaticDispatcher.dispatch(fullHttpRequest, ctx);
            return;
        }
        Channel serverChannel = ServerChannelCache.getServerDomainChannelMap().get(domain);
        if (serverChannel == null || !serverChannel.isOpen()) {
            StaticDispatcher.dispatch(fullHttpRequest, ctx);
            return;
        }

        String token = ServerChannelCache.getServerDomainToken().get(domain);
        if (token != null && !StaticDispatcher.authorize(ctx, fullHttpRequest, token)) {
            return;
        }

        if (!connected) {
            requestMessage.offer(fullHttpRequest.retain());
            if (!connecting) {
                connecting = true;
                ctx.channel().config().setAutoRead(false);
                serverChannel.attr(Server.CHANNELS).get().put(ctx.channel().id().asLongText(), ctx.channel());
                send(serverChannel, ctx, domain, MessageType.TYPE_CONNECTED, Unpooled.EMPTY_BUFFER);
            }
            return;
        }
        Channel proxyChannel = ctx.channel().attr(Server.PROXY).get();
        if (Objects.isNull(proxyChannel)) {
            ctx.close();
            return;
        }
        handle(proxyChannel, ctx, fullHttpRequest.retain());
    }

    private void handle(Channel proxyChannel, ChannelHandlerContext ctx, FullHttpRequest fullHttpRequest) throws Exception {
        List<Object> encode = new ArrayList<>();
        // 1 -> 0
        requestEncoder.encode(ctx, fullHttpRequest, encode);
        for (Object obj : encode) {
            ByteBuf buf = (ByteBuf) obj;
            send(proxyChannel, ctx, domain, MessageType.TYPE_DATA, buf);
        }
    }

    private void send(Channel complex, ChannelHandlerContext ctx, String domain, MessageType typeConnect, ByteBuf data) {
        RemoteConfig remoteConfig = new RemoteConfig();
        remoteConfig.setProxyType(ProxyType.HTTP);
        remoteConfig.setDomain(domain);

        Meta meta = new Meta(ctx.channel().id().asLongText(), remoteConfig);
        meta.setServerId(complex.id().asLongText());

        complex.writeAndFlush(new Message(typeConnect, meta, data));
    }
}
