package cn.promptness.rdp.base.protocol;

import cn.promptness.rdp.base.config.ClientConfig;

/**
 * 客户端-服务器自定义通信协议
 */

public class Message {

    /**
     * 元数据
     */
    private ClientConfig clientConfig;
    /**
     * 消息内容
     */
    private byte[] data;

    /**
     * 消息类型
     */
    private MessageType type;

    public ClientConfig getClientConfig() {
        return clientConfig;
    }

    public void setClientConfig(ClientConfig clientConfig) {
        this.clientConfig = clientConfig;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public MessageType getType() {
        return type;
    }

    public void setType(MessageType type) {
        this.type = type;
    }

}
