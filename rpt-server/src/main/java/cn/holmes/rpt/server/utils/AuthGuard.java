package cn.holmes.rpt.server.utils;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.ServerCookieDecoder;
import io.netty.util.internal.EmptyArrays;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;


public final class AuthGuard {

    private static final String COOKIE_NAME = "RPT_SESS";
    private static final String LOGIN_MARKER = "RPT_LOGIN";
    private static final String LOGIN_PAGE = "static/login.html";
    private static final int COOKIE_TTL = 7 * 24 * 3600;
    private static final String HMAC_ALGO = "HmacSHA256";
    private static final SecureRandom RNG = new SecureRandom();

    private AuthGuard() {
    }

    /**
     * @return true 放行转发后端;false 已自行写响应,调用方应 return
     */
    public static boolean authenticate(ChannelHandlerContext ctx, FullHttpRequest req, String token) throws Exception {
        if (validCookie(req, token)) {
            return true;
        }
        if (HttpMethod.POST.equals(req.method())) {
            Map<String, String> form = parseFormBody(req);
            if (form.containsKey(LOGIN_MARKER)) {
                handleLoginPost(ctx, req, token, form);
                return false;
            }
        }
        renderLoginPage(ctx, req, "");
        return false;
    }

    private static void handleLoginPost(ChannelHandlerContext ctx, FullHttpRequest req, String token, Map<String, String> form) throws Exception {
        String uri = req.uri();
        String redirect = (uri != null && uri.startsWith("/") && !uri.startsWith("//")) ? uri : "/";
        if (matches(form.get("user"), form.get("pass"), token)) {
            // 签发 cookie 并 302 回当前路径
            long exp = System.currentTimeMillis() / 1000L + COOKIE_TTL;
            String data = exp + "." + freshNonce();
            String cookieValue = data + "." + hexEncode(hmac(token, data));
            String setCookie = COOKIE_NAME + "=" + cookieValue + "; Path=/; HttpOnly; SameSite=Lax" + (form.containsKey("remember") ? "; Max-Age=" + COOKIE_TTL : "");

            FullHttpResponse resp = FullHttpHelper.buildResponse(ctx, HttpResponseStatus.FOUND, EmptyArrays.EMPTY_BYTES);
            resp.headers().set(HttpHeaderNames.LOCATION, redirect);
            resp.headers().set(HttpHeaderNames.SET_COOKIE, setCookie);
            FullHttpHelper.writeKeepAlive(ctx, req, resp);
        } else {
            renderLoginPage(ctx, req, "账号或密码错误");
        }
    }

    private static boolean validCookie(FullHttpRequest req, String token) throws Exception {
        String header = req.headers().get(HttpHeaderNames.COOKIE);
        if (header == null || header.isEmpty()) {
            return false;
        }
        Set<Cookie> cookies = ServerCookieDecoder.STRICT.decode(header);
        for (Cookie c : cookies) {
            if (COOKIE_NAME.equals(c.name()) && validCookieValue(c.value(), token)) {
                return true;
            }
        }
        return false;
    }

    /**
     * cookie 值格式:exp.nonce.sig,sig = hex(HMAC-SHA256(token, "exp.nonce"))
     */
    private static boolean validCookieValue(String value, String token) throws Exception {
        String[] parts = value.split("\\.", 3);
        if (parts.length != 3) {
            return false;
        }
        long exp = Long.parseLong(parts[0]);
        if (exp <= System.currentTimeMillis() / 1000L) {
            return false;
        }
        String data = parts[0] + "." + parts[1];
        return MessageDigest.isEqual(hmac(token, data), hexDecode(parts[2]));
    }

    /**
     * 表单 user:pass 与 token 常量时间比较
     */
    private static boolean matches(String user, String pass, String token) {
        if (user == null || pass == null || token == null) {
            return false;
        }
        return MessageDigest.isEqual((user + ":" + pass).getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8));
    }

    private static void renderLoginPage(ChannelHandlerContext ctx, FullHttpRequest req, String error) {
        String html = new String(FullHttpHelper.loadResource(LOGIN_PAGE), StandardCharsets.UTF_8);
        String errHtml = error.isEmpty() ? "" : "<div class=\"err\">" + error + "</div>";
        byte[] body = html.replace("{{ERROR}}", errHtml).getBytes(StandardCharsets.UTF_8);
        FullHttpResponse resp = FullHttpHelper.buildResponse(ctx, HttpResponseStatus.OK, body);
        resp.headers().set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_HTML);
        FullHttpHelper.writeKeepAlive(ctx, req, resp);
    }

    private static Map<String, String> parseFormBody(FullHttpRequest req) {
        ByteBuf buf = req.content();
        if (buf == null || !buf.isReadable()) {
            return new HashMap<>();
        }
        Map<String, String> map = new HashMap<>();
        QueryStringDecoder decoder = new QueryStringDecoder(buf.toString(StandardCharsets.UTF_8), false);
        for (Map.Entry<String, List<String>> e : decoder.parameters().entrySet()) {
            List<String> values = e.getValue();
            if (!values.isEmpty()) {
                map.putIfAbsent(e.getKey(), values.get(0));
            }
        }
        return map;
    }

    private static byte[] hmac(String key, String data) throws Exception {
        Mac mac = Mac.getInstance(HMAC_ALGO);
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    private static String freshNonce() {
        byte[] b = new byte[8];
        RNG.nextBytes(b);
        return hexEncode(b);
    }

    private static String hexEncode(byte[] b) {
        return ByteBufUtil.hexDump(b).toLowerCase();
    }

    private static byte[] hexDecode(String hex) {
        return ByteBufUtil.decodeHexDump(hex);
    }
}
