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
        Message proxyMessage = new Message();
        // 4个字节
        proxyMessage.setType(MessageType.getInstance(byteBuf.readInt()));

        int length = byteBuf.readInt();
        if (length > 0) {
            byte[] clientConfigByte = new byte[length];
            byteBuf.readBytes(clientConfigByte);
            ClientConfigProto.ClientConfig clientConfig = ClientConfigProto.ClientConfig.parseFrom(clientConfigByte);
            proxyMessage.setClientConfig(new ClientConfig().fromProtobuf(clientConfig));
        }

        byte[] data = null;
        if (byteBuf.isReadable()) {
            data = ByteBufUtil.getBytes(byteBuf);
        }
        proxyMessage.setData(data);

        list.add(proxyMessage);
    }
}
