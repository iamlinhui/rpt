package cn.promptness.rpt.base.protocol;

import java.util.Locale;
import java.util.Objects;

public enum ProxyType {

    /**
     *
     */
    TCP(0, "tcp"),
    HTTP(1, "http");

    ProxyType(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }


    final int code;
    final String desc;

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static ProxyType getInstance(int code) {
        for (ProxyType value : ProxyType.values()) {
            if (value.code == code) {
                return value;
            }
        }
        return null;
    }

    public static ProxyType getInstance(String desc) {
        for (ProxyType value : ProxyType.values()) {
            if (Objects.equals(value.desc, desc.toLowerCase(Locale.ROOT))) {
                return value;
            }
        }
        return null;
    }

}
