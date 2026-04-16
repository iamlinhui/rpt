package config

import (
	"os"

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
	ServerIp   string         `yaml:"serverIp"`
	ServerPort int            `yaml:"serverPort"`
	ClientKey  string         `yaml:"clientKey"`
	Config     []RemoteConfig `yaml:"config"`
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
	return &cfg, nil
}
