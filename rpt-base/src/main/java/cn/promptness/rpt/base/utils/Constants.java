package cn.promptness.rpt.base.utils;

import io.netty.channel.Channel;
import io.netty.util.AttributeKey;

import java.util.Map;
import java.util.regex.Pattern;

public interface Constants {

    String TITLE = "Reverse Proxy Tool";

    String VERSION = "2.3.5";

    AttributeKey<Map<String, Channel>> CHANNELS = AttributeKey.newInstance("CHANNELS");

    AttributeKey<Channel> PROXY = AttributeKey.newInstance("PROXY");

    AttributeKey<Channel> LOCAL = AttributeKey.newInstance("LOCAL");

    AttributeKey<String> CLIENT_KEY = AttributeKey.newInstance("CLIENT_KEY");

    AttributeKey<Application<Boolean>> APPLICATION = AttributeKey.newInstance("APPLICATION");

    Pattern COLON = Pattern.compile(":");

    Pattern BLANK = Pattern.compile("\\s");
}
