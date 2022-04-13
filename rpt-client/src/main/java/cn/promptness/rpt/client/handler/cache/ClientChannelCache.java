package cn.promptness.rpt.client.handler.cache;

import io.netty.channel.Channel;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class ClientChannelCache {

    /**
     * remoteChannelId --> localChannel 全局
     */
    private static final Map<String, Channel> LOCAL_TCP_CHANNEL_MAP = new ConcurrentHashMap<>();

    /**
     * requestChannelId --> localHttpChannel 全局
     */
    private static final Map<String, Channel> LOCAL_HTTP_CHANNEL_MAP = new ConcurrentHashMap<>();

    private static final AtomicBoolean CONNECT = new AtomicBoolean(false);

    public static Map<String, Channel> getLocalTcpChannelMap() {
        return LOCAL_TCP_CHANNEL_MAP;
    }

    public static Map<String, Channel> getLocalHttpChannelMap() {
        return LOCAL_HTTP_CHANNEL_MAP;
    }

    public static boolean getConnect() {
        return CONNECT.get();
    }

    public static void setConnect(boolean connect) {
        CONNECT.set(connect);
    }
}
