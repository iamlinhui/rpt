package main

import (
	"flag"
	"log"
	"os"
	"os/exec"
	"runtime"

	"rpt-client-go/client"
	"rpt-client-go/config"
	"rpt-client-go/protocol"
)

func init() {
	// Set Windows console to UTF-8 for proper Chinese character display
	if runtime.GOOS == "windows" {
		cmd := exec.Command("cmd", "/C", "chcp", "65001")
		cmd.Stdout = nil
		cmd.Run()
	}
	// Direct log output to stdout so it won't appear red in terminals
	log.SetOutput(os.Stdout)
}

func main() {
	configPath := flag.String("config", "client.yml", "path to client config file")
	flag.Parse()

	cfg, err := config.LoadClientConfig(*configPath)
	if err != nil {
		log.Fatalf("load config: %v", err)
	}

	tlsConfig, err := protocol.LoadTLSConfig(cfg.GetCertPath(), cfg.GetKeyPath(), cfg.GetCaPath())
	if err != nil {
		log.Fatalf("load tls config: %v", err)
	}

	c := client.New(cfg, tlsConfig)
	log.Printf("rpt-client-go starting, server: %s:%d, clientKey: %s",
		cfg.ServerIp, cfg.ServerPort, cfg.ClientKey)
	c.Run()
}
