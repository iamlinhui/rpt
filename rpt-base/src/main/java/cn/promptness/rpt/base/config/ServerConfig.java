package cn.promptness.rpt.base.config;

import java.util.List;

public class ServerConfig {

    private String serverIp;
    private int serverPort;
    private int httpPort;
    private List<String> clientKey;

    public String getServerIp() {
        return serverIp;
    }

    public void setServerIp(String serverIp) {
        this.serverIp = serverIp;
    }

    public int getServerPort() {
        return serverPort;
    }

    public void setServerPort(int serverPort) {
        this.serverPort = serverPort;
    }

    public List<String> getClientKey() {
        return clientKey;
    }

    public void setClientKey(List<String> clientKey) {
        this.clientKey = clientKey;
    }

    public int getHttpPort() {
        return httpPort;
    }

    public void setHttpPort(int httpPort) {
        this.httpPort = httpPort;
    }
}
