package cn.holmes.rpt.base.mux;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BiConsumer;

import cn.holmes.rpt.base.protocol.MessageType;

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
 */
public final class ChannelBuffer {

    private static final Logger logger = LoggerFactory.getLogger(ChannelBuffer.class);

    private final Channel target;

    private final long highWater;

    private final long lowWater;

    private final long capacity;

    private final BiConsumer<Channel, MessageType> onStateChange;

    /**
     * 以下三个字段只在 target.eventLoop() 上读写
     */
    private final Deque<ByteBuf> pending = new ArrayDeque<>();

    private long bytes;

    private boolean paused;

    private boolean closed;

    public ChannelBuffer(Channel target, long highWater, long lowWater, long capacity, BiConsumer<Channel, MessageType> onStateChange) {
        this.target = target;
        this.highWater = highWater;
        this.lowWater = lowWater;
        this.capacity = capacity;
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
        if (closed) {
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
        // 硬上限只约束已经积压的数据：单条消息不可拆分，队列空时无论多大都必须收下，否则一个超过capacity的HTTP大包会关掉一条本来健康的连接。
        if (!pending.isEmpty() && bytes + size > capacity) {
            // PAUSE 正常工作时积压上界是一个 BDP，走到这里说明对端没有遵守背压。
            // 代理已在 TCP 层向发送方 ACK 过这些字节，丢弃会造成字节流静默损坏，因此关闭该通道。
            logger.warn("通道积压超过硬上限{}字节，关闭该通道,target:{}", capacity, target);
            data.release();
            doRelease();
            target.close();
            return;
        }
        pending.add(data);
        bytes += size;
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
        if (paused && bytes <= lowWater) {
            paused = false;
            onStateChange.accept(target, MessageType.TYPE_RESUME);
        }
    }

    private void doRelease() {
        closed = true;
        ByteBuf buf;
        while ((buf = pending.poll()) != null) {
            buf.release();
        }
        bytes = 0;
        paused = false;
    }
}
