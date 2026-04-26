package protocol

import (
	"crypto/tls"
	"crypto/x509"
	"fmt"
	"io"
	"net"
	"os"
	"sync"
	"sync/atomic"
	"time"
)

// writerIdleTimeout is how long to wait before sending an automatic keepalive.
// Mirrors Java's IdleCheckHandler(60, 30, 0) - writer idle = 30s.
const writerIdleTimeout = 30 * time.Second

// readIdleTimeout is how long to wait for any incoming data before considering
// the connection dead. The server sends idle-check at 60s, so 90s is safe.
const readIdleTimeout = 90 * time.Second

// Conn wraps a TLS connection with message framing.
// It automatically sends TYPE_KEEPALIVE if no write occurs for writerIdleTimeout.
type Conn struct {
	raw net.Conn
	mu  sync.Mutex // protects writes
	rmu sync.Mutex // protects reads

	lastWrite int64 // unix nano of last successful write
	closed    int32 // atomic flag
	stopKA    chan struct{}
}

func NewConn(raw net.Conn) (*Conn, error) {
	// Enable TCP keepalive so the OS detects dead connections faster
	if tc, ok := raw.(*net.TCPConn); ok {
		tc.SetKeepAlive(true)
		tc.SetKeepAlivePeriod(30 * time.Second)
	} else if tlsConn, ok := raw.(*tls.Conn); ok {
		if tc, ok := tlsConn.NetConn().(*net.TCPConn); ok {
			tc.SetKeepAlive(true)
			tc.SetKeepAlivePeriod(30 * time.Second)
		}
	}
	c := &Conn{
		raw:    raw,
		stopKA: make(chan struct{}),
	}
	atomic.StoreInt64(&c.lastWrite, time.Now().UnixNano())
	go c.keepaliveLoop()
	return c, nil
}

// keepaliveLoop sends an automatic keepalive when writer has been idle longer
// than writerIdleTimeout. Runs for the lifetime of the connection.
func (c *Conn) keepaliveLoop() {
	ticker := time.NewTicker(writerIdleTimeout / 2)
	defer ticker.Stop()
	for {
		select {
		case <-c.stopKA:
			return
		case <-ticker.C:
			if atomic.LoadInt32(&c.closed) != 0 {
				return
			}
			last := time.Unix(0, atomic.LoadInt64(&c.lastWrite))
			if time.Since(last) < writerIdleTimeout {
				continue
			}
			// Send keepalive; if it fails, the connection is dead — close it
			// so that the pool can detect and discard it.
			if err := c.Send(&Message{Type: TypeKeepalive}); err != nil {
				c.Close()
				return
			}
		}
	}
}

func (c *Conn) Send(msg *Message) error {
	c.mu.Lock()
	defer c.mu.Unlock()
	c.raw.SetWriteDeadline(time.Now().Add(30 * time.Second))
	if err := msg.Encode(c.raw); err != nil {
		return err
	}
	c.raw.SetWriteDeadline(time.Time{})
	atomic.StoreInt64(&c.lastWrite, time.Now().UnixNano())
	return nil
}

func (c *Conn) Receive() (*Message, error) {
	c.rmu.Lock()
	defer c.rmu.Unlock()
	c.raw.SetReadDeadline(time.Now().Add(readIdleTimeout))
	msg, err := DecodeMessage(c.raw)
	if err != nil {
		return nil, err
	}
	c.raw.SetReadDeadline(time.Time{})
	return msg, nil
}

func (c *Conn) Close() error {
	if !atomic.CompareAndSwapInt32(&c.closed, 0, 1) {
		return nil
	}
	close(c.stopKA)
	return c.raw.Close()
}

// IsClosed returns whether the connection has been closed.
func (c *Conn) IsClosed() bool {
	return atomic.LoadInt32(&c.closed) != 0
}

func (c *Conn) Raw() net.Conn {
	return c.raw
}

// LoadTLSConfig loads client TLS configuration from cert files.
func LoadTLSConfig(certFile, keyFile, caFile string) (*tls.Config, error) {
	cert, err := tls.LoadX509KeyPair(certFile, keyFile)
	if err != nil {
		return nil, fmt.Errorf("load key pair: %w", err)
	}
	caCert, err := os.ReadFile(caFile)
	if err != nil {
		return nil, fmt.Errorf("read ca: %w", err)
	}
	pool := x509.NewCertPool()
	pool.AppendCertsFromPEM(caCert)
	return &tls.Config{
		Certificates:       []tls.Certificate{cert},
		RootCAs:            pool,
		InsecureSkipVerify: true,
	}, nil
}

// dialTimeout is the maximum time to wait for a TCP+TLS connection to establish.
const dialTimeout = 15 * time.Second

// DialTLS connects to the server with TLS and returns a framed Conn.
func DialTLS(addr string, tlsConfig *tls.Config) (*Conn, error) {
	dialer := &net.Dialer{Timeout: dialTimeout}
	raw, err := tls.DialWithDialer(dialer, "tcp", addr, tlsConfig)
	if err != nil {
		return nil, err
	}
	conn, err := NewConn(raw)
	if err != nil {
		raw.Close()
		return nil, err
	}
	return conn, nil
}

// ReadWriter is an interface for reading/writing bytes (for local connections)
type ReadWriter interface {
	io.ReadWriteCloser
}
