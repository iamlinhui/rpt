package cn.promptness.rpt.base.coder;

import cn.promptness.rpt.base.protocol.Message;
import cn.promptness.rpt.base.protocol.MessageType;
import cn.promptness.rpt.base.protocol.Meta;
import cn.promptness.rpt.base.serialize.Jackson;
import cn.promptness.rpt.base.serialize.Serialize;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.util.internal.EmptyArrays;


public class MessageEncoder extends MessageToByteEncoder<Message> {

    private final Serialize serialize = new Jackson();

    @Override
    protected void encode(ChannelHandlerContext channelHandlerContext, Message message, ByteBuf out) throws Exception {
        MessageType type = message.getType();
        out.writeInt(type.getCode());

        Meta meta = message.getMeta();
        byte[] protobuf = meta == null ? EmptyArrays.EMPTY_BYTES : serialize.serialize(meta);
        out.writeInt(protobuf.length);
        out.writeBytes(protobuf);

        if (message.getData() != null && message.getData().length > 0) {
            out.writeBytes(message.getData());
        }
    }
}
