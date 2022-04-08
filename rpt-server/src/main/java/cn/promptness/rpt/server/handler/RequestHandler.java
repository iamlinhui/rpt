package cn.promptness.rpt.server.handler;

import cn.promptness.rpt.server.cache.ServerChannelCache;
import cn.promptness.rpt.base.coder.HttpEncoder;
import cn.promptness.rpt.base.config.ClientConfig;
import cn.promptness.rpt.base.config.RemoteConfig;
import cn.promptness.rpt.base.protocol.Message;
import cn.promptness.rpt.base.protocol.MessageType;
import cn.promptness.rpt.base.protocol.ProxyType;
import cn.promptness.rpt.base.utils.Constants;
import cn.promptness.rpt.base.utils.StringUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.EmptyArrays;

import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * request处理器
 */
public class RequestHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private final Queue<FullHttpRequest> requestMessage = new LinkedBlockingQueue<>();

    private final AtomicBoolean connected = new AtomicBoolean(false);

    private final HttpEncoder.RequestEncoder requestEncoder = new HttpEncoder.RequestEncoder();

    private final HttpEncoder.ResponseEncoder responseEncoder = new HttpEncoder.ResponseEncoder();

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
        if (!(evt instanceof ProxyType)) {
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
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest fullHttpRequest) throws Exception {

        domain = Optional.ofNullable(domain).orElse(Constants.PATTERN.split(fullHttpRequest.headers().get(HttpHeaderNames.HOST))[0]);
        if (!StringUtils.hasText(domain)) {
            handle(ctx, fullHttpRequest, HttpResponseStatus.NO_CONTENT, Constants.page("index.html"));
            return;
        }
        Channel serverChannel = ServerChannelCache.getServerDomainChannelMap().get(domain);
        if (serverChannel == null || !serverChannel.isOpen()) {
            handle(ctx, fullHttpRequest, HttpResponseStatus.NOT_FOUND, Constants.page("index.html"));
            return;
        }
        if (!connected.get()) {
            ctx.channel().config().setAutoRead(false);
            send(serverChannel, ctx, domain, MessageType.TYPE_CONNECTED, EmptyArrays.EMPTY_BYTES);
        }
        fullHttpRequest.headers().set(Constants.REQUEST_CHANNEL_ID, ctx.channel().id().asLongText());

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

    private void handle(ChannelHandlerContext ctx, FullHttpRequest fullHttpRequest, HttpResponseStatus httpResponseStatus, byte[] result) throws Exception {
        ByteBuf buffer = ctx.channel().alloc().buffer(result.length);
        buffer.writeBytes(result);
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, httpResponseStatus, buffer);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_HTML);
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
        response.headers().set(HttpHeaderNames.SERVER, Constants.RPT);
        List<Object> encode = new ArrayList<>();
        responseEncoder.encode(ctx, response, encode);
        for (Object obj : encode) {
            ChannelFuture future = ctx.writeAndFlush(obj);
            if (!HttpUtil.isKeepAlive(fullHttpRequest)) {
                future.addListener(ChannelFutureListener.CLOSE);
            }
        }
        // 释放缓冲区内存
        ReferenceCountUtil.release(response);
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
