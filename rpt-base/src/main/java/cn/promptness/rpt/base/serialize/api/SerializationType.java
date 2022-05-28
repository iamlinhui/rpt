package cn.promptness.rpt.base.serialize.api;

public enum SerializationType {

    /**
     *
     */
    PROTOSTUFF(1, "protostuff"),
    JSON(2, "jackson");

    SerializationType(int code, String desc) {
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
}
