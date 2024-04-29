package cn.holmes.rpt.base.coder;

import cn.holmes.rpt.base.protocol.Message;
import cn.holmes.rpt.base.protocol.MessageType;
import cn.holmes.rpt.base.protocol.Meta;
import cn.holmes.rpt.base.serialize.SerializerDispatcher;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.util.internal.EmptyArrays;

/**
 * messageLength|messageType|serializerType|metaLength|meta|data
 */
public class MessageEncoder extends MessageToByteEncoder<Message> {

    private static final SerializerDispatcher SERIALIZER_DISPATCHER = new SerializerDispatcher();

    @Override
    protected void encode(ChannelHandlerContext channelHandlerContext, Message message, ByteBuf out) throws Exception {
        MessageType type = message.getType();
        out.writeInt(type.getCode());
        out.writeInt(message.getSerialization().getCode());

        Meta meta = message.getMeta();
        byte[] metaByte = meta == null ? EmptyArrays.EMPTY_BYTES : SERIALIZER_DISPATCHER.serialize(message.getSerialization(), meta);
        out.writeInt(metaByte.length);
        out.writeBytes(metaByte);

        if (message.getData() != null && message.getData().length > 0) {
            out.writeBytes(message.getData());
        }
    }
}
