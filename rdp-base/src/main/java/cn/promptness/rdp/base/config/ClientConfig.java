package cn.promptness.rdp.base.config;

import com.google.common.collect.Lists;
import com.google.protobuf.ProtocolStringList;

import java.util.List;

public class ClientConfig {

    private String serverIp;
    private int serverPort;
    private String clientKey;
    private boolean connection;
    private String channelId;

    private List<RemoteConfig> config;
    private List<String> remoteResult;

    public byte[] toProtobuf() {
        ClientConfigProto.ClientConfig.Builder clientConfigBuilder = ClientConfigProto.ClientConfig.newBuilder();
        clientConfigBuilder.setServerIp(serverIp).setServerPort(serverPort).setConnection(connection);
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
        List<ClientConfigProto.ClientConfig.RemoteConfig> configList = clientConfig.getConfigList();
        if (!configList.isEmpty()) {
            config = Lists.newArrayList();
            for (ClientConfigProto.ClientConfig.RemoteConfig remoteConfigProto : configList) {
                RemoteConfig remoteConfig = new RemoteConfig();
                remoteConfig.setRemotePort(remoteConfigProto.getRemotePort());
                remoteConfig.setLocalPort(remoteConfigProto.getLocalPort());
                remoteConfig.setLocalIp(remoteConfigProto.getLocalIp());
                remoteConfig.setDescription(remoteConfigProto.getDescription());
                config.add(remoteConfig);
            }
        }
        ProtocolStringList remoteResultList = clientConfig.getRemoteResultList();
        if (!remoteResultList.isEmpty()) {
            remoteResult = Lists.newArrayList();
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

}
