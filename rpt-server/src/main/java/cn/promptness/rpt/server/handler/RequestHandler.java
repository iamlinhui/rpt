package cn.promptness.rpt.server.handler;

import cn.promptness.rpt.base.coder.ByteArrayCodec;
import cn.promptness.rpt.base.config.ProxyType;
import cn.promptness.rpt.base.config.RemoteConfig;
import cn.promptness.rpt.base.protocol.Message;
import cn.promptness.rpt.base.protocol.MessageType;
import cn.promptness.rpt.base.protocol.Meta;
import cn.promptness.rpt.base.utils.Constants;
import cn.promptness.rpt.base.utils.StringUtils;
import cn.promptness.rpt.server.cache.DispatcherCache;
import cn.promptness.rpt.server.cache.ServerChannelCache;
import cn.promptness.rpt.server.coder.HttpEncoder;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.EmptyArrays;

import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class RequestHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private final Queue<FullHttpRequest> requestMessage = new LinkedBlockingQueue<>();

    private final AtomicBoolean connected = new AtomicBoolean(false);

    private final HttpEncoder.RequestEncoder requestEncoder = new HttpEncoder.RequestEncoder();

    private String domain;

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        Channel proxyChannel = ctx.channel().attr(Constants.PROXY).get();
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
        Optional.ofNullable(serverChannel.attr(Constants.CHANNELS).get()).ifPresent(channelMap -> channelMap.remove(ctx.channel().id().asLongText()));
        // 绑定代理连接断线
        Channel proxyChannel = ctx.channel().attr(Constants.PROXY).getAndSet(null);
        if (Objects.isNull(proxyChannel)) {
            return;
        }
        // 服务端通知断线
        Channel localChannel = proxyChannel.attr(Constants.LOCAL).getAndSet(null);
        if (Objects.isNull(localChannel)) {
            return;
        }
        // 主动断线
        proxyChannel.config().setAutoRead(true);
        send(proxyChannel, ctx, domain, MessageType.TYPE_DISCONNECTED, EmptyArrays.EMPTY_BYTES);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (!Objects.equals(ProxyType.HTTP, evt)) {
            ctx.fireUserEventTriggered(evt);
            return;
        }
        Channel proxyChannel = ctx.channel().attr(Constants.PROXY).get();
        ctx.pipeline().replace(HttpServerCodec.class, ByteArrayCodec.class.getName(), new ByteArrayCodec());
        connected.set(true);
        if (!requestMessage.isEmpty()) {
            synchronized (connected) {
                FullHttpRequest request;
                while ((request = requestMessage.poll()) != null) {
                    handle(proxyChannel, ctx, request);
                    ReferenceCountUtil.release(request);
                }
            }
        }
        ctx.channel().config().setAutoRead(true);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof FullHttpRequest) {
            super.channelRead(ctx, msg);
            return;
        }
        if (msg instanceof byte[]) {
            byte[] message = (byte[]) msg;
            Channel proxyChannel = ctx.channel().attr(Constants.PROXY).get();
            if (Objects.isNull(proxyChannel)) {
                ctx.close();
                return;
            }
            send(proxyChannel, ctx, domain, MessageType.TYPE_DATA, message);
        }
        ctx.fireChannelRead(msg);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest fullHttpRequest) throws Exception {

        domain = Optional.ofNullable(domain).orElse(Constants.COLON.split(fullHttpRequest.headers().get(HttpHeaderNames.HOST))[0]);
        if (!StringUtils.hasText(domain)) {
            DispatcherCache.dispatch(fullHttpRequest, ctx);
            return;
        }
        Channel serverChannel = ServerChannelCache.getServerDomainChannelMap().get(domain);
        if (serverChannel == null || !serverChannel.isOpen()) {
            DispatcherCache.dispatch(fullHttpRequest, ctx);
            return;
        }

        String token = ServerChannelCache.getServerDomainToken().get(domain);
        if (token != null && !DispatcherCache.authorize(ctx, fullHttpRequest, token)) {
            return;
        }

        if (!connected.get()) {
            ctx.channel().config().setAutoRead(false);
            serverChannel.attr(Constants.CHANNELS).get().put(ctx.channel().id().asLongText(), ctx.channel());
            send(serverChannel, ctx, domain, MessageType.TYPE_CONNECTED, EmptyArrays.EMPTY_BYTES);
        }
        if (!connected.get()) {
            synchronized (connected) {
                if (!connected.get()) {
                    requestMessage.offer(fullHttpRequest.retain());
                    return;
                }
            }
        }
        Channel proxyChannel = ctx.channel().attr(Constants.PROXY).get();
        if (Objects.isNull(proxyChannel)) {
            ctx.close();
            return;
        }
        handle(proxyChannel, ctx, fullHttpRequest);
    }

    private void handle(Channel proxyChannel, ChannelHandlerContext ctx, FullHttpRequest fullHttpRequest) throws Exception {
        List<Object> encode = new ArrayList<>();
        requestEncoder.encode(ctx, fullHttpRequest, encode);
        for (Object obj : encode) {
            ByteBuf buf = (ByteBuf) obj;
            byte[] data = new byte[buf.readableBytes()];
            buf.readBytes(data);
            buf.release();
            send(proxyChannel, ctx, domain, MessageType.TYPE_DATA, data);
        }
    }

    private void send(Channel complex, ChannelHandlerContext ctx, String domain, MessageType typeConnect, byte[] data) {
        RemoteConfig remoteConfig = new RemoteConfig();
        remoteConfig.setProxyType(ProxyType.HTTP);
        remoteConfig.setDomain(domain);

        Meta meta = new Meta(ctx.channel().id().asLongText(), remoteConfig);
        meta.setServerId(complex.id().asLongText());

        Message message = new Message();
        message.setMeta(meta);
        message.setData(data);
        message.setType(typeConnect);
        complex.writeAndFlush(message);
    }
}
