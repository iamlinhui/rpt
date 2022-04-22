package cn.promptness.rpt.base.config;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

public class ClientConfig implements Serializable {

    private static final long serialVersionUID = 5217521062971065225L;

    private String serverIp;
    private int serverPort;
    private String clientKey;
    private List<RemoteConfig> config;

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
