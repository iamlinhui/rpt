package cn.promptness.rpt.server.cache;

import io.netty.channel.Channel;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ServerChannelCache {

    /**
     * domain --> serverChannel 全局
     */
    private static final Map<String, Channel> SERVER_DOMAIN_CHANNEL_MAP = new ConcurrentHashMap<>();

    private static final Map<String, String> SERVER_DOMAIN_TOKEN = new ConcurrentHashMap<>();

    /**
     * requestChannelId --> httpChannel 全局
     */
    private static final Map<String, Channel> SERVER_HTTP_CHANNEL_MAP = new ConcurrentHashMap<>();

    public static Map<String, Channel> getServerDomainChannelMap() {
        return SERVER_DOMAIN_CHANNEL_MAP;
    }

    public static Map<String, Channel> getServerHttpChannelMap() {
        return SERVER_HTTP_CHANNEL_MAP;
    }

    public static Map<String, String> getServerDomainToken() {
        return SERVER_DOMAIN_TOKEN;
    }
}
