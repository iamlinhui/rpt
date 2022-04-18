package cn.promptness.rpt.base.config;

import cn.promptness.rpt.base.protocol.ProxyType;
import com.google.protobuf.ProtocolStringList;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ClientConfig implements Serializable {

    private static final long serialVersionUID = 5217521062971065225L;

    private String serverIp;
    private int serverPort;
    private int clientLimit;
    private String clientKey;
    private boolean connection;
    private String channelId;

    private List<RemoteConfig> config;
    private List<String> remoteResult;

    public byte[] toProtobuf() {
        ClientConfigProto.ClientConfig.Builder clientConfigBuilder = ClientConfigProto.ClientConfig.newBuilder();
        clientConfigBuilder.setServerPort(serverPort).setConnection(connection);
        if (serverIp != null) {
            clientConfigBuilder.setServerIp(serverIp);
        }
        if (clientKey != null) {
            clientConfigBuilder.setClientKey(clientKey);
        }
        if (channelId != null) {
            clientConfigBuilder.setChannelId(channelId);
        }
        if (config != null && !config.isEmpty()) {
            for (RemoteConfig remoteConfig : config) {
                ClientConfigProto.ClientConfig.RemoteConfig.Builder remoteBuilder = ClientConfigProto.ClientConfig.RemoteConfig.newBuilder();
                remoteBuilder.setRemotePort(remoteConfig.getRemotePort()).setLocalPort(remoteConfig.getLocalPort());
                if (remoteConfig.getLocalIp() != null) {
                    remoteBuilder.setLocalIp(remoteConfig.getLocalIp());
                }
                if (remoteConfig.getDescription() != null) {
                    remoteBuilder.setDescription(remoteConfig.getDescription());
                }
                if (remoteConfig.getDomain() != null) {
                    remoteBuilder.setDomain(remoteConfig.getDomain());
                }
                if (remoteConfig.getProxyType() != null) {
                    remoteBuilder.setProxyTypeValue(remoteConfig.getProxyType().getCode());
                }
                clientConfigBuilder.addConfig(remoteBuilder.build());
            }
        }
        if (remoteResult != null && !remoteResult.isEmpty()) {
            for (String result : remoteResult) {
                if (result != null) {
                    clientConfigBuilder.addRemoteResult(result);
                }
            }
        }
        return clientConfigBuilder.build().toByteArray();
    }

    public ClientConfig fromProtobuf(ClientConfigProto.ClientConfig clientConfig) {

        serverIp = clientConfig.getServerIp();
        serverPort = clientConfig.getServerPort();
        clientKey = clientConfig.getClientKey();
        connection = clientConfig.getConnection();
        channelId = clientConfig.getChannelId();
        List<ClientConfigProto.ClientConfig.RemoteConfig> configList = clientConfig.getConfigList();
        if (!configList.isEmpty()) {
            config = new ArrayList<>();
            for (ClientConfigProto.ClientConfig.RemoteConfig remoteConfigProto : configList) {
                RemoteConfig remoteConfig = new RemoteConfig();
                remoteConfig.setRemotePort(remoteConfigProto.getRemotePort());
                remoteConfig.setLocalPort(remoteConfigProto.getLocalPort());
                remoteConfig.setLocalIp(remoteConfigProto.getLocalIp());
                remoteConfig.setDescription(remoteConfigProto.getDescription());
                remoteConfig.setProxyType(ProxyType.getInstance(remoteConfigProto.getProxyType().getNumber()));
                remoteConfig.setDomain(remoteConfigProto.getDomain());
                config.add(remoteConfig);
            }
        }
        ProtocolStringList remoteResultList = clientConfig.getRemoteResultList();
        if (!remoteResultList.isEmpty()) {
            remoteResult = new ArrayList<>();
            remoteResult.addAll(remoteResultList);
        }
        return this;
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

    public boolean isConnection() {
        return connection;
    }

    public ClientConfig setConnection(boolean connection) {
        this.connection = connection;
        return this;
    }

    public String getChannelId() {
        return channelId;
    }

    public void setChannelId(String channelId) {
        this.channelId = channelId;
    }

    public ClientConfig setRemoteResult(List<String> remoteResult) {
        this.remoteResult = remoteResult;
        return this;
    }

    public List<String> getRemoteResult() {
        return remoteResult;
    }

    public RemoteConfig getHttpConfig(String domain) {
        for (RemoteConfig remoteConfig : config) {
            ProxyType proxyType = remoteConfig.getProxyType();
            if (proxyType == null) {
                continue;
            }
            if (Objects.equals(ProxyType.HTTP, proxyType) && Objects.equals(domain, remoteConfig.getDomain())) {
                return remoteConfig;
            }
        }
        return null;
    }

    public int getClientLimit() {
        return clientLimit;
    }

    public void setClientLimit(int clientLimit) {
        this.clientLimit = clientLimit;
    }
}
