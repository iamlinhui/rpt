package cn.promptness.rpt.server.cache;

import cn.promptness.rpt.base.utils.StringUtils;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import io.netty.util.internal.EmptyArrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;

public class DispatcherCache {

    private static final Logger logger = LoggerFactory.getLogger(DispatcherCache.class);

    private static final Pattern BLANK = Pattern.compile("\\s");

    private static final Map<String, BiConsumer<ChannelHandlerContext, FullHttpRequest>> HANDLE_MAP = new HashMap<>();

    static {
        HANDLE_MAP.put("/favicon.ico", DispatcherCache::favicon);
        HANDLE_MAP.put("/", DispatcherCache::index);
        HANDLE_MAP.put("/index.html", DispatcherCache::index);
        HANDLE_MAP.put("/static/base.css", DispatcherCache::css);
    }

    public static void dispatch(FullHttpRequest fullHttpRequest, ChannelHandlerContext ctx) {
        String uri = fullHttpRequest.uri();
        HANDLE_MAP.getOrDefault(uri, DispatcherCache::notFound).accept(ctx, fullHttpRequest);
    }

    public static boolean authorize(ChannelHandlerContext ctx, FullHttpRequest fullHttpRequest, String token) {
        String authorization = fullHttpRequest.headers().get(HttpHeaderNames.AUTHORIZATION);
        if (StringUtils.hasText(authorization)) {
            authorization = new String(Base64.getDecoder().decode(BLANK.split(authorization)[1]), StandardCharsets.UTF_8);
            if (Objects.equals(token, authorization)) {
                return true;
            }
        }
        FullHttpResponse response = buildResponse(ctx, HttpResponseStatus.UNAUTHORIZED, page("static/401.html"));
        response.headers().set(HttpHeaderNames.WWW_AUTHENTICATE, "Basic realm=\".\"");
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_HTML);
        handle(ctx, fullHttpRequest, response);
        return false;
    }

    private static void favicon(ChannelHandlerContext ctx, FullHttpRequest fullHttpRequest) {
        byte[] result = page("static/favicon.ico");
        FullHttpResponse response = buildResponse(ctx, HttpResponseStatus.OK, result);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "image/x-icon");
        response.headers().set(HttpHeaderNames.CACHE_CONTROL, "max-age=86400");
        handle(ctx, fullHttpRequest, response);
    }

    private static void index(ChannelHandlerContext ctx, FullHttpRequest fullHttpRequest) {
        byte[] result = page("static/index.html");
        FullHttpResponse response = buildResponse(ctx, HttpResponseStatus.OK, result);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_HTML);
        handle(ctx, fullHttpRequest, response);
    }

    private static void css(ChannelHandlerContext ctx, FullHttpRequest fullHttpRequest) {
        byte[] result = page("static/base.css");
        FullHttpResponse response = buildResponse(ctx, HttpResponseStatus.OK, result);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_CSS);
        response.headers().set(HttpHeaderNames.CACHE_CONTROL, "max-age=86400");
        handle(ctx, fullHttpRequest, response);
    }

    private static void notFound(ChannelHandlerContext ctx, FullHttpRequest fullHttpRequest) {
        byte[] result = page("static/404.html");
        FullHttpResponse response = buildResponse(ctx, HttpResponseStatus.NOT_FOUND, result);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_HTML);
        handle(ctx, fullHttpRequest, response);
    }

    private static FullHttpResponse buildResponse(ChannelHandlerContext ctx, HttpResponseStatus status, byte[] result) {
        ByteBuf buffer = ctx.channel().alloc().buffer(result.length);
        buffer.writeBytes(result);
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, buffer);
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
        return response;
    }

    private static void handle(ChannelHandlerContext ctx, FullHttpRequest fullHttpRequest, FullHttpResponse fullHttpResponse) {
        ChannelFuture future = ctx.writeAndFlush(fullHttpResponse);
        if (!HttpUtil.isKeepAlive(fullHttpRequest)) {
            future.addListener(ChannelFutureListener.CLOSE);
        }
    }

    private static byte[] page(String page) {
        try (InputStream resource = ClassLoader.getSystemResourceAsStream(page)) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            if (resource != null) {
                byte[] buf = new byte[1024];
                int len;
                while ((len = resource.read(buf)) != -1) {
                    bos.write(buf, 0, len);
                }
                return bos.toByteArray();
            }
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
        return EmptyArrays.EMPTY_BYTES;
    }
}
