package cn.promptness.rpt.client.handler.cache;

import io.netty.channel.Channel;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ClientChannelCache {

    /**
     * remoteChannelId --> localChannel 全局
     */
    private static final Map<String, Channel> LOCAL_TCP_CHANNEL_MAP = new ConcurrentHashMap<>();

    /**
     * requestChannelId --> localHttpChannel 全局
     */
    private static final Map<String, Channel> LOCAL_HTTP_CHANNEL_MAP = new ConcurrentHashMap<>();


    public static Map<String, Channel> getLocalTcpChannelMap() {
        return LOCAL_TCP_CHANNEL_MAP;
    }

    public static Map<String, Channel> getLocalHttpChannelMap() {
        return LOCAL_HTTP_CHANNEL_MAP;
    }

}
