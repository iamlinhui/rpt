package cn.promptness.rdp.base.coder;

import cn.promptness.rdp.base.config.ClientConfig;
import cn.promptness.rdp.base.config.ClientConfigProto;
import cn.promptness.rdp.base.protocol.Message;
import cn.promptness.rdp.base.protocol.MessageType;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;

import java.util.List;


public class MessageDecoder extends MessageToMessageDecoder<ByteBuf> {

    @Override
    protected void decode(ChannelHandlerContext channelHandlerContext, ByteBuf byteBuf, List<Object> list) throws Exception {

        // 4个字节
        MessageType type = MessageType.getInstance(byteBuf.readInt());

        byte[] clientConfigByte = new byte[byteBuf.readInt()];
        byteBuf.readBytes(clientConfigByte);
        ClientConfigProto.ClientConfig clientConfig = ClientConfigProto.ClientConfig.parseFrom(clientConfigByte);

        byte[] data = null;
        if (byteBuf.isReadable()) {
            data = ByteBufUtil.getBytes(byteBuf);
        }

        Message proxyMessage = new Message();
        proxyMessage.setType(type);
        proxyMessage.setClientConfig(new ClientConfig().fromProtobuf(clientConfig));
        proxyMessage.setData(data);

        list.add(proxyMessage);
    }
}
