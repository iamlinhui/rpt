package cn.promptness.rdp.base.config;

import java.util.List;

public class ClientConfig {

    private String serverIp;
    private Integer serverPort;
    private String clientKey;
    private boolean connection;
    private String channelId;

    private List<RemoteConfig> config;
    private List<String> remoteResult;

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

    public boolean isConnection() {
        return connection;
    }

    public void setConnection(boolean connection) {
        this.connection = connection;
    }

    public String getChannelId() {
        return channelId;
    }

    public void setChannelId(String channelId) {
        this.channelId = channelId;
    }

    public void setRemoteResult(List<String> remoteResult) {
        this.remoteResult = remoteResult;
    }

    public List<String> getRemoteResult() {
        return remoteResult;
    }
}
