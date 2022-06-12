package cn.promptness.rpt.base.config;

public class ServerToken {

    private String clientKey;

    private int minPort = 1;

    private int maxPort = 65535;

    public boolean authorize(int remotePort) {
        return remotePort >= minPort && remotePort <= maxPort;
    }

    public String getClientKey() {
        return clientKey;
    }

    public void setClientKey(String clientKey) {
        this.clientKey = clientKey;
    }

    public int getMinPort() {
        return minPort;
    }

    public void setMinPort(int minPort) {
        this.minPort = minPort;
    }

    public int getMaxPort() {
        return maxPort;
    }

    public void setMaxPort(int maxPort) {
        this.maxPort = maxPort;
    }
}
