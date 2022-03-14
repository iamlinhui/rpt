package cn.promptness.rpt.base.coder;

import cn.promptness.rpt.base.config.ClientConfig;
import cn.promptness.rpt.base.config.ClientConfigProto;
import cn.promptness.rpt.base.protocol.Message;
import cn.promptness.rpt.base.protocol.MessageType;
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

        int protobufLength = byteBuf.readInt();
        if (protobufLength > 0) {
            byte[] clientConfigByte = new byte[protobufLength];
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
