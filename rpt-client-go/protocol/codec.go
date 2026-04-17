package protocol

import (
	"compress/gzip"
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

// Conn wraps a TLS connection with gzip compression and message framing.
// It automatically sends TYPE_KEEPALIVE if no write occurs for writerIdleTimeout.
type Conn struct {
	raw      net.Conn
	gzWriter *gzip.Writer
	gzReader *gzip.Reader
	mu       sync.Mutex // protects writes
	rmu      sync.Mutex // protects reads/lazy init

	lastWrite int64 // unix nano of last successful write
	closed    int32 // atomic flag
	stopKA    chan struct{}
}

func NewConn(raw net.Conn) (*Conn, error) {
	c := &Conn{
		raw:      raw,
		gzWriter: gzip.NewWriter(raw),
		stopKA:   make(chan struct{}),
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
			// Send keepalive; if it fails, the connection is dead - the caller's
			// next Send/Receive will error out as well, so we just exit.
			if err := c.Send(&Message{Type: TypeKeepalive}); err != nil {
				return
			}
		}
	}
}

// lazyReader initializes the gzip reader on first read (avoids blocking on construction).
func (c *Conn) lazyReader() (*gzip.Reader, error) {
	if c.gzReader == nil {
		r, err := gzip.NewReader(c.raw)
		if err != nil {
			return nil, fmt.Errorf("gzip reader: %w", err)
		}
		c.gzReader = r
	}
	return c.gzReader, nil
}

func (c *Conn) Send(msg *Message) error {
	c.mu.Lock()
	defer c.mu.Unlock()
	if err := msg.Encode(c.gzWriter); err != nil {
		return err
	}
	if err := c.gzWriter.Flush(); err != nil {
		return err
	}
	atomic.StoreInt64(&c.lastWrite, time.Now().UnixNano())
	return nil
}

func (c *Conn) Receive() (*Message, error) {
	c.rmu.Lock()
	defer c.rmu.Unlock()
	r, err := c.lazyReader()
	if err != nil {
		return nil, err
	}
	return DecodeMessage(r)
}

func (c *Conn) Close() error {
	if !atomic.CompareAndSwapInt32(&c.closed, 0, 1) {
		return nil
	}
	close(c.stopKA)
	c.mu.Lock()
	defer c.mu.Unlock()
	c.gzWriter.Close()
	if c.gzReader != nil {
		c.gzReader.Close()
	}
	return c.raw.Close()
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

// DialTLS connects to the server with TLS and returns a gzip-wrapped Conn.
func DialTLS(addr string, tlsConfig *tls.Config) (*Conn, error) {
	raw, err := tls.Dial("tcp", addr, tlsConfig)
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
