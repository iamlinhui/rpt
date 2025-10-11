package cn.holmes.rpt.base.utils;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.util.AttributeKey;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public interface Constants {

    AttributeKey<Map<String, Channel>> CHANNELS = AttributeKey.newInstance("CHANNELS");
    AttributeKey<Channel> PROXY = AttributeKey.newInstance("PROXY");
    AttributeKey<Channel> LOCAL = AttributeKey.newInstance("LOCAL");

    interface Server {
        AttributeKey<String> CLIENT_KEY = AttributeKey.newInstance("CLIENT_KEY");
        AttributeKey<Class<Void>> LABEL = AttributeKey.newInstance("LABEL");
        AttributeKey<List<String>> DOMAIN = AttributeKey.newInstance("DOMAIN");
        AttributeKey<Map<Integer, ChannelFuture>> PORT_CHANNEL_FUTURE = AttributeKey.newInstance("PORT_CHANNEL_FUTURE");
    }

    interface Client {
        AttributeKey<Application<Bootstrap>> APPLICATION = AttributeKey.newInstance("APPLICATION");
    }

    interface Desktop {
        String TITLE = "Reverse Proxy Tool";
        String VERSION = "2.5.1";
    }

    Pattern COLON = Pattern.compile(":");
    Pattern BLANK = Pattern.compile("\\s");
}
