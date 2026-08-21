package client

import (
	"crypto/tls"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net"
	"strconv"
	"sync"
	"time"

	"rpt-client-go/config"
	"rpt-client-go/protocol"
	"rpt-client-go/tunnel"
)

// session 承载在共享隧道上的一个外部连接（以channelId路由）
type session struct {
	local  io.ReadWriteCloser
	tunnel *protocol.Conn
	meta   *protocol.Meta
}

type Client struct {
	cfg        *config.ClientConfig
	tlsConfig  *tls.Config
	tunnels    *tunnel.Pool
	serverAddr string

	mu         sync.Mutex
	sessions   map[string]*session // channelId -> session
	serverId   string
	stopped    bool
	stopCh     chan struct{} // closed by Stop() to interrupt backoff sleep
	resetDelay bool          // set to true after successful auth
	serverConn *protocol.Conn
}

func New(cfg *config.ClientConfig, tlsConfig *tls.Config) *Client {
	addr := net.JoinHostPort(cfg.ServerIp, strconv.Itoa(cfg.ServerPort))
	c := &Client{
		cfg:        cfg,
		tlsConfig:  tlsConfig,
		serverAddr: addr,
		sessions:   make(map[string]*session),
		stopCh:     make(chan struct{}),
	}
	c.tunnels = tunnel.NewPool(func() (*protocol.Conn, error) {
		return protocol.DialTLS(addr, tlsConfig)
	}, c.handleTunnelMessage, c.handleTunnelClosed)
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
			// 用 timer + select 替代 time.Sleep：Stop() 关闭 stopCh 可立即打断退避，否则停止一个客户端可能要等满整个退避（最长 300s）才退出 Run。
			timer := time.NewTimer(time.Duration(delay) * time.Second)
			select {
			case <-timer.C:
			case <-c.stopCh:
				timer.Stop()
				return
			}
		}
		err := c.connect()
		if err != nil {
			log.Printf("[client] connection error: %v", err)
		}
		// 控制通道断开：关闭所有隧道与会话本地连接
		c.tunnels.Close()
		c.closeSessions()
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
	c.mu.Lock()
	if c.stopped {
		c.mu.Unlock()
		return
	}
	c.stopped = true
	sc := c.serverConn
	c.mu.Unlock()
	close(c.stopCh) // 打断 Run 里可能进行中的退避 sleep
	if sc != nil {
		sc.Close()
	}
	c.tunnels.Close()
	c.closeSessions()
}

func (c *Client) closeSessions() {
	c.mu.Lock()
	defer c.mu.Unlock()
	for id, s := range c.sessions {
		s.local.Close()
		delete(c.sessions, id)
	}
}

func (c *Client) connect() error {
	log.Printf("[client] connecting to %s", c.serverAddr)
	serverConn, err := protocol.DialTLS(c.serverAddr, c.tlsConfig)
	if err != nil {
		return fmt.Errorf("dial server: %w", err)
	}
	c.mu.Lock()
	c.serverConn = serverConn
	c.mu.Unlock()
	defer func() {
		c.mu.Lock()
		c.serverConn = nil
		c.mu.Unlock()
		serverConn.Close()
	}()

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
		go c.handleConnected(serverConn, msg)
	case protocol.TypeKeepalive:
		// respond with keepalive
		serverConn.Send(&protocol.Message{Type: protocol.TypeKeepalive})
	}
	// TypeData/TypeDisconnected 只会到达共享隧道，由 handleTunnelMessage 处理
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
		c.mu.Lock()
		c.serverId = msg.Meta.ServerId
		c.mu.Unlock()
		c.resetDelay = true
		// 建立n条共享数据隧道，k个外部会话通过channelId复用（serverId由服务端注册响应回填）
		c.tunnels.Init(c.cfg.ClientKey, msg.Meta.ServerId, c.cfg.TunnelCount)
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

	// 轮询选取共享隧道承载该会话
	t := c.tunnels.Pick()
	if t == nil {
		serverConn.Send(&protocol.Message{
			Type: protocol.TypeDisconnected,
			Meta: msg.Meta,
		})
		return
	}

	if rc.ProxyType == "UDP" {
		c.connectUDP(serverConn, t, msg.Meta, rc)
	} else {
		c.connectTCP(serverConn, t, msg.Meta, rc)
	}
}

func (c *Client) connectTCP(serverConn *protocol.Conn, t *protocol.Conn, meta *protocol.Meta, rc *protocol.RemoteConfigMsg) {
	localAddr := net.JoinHostPort(rc.LocalIp, strconv.Itoa(rc.LocalPort))
	localConn, err := net.DialTimeout("tcp", localAddr, 5*time.Second)
	if err != nil {
		log.Printf("[tcp] connect local %s failed: %v", localAddr, err)
		serverConn.Send(&protocol.Message{
			Type: protocol.TypeDisconnected,
			Meta: meta,
		})
		return
	}
	c.registerSession(localConn, t, meta)
	go c.relayLocalToTunnel(meta.ChannelId)
}

func (c *Client) connectUDP(serverConn *protocol.Conn, t *protocol.Conn, meta *protocol.Meta, rc *protocol.RemoteConfigMsg) {
	localAddr := net.JoinHostPort(rc.LocalIp, strconv.Itoa(rc.LocalPort))
	udpAddr, err := net.ResolveUDPAddr("udp", localAddr)
	if err != nil {
		log.Printf("[udp] resolve %s failed: %v", localAddr, err)
		serverConn.Send(&protocol.Message{
			Type: protocol.TypeDisconnected,
			Meta: meta,
		})
		return
	}

	udpConn, err := net.DialUDP("udp", nil, udpAddr)
	if err != nil {
		log.Printf("[udp] dial %s failed: %v", localAddr, err)
		serverConn.Send(&protocol.Message{
			Type: protocol.TypeDisconnected,
			Meta: meta,
		})
		return
	}
	c.registerSession(udpConn, t, meta)
	go c.relayLocalToTunnel(meta.ChannelId)
}

// registerSession 登记会话并经隧道发送connected ACK（服务端按meta.channelId绑定会话）
func (c *Client) registerSession(local io.ReadWriteCloser, t *protocol.Conn, meta *protocol.Meta) {
	c.mu.Lock()
	c.sessions[meta.ChannelId] = &session{local: local, tunnel: t, meta: meta}
	c.mu.Unlock()
	t.Send(&protocol.Message{
		Type: protocol.TypeConnected,
		Meta: meta,
	})
}

// relayLocalToTunnel 本地->隧道中继，本地关闭时经隧道通知服务端结束会话
func (c *Client) relayLocalToTunnel(channelId string) {
	buf := make([]byte, 32*1024)
	for {
		s := c.session(channelId)
		if s == nil {
			return
		}
		n, err := s.local.Read(buf)
		if n > 0 {
			if sendErr := s.tunnel.Send(&protocol.Message{
				Type: protocol.TypeData,
				Meta: s.meta,
				Data: buf[:n],
			}); sendErr != nil {
				// 隧道已坏，隧道读循环会清理该会话，这里直接退出
				return
			}
		}
		if err != nil {
			// 本地关闭：通知服务端释放会话，隧道保持复用
			c.endSession(channelId, true)
			return
		}
	}
}

// handleTunnelMessage 共享隧道下行消息：按meta.channelId路由到会话本地连接
func (c *Client) handleTunnelMessage(conn *protocol.Conn, msg *protocol.Message) {
	if msg.Meta == nil {
		return
	}
	switch msg.Type {
	case protocol.TypeData:
		s := c.session(msg.Meta.ChannelId)
		if s == nil || len(msg.Data) == 0 {
			return
		}
		if _, err := s.local.Write(msg.Data); err != nil {
			c.endSession(msg.Meta.ChannelId, true)
		}
	case protocol.TypeDisconnected:
		// 服务端通知会话结束，隧道保持复用
		c.endSession(msg.Meta.ChannelId, false)
	}
}

// handleTunnelClosed 隧道断开：关闭其上承载的所有外部连接（补连由隧道池负责）
func (c *Client) handleTunnelClosed(conn *protocol.Conn) {
	c.mu.Lock()
	var doomed []string
	for id, s := range c.sessions {
		if s.tunnel == conn {
			doomed = append(doomed, id)
		}
	}
	c.mu.Unlock()
	for _, id := range doomed {
		c.endSession(id, false)
	}
}

// endSession 幂等拆除会话；notify为true时经隧道发TypeDisconnected通知服务端
func (c *Client) endSession(channelId string, notify bool) {
	c.mu.Lock()
	s, ok := c.sessions[channelId]
	if ok {
		delete(c.sessions, channelId)
	}
	c.mu.Unlock()
	if !ok {
		return
	}
	s.local.Close()
	if notify && !s.tunnel.IsClosed() {
		s.tunnel.Send(&protocol.Message{
			Type: protocol.TypeDisconnected,
			Meta: s.meta,
		})
	}
}

func (c *Client) session(channelId string) *session {
	c.mu.Lock()
	defer c.mu.Unlock()
	return c.sessions[channelId]
}
