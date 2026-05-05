package cn.holmes.rpt.server.handler;

import cn.holmes.rpt.base.config.ServerConfig;
import cn.holmes.rpt.base.serialize.json.JacksonUtil;
import cn.holmes.rpt.base.utils.Config;
import cn.holmes.rpt.base.utils.Constants.Server;
import cn.holmes.rpt.server.cache.ServerChannelCache;
import cn.holmes.rpt.server.cache.TrafficStatsCache;
import cn.holmes.rpt.server.utils.FullHttpHelper;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Dashboard HTTP API + 静态页面处理器
 */
public class DashboardHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest req) throws Exception {
        if (!checkAuth(req)) {
            FullHttpResponse resp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.UNAUTHORIZED, Unpooled.copiedBuffer("Unauthorized", StandardCharsets.UTF_8));
            resp.headers().set(HttpHeaderNames.WWW_AUTHENTICATE, "Basic realm=\"RPT Dashboard\"");
            resp.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
            resp.headers().set(HttpHeaderNames.CONTENT_LENGTH, resp.content().readableBytes());
            FullHttpHelper.writeKeepAlive(ctx, req, resp);
            return;
        }

        String uri = req.uri();
        HttpMethod method = req.method();

        if (method == HttpMethod.GET && "/api/status".equals(uri)) {
            handleStatus(ctx, req);
        } else if (method == HttpMethod.GET && "/api/clients".equals(uri)) {
            handleClients(ctx, req);
        } else if (method == HttpMethod.DELETE && uri.startsWith("/api/clients/")) {
            handleKick(ctx, req, uri.substring("/api/clients/".length()));
        } else if (method == HttpMethod.GET && "/api/domains".equals(uri)) {
            handleDomains(ctx, req);
        } else if (method == HttpMethod.GET && "/favicon.ico".equals(uri)) {
            serveFavicon(ctx, req);
        } else if (method == HttpMethod.GET) {
            serveDashboard(ctx, req);
        } else {
            sendJson(ctx, req, HttpResponseStatus.NOT_FOUND, Collections.singletonMap("error", "Not Found"));
        }
    }

    private boolean checkAuth(FullHttpRequest req) {
        ServerConfig config = Config.getServerConfig();
        String user = config.getDashboardUser();
        String pass = config.getDashboardPassword();
        if (user == null || user.isEmpty()) {
            return true;
        }
        return FullHttpHelper.verifyToken(req, user + ":" + pass);
    }

    private void handleStatus(ChannelHandlerContext ctx, FullHttpRequest req) throws Exception {
        ServerConfig config = Config.getServerConfig();
        long uptime = TrafficStatsCache.uptime();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("uptime", uptime);
        data.put("uptimeText", formatDuration(uptime));
        data.put("totalConnections", TrafficStatsCache.totalConnections());
        data.put("onlineClients", ServerChannelCache.getServerChannelMap().size());
        data.put("proxyChannels", TrafficStatsCache.proxyChannelsTotal());
        data.put("serverPort", config.getServerPort());
        data.put("httpPort", config.getHttpPort());
        data.put("httpsPort", config.getHttpsPort());
        data.put("dashboardPort", config.getDashboardPort());
        sendJson(ctx, req, HttpResponseStatus.OK, data);
    }

    private void handleClients(ChannelHandlerContext ctx, FullHttpRequest req) throws Exception {
        List<Map<String, Object>> clients = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        for (Map.Entry<String, Channel> entry : ServerChannelCache.getServerChannelMap().entrySet()) {
            Channel ch = entry.getValue();
            if (!ch.isActive()) {
                continue;
            }

            Map<String, Object> client = new LinkedHashMap<>();
            client.put("channelId", entry.getKey());
            client.put("clientKey", Optional.ofNullable(ch.attr(Server.CLIENT_KEY).get()).orElse(""));
            client.put("remoteAddress", ch.remoteAddress() != null ? ch.remoteAddress().toString() : "");

            Long connectTime = ch.attr(Server.CONNECT_TIME).get();
            client.put("connectTime", connectTime != null ? sdf.format(new Date(connectTime)) : "");

            // TCP ports
            List<Integer> tcpPorts = new ArrayList<>();
            Map<Integer, ChannelFuture> tcpMap = ch.attr(Server.TCP_PORT_CHANNEL_FUTURE).get();
            if (tcpMap != null) tcpPorts.addAll(tcpMap.keySet());
            client.put("tcpPorts", tcpPorts);

            // UDP ports
            List<Integer> udpPorts = new ArrayList<>();
            Map<Integer, ChannelFuture> udpMap = ch.attr(Server.UDP_PORT_CHANNEL_FUTURE).get();
            if (udpMap != null) udpPorts.addAll(udpMap.keySet());
            client.put("udpPorts", udpPorts);

            // Domains
            List<String> domains = ch.attr(Server.DOMAIN).get();
            client.put("domains", domains != null ? domains : Collections.emptyList());

            // Active sessions
            Map<String, Channel> channels = ch.attr(Server.CHANNELS).get();
            client.put("activeSessions", channels != null ? channels.size() : 0);

            // Proxy channels (由客户端ProxyChannelCache建立的代理连接数)
            client.put("proxyChannels", TrafficStatsCache.proxyChannels(entry.getKey()));

            // Traffic
            long[] traffic = TrafficStatsCache.getTraffic(entry.getKey());
            client.put("bytesIn", traffic[0]);
            client.put("bytesOut", traffic[1]);
            client.put("bytesInText", formatBytes(traffic[0]));
            client.put("bytesOutText", formatBytes(traffic[1]));

            // Speed
            long[] speed = TrafficStatsCache.getSpeed(entry.getKey());
            client.put("speedIn", speed[0]);
            client.put("speedOut", speed[1]);
            client.put("speedInText", formatBytes(speed[0]) + "/s");
            client.put("speedOutText", formatBytes(speed[1]) + "/s");

            clients.add(client);
        }
        sendJson(ctx, req, HttpResponseStatus.OK, clients);
    }

    private void handleKick(ChannelHandlerContext ctx, FullHttpRequest req, String channelId) throws Exception {
        Channel ch = ServerChannelCache.getServerChannelMap().get(channelId);
        Map<String, Object> result = new LinkedHashMap<>();
        if (ch != null && ch.isActive()) {
            ch.close();
            result.put("success", true);
            result.put("message", "Client disconnected");
        } else {
            result.put("success", false);
            result.put("message", "Client not found or already disconnected");
        }
        sendJson(ctx, req, HttpResponseStatus.OK, result);
    }

    private void handleDomains(ChannelHandlerContext ctx, FullHttpRequest req) throws Exception {
        List<Map<String, Object>> domains = new ArrayList<>();
        for (Map.Entry<String, Channel> entry : ServerChannelCache.getServerDomainChannelMap().entrySet()) {
            Map<String, Object> domain = new LinkedHashMap<>();
            domain.put("domain", entry.getKey());
            Channel ch = entry.getValue();
            domain.put("clientKey", ch.attr(Server.CLIENT_KEY).get());
            domain.put("online", ch.isActive());
            domain.put("token", ServerChannelCache.getServerDomainToken().containsKey(entry.getKey()));
            domains.add(domain);
        }
        sendJson(ctx, req, HttpResponseStatus.OK, domains);
    }

    private void serveDashboard(ChannelHandlerContext ctx, FullHttpRequest req) {
        byte[] bytes = FullHttpHelper.loadResource("static/dashboard.html");
        FullHttpResponse resp = FullHttpHelper.buildResponse(ctx, HttpResponseStatus.OK, bytes);
        resp.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/html; charset=UTF-8");
        FullHttpHelper.writeKeepAlive(ctx, req, resp);
    }

    private void serveFavicon(ChannelHandlerContext ctx, FullHttpRequest req) {
        byte[] bytes = FullHttpHelper.loadResource("static/favicon.ico");
        FullHttpResponse resp = FullHttpHelper.buildResponse(ctx, HttpResponseStatus.OK, bytes);
        resp.headers().set(HttpHeaderNames.CONTENT_TYPE, "image/x-icon");
        resp.headers().set(HttpHeaderNames.CACHE_CONTROL, "max-age=86400");
        FullHttpHelper.writeKeepAlive(ctx, req, resp);
    }

    private void sendJson(ChannelHandlerContext ctx, FullHttpRequest req, HttpResponseStatus status, Object data) throws Exception {
        byte[] json = JacksonUtil.getJsonMapper().writeValueAsBytes(data);
        FullHttpResponse resp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, Unpooled.wrappedBuffer(json));
        resp.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8");
        resp.headers().set(HttpHeaderNames.CONTENT_LENGTH, json.length);
        resp.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
        FullHttpHelper.writeKeepAlive(ctx, req, resp);
    }

    private static String formatDuration(long millis) {
        long seconds = millis / 1000;
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        if (days > 0) {
            return String.format("%dd %dh %dm", days, hours, minutes);
        }
        if (hours > 0) {
            return String.format("%dh %dm", hours, minutes);
        }
        return String.format("%dm", minutes);
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        }
        if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024));
        }
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ctx.close();
    }
}
