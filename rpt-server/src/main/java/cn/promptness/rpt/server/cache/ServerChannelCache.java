package cn.promptness.rpt.server.cache;

import io.netty.channel.Channel;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ServerChannelCache {

    /**
     * domain --> serverChannel 全局
     */
    private static final Map<String, Channel> SERVER_DOMAIN_CHANNEL_MAP = new ConcurrentHashMap<>();

    private static final Map<String, String> SERVER_DOMAIN_TOKEN = new ConcurrentHashMap<>();

    public static Map<String, Channel> getServerDomainChannelMap() {
        return SERVER_DOMAIN_CHANNEL_MAP;
    }

    public static Map<String, String> getServerDomainToken() {
        return SERVER_DOMAIN_TOKEN;
    }

    private static final Map<String, Channel> SERVER_CHANNEL_MAP = new ConcurrentHashMap<>();

    public static Map<String, Channel> getServerChannelMap() {
        return SERVER_CHANNEL_MAP;
    }

    public static void remove(List<String> domainList) {
        for (String domain : domainList) {
            SERVER_DOMAIN_CHANNEL_MAP.remove(domain);
            SERVER_DOMAIN_TOKEN.remove(domain);
        }
    }
}
