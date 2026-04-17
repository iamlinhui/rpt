package proxy

import (
	"log"
	"sync"
	"time"

	"github.com/holmes/rpt-client-go/protocol"
)

const (
	maxPoolSize  = 128
	corePoolSize = 3
)

// pooledConn wraps a proxy connection with a keepalive goroutine.
type pooledConn struct {
	conn *protocol.Conn
	stop chan struct{}
}

// Pool manages a pool of pre-established proxy connections to the server.
type Pool struct {
	mu       sync.Mutex
	conns    []*pooledConn
	dialFunc func() (*protocol.Conn, error)
}

func NewPool(dialFunc func() (*protocol.Conn, error)) *Pool {
	return &Pool{
		dialFunc: dialFunc,
	}
}

// Init pre-warms the pool with corePoolSize connections.
func (p *Pool) Init() {
	for i := 0; i < corePoolSize; i++ {
		go func() {
			conn, err := p.dialFunc()
			if err != nil {
				log.Printf("[pool] pre-warm connection failed: %v", err)
				return
			}
			p.Put(conn)
		}()
	}
}

// Get returns an active connection from the pool, or dials a new one.
// The returned connection is no longer managed by the pool's keepalive.
func (p *Pool) Get() (*protocol.Conn, error) {
	p.mu.Lock()
	for len(p.conns) > 0 {
		pc := p.conns[0]
		p.conns = p.conns[1:]
		p.mu.Unlock()
		// Stop the keepalive goroutine for this connection
		close(pc.stop)
		return pc.conn, nil
	}
	p.mu.Unlock()
	return p.dialFunc()
}

// Put returns a connection to the pool and starts a keepalive goroutine.
func (p *Pool) Put(conn *protocol.Conn) {
	p.mu.Lock()
	if len(p.conns) >= maxPoolSize {
		p.mu.Unlock()
		conn.Close()
		return
	}
	pc := &pooledConn{
		conn: conn,
		stop: make(chan struct{}),
	}
	p.conns = append(p.conns, pc)
	size := len(p.conns)
	p.mu.Unlock()

	go p.keepalive(pc)
	log.Printf("[pool] idle connections: %d", size)
}

// keepalive sends periodic heartbeats while the connection sits in the pool.
// When the connection is taken out of the pool (via Get), the stop channel is closed
// and this goroutine exits, handing over the connection cleanly.
func (p *Pool) keepalive(pc *pooledConn) {
	ticker := time.NewTicker(30 * time.Second)
	defer ticker.Stop()

	for {
		select {
		case <-pc.stop:
			return
		case <-ticker.C:
			if err := pc.conn.Send(&protocol.Message{Type: protocol.TypeKeepalive}); err != nil {
				p.removeAndClose(pc)
				return
			}
		}
	}
}

func (p *Pool) removeAndClose(pc *pooledConn) {
	p.mu.Lock()
	for i, c := range p.conns {
		if c == pc {
			p.conns = append(p.conns[:i], p.conns[i+1:]...)
			break
		}
	}
	p.mu.Unlock()
	pc.conn.Close()
}

// Clear closes all connections in the pool.
func (p *Pool) Clear() {
	p.mu.Lock()
	conns := p.conns
	p.conns = nil
	p.mu.Unlock()
	for _, pc := range conns {
		close(pc.stop)
		pc.conn.Close()
	}
}
