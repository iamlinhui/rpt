package proxy

import (
	"log"
	"sync"

	"github.com/holmes/rpt-client-go/protocol"
)

const (
	maxPoolSize  = 128
	corePoolSize = 3
)

// Pool manages a pool of pre-established proxy connections to the server.
type Pool struct {
	mu       sync.Mutex
	conns    []*protocol.Conn
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
func (p *Pool) Get() (*protocol.Conn, error) {
	p.mu.Lock()
	for len(p.conns) > 0 {
		conn := p.conns[0]
		p.conns = p.conns[1:]
		p.mu.Unlock()
		// Simple liveness check - just return it
		return conn, nil
	}
	p.mu.Unlock()
	return p.dialFunc()
}

// Put returns a connection to the pool.
func (p *Pool) Put(conn *protocol.Conn) {
	p.mu.Lock()
	defer p.mu.Unlock()
	if len(p.conns) >= maxPoolSize {
		conn.Close()
		return
	}
	p.conns = append(p.conns, conn)
	log.Printf("[pool] idle connections: %d", len(p.conns))
}

// Remove removes a specific connection from the pool.
func (p *Pool) Remove(conn *protocol.Conn) {
	p.mu.Lock()
	defer p.mu.Unlock()
	for i, c := range p.conns {
		if c == conn {
			p.conns = append(p.conns[:i], p.conns[i+1:]...)
			return
		}
	}
}

// Clear closes all connections in the pool.
func (p *Pool) Clear() {
	p.mu.Lock()
	defer p.mu.Unlock()
	for _, c := range p.conns {
		c.Close()
	}
	p.conns = nil
}
