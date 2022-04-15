package cn.promptness.rpt.base.utils;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.regex.Pattern;

public class Constants {

    private Constants() {
    }

    private static final Logger logger = LoggerFactory.getLogger(Constants.class);

    public static final String TITLE = "Reverse Proxy Tool";

    public static final String RPT = "rpt";

    public static final Pattern PATTERN = Pattern.compile(":");

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
        return new byte[0];
    }


}
