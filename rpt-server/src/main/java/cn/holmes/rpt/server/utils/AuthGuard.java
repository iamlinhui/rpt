package cn.holmes.rpt.server.utils;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.ServerCookieDecoder;
import io.netty.util.internal.EmptyArrays;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HashMap;
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
    public static boolean authenticate(ChannelHandlerContext ctx, FullHttpRequest req, String token) {
        if (validCookie(req, token)) {
            return true;
        }
        if (HttpMethod.POST.equals(req.method()) && bodyString(req).contains(LOGIN_MARKER + "=")) {
            handleLoginPost(ctx, req, token);
            return false;
        }
        renderLoginPage(ctx, req, "");
        return false;
    }

    private static void handleLoginPost(ChannelHandlerContext ctx, FullHttpRequest req, String token) {
        Map<String, String> form = parseFormBody(req);
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

    private static boolean validCookie(FullHttpRequest req, String token) {
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
    private static boolean validCookieValue(String value, String token) {
        String[] parts = value.split("\\.", 3);
        if (parts.length != 3) {
            return false;
        }
        long exp;
        try {
            exp = Long.parseLong(parts[0]);
        } catch (NumberFormatException e) {
            return false;
        }
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
        String errHtml = error.isEmpty() ? "" : "<div class=\"err\">" + escape(error) + "</div>";
        byte[] body = html.replace("{{ERROR}}", errHtml).getBytes(StandardCharsets.UTF_8);
        FullHttpResponse resp = FullHttpHelper.buildResponse(ctx, HttpResponseStatus.OK, body);
        resp.headers().set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_HTML);
        FullHttpHelper.writeKeepAlive(ctx, req, resp);
    }

    private static Map<String, String> parseFormBody(FullHttpRequest req) {
        Map<String, String> map = new HashMap<>();
        String body = bodyString(req);
        if (body.isEmpty()) {
            return map;
        }
        for (String pair : body.split("&")) {
            int eq = pair.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String k = urlDecode(pair.substring(0, eq));
            String v = urlDecode(pair.substring(eq + 1));
            map.putIfAbsent(k, v);
        }
        return map;
    }

    private static String bodyString(FullHttpRequest req) {
        ByteBuf buf = req.content();
        return (buf == null || !buf.isReadable()) ? "" : buf.toString(StandardCharsets.UTF_8);
    }

    private static String urlDecode(String s) {
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return s;
        }
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static byte[] hmac(String key, String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String freshNonce() {
        byte[] b = new byte[8];
        RNG.nextBytes(b);
        return hexEncode(b);
    }

    private static String hexEncode(byte[] b) {
        char[] out = new char[b.length * 2];
        char[] hex = "0123456789abcdef".toCharArray();
        for (int i = 0; i < b.length; i++) {
            out[i * 2] = hex[(b[i] >> 4) & 0xf];
            out[i * 2 + 1] = hex[b[i] & 0xf];
        }
        return new String(out);
    }

    private static byte[] hexDecode(String hex) {
        int len = hex.length();
        if (len % 2 != 0) {
            return EmptyArrays.EMPTY_BYTES;
        }
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            int h = Character.digit(hex.charAt(i), 16);
            int l = Character.digit(hex.charAt(i + 1), 16);
            if (h < 0 || l < 0) {
                return EmptyArrays.EMPTY_BYTES;
            }
            out[i / 2] = (byte) ((h << 4) | l);
        }
        return out;
    }
}
