package cn.promptness.rpt.base.utils;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

public class Constants {

    private Constants() {
    }

    private static final Logger logger = LoggerFactory.getLogger(Constants.class);

    public static final String REQUEST_CHANNEL_ID = "REQUEST_CHANNEL_ID";

    public static final String RPT = "rpt";

    public static final Pattern PATTERN = Pattern.compile(":");

    private static final byte[] NOT_FOUND = "<html><head><title>404 Not Found</title></head><body><center><h1>404 Not Found</h1></center><hr><center>rpt/2.0.0</center></body></html>".getBytes(StandardCharsets.UTF_8);

    public static byte[] page(String page) {
        try (InputStream resource = ClassLoader.getSystemResourceAsStream(page)) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            if (resource != null) {
                byte[] buf = new byte[1024];
                int len;
                while ((len = resource.read(buf)) != -1) {
                    bos.write(buf, 0, len);
                }
                return bos.toByteArray();
            }
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
        return NOT_FOUND;
    }


}
