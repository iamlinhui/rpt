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
)

// Conn wraps a TLS connection with gzip compression and message framing.
type Conn struct {
	raw      net.Conn
	gzWriter *gzip.Writer
	gzReader *gzip.Reader
	mu       sync.Mutex // protects writes
	rmu      sync.Mutex // protects reads/lazy init
}

func NewConn(raw net.Conn) (*Conn, error) {
	return &Conn{
		raw:      raw,
		gzWriter: gzip.NewWriter(raw),
	}, nil
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
	return c.gzWriter.Flush()
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
