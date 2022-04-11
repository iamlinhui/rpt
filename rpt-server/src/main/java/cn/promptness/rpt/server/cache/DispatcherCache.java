package cn.promptness.rpt.server.cache;

import cn.promptness.rpt.base.coder.HttpEncoder;
import cn.promptness.rpt.base.utils.Constants;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

public class DispatcherCache {

    private static final Logger logger = LoggerFactory.getLogger(DispatcherCache.class);

    private static final Map<String, BiConsumer<ChannelHandlerContext, FullHttpRequest>> HANDLE_MAP = new HashMap<String, BiConsumer<ChannelHandlerContext, FullHttpRequest>>() {{
        put("/favicon.ico", DispatcherCache::favicon);
        put("/", DispatcherCache::index);
        put("/index.html", DispatcherCache::index);
        put("/404", DispatcherCache::notFound);
    }};

    public static void doDispatch(FullHttpRequest fullHttpRequest, ChannelHandlerContext ctx) {
        String uri = fullHttpRequest.uri();
        HANDLE_MAP.getOrDefault(uri, DispatcherCache::notFound).accept(ctx, fullHttpRequest);
    }

    private static void favicon(ChannelHandlerContext ctx, FullHttpRequest fullHttpRequest) {
        byte[] result = Constants.page("page/favicon.ico");
        FullHttpResponse response = buildResponse(ctx, HttpResponseStatus.OK, result);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "image/x-icon");
        response.headers().set("Cache-Control", "max-age=86400");
        handle(ctx, fullHttpRequest, response);
    }

    private static void index(ChannelHandlerContext ctx, FullHttpRequest fullHttpRequest) {
        byte[] result = Constants.page("page/index.html");
        FullHttpResponse response = buildResponse(ctx, HttpResponseStatus.OK, result);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_HTML);
        handle(ctx, fullHttpRequest, response);
    }

    private static void notFound(ChannelHandlerContext ctx, FullHttpRequest fullHttpRequest) {
        byte[] result = Constants.page("page/404.html");
        FullHttpResponse response = buildResponse(ctx, HttpResponseStatus.NOT_FOUND, result);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_HTML);
        handle(ctx, fullHttpRequest, response);
    }

    private static FullHttpResponse buildResponse(ChannelHandlerContext ctx, HttpResponseStatus status, byte[] result) {
        ByteBuf buffer = ctx.channel().alloc().buffer(result.length);
        buffer.writeBytes(result);
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, buffer);
        response.headers().set(HttpHeaderNames.SERVER, Constants.RPT);
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
        return response;
    }

    private static void handle(ChannelHandlerContext ctx, FullHttpRequest fullHttpRequest, FullHttpResponse fullHttpResponse) {
        List<Object> encode = new ArrayList<>();
        try {
            new HttpEncoder.ResponseEncoder().encode(ctx, fullHttpResponse, encode);
            for (Object obj : encode) {
                ChannelFuture future = ctx.writeAndFlush(obj);
                if (!HttpUtil.isKeepAlive(fullHttpRequest)) {
                    future.addListener(ChannelFutureListener.CLOSE);
                }
            }
        } catch (Exception e) {
            logger.error(e.getMessage());
        } finally {
            // 释放缓冲区内存
            ReferenceCountUtil.release(fullHttpResponse);
        }
    }

}
