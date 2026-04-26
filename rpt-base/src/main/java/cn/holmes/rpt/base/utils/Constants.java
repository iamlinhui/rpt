package cn.holmes.rpt.base.utils;

import cn.holmes.rpt.base.config.ProxyType;
import cn.holmes.rpt.base.serialize.api.SerializationType;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.util.AttributeKey;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;

public interface Constants {

    AttributeKey<SerializationType> SERIALIZATION_TYPE = AttributeKey.newInstance("SERIALIZATION_TYPE");

    interface Server {
        AttributeKey<String> CLIENT_KEY = AttributeKey.newInstance("CLIENT_KEY");
        AttributeKey<List<String>> DOMAIN = AttributeKey.newInstance("DOMAIN");
        AttributeKey<Map<Integer, ChannelFuture>> TCP_PORT_CHANNEL_FUTURE = AttributeKey.newInstance("TCP_PORT_CHANNEL_FUTURE");
        AttributeKey<Map<Integer, ChannelFuture>> UDP_PORT_CHANNEL_FUTURE = AttributeKey.newInstance("UDP_PORT_CHANNEL_FUTURE");

        AttributeKey<Map<String, Channel>> CHANNELS = AttributeKey.newInstance("CHANNELS");
        AttributeKey<Channel> PROXY = AttributeKey.newInstance("PROXY");
        AttributeKey<Channel> LOCAL = AttributeKey.newInstance("LOCAL");
        AttributeKey<ProxyType> PROXY_TYPE = AttributeKey.newInstance("PROXY_TYPE");
        AttributeKey<InetSocketAddress> UDP_SENDER = AttributeKey.newInstance("UDP_SENDER");
    }

    interface Client {
        AttributeKey<Application<Bootstrap>> APPLICATION = AttributeKey.newInstance("APPLICATION");

        AttributeKey<Map<String, Channel>> CHANNELS = AttributeKey.newInstance("CHANNELS");
        AttributeKey<Channel> PROXY = AttributeKey.newInstance("PROXY");
        AttributeKey<Channel> LOCAL = AttributeKey.newInstance("LOCAL");
        AttributeKey<InetSocketAddress> UDP_TARGET = AttributeKey.newInstance("UDP_TARGET");
    }

    interface Desktop {
        String TITLE = "Reverse Proxy Tool";
        String VERSION = "2.6.1";
    }

}
