package main

import (
	"context"
	"embed"
	"fmt"
	"image"
	"image/png"
	"log"
	"os"
	"os/exec"
	"runtime"
	"strings"
	"sync"
	"time"

	"github.com/energye/systray"
	wr "github.com/wailsapp/wails/v2/pkg/runtime"

	"github.com/wailsapp/wails/v2"
	"github.com/wailsapp/wails/v2/pkg/options"
	"github.com/wailsapp/wails/v2/pkg/options/assetserver"
	wopts "github.com/wailsapp/wails/v2/pkg/options/windows"

	"rpt-client-go/client"
	"rpt-client-go/config"
	"rpt-client-go/protocol"

	"golang.org/x/image/draw"
	"gopkg.in/yaml.v3"

	"bytes"
)

//go:embed frontend/*
var assets embed.FS

//go:embed icon.ico
var icoData []byte

//go:embed icon.png
var pngData []byte

const (
	appTitle   = "Reverse Proxy Tool"
	appVersion = "2.6.1"
)

func main() {
	app := NewApp()

	err := wails.Run(&options.App{
		Title:     appTitle,
		Width:     820,
		Height:    580,
		MinWidth:  650,
		MinHeight: 450,
		AssetServer: &assetserver.Options{
			Assets: assets,
		},
		OnStartup:  app.startup,
		OnShutdown: app.shutdown,
		OnBeforeClose: func(ctx context.Context) bool {
			app.mu.Lock()
			quitting := app.quitting
			app.mu.Unlock()
			if quitting {
				return false
			}
			wr.WindowHide(ctx)
			return true // prevent close, just hide
		},
		Bind: []interface{}{
			app,
		},
		Windows: &wopts.Options{
			WebviewIsTransparent: false,
			WindowIsTranslucent:  false,
		},
	})
	if err != nil {
		log.Fatal(err)
	}
}

// ==================== Config ====================

type DesktopConfig struct {
	ServerIp   string                `yaml:"serverIp" json:"serverIp"`
	ServerPort int                   `yaml:"serverPort" json:"serverPort"`
	ClientKey  string                `yaml:"clientKey" json:"clientKey"`
	CertFile   string                `yaml:"certFile" json:"certFile"`
	KeyFile    string                `yaml:"keyFile" json:"keyFile"`
	CaFile     string                `yaml:"caFile" json:"caFile"`
	Config     []config.RemoteConfig `yaml:"config" json:"config"`
}

const configFile = "desktop.yml"

func loadDesktopConfig() *DesktopConfig {
	cfg := &DesktopConfig{
		ServerIp:   "127.0.0.1",
		ServerPort: 6167,
		CertFile:   "client.crt",
		KeyFile:    "pkcs8_client.key",
		CaFile:     "ca.crt",
	}
	data, err := os.ReadFile(configFile)
	if err != nil {
		return cfg
	}
	if err = yaml.Unmarshal(data, cfg); err != nil {
		return cfg
	}
	return cfg
}

func saveDesktopConfig(cfg *DesktopConfig) {
	data, _ := yaml.Marshal(cfg)
	if err := os.WriteFile(configFile, data, 0644); err != nil {
		log.Printf("保存配置失败: %v", err)
	}
}

// ==================== App ====================

type App struct {
	ctx      context.Context
	cfg      *DesktopConfig
	mu       sync.Mutex
	client   *client.Client
	running  bool
	quitting bool
	done     chan struct{} // signals when Run() goroutine exits
	trayRun  func()
	trayEnd  func()
	logBuf   strings.Builder
	logMu    sync.Mutex
}

func NewApp() *App {
	app := &App{cfg: loadDesktopConfig()}
	app.trayRun, app.trayEnd = systray.RunWithExternalLoop(app.onTrayReady, app.onTrayExit)
	return app
}

func (a *App) startup(ctx context.Context) {
	a.ctx = ctx
	log.SetOutput(&logAdapter{app: a})
	log.SetFlags(0)
	if a.trayRun != nil {
		dispatchOnMainThread(a.trayRun)
	}
}

func (a *App) shutdown(_ context.Context) {
	if a.trayEnd != nil {
		a.trayEnd()
	}
}

func (a *App) onTrayReady() {
	if runtime.GOOS == "darwin" {
		systray.SetIcon(resizePNG(pngData, 22, 22))
	} else {
		systray.SetIcon(icoData)
	}
	if runtime.GOOS != "darwin" {
		systray.SetTitle(appTitle)
	}
	systray.SetTooltip(fmt.Sprintf("%s v%s", appTitle, appVersion))

	showWindow := func() {
		if a.ctx == nil {
			return
		}
		wr.WindowShow(a.ctx)
		wr.WindowUnminimise(a.ctx)
	}

	systray.SetOnClick(func(menu systray.IMenu) { showWindow() })
	systray.SetOnDClick(func(menu systray.IMenu) { showWindow() })

	mShow := systray.AddMenuItem("显示", "显示主窗口")
	systray.AddSeparator()
	mQuit := systray.AddMenuItem("退出", "退出程序")

	mShow.Click(func() { showWindow() })
	mQuit.Click(func() {
		a.mu.Lock()
		a.quitting = true
		a.mu.Unlock()
		a.Stop()
		if a.ctx != nil {
			wr.Quit(a.ctx)
			return
		}
		systray.Quit()
	})
}

func (a *App) onTrayExit() {
}

func (a *App) GetConfig() *DesktopConfig {
	return a.cfg
}

func (a *App) GetVersion() string {
	return appVersion
}

func (a *App) SaveServerConfig(serverIp string, serverPort int, clientKey, certFile, keyFile, caFile string) {
	a.cfg.ServerIp = serverIp
	a.cfg.ServerPort = serverPort
	a.cfg.ClientKey = clientKey
	a.cfg.CertFile = certFile
	a.cfg.KeyFile = keyFile
	a.cfg.CaFile = caFile
	saveDesktopConfig(a.cfg)
}

func (a *App) AddProxyConfig(proxyType, localIp string, localPort, remotePort int, domain, token, description string) string {
	if err := validateProxyConfig(proxyType, localIp, localPort, remotePort, domain); err != "" {
		return err
	}
	rc := config.RemoteConfig{
		ProxyType: config.ProxyType(proxyType), LocalIp: localIp, LocalPort: localPort,
		RemotePort: remotePort, Domain: domain, Token: token, Description: description,
	}
	if rc.LocalIp == "" {
		rc.LocalIp = "127.0.0.1"
	}
	a.cfg.Config = append(a.cfg.Config, rc)
	saveDesktopConfig(a.cfg)
	return ""
}

func (a *App) UpdateProxyConfig(index int, proxyType, localIp string, localPort, remotePort int, domain, token, description string) string {
	if index < 0 || index >= len(a.cfg.Config) {
		return "无效索引"
	}
	if err := validateProxyConfig(proxyType, localIp, localPort, remotePort, domain); err != "" {
		return err
	}
	a.cfg.Config[index] = config.RemoteConfig{
		ProxyType: config.ProxyType(proxyType), LocalIp: localIp, LocalPort: localPort,
		RemotePort: remotePort, Domain: domain, Token: token, Description: description,
	}
	saveDesktopConfig(a.cfg)
	return ""
}

func validateProxyConfig(proxyType, localIp string, localPort, remotePort int, domain string) string {
	if localIp == "" {
		localIp = "127.0.0.1"
	}
	if localPort < 1 || localPort > 65535 {
		return "本地端口必须在 1-65535 之间"
	}
	if proxyType == "TCP" || proxyType == "UDP" {
		if remotePort < 1024 || remotePort > 65535 {
			return "暴露端口必须在 1024-65535 之间"
		}
	}
	if proxyType == "HTTP" {
		if domain == "" {
			return "HTTP类型必须填写域名"
		}
	}
	return ""
}

func (a *App) DeleteProxyConfig(index int) {
	if index < 0 || index >= len(a.cfg.Config) {
		return
	}
	a.cfg.Config = append(a.cfg.Config[:index], a.cfg.Config[index+1:]...)
	saveDesktopConfig(a.cfg)
}

func (a *App) SelectFile(title string) string {
	result, err := wr.OpenFileDialog(a.ctx, wr.OpenDialogOptions{
		Title: title,
	})
	if err != nil {
		return ""
	}
	return result
}

func (a *App) OpenURL(url string) {
	switch runtime.GOOS {
	case "windows":
		if err := exec.Command("rundll32", "url.dll,FileProtocolHandler", url).Start(); err != nil {
			log.Printf("打开链接失败: %v", err)
		}
	case "darwin":
		if err := exec.Command("open", url).Start(); err != nil {
			log.Printf("打开链接失败: %v", err)
		}
	default:
		if err := exec.Command("xdg-open", url).Start(); err != nil {
			log.Printf("打开链接失败: %v", err)
		}
	}
}

func (a *App) Start() string {
	a.mu.Lock()
	defer a.mu.Unlock()
	if a.running {
		return "已在运行中"
	}
	if a.cfg.ServerIp == "" || a.cfg.ServerPort == 0 || a.cfg.ClientKey == "" {
		return "请先配置服务器信息"
	}
	if len(a.cfg.Config) == 0 {
		return "请先添加映射配置"
	}
	// Validate all configs before starting
	for _, rc := range a.cfg.Config {
		if rc.LocalPort < 1 || rc.LocalPort > 65535 {
			return fmt.Sprintf("配置 [%s] 本地端口无效: %d", rc.ProxyType, rc.LocalPort)
		}
		if rc.ProxyType == config.ProxyTCP || rc.ProxyType == config.ProxyUDP {
			if rc.RemotePort < 1024 || rc.RemotePort > 65535 {
				return fmt.Sprintf("配置 [%s] 暴露端口必须在 1024-65535 之间: %d", rc.ProxyType, rc.RemotePort)
			}
		}
		if rc.ProxyType == config.ProxyHTTP && rc.Domain == "" {
			return "HTTP配置缺少域名"
		}
	}
	tlsConfig, err := protocol.LoadTLSConfig(a.cfg.CertFile, a.cfg.KeyFile, a.cfg.CaFile)
	if err != nil {
		return "加载TLS配置失败: " + err.Error()
	}
	cc := &config.ClientConfig{
		ServerIp: a.cfg.ServerIp, ServerPort: a.cfg.ServerPort,
		ClientKey: a.cfg.ClientKey, Config: a.cfg.Config,
	}
	c := client.New(cc, tlsConfig)
	a.client = c
	a.running = true
	a.done = make(chan struct{})
	go func() {
		defer close(a.done)
		log.Printf("启动连接, server: %s:%d, clientKey: %s", a.cfg.ServerIp, a.cfg.ServerPort, a.cfg.ClientKey)
		c.Run()
		a.mu.Lock()
		a.running = false
		a.client = nil
		a.mu.Unlock()
		log.Println("连接已断开")
	}()
	return ""
}

func (a *App) Stop() {
	a.mu.Lock()
	c := a.client
	done := a.done
	a.mu.Unlock()
	if c != nil {
		c.Stop()
	}
	// Wait for the Run() goroutine to finish (with timeout)
	if done != nil {
		select {
		case <-done:
		case <-time.After(5 * time.Second):
			log.Println("停止超时，强制关闭")
		}
	}
}

func (a *App) IsRunning() bool {
	a.mu.Lock()
	defer a.mu.Unlock()
	return a.running
}

func (a *App) GetLogs() string {
	a.logMu.Lock()
	defer a.logMu.Unlock()
	s := a.logBuf.String()
	a.logBuf.Reset()
	return s
}

// ==================== Helpers ====================

func resizePNG(data []byte, w, h int) []byte {
	src, err := png.Decode(bytes.NewReader(data))
	if err != nil {
		return data
	}
	dst := image.NewRGBA(image.Rect(0, 0, w, h))
	draw.CatmullRom.Scale(dst, dst.Bounds(), src, src.Bounds(), draw.Over, nil)
	var buf bytes.Buffer
	if err = png.Encode(&buf, dst); err != nil {
		return data
	}
	return buf.Bytes()
}

// ==================== Log Adapter ====================

type logAdapter struct{ app *App }

func (l *logAdapter) Write(p []byte) (int, error) {
	line := time.Now().Format("2006-01-02 15:04:05") + " | " + strings.TrimRight(string(p), "\n") + "\n"
	l.app.logMu.Lock()
	l.app.logBuf.WriteString(line)
	l.app.logMu.Unlock()
	return len(p), nil
}
