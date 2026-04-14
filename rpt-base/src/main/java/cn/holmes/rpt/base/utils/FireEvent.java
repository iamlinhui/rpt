package cn.holmes.rpt.base.utils;

import cn.holmes.rpt.base.protocol.MessageType;
import io.netty.channel.Channel;

public class FireEvent {

    private String channelId;

    private Channel proxyChannel;

    private MessageType messageType;

    public FireEvent(String channelId, Channel proxyChannel, MessageType messageType) {
        this.channelId = channelId;
        this.proxyChannel = proxyChannel;
        this.messageType = messageType;
    }

    public String getChannelId() {
        return channelId;
    }

    public void setChannelId(String channelId) {
        this.channelId = channelId;
    }

    public MessageType getMessageType() {
        return messageType;
    }

    public void setMessageType(MessageType messageType) {
        this.messageType = messageType;
    }

    public Channel getProxyChannel() {
        return proxyChannel;
    }

    public void setProxyChannel(Channel proxyChannel) {
        this.proxyChannel = proxyChannel;
    }
}
