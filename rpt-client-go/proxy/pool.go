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
// Keepalive is handled automatically by protocol.Conn itself, so the pool
// only tracks the connections.
type Pool struct {
	mu       sync.Mutex
	conns    []*protocol.Conn
	dialFunc func() (*protocol.Conn, error)
}

func NewPool(dialFunc func() (*protocol.Conn, error)) *Pool {
	return &Pool{dialFunc: dialFunc}
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
// Dead connections (closed by keepalive failure) are automatically discarded.
func (p *Pool) Get() (*protocol.Conn, error) {
	p.mu.Lock()
	for len(p.conns) > 0 {
		conn := p.conns[0]
		p.conns = p.conns[1:]
		if !conn.IsClosed() {
			p.mu.Unlock()
			return conn, nil
		}
		// Connection already dead, skip it
	}
	p.mu.Unlock()
	return p.dialFunc()
}

// Put returns a connection to the pool.
func (p *Pool) Put(conn *protocol.Conn) {
	if conn.IsClosed() {
		return
	}
	p.mu.Lock()
	if len(p.conns) >= maxPoolSize {
		p.mu.Unlock()
		conn.Close()
		return
	}
	p.conns = append(p.conns, conn)
	size := len(p.conns)
	p.mu.Unlock()
	log.Printf("[pool] idle connections: %d", size)
}

// Clear closes all connections in the pool.
func (p *Pool) Clear() {
	p.mu.Lock()
	conns := p.conns
	p.conns = nil
	p.mu.Unlock()
	for _, c := range conns {
		c.Close()
	}
}
