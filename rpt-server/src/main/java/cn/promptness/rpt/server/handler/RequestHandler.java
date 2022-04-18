package cn.promptness.rpt.server.handler;

import cn.promptness.rpt.base.coder.HttpEncoder;
import cn.promptness.rpt.base.config.ClientConfig;
import cn.promptness.rpt.base.config.RemoteConfig;
import cn.promptness.rpt.base.handler.ByteIdleCheckHandler;
import cn.promptness.rpt.base.protocol.Message;
import cn.promptness.rpt.base.protocol.MessageType;
import cn.promptness.rpt.base.protocol.ProxyType;
import cn.promptness.rpt.base.utils.Constants;
import cn.promptness.rpt.base.utils.StringUtils;
import cn.promptness.rpt.server.cache.DispatcherCache;
import cn.promptness.rpt.server.cache.ServerChannelCache;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.bytes.ByteArrayDecoder;
import io.netty.handler.codec.bytes.ByteArrayEncoder;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpObjectAggregator;
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
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.channel().close();
    }

    /**
     * 连接中断
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        ctx.channel().config().setAutoRead(true);
        Channel remove = ServerChannelCache.getServerHttpChannelMap().remove(ctx.channel().id().asLongText());
        if (remove != null) {
            remove.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
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
        send(serverChannel, ctx, domain, MessageType.TYPE_DISCONNECTED, EmptyArrays.EMPTY_BYTES);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (!Objects.equals(ProxyType.HTTP, evt)) {
            ctx.fireUserEventTriggered(evt);
            return;
        }
        if (domain == null) {
            return;
        }
        Channel serverChannel = ServerChannelCache.getServerDomainChannelMap().get(domain);
        if (serverChannel == null) {
            return;
        }
        ctx.pipeline().addFirst(new ByteArrayDecoder(), new ByteArrayEncoder(), new ByteIdleCheckHandler(0, 30, 0));
        ctx.pipeline().remove(HttpServerCodec.class);
        ctx.pipeline().remove(HttpObjectAggregator.class);
        connected.set(true);
        if (!requestMessage.isEmpty()) {
            synchronized (connected) {
                FullHttpRequest request;
                while ((request = requestMessage.poll()) != null) {
                    handle(serverChannel, ctx, request);
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
            Channel serverChannel = ServerChannelCache.getServerDomainChannelMap().get(domain);
            if (serverChannel != null) {
                send(serverChannel, ctx, domain, MessageType.TYPE_DATA, message);
            }
            return;
        }
        ctx.fireChannelRead(msg);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest fullHttpRequest) throws Exception {

        domain = Optional.ofNullable(domain).orElse(Constants.PATTERN.split(fullHttpRequest.headers().get(HttpHeaderNames.HOST))[0]);
        if (!StringUtils.hasText(domain)) {
            DispatcherCache.doDispatch(fullHttpRequest, ctx);
            return;
        }
        Channel serverChannel = ServerChannelCache.getServerDomainChannelMap().get(domain);
        if (serverChannel == null || !serverChannel.isOpen()) {
            DispatcherCache.doDispatch(fullHttpRequest, ctx);
            return;
        }
        if (!connected.get()) {
            ctx.channel().config().setAutoRead(false);
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
        handle(serverChannel, ctx, fullHttpRequest);
    }

    private void handle(Channel serverChannel, ChannelHandlerContext ctx, FullHttpRequest fullHttpRequest) throws Exception {
        List<Object> encode = new ArrayList<>();
        requestEncoder.encode(ctx, fullHttpRequest, encode);
        for (Object obj : encode) {
            ByteBuf buf = (ByteBuf) obj;
            byte[] data = new byte[buf.readableBytes()];
            buf.readBytes(data);
            buf.release();
            send(serverChannel, ctx, domain, MessageType.TYPE_DATA, data);
        }
    }


    private void send(Channel serverChannel, ChannelHandlerContext ctx, String domain, MessageType typeConnect, byte[] data) {
        RemoteConfig remoteConfig = new RemoteConfig();
        remoteConfig.setProxyType(ProxyType.HTTP);
        remoteConfig.setDomain(domain);

        ClientConfig clientConfig = new ClientConfig();
        clientConfig.setConfig(Collections.singletonList(remoteConfig));
        clientConfig.setChannelId(ctx.channel().id().asLongText());

        Message message = new Message();
        message.setClientConfig(clientConfig);
        message.setData(data);
        message.setType(typeConnect);
        serverChannel.writeAndFlush(message);
    }
}
