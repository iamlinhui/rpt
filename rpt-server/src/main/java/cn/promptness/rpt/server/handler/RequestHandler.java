package cn.promptness.rpt.server.handler;

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
import io.netty.util.internal.EmptyArrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * request处理器
 */
public class RequestHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static final Logger logger = LoggerFactory.getLogger(RequestHandler.class);

    private final Queue<FullHttpRequest> requestMessage = new LinkedList<>();

    private final AtomicBoolean connected = new AtomicBoolean(false);

    /**
     * domain -> serverChannel 全局
     */
    private final Map<String, Channel> serverChannelMap;

    /**
     * httpChannelId -> httpChannel 全局
     */
    private final Map<String, Channel> httpChannelMap;

    private String domain;

    public RequestHandler(Map<String, Channel> serverChannelMap, Map<String, Channel> httpChannelMap) {
        this.serverChannelMap = serverChannelMap;
        this.httpChannelMap = httpChannelMap;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.channel().close();
    }

    /**
     * 连接中断
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        logger.info("断开连接,{}", domain == null ? "未知连接" : domain);
        ctx.channel().config().setAutoRead(true);
        Channel remove = httpChannelMap.remove(ctx.channel().id().asLongText());
        if (remove != null) {
            remove.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
        if (!StringUtils.hasText(domain)) {
            return;
        }
        Channel serverChannel = serverChannelMap.get(domain);
        if (serverChannel == null) {
            return;
        }
        logger.info("通知客户端,断开连接,{}", domain);
        send(serverChannel, ctx, domain, MessageType.TYPE_DISCONNECTED, EmptyArrays.EMPTY_BYTES);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (!(evt instanceof ProxyType)) {
            ctx.fireUserEventTriggered(evt);
        }
        if (!StringUtils.hasText(domain)) {
            return;
        }
        Channel serverChannel = serverChannelMap.get(domain);
        if (serverChannel == null) {
            return;
        }
        logger.info("恢复请求{}可读状态,开始传输数据", domain);
        connected.set(true);
        if (!requestMessage.isEmpty()) {
            synchronized (connected) {
                FullHttpRequest request;
                while ((request = requestMessage.poll()) != null) {
                    handle(serverChannel, ctx, request);
                }
            }
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest fullHttpRequest) throws Exception {

        String hostAndPort = fullHttpRequest.headers().get(HttpHeaderNames.HOST);
        domain = StringUtils.hasText(domain) ? domain : hostAndPort.split(":")[0];
        logger.info("接收到来自{}的请求", domain);
        if (!StringUtils.hasText(domain)) {
            handle(ctx, fullHttpRequest, HttpResponseStatus.NO_CONTENT);
            return;
        }
        Channel serverChannel = serverChannelMap.get(domain);
        if (serverChannel == null || !serverChannel.isOpen()) {
            handle(ctx, fullHttpRequest, HttpResponseStatus.NOT_FOUND);
            return;
        }
        if (!connected.get()) {
            ctx.channel().config().setAutoRead(false);
            logger.info("设置{}请求为不可读,传输[建立连接]通知到客户端", domain);
            send(serverChannel, ctx, domain, MessageType.TYPE_CONNECTED, EmptyArrays.EMPTY_BYTES);
        }
        fullHttpRequest.headers().set(Constants.REQUEST_CHANNEL_ID, ctx.channel().id().asLongText());

        if (!connected.get()) {
            synchronized (connected) {
                if (!connected.get()) {
                    requestMessage.offer(fullHttpRequest);
                    return;
                }
            }
        }
        handle(serverChannel, ctx, fullHttpRequest);
    }

    private void handle(Channel serverChannel, ChannelHandlerContext ctx, FullHttpRequest fullHttpRequest) throws Exception {
        List<Object> encode = HttpEncoder.encode(ctx, fullHttpRequest);
        for (Object obj : encode) {
            ByteBuf buf = (ByteBuf) obj;
            byte[] data = new byte[buf.readableBytes()];
            buf.readBytes(data);
            buf.release();
            send(serverChannel, ctx, domain, MessageType.TYPE_DATA, data);
        }
    }

    private void handle(ChannelHandlerContext ctx, FullHttpRequest fullHttpRequest, HttpResponseStatus httpResponseStatus) throws Exception {
        handle(ctx, fullHttpRequest, httpResponseStatus, httpResponseStatus.codeAsText().toByteArray());
    }

    private void handle(ChannelHandlerContext ctx, FullHttpRequest fullHttpRequest, HttpResponseStatus httpResponseStatus, byte[] result) throws Exception {
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, httpResponseStatus, Unpooled.wrappedBuffer(result));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_PLAIN);
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
        response.headers().set(HttpHeaderNames.SERVER, Constants.RPT);

        List<Object> encode = HttpEncoder.encode(ctx, response);
        for (Object obj : encode) {
            ChannelFuture future = ctx.writeAndFlush(obj);
            if (!HttpUtil.isKeepAlive(fullHttpRequest)) {
                future.addListener(ChannelFutureListener.CLOSE);
            }
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
