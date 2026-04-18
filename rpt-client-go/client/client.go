package client

import (
	"crypto/tls"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net"
	"sync"
	"time"

	"rpt-client-go/config"
	"rpt-client-go/protocol"
	"rpt-client-go/proxy"
)

type Client struct {
	cfg        *config.ClientConfig
	tlsConfig  *tls.Config
	pool       *proxy.Pool
	serverAddr string

	mu         sync.Mutex
	channels   map[string]io.Closer // channelId -> local connection
	stopped    bool
	resetDelay bool // set to true after successful auth
}

func New(cfg *config.ClientConfig, tlsConfig *tls.Config) *Client {
	addr := fmt.Sprintf("%s:%d", cfg.ServerIp, cfg.ServerPort)
	c := &Client{
		cfg:        cfg,
		tlsConfig:  tlsConfig,
		serverAddr: addr,
		channels:   make(map[string]io.Closer),
	}
	c.pool = proxy.NewPool(func() (*protocol.Conn, error) {
		return protocol.DialTLS(addr, tlsConfig)
	})
	return c
}

func (c *Client) Run() {
	delay := 0
	for {
		if c.stopped {
			return
		}
		if delay > 0 {
			log.Printf("[client] reconnecting in %d seconds...", delay)
			time.Sleep(time.Duration(delay) * time.Second)
		}
		err := c.connect()
		if err != nil {
			log.Printf("[client] connection error: %v", err)
		}
		// Clear local channels and pool on disconnect
		c.clearChannels()
		c.pool.Clear()
		if c.resetDelay {
			c.resetDelay = false
			delay = 0
		}
		delay += 3
		if delay > 300 {
			delay = 300
		}
	}
}

func (c *Client) Stop() {
	c.stopped = true
	c.clearChannels()
}

func (c *Client) clearChannels() {
	c.mu.Lock()
	defer c.mu.Unlock()
	for id, ch := range c.channels {
		ch.Close()
		delete(c.channels, id)
	}
}

func (c *Client) connect() error {
	log.Printf("[client] connecting to %s", c.serverAddr)
	serverConn, err := protocol.DialTLS(c.serverAddr, c.tlsConfig)
	if err != nil {
		return fmt.Errorf("dial server: %w", err)
	}
	defer serverConn.Close()

	// Send register message
	rcList, _ := json.Marshal(c.cfg.Config)
	regMsg := &protocol.Message{
		Type: protocol.TypeRegister,
		Meta: &protocol.Meta{
			ClientKey:        c.cfg.ClientKey,
			RemoteConfigList: rcList,
		},
	}
	if err := serverConn.Send(regMsg); err != nil {
		return fmt.Errorf("send register: %w", err)
	}

	// Keepalive is handled automatically by protocol.Conn (writer-idle 30s).

	// Read messages loop
	for {
		msg, err := serverConn.Receive()
		if err != nil {
			return fmt.Errorf("receive: %w", err)
		}
		c.handleMessage(serverConn, msg)
	}
}

func (c *Client) handleMessage(serverConn *protocol.Conn, msg *protocol.Message) {
	switch msg.Type {
	case protocol.TypeAuth:
		c.handleAuth(serverConn, msg)
	case protocol.TypeConnected:
		c.handleConnected(serverConn, msg)
	case protocol.TypeData:
		c.handleData(msg)
	case protocol.TypeDisconnected:
		c.handleDisconnected(msg)
	case protocol.TypeKeepalive:
		// respond with keepalive
		serverConn.Send(&protocol.Message{Type: protocol.TypeKeepalive})
	}
}

func (c *Client) handleAuth(serverConn *protocol.Conn, msg *protocol.Message) {
	if msg.Meta == nil {
		return
	}
	for _, result := range msg.Meta.RemoteResult {
		log.Printf("[auth] %s", result)
	}
	if msg.Meta.Connection {
		log.Printf("[auth] connected successfully, clientKey: %s", msg.Meta.ClientKey)
		c.resetDelay = true
		c.pool.Init()
	} else {
		log.Printf("[auth] connection rejected, clientKey: %s", msg.Meta.ClientKey)
	}
}

func (c *Client) handleConnected(serverConn *protocol.Conn, msg *protocol.Message) {
	if msg.Meta == nil {
		return
	}
	rc := msg.Meta.GetRemoteConfig()
	if rc == nil {
		return
	}

	// For HTTP proxy, look up local config by domain
	if rc.ProxyType == "HTTP" {
		httpCfg := c.cfg.GetHttpConfig(rc.Domain)
		if httpCfg == nil {
			return
		}
		rc.LocalIp = httpCfg.LocalIp
		rc.LocalPort = httpCfg.LocalPort
		msg.Meta.SetRemoteConfig(rc)
	}

	// Get a proxy connection from pool
	proxyConn, err := c.pool.Get()
	if err != nil {
		log.Printf("[connected] failed to get proxy conn: %v", err)
		serverConn.Send(&protocol.Message{
			Type: protocol.TypeDisconnected,
			Meta: msg.Meta,
		})
		return
	}

	if rc.ProxyType == "UDP" {
		c.connectUDP(serverConn, proxyConn, msg.Meta, rc)
	} else {
		c.connectTCP(serverConn, proxyConn, msg.Meta, rc)
	}
}

func (c *Client) connectTCP(serverConn *protocol.Conn, proxyConn *protocol.Conn, meta *protocol.Meta, rc *protocol.RemoteConfigMsg) {
	localAddr := fmt.Sprintf("%s:%d", rc.LocalIp, rc.LocalPort)
	localConn, err := net.DialTimeout("tcp", localAddr, 5*time.Second)
	if err != nil {
		log.Printf("[tcp] connect local %s failed: %v", localAddr, err)
		c.pool.Put(proxyConn)
		serverConn.Send(&protocol.Message{
			Type: protocol.TypeDisconnected,
			Meta: meta,
		})
		return
	}

	channelId := meta.ChannelId
	c.mu.Lock()
	c.channels[channelId] = localConn
	c.mu.Unlock()

	// Send connected ACK
	proxyConn.Send(&protocol.Message{
		Type: protocol.TypeConnected,
		Meta: meta,
	})

	// Session end is coordinated via once/done so pool.Put runs exactly once
	// and the peer goroutine doesn't send on a pooled connection.
	var once sync.Once
	done := make(chan struct{})
	endSession := func(returnToPool bool) {
		once.Do(func() {
			close(done)
			localConn.Close()
			c.removeChannel(channelId)
			if returnToPool {
				c.pool.Put(proxyConn)
			} else {
				proxyConn.Close()
			}
		})
	}

	// Bidirectional relay
	go c.relayLocalToProxy(localConn, proxyConn, meta, done, endSession)
	go c.relayProxyToLocal(localConn, proxyConn, done, endSession)
}

func (c *Client) connectUDP(serverConn *protocol.Conn, proxyConn *protocol.Conn, meta *protocol.Meta, rc *protocol.RemoteConfigMsg) {
	localAddr := fmt.Sprintf("%s:%d", rc.LocalIp, rc.LocalPort)
	udpAddr, err := net.ResolveUDPAddr("udp", localAddr)
	if err != nil {
		log.Printf("[udp] resolve %s failed: %v", localAddr, err)
		c.pool.Put(proxyConn)
		serverConn.Send(&protocol.Message{
			Type: protocol.TypeDisconnected,
			Meta: meta,
		})
		return
	}

	udpConn, err := net.DialUDP("udp", nil, udpAddr)
	if err != nil {
		log.Printf("[udp] dial %s failed: %v", localAddr, err)
		c.pool.Put(proxyConn)
		serverConn.Send(&protocol.Message{
			Type: protocol.TypeDisconnected,
			Meta: meta,
		})
		return
	}

	channelId := meta.ChannelId
	c.mu.Lock()
	c.channels[channelId] = udpConn
	c.mu.Unlock()

	// Send connected ACK
	proxyConn.Send(&protocol.Message{
		Type: protocol.TypeConnected,
		Meta: meta,
	})

	var once sync.Once
	done := make(chan struct{})
	endSession := func(returnToPool bool) {
		once.Do(func() {
			close(done)
			udpConn.Close()
			c.removeChannel(channelId)
			if returnToPool {
				c.pool.Put(proxyConn)
			} else {
				proxyConn.Close()
			}
		})
	}

	// Relay: UDP local -> proxy
	go func() {
		buf := make([]byte, 65535)
		for {
			n, err := udpConn.Read(buf)
			if err != nil {
				select {
				case <-done:
					return
				default:
				}
				proxyConn.Send(&protocol.Message{
					Type: protocol.TypeDisconnected,
					Meta: meta,
				})
				endSession(true)
				return
			}
			data := make([]byte, n)
			copy(data, buf[:n])
			if sendErr := proxyConn.Send(&protocol.Message{
				Type: protocol.TypeData,
				Meta: meta,
				Data: data,
			}); sendErr != nil {
				endSession(false)
				return
			}
		}
	}()

	// Relay: proxy -> UDP local
	go c.relayProxyToUDP(udpConn, proxyConn, done, endSession)
}

func (c *Client) relayLocalToProxy(local net.Conn, proxyConn *protocol.Conn, meta *protocol.Meta, done <-chan struct{}, endSession func(bool)) {
	buf := make([]byte, 32*1024)
	for {
		n, err := local.Read(buf)
		if n > 0 {
			data := make([]byte, n)
			copy(data, buf[:n])
			// Avoid writing on a pooled connection after session end
			select {
			case <-done:
				return
			default:
			}
			if sendErr := proxyConn.Send(&protocol.Message{
				Type: protocol.TypeData,
				Meta: meta,
				Data: data,
			}); sendErr != nil {
				endSession(false)
				return
			}
		}
		if err != nil {
			// Local closed. If session already ended via the other goroutine, skip.
			select {
			case <-done:
				return
			default:
			}
			// Notify server of disconnect. Do NOT return proxy conn to pool here —
			// relayProxyToLocal is still blocking on Receive(). Let that goroutine
			// handle the pool return when it receives the server's disconnect reply.
			if sendErr := proxyConn.Send(&protocol.Message{
				Type: protocol.TypeDisconnected,
				Meta: meta,
			}); sendErr != nil {
				// Send failed — proxy conn is broken. endSession(false) will close it
				// and relayProxyToLocal's Receive will error out too.
				endSession(false)
			}
			return
		}
	}
}

func (c *Client) relayProxyToLocal(local net.Conn, proxyConn *protocol.Conn, done <-chan struct{}, endSession func(bool)) {
	for {
		msg, err := proxyConn.Receive()
		if err != nil {
			// Read error — connection is broken, close everything.
			endSession(false)
			return
		}
		switch msg.Type {
		case protocol.TypeData:
			if len(msg.Data) > 0 {
				if _, werr := local.Write(msg.Data); werr != nil {
					endSession(false)
					return
				}
			}
		case protocol.TypeDisconnected:
			// Server indicates session end. This goroutine is the reader so it's
			// safe to return the proxy conn to the pool — no one else is reading.
			endSession(true)
			return
		case protocol.TypeKeepalive:
			// ignore keepalive
		}
	}
}

func (c *Client) relayProxyToUDP(udpConn *net.UDPConn, proxyConn *protocol.Conn, done <-chan struct{}, endSession func(bool)) {
	for {
		msg, err := proxyConn.Receive()
		if err != nil {
			endSession(false)
			return
		}
		switch msg.Type {
		case protocol.TypeData:
			if len(msg.Data) > 0 {
				if _, werr := udpConn.Write(msg.Data); werr != nil {
					endSession(false)
					return
				}
			}
		case protocol.TypeDisconnected:
			endSession(true)
			return
		case protocol.TypeKeepalive:
			// ignore keepalive
		}
	}
}

func (c *Client) handleData(msg *protocol.Message) {
	// Data on the server connection is handled in the proxy relay goroutines.
	// This is for data arriving on the main server channel (not expected normally).
}

func (c *Client) handleDisconnected(msg *protocol.Message) {
	if msg.Meta == nil {
		return
	}
	channelId := msg.Meta.ChannelId
	c.mu.Lock()
	if ch, ok := c.channels[channelId]; ok {
		ch.Close()
		delete(c.channels, channelId)
	}
	c.mu.Unlock()
}

func (c *Client) removeChannel(channelId string) {
	c.mu.Lock()
	delete(c.channels, channelId)
	c.mu.Unlock()
}
