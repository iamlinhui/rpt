package cn.promptness.rpt.base.utils;

import io.netty.channel.Channel;
import io.netty.util.AttributeKey;

import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public interface Constants {

    String TITLE = "Reverse Proxy Tool";

    String VERSION = "2.3.0";

    AttributeKey<Map<String, Channel>> CHANNELS = AttributeKey.newInstance("CHANNELS");

    AttributeKey<String> CLIENT_KEY = AttributeKey.newInstance("CLIENT_KEY");

    AttributeKey<Supplier<Boolean>> APPLICATION = AttributeKey.newInstance("APPLICATION");

    Pattern COLON = Pattern.compile(":");

    Pattern BLANK = Pattern.compile("\\s");
}
