package main

import (
	"flag"
	"log"
	"os"
	"os/exec"
	"runtime"

	"github.com/holmes/rpt-client-go/client"
	"github.com/holmes/rpt-client-go/config"
	"github.com/holmes/rpt-client-go/protocol"
)

func init() {
	// Set Windows console to UTF-8 for proper Chinese character display
	if runtime.GOOS == "windows" {
		cmd := exec.Command("cmd", "/C", "chcp", "65001")
		cmd.Stdout = os.Stdout
		cmd.Run()
	}
}

func main() {
	configPath := flag.String("config", "client.yml", "path to client config file")
	certFile := flag.String("cert", "client.crt", "path to client certificate")
	keyFile := flag.String("key", "pkcs8_client.key", "path to client private key")
	caFile := flag.String("ca", "ca.crt", "path to CA certificate")
	flag.Parse()

	cfg, err := config.LoadClientConfig(*configPath)
	if err != nil {
		log.Fatalf("load config: %v", err)
	}

	tlsConfig, err := protocol.LoadTLSConfig(*certFile, *keyFile, *caFile)
	if err != nil {
		log.Fatalf("load tls config: %v", err)
	}

	c := client.New(cfg, tlsConfig)
	log.Printf("rpt-client-go starting, server: %s:%d, clientKey: %s",
		cfg.ServerIp, cfg.ServerPort, cfg.ClientKey)
	c.Run()
}
