package cn.promptness.rpt.base.coder;

import cn.promptness.rpt.base.config.ClientConfig;
import cn.promptness.rpt.base.protocol.Message;
import cn.promptness.rpt.base.protocol.MessageType;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.util.internal.EmptyArrays;


public class MessageEncoder extends MessageToByteEncoder<Message> {

    @Override
    protected void encode(ChannelHandlerContext channelHandlerContext, Message message, ByteBuf out) throws Exception {
        MessageType type = message.getType();
        out.writeInt(type.getCode());

        ClientConfig clientConfig = message.getClientConfig();
        byte[] protobuf = clientConfig == null ? EmptyArrays.EMPTY_BYTES : clientConfig.toProtobuf();
        out.writeInt(protobuf.length);
        out.writeBytes(protobuf);

        if (message.getData() != null && message.getData().length > 0) {
            out.writeBytes(message.getData());
        }
    }
}
