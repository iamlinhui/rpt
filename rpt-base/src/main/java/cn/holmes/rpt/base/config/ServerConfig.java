package cn.holmes.rpt.base.config;

import java.util.List;
import java.util.Objects;

public class ServerConfig {

    private String serverIp;
    private int serverPort;
    private int httpPort;
    private int httpsPort;
    private String domainCert;
    private String domainKey;
    private boolean ipFilter;
    private List<ServerToken> token;

    public boolean authorize(String clientKey) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        for (ServerToken serverToken : token) {
            if (Objects.equals(clientKey, serverToken.getClientKey())) {
                return true;
            }
        }
        return false;
    }

    public ServerToken getServerToken(String clientKey) {
        for (ServerToken serverToken : token) {
            if (Objects.equals(clientKey, serverToken.getClientKey())) {
                return serverToken;
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

    public List<ServerToken> getToken() {
        return token;
    }

    public void setToken(List<ServerToken> token) {
        this.token = token;
    }

    public int getHttpPort() {
        return httpPort;
    }

    public void setHttpPort(int httpPort) {
        this.httpPort = httpPort;
    }

    public int getHttpsPort() {
        return httpsPort;
    }

    public void setHttpsPort(int httpsPort) {
        this.httpsPort = httpsPort;
    }

    public String getDomainCert() {
        return domainCert;
    }

    public void setDomainCert(String domainCert) {
        this.domainCert = domainCert;
    }

    public String getDomainKey() {
        return domainKey;
    }

    public void setDomainKey(String domainKey) {
        this.domainKey = domainKey;
    }

    public boolean ipFilter() {
        return ipFilter;
    }

    public void setIpFilter(boolean ipFilter) {
        this.ipFilter = ipFilter;
    }
}
