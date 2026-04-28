package config

import (
	"os"
	"path/filepath"

	"gopkg.in/yaml.v3"
)

type ProxyType string

const (
	ProxyTCP  ProxyType = "TCP"
	ProxyHTTP ProxyType = "HTTP"
	ProxyUDP  ProxyType = "UDP"
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
	Config         []RemoteConfig `yaml:"config"`
	configDir      string
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
	if absPath, err := filepath.Abs(path); err == nil {
		cfg.configDir = filepath.Dir(absPath)
	} else {
		cfg.configDir = filepath.Dir(path)
	}
	return &cfg, nil
}
