package cn.holmes.rpt.server.executor;

import cn.holmes.rpt.base.executor.MessageExecutor;
import cn.holmes.rpt.base.protocol.Message;
import cn.holmes.rpt.base.protocol.MessageType;
import cn.holmes.rpt.base.utils.Constants;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.internal.EmptyArrays;

import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.Optional;

public class DataExecutor implements MessageExecutor {
    @Override
    public MessageType getMessageType() {
        return MessageType.TYPE_DATA;
    }

    @Override
    public void execute(ChannelHandlerContext context, Message message) throws Exception {
        Channel localChannel = context.channel().attr(Constants.LOCAL).get();
        if (Objects.isNull(localChannel)) {
            return;
        }
        byte[] data = Optional.ofNullable(message.getData()).orElse(EmptyArrays.EMPTY_BYTES);
        InetSocketAddress udpSender = context.channel().attr(Constants.UDP_SENDER).get();
        if (udpSender != null) {
            // UDP: 将字节数据包装为DatagramPacket发送回外部客户端
            ByteBuf buf = localChannel.alloc().buffer(data.length);
            buf.writeBytes(data);
            localChannel.writeAndFlush(new DatagramPacket(buf, udpSender));
        } else {
            localChannel.writeAndFlush(data);
        }
    }
}
