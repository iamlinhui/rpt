package cn.holmes.rpt.server.cache;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 服务端所有UDP端口共享的会话与待连接缓冲配额，生命周期与RegisterExecutor一致。
 */
public class UdpSessionQuota {

    private final AtomicInteger sessionCount = new AtomicInteger();

    private final AtomicLong bufferedBytes = new AtomicLong();

    public boolean acquireSession(int limit) {
        int current;
        do {
            current = sessionCount.get();
            if (current >= limit) {
                return false;
            }
        } while (!sessionCount.compareAndSet(current, current + 1));
        return true;
    }

    public void releaseSession() {
        sessionCount.decrementAndGet();
    }

    public boolean acquireBuffer(long size, long limit) {
        long current;
        do {
            current = bufferedBytes.get();
            if (size > limit - current) {
                return false;
            }
        } while (!bufferedBytes.compareAndSet(current, current + size));
        return true;
    }

    public void releaseBuffer(long size) {
        bufferedBytes.addAndGet(-size);
    }
}
