package cn.holmes.rpt.server.utils;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import io.netty.util.internal.EmptyArrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 静态资源加载 & HTTP 响应构建 工具类
 */
public final class FullHttpHelper {

    private static final Logger logger = LoggerFactory.getLogger(FullHttpHelper.class);

    private static final Map<String, byte[]> RESOURCE_CACHE = new ConcurrentHashMap<>();

    private FullHttpHelper() {
    }

    public static byte[] loadResource(String path) {
        return RESOURCE_CACHE.computeIfAbsent(path, FullHttpHelper::doLoad);
    }

    private static byte[] doLoad(String path) {
        try (InputStream is = ClassLoader.getSystemResourceAsStream(path)) {
            if (is != null) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int len;
                while ((len = is.read(buf)) != -1) {
                    bos.write(buf, 0, len);
                }
                return bos.toByteArray();
            }
        } catch (Exception e) {
            logger.error("Failed to load resource: {}", path, e);
        }
        return EmptyArrays.EMPTY_BYTES;
    }

    public static FullHttpResponse buildResponse(ChannelHandlerContext ctx, HttpResponseStatus status, byte[] body) {
        ByteBuf buffer = ctx.channel().alloc().buffer(body.length);
        buffer.writeBytes(body);
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, buffer);
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, body.length);
        return response;
    }

    public static void writeKeepAlive(ChannelHandlerContext ctx, FullHttpRequest request, FullHttpResponse response) {
        if (!HttpUtil.isKeepAlive(request)) {
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        } else {
            ctx.writeAndFlush(response);
        }
    }

    /**
     * 渲染静态页面(index / favicon)
     */
    public static void serveIndex(ChannelHandlerContext ctx, FullHttpRequest req) {
        if ("/favicon.ico".equals(req.uri())) {
            byte[] body = loadResource("static/favicon.ico");
            FullHttpResponse resp = buildResponse(ctx, HttpResponseStatus.OK, body);
            resp.headers().set(HttpHeaderNames.CONTENT_TYPE, "image/x-icon");
            resp.headers().set(HttpHeaderNames.CACHE_CONTROL, "max-age=86400");
            writeKeepAlive(ctx, req, resp);
            return;
        }
        byte[] body = loadResource("static/index.html");
        FullHttpResponse resp = buildResponse(ctx, HttpResponseStatus.OK, body);
        resp.headers().set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_HTML);
        writeKeepAlive(ctx, req, resp);
    }
}
