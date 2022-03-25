package cn.promptness.rpt.base.utils;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

public class ScheduledThreadFactory implements ThreadFactory {

    private final AtomicLong threadNumber = new AtomicLong(1L);
    private final String namePrefix;
    private final boolean daemon;
    private static final ThreadGroup THREAD_GROUP = new ThreadGroup("rpt");

    public static ThreadFactory create(String namePrefix, boolean daemon) {
        return new ScheduledThreadFactory(namePrefix, daemon);
    }

    private ScheduledThreadFactory(String namePrefix, boolean daemon) {
        this.namePrefix = namePrefix;
        this.daemon = daemon;
    }

    @Override
    public Thread newThread(Runnable runnable) {
        Thread thread = new Thread(THREAD_GROUP, runnable, THREAD_GROUP.getName() + "-" + this.namePrefix + "-" + this.threadNumber.getAndIncrement());
        thread.setDaemon(this.daemon);
        if (thread.getPriority() != Thread.NORM_PRIORITY) {
            thread.setPriority(Thread.NORM_PRIORITY);
        }
        return thread;
    }
}
