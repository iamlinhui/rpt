package cn.promptness.rdp.base.coder;

import cn.promptness.rdp.base.protocol.Message;
import cn.promptness.rdp.base.protocol.MessageType;
import com.google.gson.Gson;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.util.CharsetUtil;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;


public class MessageEncoder extends MessageToByteEncoder<Message> {

    @Override
    protected void encode(ChannelHandlerContext channelHandlerContext, Message message, ByteBuf out) throws Exception {
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
            try (DataOutputStream dataOutputStream = new DataOutputStream(byteArrayOutputStream)) {
                MessageType type = message.getType();
                dataOutputStream.writeInt(type.getCode());

                byte[] clientConfigBytes = new Gson().toJson(message.getClientConfig()).getBytes(CharsetUtil.UTF_8);
                dataOutputStream.writeInt(clientConfigBytes.length);
                dataOutputStream.write(clientConfigBytes);

                if (message.getData() != null && message.getData().length > 0) {
                    dataOutputStream.write(message.getData());
                }

                byte[] data = byteArrayOutputStream.toByteArray();
                out.writeInt(data.length);
                out.writeBytes(data);
            }
        }


    }
}
