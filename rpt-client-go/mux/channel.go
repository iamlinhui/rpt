// Package mux 提供通道级带水位线的缓冲区，把流控粒度从"整条隧道"下沉到"单个会话"。
//
// 隧道读循环只负责把下行数据塞进缓冲区（TryPush 永不阻塞），由每个会话自己的
// writer goroutine 消费并写本地连接。慢的本地服务只堆自己的队列，不再拖停
// 同隧道上的其他会话（队头阻塞）。
//
// 积压达到高水位时回调 onPause 向服务端发 TYPE_PAUSE，服务端随即停止从外部
// 连接读取；排空到低水位回调 onResume 发 TYPE_RESUME 恢复。
package mux

import "sync"

// ChannelBuf 单个会话的下行积压队列。所有方法可被任意 goroutine 并发调用。
type ChannelBuf struct {
	mu       sync.Mutex
	cond     *sync.Cond
	pending  [][]byte
	bytes    int64
	high     int64
	low      int64
	capacity int64
	paused   bool
	closed   bool

	// sigMu 串行化背压信号的发送；sigSeq/sigSent 保证只有最新状态会发出
	sigMu   sync.Mutex
	sigSeq  uint64
	sigSent uint64

	onPause  func()
	onResume func()
}

// New 创建缓冲区。onPause/onResume 在锁外调用，可安全地在其中发送隧道消息。
func New(high, low, capacity int64, onPause, onResume func()) *ChannelBuf {
	b := &ChannelBuf{
		high:     high,
		low:      low,
		capacity: capacity,
		onPause:  onPause,
		onResume: onResume,
	}
	b.cond = sync.NewCond(&b.mu)
	return b
}

// TryPush 非阻塞入队，供隧道读循环调用。
//
// 返回 false 表示积压超过硬上限：代理已在 TCP 层向发送方 ACK 过这些字节，
// 丢弃会造成字节流静默损坏，因此由调用方关闭该会话，绝不丢数据。
func (b *ChannelBuf) TryPush(data []byte) bool {
	b.mu.Lock()
	if b.closed {
		b.mu.Unlock()
		return true
	}
	// 硬上限只约束已经积压的数据：单条消息不可拆分，队列空时无论多大都必须收下，
	// 否则一个超过 capacity 的 HTTP 大包（服务端聚合后单条可达 8MB）会关掉一条本来健康的连接。
	if len(b.pending) > 0 && b.bytes+int64(len(data)) > b.capacity {
		b.mu.Unlock()
		return false
	}
	b.pending = append(b.pending, data)
	b.bytes += int64(len(data))
	pause := !b.paused && b.bytes >= b.high
	var seq uint64
	if pause {
		b.paused = true
		b.sigSeq++
		seq = b.sigSeq
	}
	b.cond.Signal()
	b.mu.Unlock()
	if pause {
		b.signal(seq, true)
	}
	return true
}

// Pop 阻塞取出一段数据，供会话 writer goroutine 调用。缓冲区关闭后返回 nil。
func (b *ChannelBuf) Pop() []byte {
	b.mu.Lock()
	for len(b.pending) == 0 && !b.closed {
		b.cond.Wait()
	}
	if len(b.pending) == 0 {
		b.mu.Unlock()
		return nil
	}
	data := b.pending[0]
	b.pending[0] = nil
	b.pending = b.pending[1:]
	b.bytes -= int64(len(data))
	resume := b.paused && b.bytes <= b.low
	var seq uint64
	if resume {
		b.paused = false
		b.sigSeq++
		seq = b.sigSeq
	}
	b.mu.Unlock()
	if resume {
		b.signal(seq, false)
	}
	return data
}

// signal 发送背压信号，保证到达对端的顺序与状态翻转顺序一致。
//
// TryPush（隧道读循环）与 Pop（会话 writer）在不同 goroutine 上翻转状态，若各自
// 直接发送，可能出现 RESUME 抢在 PAUSE 前面到达对端——对端便会永久停在暂停态。
// 序号比当前已发出的旧，说明更新的状态已经发过，本次信号作废即可（对端状态仍与
// 缓冲区一致）。
func (b *ChannelBuf) signal(seq uint64, paused bool) {
	b.sigMu.Lock()
	defer b.sigMu.Unlock()
	if seq <= b.sigSent {
		return
	}
	b.sigSent = seq
	if paused {
		b.onPause()
	} else {
		b.onResume()
	}
}

// Close 释放积压并唤醒 writer goroutine 退出，幂等。
func (b *ChannelBuf) Close() {
	b.mu.Lock()
	b.closed = true
	b.pending = nil
	b.bytes = 0
	b.paused = false
	b.mu.Unlock()
	b.cond.Broadcast()
}
