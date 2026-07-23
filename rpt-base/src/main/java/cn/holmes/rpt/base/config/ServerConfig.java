package cn.holmes.rpt.base.config;

import java.util.List;
import java.util.Objects;

public class ServerConfig {

    private String serverIp;
    private int serverPort;
    private String serverCaPath;
    private String serverCertPath;
    private String serverKeyPath;
    private int httpPort;
    private int httpsPort;
    private String domainCert;
    private String domainKey;
    /**
     * 限制连接暴露端口的 IP 必须属于这些国家（ISO 码，逗号分隔，如 "CN" 或 "CN,HK"）。
     * 由 IpCountryFilter 在 config 阶段注入。空值 = 关闭国家过滤（放行所有）。
     * 不再用 Locale.getDefault().getCountry()——那会跟随运行机器系统语言，
     * 服务端 LANG=en_US 时会把非 US 客户端全拒。
     */
    private String ipFilterCountry;
    private List<ServerToken> token;
    private int dashboardPort;
    private String dashboardUser;
    private String dashboardPassword;

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

    public String getServerCaPath() {
        return serverCaPath;
    }

    public void setServerCaPath(String serverCaPath) {
        this.serverCaPath = serverCaPath;
    }

    public String getServerCertPath() {
        return serverCertPath;
    }

    public void setServerCertPath(String serverCertPath) {
        this.serverCertPath = serverCertPath;
    }

    public String getServerKeyPath() {
        return serverKeyPath;
    }

    public void setServerKeyPath(String serverKeyPath) {
        this.serverKeyPath = serverKeyPath;
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

    /**
     * 国家过滤是否开启：ipFilterCountry 有值则开启，空则关闭（放行所有）。
     */
    public boolean ipFilterEnabled() {
        return ipFilterCountry != null && !ipFilterCountry.trim().isEmpty();
    }

    public String getIpFilterCountry() {
        return ipFilterCountry;
    }

    public void setIpFilterCountry(String ipFilterCountry) {
        this.ipFilterCountry = ipFilterCountry;
    }

    public int getDashboardPort() {
        return dashboardPort;
    }

    public void setDashboardPort(int dashboardPort) {
        this.dashboardPort = dashboardPort;
    }

    public String getDashboardUser() {
        return dashboardUser;
    }

    public void setDashboardUser(String dashboardUser) {
        this.dashboardUser = dashboardUser;
    }

    public String getDashboardPassword() {
        return dashboardPassword;
    }

    public void setDashboardPassword(String dashboardPassword) {
        this.dashboardPassword = dashboardPassword;
    }
}
