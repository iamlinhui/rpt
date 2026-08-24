package client

import (
	"crypto/tls"
	"encoding/json"
	"errors"
	"fmt"
	"log"
	"net"
	"strconv"
	"sync"
	"sync/atomic"
	"time"

	"rpt-client-go/config"
	"rpt-client-go/mux"
	"rpt-client-go/protocol"
	"rpt-client-go/tunnel"
)

// session 承载在共享隧道上的一个外部连接（以channelId路由）
type session struct {
	local  net.Conn
	tunnel *protocol.Conn
	meta   *protocol.Meta
	// buf 下行积压队列：隧道读循环只入队不写本地，由 sessionWriter 消费，避免慢本地服务拖停整条隧道
	buf *mux.ChannelBuf
	// paused 被服务端 TYPE_PAUSE 暂停中，本地->隧道方向停止读取
	paused int32
	// resumeCh 唤醒因暂停而阻塞的中继循环（容量1，用非阻塞投递）
	resumeCh chan struct{}
	// done 会话已拆除，唤醒阻塞在暂停等待中的中继循环
	done chan struct{}
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
	doomed := make([]*session, 0, len(c.sessions))
	for id, s := range c.sessions {
		doomed = append(doomed, s)
		delete(c.sessions, id)
	}
	c.mu.Unlock()
	for _, s := range doomed {
		s.teardown()
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
	go c.relayLocalToTunnel(c.registerSession(localConn, t, meta), meta.ChannelId)
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
	go c.relayLocalToTunnel(c.registerSession(udpConn, t, meta), meta.ChannelId)
}

// registerSession 登记会话并经隧道发送connected ACK（服务端按meta.channelId绑定会话）
func (c *Client) registerSession(local net.Conn, t *protocol.Conn, meta *protocol.Meta) *session {
	s := &session{
		local:    local,
		tunnel:   t,
		meta:     meta,
		resumeCh: make(chan struct{}, 1),
		done:     make(chan struct{}),
	}
	s.buf = mux.New(c.cfg.HighWater, c.cfg.LowWater, c.cfg.Capacity,
		func() { c.signal(s, protocol.TypePause) },
		func() { c.signal(s, protocol.TypeResume) })
	c.mu.Lock()
	c.sessions[meta.ChannelId] = s
	c.mu.Unlock()
	t.Send(&protocol.Message{
		Type: protocol.TypeConnected,
		Meta: meta,
	})
	go c.sessionWriter(s, meta.ChannelId)
	return s
}

// signal 向服务端发送本通道的背压信号，由缓冲区水位回调触发
func (c *Client) signal(s *session, msgType int) {
	if s.tunnel.IsClosed() {
		return
	}
	s.tunnel.Send(&protocol.Message{
		Type: msgType,
		Meta: s.meta,
	})
}

// sessionWriter 隧道->本地方向的唯一写者：从积压队列取数据写本地连接。
// 隧道读循环因此永不阻塞在慢本地服务上。
func (c *Client) sessionWriter(s *session, channelId string) {
	for {
		data := s.buf.Pop()
		if data == nil {
			return
		}
		if _, err := s.local.Write(data); err != nil {
			c.endSession(channelId, true)
			return
		}
	}
}

// relayLocalToTunnel 本地->隧道中继，本地关闭时经隧道通知服务端结束会话
func (c *Client) relayLocalToTunnel(s *session, channelId string) {
	buf := make([]byte, 32*1024)
	for {
		// 被服务端暂停：阻塞等待恢复，不做轮询
		for atomic.LoadInt32(&s.paused) == 1 {
			select {
			case <-s.resumeCh:
			case <-s.done:
				return
			}
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
			// 读超时是 PAUSE 打断在途 Read 的手段，不是会话结束：已读到的字节上面已发出，回到暂停检查点
			var netErr net.Error
			if errors.As(err, &netErr) && netErr.Timeout() {
				continue
			}
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
		// 入队即返回，写本地由 sessionWriter 负责；msg.Data 是每条消息独立分配的切片，可安全异步持有
		if !s.buf.TryPush(msg.Data) {
			// 代理已向发送方 ACK 过这些字节，丢弃等于静默损坏字节流，只能关闭该通道
			log.Printf("[mux] channel %s backlog exceeded %d bytes, closing session", msg.Meta.ChannelId, c.cfg.Capacity)
			c.endSession(msg.Meta.ChannelId, true)
		}
	case protocol.TypePause:
		c.pauseSession(msg.Meta.ChannelId)
	case protocol.TypeResume:
		c.resumeSession(msg.Meta.ChannelId)
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

// pauseSession 收到服务端 TYPE_PAUSE：停止本地->隧道方向读取，并打断在途的阻塞 Read
func (c *Client) pauseSession(channelId string) {
	s := c.session(channelId)
	if s == nil {
		return
	}
	if !atomic.CompareAndSwapInt32(&s.paused, 0, 1) {
		return
	}
	// 过期的读截止时间使在途 Read 立即返回超时，中继循环随即回到暂停检查点
	s.local.SetReadDeadline(time.Now())
}

// resumeSession 收到服务端 TYPE_RESUME：清除读截止时间并唤醒中继循环
func (c *Client) resumeSession(channelId string) {
	s := c.session(channelId)
	if s == nil {
		return
	}
	if !atomic.CompareAndSwapInt32(&s.paused, 1, 0) {
		return
	}
	s.local.SetReadDeadline(time.Time{})
	select {
	case s.resumeCh <- struct{}{}:
	default:
	}
}

// teardown 释放会话本地资源：关闭连接、清空积压、唤醒两个中继 goroutine 退出
func (s *session) teardown() {
	close(s.done)
	s.buf.Close()
	s.local.Close()
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
	s.teardown()
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
