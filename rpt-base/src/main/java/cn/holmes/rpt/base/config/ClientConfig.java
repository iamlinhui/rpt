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

    /**
     * 共享数据隧道数量（k个外部连接复用n条隧道，默认4）
     */
    private int tunnelCount = 4;

    /**
     * 通道级背压高水位（字节）：单通道积压达到该值时向服务端发TYPE_PAUSE
     */
    private long highWater = 256 * 1024L;

    /**
     * 通道级背压低水位（字节）：单通道积压排空到该值时向服务端发TYPE_RESUME
     */
    private long lowWater = 64 * 1024L;

    /**
     * 单通道积压硬上限（字节）：背压正常时不会触及，触及即关闭该通道
     */
    private long capacity = 4 * 1024 * 1024L;

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

    public int getTunnelCount() {
        return tunnelCount;
    }

    public void setTunnelCount(int tunnelCount) {
        this.tunnelCount = tunnelCount;
    }

    public long getHighWater() {
        return highWater;
    }

    public void setHighWater(long highWater) {
        this.highWater = highWater;
    }

    public long getLowWater() {
        return lowWater;
    }

    public void setLowWater(long lowWater) {
        this.lowWater = lowWater;
    }

    public long getCapacity() {
        return capacity;
    }

    public void setCapacity(long capacity) {
        this.capacity = capacity;
    }
}
