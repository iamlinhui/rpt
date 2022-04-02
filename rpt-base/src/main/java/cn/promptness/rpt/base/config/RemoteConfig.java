package cn.promptness.rpt.base.config;

import cn.promptness.rpt.base.protocol.ProxyType;

import java.io.Serializable;

public class RemoteConfig implements Serializable {

    private static final long serialVersionUID = -8091778644881703493L;

    private int remotePort;
    private int localPort;
    private String localIp;
    private String description;
    /**
     * 不填写默认就是TCP
     */
    private ProxyType proxyType;
    private String domain;

    public int getRemotePort() {
        return remotePort;
    }

    public void setRemotePort(int remotePort) {
        this.remotePort = remotePort;
    }

    public int getLocalPort() {
        return localPort;
    }

    public void setLocalPort(int localPort) {
        this.localPort = localPort;
    }

    public String getLocalIp() {
        return localIp;
    }

    public void setLocalIp(String localIp) {
        this.localIp = localIp;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ProxyType getProxyType() {
        return proxyType;
    }

    public void setProxyType(ProxyType proxyType) {
        this.proxyType = proxyType;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }
}
