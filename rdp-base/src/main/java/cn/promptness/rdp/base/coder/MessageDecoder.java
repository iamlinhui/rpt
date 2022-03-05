package cn.promptness.rdp.base.coder;

import cn.promptness.rdp.base.config.ClientConfig;
import cn.promptness.rdp.base.protocol.Message;
import cn.promptness.rdp.base.protocol.MessageType;
import com.google.gson.Gson;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.util.CharsetUtil;

import java.util.List;


public class MessageDecoder extends MessageToMessageDecoder<ByteBuf> {

    @Override
    protected void decode(ChannelHandlerContext channelHandlerContext, ByteBuf byteBuf, List<Object> list) throws Exception {

        // 4个字节
        MessageType type = MessageType.getInstance(byteBuf.readInt());
        // 4个字节
        int clientConfigLength = byteBuf.readInt();
        CharSequence clientConfigString = byteBuf.readCharSequence(clientConfigLength, CharsetUtil.UTF_8);
        ClientConfig clientConfig = new Gson().fromJson(clientConfigString.toString(), ClientConfig.class);

        byte[] data = null;
        if (byteBuf.isReadable()) {
            data = ByteBufUtil.getBytes(byteBuf);
        }

        Message proxyMessage = new Message();
        proxyMessage.setType(type);
        proxyMessage.setClientConfig(clientConfig);
        proxyMessage.setData(data);

        list.add(proxyMessage);
    }
}
