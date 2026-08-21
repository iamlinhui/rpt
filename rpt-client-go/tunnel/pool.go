package tunnel

import (
	"log"
	"sync"
	"sync/atomic"
	"time"

	"rpt-client-go/protocol"
)

const (
	// reconnectBackoffLimit 补连退避上限（秒）
	reconnectBackoffLimit = 30
	// maxTunnelCount 隧道数量上限
	maxTunnelCount = 16
	// defaultTunnelCount 默认隧道数量
	defaultTunnelCount = 4
)

// Pool 共享数据隧道池：控制通道认证成功后建立n条常驻隧道，
// k个外部会话通过channelId复用这n条隧道（轮询选路）。
// 隧道断开自动补连，控制通道断开时Close关闭全部。
type Pool struct {
	mu        sync.Mutex
	tunnels   []*protocol.Conn
	counter   int64
	dial      func() (*protocol.Conn, error)
	onMessage func(*protocol.Conn, *protocol.Message)
	onClosed  func(*protocol.Conn)
	clientKey string
	serverId  string
	stopCh    chan struct{}
	stopped   bool
}

func NewPool(dial func() (*protocol.Conn, error), onMessage func(*protocol.Conn, *protocol.Message), onClosed func(*protocol.Conn)) *Pool {
	return &Pool{
		dial:      dial,
		onMessage: onMessage,
		onClosed:  onClosed,
		stopCh:    make(chan struct{}),
		stopped:   true,
	}
}

// Init 建立n条共享隧道（每次控制通道认证成功后调用）
func (p *Pool) Init(clientKey, serverId string, count int) {
	if count < 1 {
		count = defaultTunnelCount
	}
	if count > maxTunnelCount {
		count = maxTunnelCount
	}
	p.mu.Lock()
	p.clientKey = clientKey
	p.serverId = serverId
	p.stopCh = make(chan struct{})
	p.stopped = false
	stopCh := p.stopCh
	p.mu.Unlock()
	log.Printf("[tunnel] establishing %d shared data tunnels", count)
	for i := 0; i < count; i++ {
		go p.connect(stopCh, 0)
	}
}

// Pick 轮询选择一条活跃隧道，无可用隧道返回nil
func (p *Pool) Pick() *protocol.Conn {
	p.mu.Lock()
	defer p.mu.Unlock()
	n := len(p.tunnels)
	if n == 0 {
		return nil
	}
	for i := 0; i < n; i++ {
		idx := int((atomic.AddInt64(&p.counter, 1) - 1) % int64(n))
		if idx < 0 {
			idx += n
		}
		t := p.tunnels[idx]
		if !t.IsClosed() {
			return t
		}
	}
	return nil
}

// Close 控制通道断开时关闭所有隧道并停止补连
func (p *Pool) Close() {
	p.mu.Lock()
	if !p.stopped {
		p.stopped = true
		close(p.stopCh)
	}
	conns := p.tunnels
	p.tunnels = nil
	p.mu.Unlock()
	for _, c := range conns {
		c.Close()
	}
}

// connect 建立单条隧道并发送TypeTunnel注册归属
func (p *Pool) connect(stopCh chan struct{}, backoff int) {
	select {
	case <-stopCh:
		return
	default:
	}
	conn, err := p.dial()
	if err != nil {
		log.Printf("[tunnel] dial failed: %v", err)
		p.scheduleReconnect(stopCh, backoff)
		return
	}
	p.mu.Lock()
	clientKey, serverId := p.clientKey, p.serverId
	p.mu.Unlock()
	// 首个消息注册隧道归属，服务端据此绑定serverId与clientKey校验
	if err := conn.Send(&protocol.Message{
		Type: protocol.TypeTunnel,
		Meta: &protocol.Meta{ClientKey: clientKey, ServerId: serverId},
	}); err != nil {
		conn.Close()
		p.scheduleReconnect(stopCh, backoff)
		return
	}
	p.mu.Lock()
	current := !p.stopped && p.stopCh == stopCh
	if current {
		p.tunnels = append(p.tunnels, conn)
	}
	size := len(p.tunnels)
	p.mu.Unlock()
	if !current {
		conn.Close()
		return
	}
	log.Printf("[tunnel] established, current count: %d", size)
	go p.readLoop(conn, stopCh)
}

// readLoop 读取隧道下行消息：会话消息按channelId回调分发，隧道断开自动补连
func (p *Pool) readLoop(conn *protocol.Conn, stopCh chan struct{}) {
	for {
		msg, err := conn.Receive()
		if err != nil {
			p.remove(conn)
			conn.Close()
			if p.onClosed != nil {
				p.onClosed(conn)
			}
			log.Printf("[tunnel] tunnel closed, reconnecting: %v", err)
			p.scheduleReconnect(stopCh, 0)
			return
		}
		if msg.Type == protocol.TypeKeepalive {
			conn.Send(&protocol.Message{Type: protocol.TypeKeepalive})
			continue
		}
		if p.onMessage != nil {
			p.onMessage(conn, msg)
		}
	}
}

func (p *Pool) remove(conn *protocol.Conn) {
	p.mu.Lock()
	defer p.mu.Unlock()
	for i, t := range p.tunnels {
		if t == conn {
			p.tunnels = append(p.tunnels[:i], p.tunnels[i+1:]...)
			return
		}
	}
}

// scheduleReconnect 延迟补连，控制通道断开（stopCh关闭）则放弃
func (p *Pool) scheduleReconnect(stopCh chan struct{}, backoff int) {
	delay := backoff + 3
	if delay > reconnectBackoffLimit {
		delay = reconnectBackoffLimit
	}
	go func() {
		timer := time.NewTimer(time.Duration(delay) * time.Second)
		defer timer.Stop()
		select {
		case <-timer.C:
			p.connect(stopCh, delay)
		case <-stopCh:
		}
	}()
}
