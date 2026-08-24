package cn.holmes.rpt.base.protocol;

public enum MessageType {

    /**
     *
     */
    TYPE_REGISTER(1, "注册"),
    TYPE_AUTH(2, "授权"),
    TYPE_CONNECTED(3, "建立连接"),
    TYPE_DISCONNECTED(4, "断开连接"),
    TYPE_KEEPALIVE(5, "心跳"),
    TYPE_DATA(6, "数据传输"),
    TYPE_TUNNEL(7, "隧道注册"),
    /**
     * 通道级背压：对端该通道缓冲区达高水位，停止读取本端源数据
     */
    TYPE_PAUSE(8, "通道暂停"),
    /**
     * 通道级背压恢复：对端该通道缓冲区排空到低水位，恢复读取本端源数据
     */
    TYPE_RESUME(9, "通道恢复");

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

    public static MessageType getInstance(int code) {
        for (MessageType value : MessageType.values()) {
            if (value.code == code) {
                return value;
            }
        }
        return null;
    }
}
