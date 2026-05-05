package cn.holmes.rpt.server.cache;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

/**
 * 流量统计收集器（线程安全）
 * 支持总流量统计和滑动窗口流速计算
 */
public class TrafficStatsCache {

    private static final long START_TIME = System.currentTimeMillis();
    private static final AtomicLong TOTAL_CONNECTIONS = new AtomicLong();

    /**
     * serverChannelId → 当前活跃的代理连接数
     */
    private static final Map<String, AtomicLong> PROXY_CHANNELS = new ConcurrentHashMap<>();

    /**
     * serverChannelId → AtomicLongArray[bytesIn, bytesOut]
     */
    private static final Map<String, AtomicLongArray> TRAFFIC = new ConcurrentHashMap<>();

    /**
     * 滑动窗口大小（秒），用于计算平均流速
     */
    private static final int WINDOW_SIZE = 5;

    /**
     * serverChannelId → SpeedWindow
     */
    private static final Map<String, SpeedWindow> SPEED = new ConcurrentHashMap<>();

    /**
     * 滑动窗口：环形缓冲区记录每秒流量
     */
    private static class SpeedWindow {
        private final long[] inSlots = new long[WINDOW_SIZE];
        private final long[] outSlots = new long[WINDOW_SIZE];
        private int currentSlot = -1;
        private long currentSecond = 0;

        /**
         * 记录流量，自动按秒归档
         */
        synchronized void record(int bytes, boolean isIn) {
            long now = System.currentTimeMillis() / 1000;
            if (now != currentSecond) {
                // 跳过的秒数需要清零
                long gap = currentSecond == 0 ? 1 : Math.min(now - currentSecond, WINDOW_SIZE);
                for (long i = 0; i < gap; i++) {
                    currentSlot = (currentSlot + 1) % WINDOW_SIZE;
                    inSlots[currentSlot] = 0;
                    outSlots[currentSlot] = 0;
                }
                currentSecond = now;
            }
            if (isIn) {
                inSlots[currentSlot] += bytes;
            } else {
                outSlots[currentSlot] += bytes;
            }
        }

        /**
         * 计算最近 WINDOW_SIZE 秒的平均流速 [bytesIn/s, bytesOut/s]
         */
        synchronized long[] getSpeed() {
            long now = System.currentTimeMillis() / 1000;
            long totalIn = 0, totalOut = 0;
            int validSlots = 0;
            for (int i = 0; i < WINDOW_SIZE; i++) {
                // 跳过太旧的 slot（如果超过窗口时间没有数据）
                long slotAge = now - currentSecond + ((currentSlot - i + WINDOW_SIZE) % WINDOW_SIZE);
                if (slotAge < WINDOW_SIZE) {
                    int idx = (currentSlot - i + WINDOW_SIZE) % WINDOW_SIZE;
                    totalIn += inSlots[idx];
                    totalOut += outSlots[idx];
                    validSlots++;
                }
            }
            if (validSlots == 0) {
                return new long[]{0, 0};
            }
            return new long[]{totalIn / validSlots, totalOut / validSlots};
        }
    }

    public static void incrementConnections() {
        TOTAL_CONNECTIONS.incrementAndGet();
    }

    public static long totalConnections() {
        return TOTAL_CONNECTIONS.get();
    }

    public static void incrementProxyChannels(String serverChannelId) {
        PROXY_CHANNELS.computeIfAbsent(serverChannelId, k -> new AtomicLong()).incrementAndGet();
    }

    public static void decrementProxyChannels(String serverChannelId) {
        AtomicLong count = PROXY_CHANNELS.get(serverChannelId);
        if (count != null) {
            count.decrementAndGet();
        }
    }

    public static long proxyChannels(String serverChannelId) {
        AtomicLong count = PROXY_CHANNELS.get(serverChannelId);
        return count != null ? count.get() : 0;
    }

    public static long proxyChannelsTotal() {
        long total = 0;
        for (AtomicLong count : PROXY_CHANNELS.values()) {
            total += count.get();
        }
        return total;
    }

    public static long uptime() {
        return System.currentTimeMillis() - START_TIME;
    }

    public static void recordIn(String serverChannelId, int bytes) {
        if (serverChannelId == null || bytes <= 0) {
            return;
        }
        TRAFFIC.computeIfAbsent(serverChannelId, k -> new AtomicLongArray(2)).addAndGet(0, bytes);
        SPEED.computeIfAbsent(serverChannelId, k -> new SpeedWindow()).record(bytes, true);
    }

    public static void recordOut(String serverChannelId, int bytes) {
        if (serverChannelId == null || bytes <= 0) {
            return;
        }
        TRAFFIC.computeIfAbsent(serverChannelId, k -> new AtomicLongArray(2)).addAndGet(1, bytes);
        SPEED.computeIfAbsent(serverChannelId, k -> new SpeedWindow()).record(bytes, false);
    }

    public static long[] getTraffic(String serverChannelId) {
        AtomicLongArray arr = TRAFFIC.get(serverChannelId);
        if (arr == null) {
            return new long[]{0, 0};
        }
        return new long[]{arr.get(0), arr.get(1)};
    }

    /**
     * 获取流速 [bytesIn/s, bytesOut/s]，最近5秒平均值
     */
    public static long[] getSpeed(String serverChannelId) {
        SpeedWindow window = SPEED.get(serverChannelId);
        if (window == null) {
            return new long[]{0, 0};
        }
        return window.getSpeed();
    }

    public static void remove(String serverChannelId) {
        TRAFFIC.remove(serverChannelId);
        SPEED.remove(serverChannelId);
        PROXY_CHANNELS.remove(serverChannelId);
    }
}
