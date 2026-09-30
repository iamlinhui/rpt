package cn.holmes.rpt.base.mux;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelOutboundBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import cn.holmes.rpt.base.protocol.MessageType;
import cn.holmes.rpt.base.utils.ChannelUtils;

/**
 * 通道级带水位线缓冲区：隧道下行数据先入本通道队列，再按目标连接的可写性排空。
 * <p>
 * 共享隧道上一个慢通道不再拖停整条隧道——积压只堆在自己的队列里，达到高水位时
 * 通过 {@code onStateChange} 回调向对端发 TYPE_PAUSE / TYPE_RESUME：积压达到高水位时
 * 通知对端停止读取该通道的源数据，排空到低水位再恢复。
 * <p>
 * 线程模型：所有状态只在目标连接自己的 eventLoop 上访问，{@link #write} 从隧道
 * eventLoop 调用时会自动 hop 过去。因此队列用非线程安全的 {@link ArrayDeque} 即可，
 * 且天然保证写入顺序——若队列和直写分处两个线程，会出现后到的数据抢先写出的乱序。
 * <p>
 * 关闭语义：对端正常结束（TYPE_DISCONNECTED）走 {@link #closeAfterDrain}，积压全部写出后再发 FIN；
 * 数据已不完整的场景（隧道断开、积压超绝对上限、排空停滞）走 {@link ChannelUtils#reset}，发 RST 让接收方感知失败，
 * 而不是用 FIN 把截断的流伪装成正常结束。
 */
public final class ChannelBuffer {

    private static final Logger logger = LoggerFactory.getLogger(ChannelBuffer.class);

    /**
     * 未配置绝对上限时取 capacity 的倍数
     */
    private static final int DEFAULT_LIMIT_FACTOR = 8;

    /**
     * 关闭排空的停滞超时：连续这么久没有任何字节写出才放弃，慢但持续读取的连接不受影响
     */
    private static final long DEFAULT_DRAIN_TIMEOUT_MILLIS = 30_000L;

    /**
     * 停滞检测的采样次数（每个超时周期内）
     */
    private static final int DRAIN_CHECKS = 6;

    private final Channel target;

    private final long highWater;

    private final long lowWater;

    private final long capacity;

    private final long limit;

    private final long drainTimeoutMillis;

    private final BiConsumer<Channel, MessageType> onStateChange;

    /**
     * 以下字段只在 target.eventLoop() 上读写
     */
    private final Deque<ByteBuf> pending = new ArrayDeque<>();

    private long bytes;

    private boolean paused;

    private boolean closed;

    private boolean closing;

    private boolean finishing;

    private boolean warned;

    private ScheduledFuture<?> drainWatchdog;

    private long lastRemaining;

    private int stalledChecks;

    public ChannelBuffer(Channel target, long highWater, long lowWater, long capacity, long limit, BiConsumer<Channel, MessageType> onStateChange) {
        this(target, highWater, lowWater, capacity, limit, DEFAULT_DRAIN_TIMEOUT_MILLIS, onStateChange);
    }

    ChannelBuffer(Channel target, long highWater, long lowWater, long capacity, long limit, long drainTimeoutMillis, BiConsumer<Channel, MessageType> onStateChange) {
        this.target = target;
        this.highWater = highWater;
        this.lowWater = lowWater;
        this.capacity = capacity;
        this.limit = limit > 0 ? Math.max(limit, capacity) : capacity * DEFAULT_LIMIT_FACTOR;
        this.drainTimeoutMillis = drainTimeoutMillis;
        this.onStateChange = onStateChange;
    }

    /**
     * 写入数据，可从任意线程调用（隧道 eventLoop）。data 的所有权移交给本缓冲区。
     */
    public void write(ByteBuf data) {
        if (target.eventLoop().inEventLoop()) {
            offerAndDrain(data);
            return;
        }
        try {
            target.eventLoop().execute(() -> offerAndDrain(data));
        } catch (RejectedExecutionException e) {
            // eventLoop已关闭，数据无处可去，释放引用避免泄漏
            data.release();
        }
    }

    /**
     * 排空积压，必须在 target.eventLoop() 上调用（目标连接可写性变化时）。
     */
    public void drain() {
        if (target.eventLoop().inEventLoop()) {
            doDrain();
            return;
        }
        target.eventLoop().execute(this::doDrain);
    }

    /**
     * 对端正常结束该会话：停止读取目标连接，积压全部写出并刷到内核后再关闭。
     * <p>
     * 与 {@link #write} 同样 hop 到 target.eventLoop()，因此排在此前所有写入之后执行，不会越过积压数据。
     */
    public void closeAfterDrain() {
        if (target.eventLoop().inEventLoop()) {
            doCloseAfterDrain();
            return;
        }
        try {
            target.eventLoop().execute(this::doCloseAfterDrain);
        } catch (RejectedExecutionException e) {
            target.close();
        }
    }

    /**
     * 释放全部积压，目标连接断开时调用。
     */
    public void release() {
        if (target.eventLoop().inEventLoop()) {
            doRelease();
            return;
        }
        target.eventLoop().execute(this::doRelease);
    }

    private void offerAndDrain(ByteBuf data) {
        // 对端已结束该会话，不应再有数据；关闭流程中的数据无法保证顺序，直接丢弃。
        // 目标连接已关闭时同样丢弃：隧道线程可能在 channelInactive 释放缓冲区之后才懒创建出新缓冲区，
        // 这里若入队，之后再没有人 release，数据会一直占住（池化直接）内存，还会给对端发无意义的 PAUSE
        if (closed || closing || !target.isOpen()) {
            data.release();
            return;
        }
        // 快路径：队列空且目标可写时直写。队列空意味着没有数据排在前面，且所有队列操作都在target.eventLoop()上，直写与走队列的顺序一致。
        // 绕开队列同时避开了单条消息本身就超过水位线的情况——HTTP代理聚合后单条TYPE_DATA可达8MB，走队列会白发一对PAUSE/RESUME。
        if (!paused && pending.isEmpty() && target.isWritable()) {
            target.writeAndFlush(data);
            return;
        }
        int size = data.readableBytes();
        // 上限只约束已经积压的数据：单条消息不可拆分，队列空时无论多大都必须收下，否则一个超过上限的HTTP大包会关掉一条本来健康的连接。
        if (!pending.isEmpty() && bytes + size > limit) {
            // PAUSE 发出后仍会有一整条隧道管道（出站缓冲 + 两端内核缓冲）的在途数据到达，超过 capacity 属正常；
            // 超过绝对上限说明对端没有遵守背压。这些字节已在 TCP 层向发送方 ACK 过，无法补发，以 RST 告知接收方传输失败。
            logger.warn("通道积压超过绝对上限{}字节，放弃该通道,target:{}", limit, target);
            data.release();
            abort();
            return;
        }
        pending.add(data);
        bytes += size;
        if (!warned && bytes > capacity) {
            warned = true;
            logger.warn("通道积压{}字节超过capacity{}字节，对端背压响应偏慢,target:{}", bytes, capacity, target);
        }
        if (!paused && bytes >= highWater) {
            paused = true;
            onStateChange.accept(target, MessageType.TYPE_PAUSE);
        }
        doDrain();
    }

    private void doDrain() {
        if (closed) {
            return;
        }
        while (target.isWritable() && !pending.isEmpty()) {
            ByteBuf buf = pending.poll();
            bytes -= buf.readableBytes();
            target.writeAndFlush(buf);
        }
        if (closing) {
            // 对端已移除该会话，不再发送背压信号
            finishIfDrained();
            return;
        }
        if (paused && bytes <= lowWater) {
            paused = false;
            onStateChange.accept(target, MessageType.TYPE_RESUME);
        }
    }

    private void doCloseAfterDrain() {
        if (closed || closing) {
            return;
        }
        closing = true;
        // 会话已从路由表移除，继续读取的数据无处可去（HTTP入口读到数据还会直接关连接），先停读
        target.config().setAutoRead(false);
        lastRemaining = remaining();
        long interval = Math.max(1L, drainTimeoutMillis / DRAIN_CHECKS);
        try {
            drainWatchdog = target.eventLoop().scheduleAtFixedRate(this::checkDrainProgress, interval, interval, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException ignored) {
            // eventLoop关闭中，连接随之关闭
        }
        doDrain();
    }

    private void finishIfDrained() {
        if (finishing || !pending.isEmpty()) {
            return;
        }
        finishing = true;
        // 空写的 future 在此前所有写入刷出后才完成，挂 CLOSE 保证 FIN 在最后一个字节之后；直接 close() 会丢弃出站缓冲
        target.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
    }

    /**
     * 停滞检测：剩余字节（队列 + 出站缓冲）有减少即视为有进展，连续一个超时周期无进展才放弃
     */
    private void checkDrainProgress() {
        if (closed || !target.isOpen()) {
            cancelWatchdog();
            return;
        }
        long now = remaining();
        if (now < lastRemaining) {
            lastRemaining = now;
            stalledChecks = 0;
            return;
        }
        if (++stalledChecks < DRAIN_CHECKS) {
            return;
        }
        logger.warn("通道关闭排空停滞超过{}ms，放弃剩余{}字节,target:{}", drainTimeoutMillis, now, target);
        abort();
    }

    /**
     * 数据已不完整：释放积压并以 RST 关闭目标连接
     */
    private void abort() {
        doRelease();
        ChannelUtils.reset(target);
    }

    private long remaining() {
        ChannelOutboundBuffer outbound = target.unsafe().outboundBuffer();
        return bytes + (outbound != null ? outbound.totalPendingWriteBytes() : 0);
    }

    private void cancelWatchdog() {
        if (drainWatchdog != null) {
            drainWatchdog.cancel(false);
            drainWatchdog = null;
        }
    }

    private void doRelease() {
        closed = true;
        cancelWatchdog();
        ByteBuf buf;
        while ((buf = pending.poll()) != null) {
            buf.release();
        }
        bytes = 0;
        paused = false;
    }
}
