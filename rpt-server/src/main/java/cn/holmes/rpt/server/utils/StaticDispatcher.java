package cn.holmes.rpt.server.utils;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

public class StaticDispatcher {

    private static final Map<String, BiConsumer<ChannelHandlerContext, FullHttpRequest>> HANDLE_MAP = new HashMap<>();

    private static final List<String> WHITE_URI = Arrays.asList("/favicon.ico", "/static/base.css");

    static {
        HANDLE_MAP.put("/favicon.ico", StaticDispatcher::favicon);
        HANDLE_MAP.put("/", StaticDispatcher::index);
        HANDLE_MAP.put("/index.html", StaticDispatcher::index);
        HANDLE_MAP.put("/static/base.css", StaticDispatcher::css);
    }

    public static void dispatch(FullHttpRequest fullHttpRequest, ChannelHandlerContext ctx) {
        String uri = fullHttpRequest.uri();
        HANDLE_MAP.getOrDefault(uri, StaticDispatcher::notFound).accept(ctx, fullHttpRequest);
    }

    public static boolean authorize(ChannelHandlerContext ctx, FullHttpRequest fullHttpRequest, String token) {
        if (FullHttpHelper.verifyToken(fullHttpRequest, token)) {
            return true;
        }
        String uri = fullHttpRequest.uri();
        if (WHITE_URI.contains(uri)) {
            dispatch(fullHttpRequest, ctx);
        } else {
            unauthorized(ctx, fullHttpRequest);
        }
        return false;
    }


    private static void unauthorized(ChannelHandlerContext ctx, FullHttpRequest fullHttpRequest) {
        FullHttpResponse response = FullHttpHelper.buildResponse(ctx, HttpResponseStatus.UNAUTHORIZED, FullHttpHelper.loadResource("static/401.html"));
        response.headers().set(HttpHeaderNames.WWW_AUTHENTICATE, "Basic realm=\"Restricted Area\"");
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_HTML);
        FullHttpHelper.writeKeepAlive(ctx, fullHttpRequest, response);
    }

    private static void favicon(ChannelHandlerContext ctx, FullHttpRequest fullHttpRequest) {
        FullHttpResponse response = FullHttpHelper.buildResponse(ctx, HttpResponseStatus.OK, FullHttpHelper.loadResource("static/favicon.ico"));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "image/x-icon");
        response.headers().set(HttpHeaderNames.CACHE_CONTROL, "max-age=86400");
        FullHttpHelper.writeKeepAlive(ctx, fullHttpRequest, response);
    }

    private static void index(ChannelHandlerContext ctx, FullHttpRequest fullHttpRequest) {
        FullHttpResponse response = FullHttpHelper.buildResponse(ctx, HttpResponseStatus.OK, FullHttpHelper.loadResource("static/index.html"));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_HTML);
        FullHttpHelper.writeKeepAlive(ctx, fullHttpRequest, response);
    }

    private static void css(ChannelHandlerContext ctx, FullHttpRequest fullHttpRequest) {
        FullHttpResponse response = FullHttpHelper.buildResponse(ctx, HttpResponseStatus.OK, FullHttpHelper.loadResource("static/base.css"));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_CSS);
        response.headers().set(HttpHeaderNames.CACHE_CONTROL, "max-age=86400");
        FullHttpHelper.writeKeepAlive(ctx, fullHttpRequest, response);
    }

    private static void notFound(ChannelHandlerContext ctx, FullHttpRequest fullHttpRequest) {
        FullHttpResponse response = FullHttpHelper.buildResponse(ctx, HttpResponseStatus.OK, FullHttpHelper.loadResource("static/404.html"));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_HTML);
        FullHttpHelper.writeKeepAlive(ctx, fullHttpRequest, response);
    }
}
