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

// DefaultLimitFactor limit 未配置时取 capacity 的倍数
const DefaultLimitFactor = 8

// ChannelBuf 单个会话的下行积压队列。所有方法可被任意 goroutine 并发调用。
type ChannelBuf struct {
	mu       sync.Mutex
	cond     *sync.Cond
	pending  [][]byte
	bytes    int64
	high     int64
	low      int64
	capacity int64
	limit    int64
	paused   bool
	warned   bool
	// closing 对端已结束会话：不再接收新数据，积压排空后 Pop 返回 nil
	closing bool
	closed  bool

	// sigMu 串行化背压信号的发送；sigSeq/sigSent 保证只有最新状态会发出
	sigMu   sync.Mutex
	sigSeq  uint64
	sigSent uint64

	onPause  func()
	onResume func()
}

// New 创建缓冲区。onPause/onResume 在锁外调用，可安全地在其中发送隧道消息。
//
// capacity 是告警线：PAUSE 生效前隧道里的在途数据仍会到达，积压越过 capacity 属正常，只告警不丢数据。
// limit 是绝对上限，<=0 时取 capacity 的 8 倍，且不低于 capacity。
func New(high, low, capacity, limit int64, onPause, onResume func()) *ChannelBuf {
	if limit <= 0 {
		limit = capacity * DefaultLimitFactor
	} else if limit < capacity {
		limit = capacity
	}
	b := &ChannelBuf{
		high:     high,
		low:      low,
		capacity: capacity,
		limit:    limit,
		onPause:  onPause,
		onResume: onResume,
	}
	b.cond = sync.NewCond(&b.mu)
	return b
}

// TryPush 非阻塞入队，供隧道读循环调用。
//
// ok 为 false 表示积压超过绝对上限，对端没有遵守背压：代理已在 TCP 层向发送方 ACK 过这些字节，
// 丢弃会造成字节流静默损坏，因此由调用方以 RST 放弃该会话。
// warn 为 true 表示本次入队首次越过 capacity 告警线，由调用方记录日志。
func (b *ChannelBuf) TryPush(data []byte) (ok, warn bool) {
	b.mu.Lock()
	if b.closed || b.closing {
		b.mu.Unlock()
		return true, false
	}
	// 上限只约束已经积压的数据：单条消息不可拆分，队列空时无论多大都必须收下，
	// 否则一个超过上限的 HTTP 大包（服务端聚合后单条可达 8MB）会关掉一条本来健康的连接。
	if len(b.pending) > 0 && b.bytes+int64(len(data)) > b.limit {
		b.mu.Unlock()
		return false, false
	}
	b.pending = append(b.pending, data)
	b.bytes += int64(len(data))
	if !b.warned && b.bytes > b.capacity {
		b.warned = true
		warn = true
	}
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
	return true, warn
}

// Pop 阻塞取出一段数据，供会话 writer goroutine 调用。
// 缓冲区关闭、或处于 closing 且积压已排空时返回 nil。
func (b *ChannelBuf) Pop() []byte {
	b.mu.Lock()
	for len(b.pending) == 0 && !b.closed && !b.closing {
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
	// 会话已结束，对端不再读取，RESUME 没有意义
	resume := b.paused && !b.closing && b.bytes <= b.low
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

// Limit 规范化后的绝对上限
func (b *ChannelBuf) Limit() int64 {
	return b.limit
}

// CloseAfterDrain 对端已正常结束会话：拒绝新数据，保留积压由 writer 继续写完，
// 排空后 Pop 返回 nil。幂等。
func (b *ChannelBuf) CloseAfterDrain() {
	b.mu.Lock()
	b.closing = true
	b.mu.Unlock()
	b.cond.Broadcast()
}

// Closing 是否已进入排空关闭流程
func (b *ChannelBuf) Closing() bool {
	b.mu.Lock()
	defer b.mu.Unlock()
	return b.closing
}

// Drained 排空关闭流程已完成：积压全部取出且未被 Close 放弃
func (b *ChannelBuf) Drained() bool {
	b.mu.Lock()
	defer b.mu.Unlock()
	return b.closing && !b.closed && len(b.pending) == 0
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
