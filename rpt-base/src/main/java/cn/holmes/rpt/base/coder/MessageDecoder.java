package cn.holmes.rpt.base.coder;

import cn.holmes.rpt.base.protocol.Message;
import cn.holmes.rpt.base.protocol.MessageType;
import cn.holmes.rpt.base.protocol.Meta;
import cn.holmes.rpt.base.serialize.SerializerDispatcher;
import cn.holmes.rpt.base.serialize.api.SerializationType;
import cn.holmes.rpt.base.utils.Constants;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;

import java.util.List;

/**
 * messageLength|messageType|serializerType|metaLength|meta|data
 */
public class MessageDecoder extends MessageToMessageDecoder<ByteBuf> {

    private static final SerializerDispatcher SERIALIZER_DISPATCHER = new SerializerDispatcher();

    @Override
    protected void decode(ChannelHandlerContext channelHandlerContext, ByteBuf byteBuf, List<Object> list) throws Exception {
        Message message = new Message();
        // 4个字节
        message.setType(MessageType.getInstance(byteBuf.readInt()));
        SerializationType serialization = SerializationType.getInstance(byteBuf.readInt());
        channelHandlerContext.channel().attr(Constants.SERIALIZATION_TYPE).set(serialization);
        int metaByteLength = byteBuf.readInt();
        if (metaByteLength > 0) {
            byte[] metaByte = new byte[metaByteLength];
            byteBuf.readBytes(metaByte);
            Meta meta = SERIALIZER_DISPATCHER.deserialize(serialization, metaByte, Meta.class);
            message.setMeta(meta);
        }
        if (byteBuf.isReadable()) {
            byte[] data = ByteBufUtil.getBytes(byteBuf);
            message.setData(data);
        }
        list.add(message);
    }
}
