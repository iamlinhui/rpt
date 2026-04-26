package cn.holmes.rpt.base.config;

public enum ProxyType {

    /**
     *
     */
    TCP(0, "tcp"),
    HTTP(1, "http"),
    UDP(2, "udp");

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

}
