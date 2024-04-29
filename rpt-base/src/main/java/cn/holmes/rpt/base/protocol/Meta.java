package cn.holmes.rpt.base.protocol;

import cn.holmes.rpt.base.config.RemoteConfig;

import java.util.Collections;
import java.util.List;

public class Meta {

    private String clientKey;
    private boolean connection;
    private String channelId;
    private String serverId;
    private List<RemoteConfig> remoteConfigList;
    private List<String> remoteResult;

    public Meta() {

    }

    public Meta(String clientKey, List<RemoteConfig> remoteConfigList) {
        this.clientKey = clientKey;
        this.remoteConfigList = remoteConfigList;
    }

    public Meta(String channelId, RemoteConfig remoteConfig) {
        this.channelId = channelId;
        this.remoteConfigList = Collections.singletonList(remoteConfig);
    }

    public String getClientKey() {
        return clientKey;
    }

    public Meta setClientKey(String clientKey) {
        this.clientKey = clientKey;
        return this;
    }

    public boolean isConnection() {
        return connection;
    }

    public Meta setConnection(boolean connection) {
        this.connection = connection;
        return this;
    }

    public String getChannelId() {
        return channelId;

    }

    public Meta setChannelId(String channelId) {
        this.channelId = channelId;
        return this;
    }

    public String getServerId() {
        return serverId;
    }

    public Meta setServerId(String serverId) {
        this.serverId = serverId;
        return this;
    }

    public RemoteConfig getRemoteConfig() {
        if (remoteConfigList == null || remoteConfigList.isEmpty()) {
            return null;
        }
        return remoteConfigList.get(0);
    }

    public List<RemoteConfig> getRemoteConfigList() {
        return remoteConfigList;
    }

    public Meta setRemoteConfigList(List<RemoteConfig> remoteConfigList) {
        this.remoteConfigList = remoteConfigList;
        return this;
    }

    public List<String> getRemoteResult() {
        return remoteResult;
    }

    public Meta setRemoteResult(List<String> remoteResult) {
        this.remoteResult = remoteResult;
        return this;
    }

    public Meta addRemoteResult(String remoteResult) {
        if (this.remoteResult != null) {
            this.remoteResult.add(remoteResult);
        }
        return this;
    }
}
