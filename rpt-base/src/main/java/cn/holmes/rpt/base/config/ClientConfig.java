package cn.holmes.rpt.base.config;

import java.util.List;
import java.util.Objects;

public class ClientConfig {

    private String serverIp;
    private int serverPort;
    private String clientCaPath;
    private String clientCertPath;
    private String clientKeyPath;

    private String clientKey;
    private List<RemoteConfig> config;

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

    public String getClientCaPath() {
        return clientCaPath;
    }

    public void setClientCaPath(String clientCaPath) {
        this.clientCaPath = clientCaPath;
    }

    public String getClientCertPath() {
        return clientCertPath;
    }

    public void setClientCertPath(String clientCertPath) {
        this.clientCertPath = clientCertPath;
    }

    public String getClientKeyPath() {
        return clientKeyPath;
    }

    public void setClientKeyPath(String clientKeyPath) {
        this.clientKeyPath = clientKeyPath;
    }

    public RemoteConfig getHttpConfig(String domain) {
        if (config == null || config.isEmpty()) {
            return null;
        }
        for (RemoteConfig remoteConfig : config) {
            ProxyType proxyType = remoteConfig.getProxyType();
            if (Objects.equals(ProxyType.HTTP, proxyType) && Objects.equals(domain, remoteConfig.getDomain())) {
                return remoteConfig;
            }
        }
        return null;
    }

    public String getClientKey() {
        return clientKey;
    }

    public void setClientKey(String clientKey) {
        this.clientKey = clientKey;
    }

    public List<RemoteConfig> getConfig() {
        return config;
    }

    public void setConfig(List<RemoteConfig> config) {
        this.config = config;
    }
}
