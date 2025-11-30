package cn.holmes.rpt.base.coder;

import cn.holmes.rpt.base.protocol.Message;
import cn.holmes.rpt.base.protocol.MessageType;
import cn.holmes.rpt.base.protocol.Meta;
import cn.holmes.rpt.base.serialize.SerializerDispatcher;
import cn.holmes.rpt.base.serialize.api.SerializationType;
import cn.holmes.rpt.base.utils.Constants;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.util.internal.EmptyArrays;

import java.util.Optional;

/**
 * messageLength|messageType|serializerType|metaLength|meta|data
 */
public class MessageEncoder extends MessageToByteEncoder<Message> {

    private static final SerializerDispatcher SERIALIZER_DISPATCHER = new SerializerDispatcher();

    @Override
    protected void encode(ChannelHandlerContext channelHandlerContext, Message message, ByteBuf out) throws Exception {
        SerializationType serializationType = Optional.ofNullable(channelHandlerContext.channel().attr(Constants.SERIALIZATION_TYPE).get()).orElse(message.getSerialization());
        MessageType type = message.getType();
        out.writeInt(type.getCode());
        out.writeInt(serializationType.getCode());
        Meta meta = message.getMeta();
        byte[] metaByte = meta == null ? EmptyArrays.EMPTY_BYTES : SERIALIZER_DISPATCHER.serialize(serializationType, meta);
        out.writeInt(metaByte.length);
        out.writeBytes(metaByte);

        if (message.getData() != null && message.getData().length > 0) {
            out.writeBytes(message.getData());
        }
    }
}
