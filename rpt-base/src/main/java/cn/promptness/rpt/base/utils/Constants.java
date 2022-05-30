package cn.promptness.rpt.base.utils;

import io.netty.channel.Channel;
import io.netty.channel.nio.NioEventLoopGroup;
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
        AttributeKey<NioEventLoopGroup> REMOTE_BOSS_GROUP = AttributeKey.newInstance("REMOTE_BOSS_GROUP");
        AttributeKey<NioEventLoopGroup> REMOTE_WORKER_GROUP = AttributeKey.newInstance("REMOTE_WORKER_GROUP");
    }

    interface Client {
        AttributeKey<Application> APPLICATION = AttributeKey.newInstance("APPLICATION");
    }

    interface Desktop {
        String TITLE = "Reverse Proxy Tool";
        String VERSION = "2.4.0";
    }

    Pattern COLON = Pattern.compile(":");
    Pattern BLANK = Pattern.compile("\\s");
}
