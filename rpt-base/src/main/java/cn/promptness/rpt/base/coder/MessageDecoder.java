package cn.promptness.rpt.base.coder;

import cn.promptness.rpt.base.protocol.Message;
import cn.promptness.rpt.base.protocol.MessageType;
import cn.promptness.rpt.base.protocol.Meta;
import cn.promptness.rpt.base.utils.MetaUtils;
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
            byte[] metaByte = new byte[protobufLength];
            byteBuf.readBytes(metaByte);
            Meta meta = MetaUtils.deserialize(metaByte);
            proxyMessage.setMeta(meta);
        }
        if (byteBuf.isReadable()) {
            byte[] data = ByteBufUtil.getBytes(byteBuf);
            proxyMessage.setData(data);
        }
        list.add(proxyMessage);
    }
}
