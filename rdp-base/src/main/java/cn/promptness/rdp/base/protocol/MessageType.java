package cn.promptness.rdp.base.protocol;

public enum MessageType {

    /**
     *
     */
    TYPE_REGISTER(1, "注册"),
    TYPE_AUTH(2, "授权"),
    TYPE_CONNECTED(3, "建立连接"),
    TYPE_DISCONNECTED(4, "断开连接"),
    TYPE_KEEPALIVE(5, "心跳"),
    TYPE_DATA(6, "数据传输");

    MessageType(int code, String desc) {
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

    public static MessageType getInstance(int code){
        for (MessageType value : MessageType.values()) {
            if (value.code == code) {
                return value;
            }
        }
        return null;
    }
}
