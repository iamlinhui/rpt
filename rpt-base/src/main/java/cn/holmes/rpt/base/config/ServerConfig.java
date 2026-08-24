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
    private int dashboardPort;
    private String dashboardUser;
    private String dashboardPassword;
    /**
     * 限制连接暴露端口的 IP 必须属于这些国家（ISO 码，逗号分隔，如 "CN" 或 "CN,HK"）。
     */
    private String ipFilterCountry;
    private List<ServerToken> token;

    /**
     * 通道级背压高水位（字节）：单通道积压达到该值时向客户端发TYPE_PAUSE
     */
    private long highWater = 256 * 1024L;

    /**
     * 通道级背压低水位（字节）：单通道积压排空到该值时向客户端发TYPE_RESUME
     */
    private long lowWater = 64 * 1024L;

    /**
     * 单通道积压硬上限（字节）：背压正常时不会触及，触及即关闭该通道
     */
    private long capacity = 4 * 1024 * 1024L;

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
