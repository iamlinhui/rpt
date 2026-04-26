package cn.holmes.rpt.base.protocol;

import cn.holmes.rpt.base.serialize.api.SerializationType;
import io.netty.buffer.ByteBuf;
import io.netty.util.ReferenceCounted;

/**
 * 客户端-服务器自定义通信协议
 */
public class Message implements ReferenceCounted {

    /**
     * 消息类型
     */
    private MessageType type;

    private SerializationType serialization = SerializationType.PROTOSTUFF;

    /**
     * 元数据
     */
    private Meta meta;

    /**
     * 零拷贝数据缓冲区 (用于数据传输消息，避免ByteBuf→byte[]→ByteBuf的拷贝)
     */
    private ByteBuf dataBuf;

    public Message() {
    }

    public Message(MessageType type, Meta meta, ByteBuf dataBuf) {
        this.meta = meta;
        this.dataBuf = dataBuf;
        this.type = type;
    }

    public Meta getMeta() {
        return meta;
    }

    public void setMeta(Meta meta) {
        this.meta = meta;
    }

    public ByteBuf getDataBuf() {
        return dataBuf;
    }

    public void setDataBuf(ByteBuf dataBuf) {
        this.dataBuf = dataBuf;
    }

    public MessageType getType() {
        return type;
    }

    public void setType(MessageType type) {
        this.type = type;
    }

    public SerializationType getSerialization() {
        return serialization;
    }

    public void setSerialization(SerializationType serialization) {
        this.serialization = serialization;
    }

    /**
     * 判断是否持有零拷贝ByteBuf数据
     */
    public boolean hasDataBuf() {
        return dataBuf != null && dataBuf.isReadable();
    }

    @Override
    public int refCnt() {
        return dataBuf != null ? dataBuf.refCnt() : 1;
    }

    @Override
    public Message retain() {
        if (dataBuf != null) {
            dataBuf.retain();
        }
        return this;
    }

    @Override
    public Message retain(int increment) {
        if (dataBuf != null) {
            dataBuf.retain(increment);
        }
        return this;
    }

    @Override
    public Message touch() {
        if (dataBuf != null) {
            dataBuf.touch();
        }
        return this;
    }

    @Override
    public Message touch(Object hint) {
        if (dataBuf != null) {
            dataBuf.touch(hint);
        }
        return this;
    }

    @Override
    public boolean release() {
        return dataBuf != null && dataBuf.release();
    }

    @Override
    public boolean release(int decrement) {
        return dataBuf != null && dataBuf.release(decrement);
    }
}
