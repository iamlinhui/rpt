package cn.promptness.rpt.base.utils;

import io.netty.channel.Channel;
import io.netty.util.AttributeKey;

import java.util.Map;
import java.util.regex.Pattern;

public interface Constants {

    AttributeKey<Map<String, Channel>> CHANNELS = AttributeKey.newInstance("CHANNELS");

    Pattern COLON = Pattern.compile(":");

    Pattern BLANK = Pattern.compile("\\s");
}
