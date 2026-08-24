package config

import (
	"os"
	"path/filepath"

	"gopkg.in/yaml.v3"
)

type ProxyType string

const (
	ProxyTCP    ProxyType = "TCP"
	ProxyHTTP   ProxyType = "HTTP"
	ProxyUDP    ProxyType = "UDP"
	ProxySOCKS5 ProxyType = "SOCKS5"
)

type RemoteConfig struct {
	RemotePort  int       `json:"remotePort" yaml:"remotePort"`
	LocalPort   int       `json:"localPort" yaml:"localPort"`
	LocalIp     string    `json:"localIp" yaml:"localIp"`
	Description string    `json:"description" yaml:"description"`
	ProxyType   ProxyType `json:"proxyType" yaml:"proxyType"`
	Domain      string    `json:"domain" yaml:"domain"`
	Token       string    `json:"token" yaml:"token"`
}

type ClientConfig struct {
	ServerIp       string         `yaml:"serverIp"`
	ServerPort     int            `yaml:"serverPort"`
	ClientCaPath   string         `yaml:"clientCaPath"`
	ClientCertPath string         `yaml:"clientCertPath"`
	ClientKeyPath  string         `yaml:"clientKeyPath"`
	ClientKey      string         `yaml:"clientKey"`
	TunnelCount    int            `yaml:"tunnelCount"`
	// 通道级背压水位线（字节）：积压达 HighWater 发 TYPE_PAUSE，回落到 LowWater 发 TYPE_RESUME；
	// Capacity 为兜底硬上限，触达即关闭该通道（不丢数据）
	HighWater int64          `yaml:"highWater"`
	LowWater  int64          `yaml:"lowWater"`
	Capacity  int64          `yaml:"capacity"`
	Config    []RemoteConfig `yaml:"config"`
	configDir string
}

// 水位线默认值，与 Java 端保持一致
const (
	defaultHighWater = 256 * 1024
	defaultLowWater  = 64 * 1024
	defaultCapacity  = 4 * 1024 * 1024
)

// applyDefaults 未配置或配置不合法时回落到默认水位线
func (c *ClientConfig) applyDefaults() {
	if c.HighWater <= 0 {
		c.HighWater = defaultHighWater
	}
	if c.LowWater <= 0 {
		c.LowWater = defaultLowWater
	}
	if c.LowWater >= c.HighWater {
		c.LowWater = c.HighWater / 4
	}
	if c.Capacity < c.HighWater {
		c.Capacity = defaultCapacity
		if c.Capacity < c.HighWater {
			c.Capacity = c.HighWater
		}
	}
}

func (c *ClientConfig) GetCaPath() string {
	return c.resolvePath(c.ClientCaPath, "ca.crt")
}

func (c *ClientConfig) GetCertPath() string {
	return c.resolvePath(c.ClientCertPath, "client.crt")
}

func (c *ClientConfig) GetKeyPath() string {
	return c.resolvePath(c.ClientKeyPath, "pkcs8_client.key")
}

func (c *ClientConfig) resolvePath(value, fallback string) string {
	path := value
	if path == "" {
		path = fallback
	}
	if filepath.IsAbs(path) || c.configDir == "" {
		return path
	}
	return filepath.Join(c.configDir, path)
}

func (c *ClientConfig) GetHttpConfig(domain string) *RemoteConfig {
	for i := range c.Config {
		rc := &c.Config[i]
		if rc.ProxyType == ProxyHTTP && rc.Domain == domain {
			return rc
		}
	}
	return nil
}

func LoadClientConfig(path string) (*ClientConfig, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return nil, err
	}
	var cfg ClientConfig
	if err := yaml.Unmarshal(data, &cfg); err != nil {
		return nil, err
	}
	cfg.applyDefaults()
	if absPath, err := filepath.Abs(path); err == nil {
		cfg.configDir = filepath.Dir(absPath)
	} else {
		cfg.configDir = filepath.Dir(path)
	}
	return &cfg, nil
}
