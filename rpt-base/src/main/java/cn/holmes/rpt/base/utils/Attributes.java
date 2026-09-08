package cn.holmes.rpt.base.utils;

import cn.holmes.rpt.base.bootstrap.Application;
import cn.holmes.rpt.base.config.ProxyType;
import cn.holmes.rpt.base.protocol.Endpoint;
import cn.holmes.rpt.base.mux.ChannelBuffer;
import cn.holmes.rpt.base.serialize.api.SerializationType;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.util.AttributeKey;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface Attributes {

    AttributeKey<SerializationType> SERIALIZATION_TYPE = AttributeKey.newInstance("SERIALIZATION_TYPE");

    interface Server {
        AttributeKey<String> CLIENT_KEY = AttributeKey.newInstance("CLIENT_KEY");
        AttributeKey<List<String>> DOMAIN = AttributeKey.newInstance("DOMAIN");
        AttributeKey<Map<Integer, ChannelFuture>> TCP_PORT_CHANNEL_FUTURE = AttributeKey.newInstance("TCP_PORT_CHANNEL_FUTURE");
        AttributeKey<Map<Integer, ChannelFuture>> UDP_PORT_CHANNEL_FUTURE = AttributeKey.newInstance("UDP_PORT_CHANNEL_FUTURE");

        AttributeKey<Long> CONNECT_TIME = AttributeKey.newInstance("CONNECT_TIME");
        AttributeKey<Map<String, Channel>> CHANNELS = AttributeKey.newInstance("CHANNELS");
        AttributeKey<Channel> PROXY = AttributeKey.newInstance("PROXY");
        AttributeKey<String> SERVER_ID = AttributeKey.newInstance("SERVER_ID");
        AttributeKey<ProxyType> PROXY_TYPE = AttributeKey.newInstance("PROXY_TYPE");
        AttributeKey<Endpoint> DYNAMIC_TARGET = AttributeKey.newInstance("DYNAMIC_TARGET");
        /**
         * 隧道上承载的会话channelId集合（多路复用反向索引，隧道断开时据此清理会话）
         */
        AttributeKey<Set<String>> STREAM_SET = AttributeKey.newInstance("STREAM_SET");
        /**
         * 控制通道上挂的共享数据隧道集合（控制通道断开时一并关闭）
         */
        AttributeKey<Set<Channel>> TUNNEL_SET = AttributeKey.newInstance("TUNNEL_SET");
        /**
         * UDP本地通道上的会话channelId → 对端发送者地址（多路复用下按会话区分，挂在UDP DatagramChannel上）
         */
        AttributeKey<Map<String, InetSocketAddress>> UDP_SENDERS = AttributeKey.newInstance("UDP_SENDERS");
        /**
         * 外部连接上的通道级带水位线缓冲区（隧道→外部连接方向，达高水位发TYPE_PAUSE）
         */
        AttributeKey<ChannelBuffer> BUFFER = AttributeKey.newInstance("SERVER_BUFFER");
        /**
         * 外部连接被通道级TYPE_PAUSE暂停中，隧道级背压恢复时跳过该通道
         */
        AttributeKey<Boolean> PAUSED = AttributeKey.newInstance("SERVER_PAUSED");
        /**
         * 外部连接对应的会话channelId（供缓冲区回调构造PAUSE/RESUME的Meta）
         */
        AttributeKey<String> CHANNEL_ID = AttributeKey.newInstance("SERVER_CHANNEL_ID");
    }

    interface Client {
        AttributeKey<Application<Bootstrap>> APPLICATION = AttributeKey.newInstance("APPLICATION");

        AttributeKey<Map<String, Channel>> CHANNELS = AttributeKey.newInstance("CHANNELS");
        /**
         * 本地连接 → 承载它的共享隧道
         */
        AttributeKey<Channel> TUNNEL = AttributeKey.newInstance("TUNNEL");
        /**
         * 共享隧道 → 控制通道
         */
        AttributeKey<Channel> CONTROL = AttributeKey.newInstance("CONTROL");
        /**
         * 控制通道上服务端回填的serverId（服务端侧控制通道channel id，隧道补连注册用）
         */
        AttributeKey<String> SERVER_ID = AttributeKey.newInstance("CLIENT_SERVER_ID");
        /**
         * 隧道上承载的会话channelId集合（隧道级背压、隧道断开时清理本地连接）
         */
        AttributeKey<Set<String>> STREAM_SET = AttributeKey.newInstance("CLIENT_STREAM_SET");
        AttributeKey<InetSocketAddress> UDP_TARGET = AttributeKey.newInstance("UDP_TARGET");
        /**
         * 本地连接上的通道级带水位线缓冲区（隧道→本地连接方向，达高水位发TYPE_PAUSE）
         */
        AttributeKey<ChannelBuffer> BUFFER = AttributeKey.newInstance("CLIENT_BUFFER");
        /**
         * 本地连接被通道级TYPE_PAUSE暂停中，隧道级背压恢复时跳过该通道
         */
        AttributeKey<Boolean> PAUSED = AttributeKey.newInstance("CLIENT_PAUSED");
        /**
         * 本地连接对应的会话channelId（供缓冲区回调构造PAUSE/RESUME的Meta）
         */
        AttributeKey<String> CHANNEL_ID = AttributeKey.newInstance("CLIENT_CHANNEL_ID");
    }

}
