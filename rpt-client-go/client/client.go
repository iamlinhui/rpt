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

	"github.com/holmes/rpt-client-go/config"
	"github.com/holmes/rpt-client-go/protocol"
	"github.com/holmes/rpt-client-go/proxy"
)

type Client struct {
	cfg        *config.ClientConfig
	tlsConfig  *tls.Config
	pool       *proxy.Pool
	serverAddr string

	mu       sync.Mutex
	channels map[string]io.Closer // channelId -> local connection
	stopped  bool
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
		// Clear local channels on disconnect
		c.clearChannels()
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

	// Bidirectional relay: local <-> proxy
	go c.relayLocalToProxy(channelId, localConn, proxyConn, meta)
	go c.relayProxyToLocal(channelId, localConn, proxyConn)
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

	// Relay: UDP local -> proxy
	go func() {
		buf := make([]byte, 65535)
		for {
			n, err := udpConn.Read(buf)
			if err != nil {
				proxyConn.Send(&protocol.Message{
					Type: protocol.TypeDisconnected,
					Meta: meta,
				})
				c.removeChannel(channelId)
				return
			}
			data := make([]byte, n)
			copy(data, buf[:n])
			proxyConn.Send(&protocol.Message{
				Type: protocol.TypeData,
				Meta: meta,
				Data: data,
			})
		}
	}()

	// Relay: proxy -> UDP local
	go c.relayProxyToUDP(channelId, udpConn, proxyConn)
}

func (c *Client) relayLocalToProxy(channelId string, local net.Conn, proxyConn *protocol.Conn, meta *protocol.Meta) {
	buf := make([]byte, 32*1024)
	for {
		n, err := local.Read(buf)
		if n > 0 {
			data := make([]byte, n)
			copy(data, buf[:n])
			proxyConn.Send(&protocol.Message{
				Type: protocol.TypeData,
				Meta: meta,
				Data: data,
			})
		}
		if err != nil {
			proxyConn.Send(&protocol.Message{
				Type: protocol.TypeDisconnected,
				Meta: meta,
			})
			c.removeChannel(channelId)
			return
		}
	}
}

func (c *Client) relayProxyToLocal(channelId string, local net.Conn, proxyConn *protocol.Conn) {
	for {
		msg, err := proxyConn.Receive()
		if err != nil {
			local.Close()
			c.removeChannel(channelId)
			return
		}
		switch msg.Type {
		case protocol.TypeData:
			if len(msg.Data) > 0 {
				local.Write(msg.Data)
			}
		case protocol.TypeDisconnected:
			local.Close()
			c.pool.Put(proxyConn)
			c.removeChannel(channelId)
			return
		}
	}
}

func (c *Client) relayProxyToUDP(channelId string, udpConn *net.UDPConn, proxyConn *protocol.Conn) {
	for {
		msg, err := proxyConn.Receive()
		if err != nil {
			udpConn.Close()
			c.removeChannel(channelId)
			return
		}
		switch msg.Type {
		case protocol.TypeData:
			if len(msg.Data) > 0 {
				udpConn.Write(msg.Data)
			}
		case protocol.TypeDisconnected:
			udpConn.Close()
			c.pool.Put(proxyConn)
			c.removeChannel(channelId)
			return
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
