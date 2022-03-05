package cn.promptness.rdp.base.coder;

import cn.promptness.rdp.base.protocol.Message;
import cn.promptness.rdp.base.protocol.MessageType;
import com.google.gson.Gson;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.util.CharsetUtil;


public class MessageEncoder extends MessageToByteEncoder<Message> {

    @Override
    protected void encode(ChannelHandlerContext channelHandlerContext, Message message, ByteBuf out) throws Exception {
        MessageType type = message.getType();
        out.writeInt(type.getCode());

        byte[] clientConfigBytes = new Gson().toJson(message.getClientConfig()).getBytes(CharsetUtil.UTF_8);
        out.writeInt(clientConfigBytes.length);
        out.writeBytes(clientConfigBytes);

        if (message.getData() != null && message.getData().length > 0) {
            out.writeBytes(message.getData());
        }
    }
}
