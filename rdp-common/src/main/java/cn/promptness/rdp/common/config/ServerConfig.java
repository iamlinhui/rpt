package cn.promptness.rdp.common.config;

import java.util.List;

public class ServerConfig {

    private String serverIp;
    private Integer serverPort;
    private List<String> clientKey;

    public String getServerIp() {
        return serverIp;
    }

    public void setServerIp(String serverIp) {
        this.serverIp = serverIp;
    }

    public Integer getServerPort() {
        return serverPort;
    }

    public void setServerPort(Integer serverPort) {
        this.serverPort = serverPort;
    }

    public List<String> getClientKey() {
        return clientKey;
    }

    public void setClientKey(List<String> clientKey) {
        this.clientKey = clientKey;
    }
}
