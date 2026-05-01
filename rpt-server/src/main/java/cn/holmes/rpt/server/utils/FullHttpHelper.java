package cn.holmes.rpt.server.utils;

import cn.holmes.rpt.base.utils.StringUtils;
import io.netty.buffer.ByteBuf;
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
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 静态资源加载 & HTTP 响应构建 工具类
 * <p>供 {@link StaticDispatcher} 和 DashboardHandler 共用</p>
 */
public final class FullHttpHelper {

    private static final Logger logger = LoggerFactory.getLogger(FullHttpHelper.class);

    /** 资源缓存，启动后只读 */
    private static final Map<String, byte[]> RESOURCE_CACHE = new ConcurrentHashMap<>();

    private FullHttpHelper() {
    }

    /**
     * 从 classpath 加载资源（带缓存）
     */
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

    public static boolean verifyToken(FullHttpRequest request, String token) {
        String authorization = request.headers().get(HttpHeaderNames.AUTHORIZATION);
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Basic ")) {
            return false;
        }
        String decoded = new String(Base64.getDecoder().decode(authorization.substring(6)), StandardCharsets.UTF_8);
        return Objects.equals(token, decoded);
    }

    /**
     * 构建 HTTP 响应
     */
    public static FullHttpResponse buildResponse(ChannelHandlerContext ctx, HttpResponseStatus status, byte[] body) {
        ByteBuf buffer = ctx.channel().alloc().buffer(body.length);
        buffer.writeBytes(body);
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, buffer);
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, body.length);
        return response;
    }

    /**
     * 写出响应，根据 keep-alive 决定是否关闭（HTTP 代理场景）
     */
    public static void writeKeepAlive(ChannelHandlerContext ctx, FullHttpRequest request, FullHttpResponse response) {
        if (!io.netty.handler.codec.http.HttpUtil.isKeepAlive(request)) {
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        } else {
            ctx.writeAndFlush(response);
        }
    }
}

