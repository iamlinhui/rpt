package cn.promptness.rpt.base.utils;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

public interface Constants {

    String REQUEST_CHANNEL_ID = "REQUEST_CHANNEL_ID";

    String RPT = "rpt";

    Pattern PATTERN = Pattern.compile(":");

    byte[] NOT_FOUND = "<html><head><title>404 Not Found</title></head><body><center><h1>404 Not Found</h1></center><hr><center>rpt/2.0.0</center></body></html>".getBytes(StandardCharsets.UTF_8);

}
