package cn.holmes.rpt.client.executor;

import cn.holmes.rpt.base.executor.MessageExecutor;
import cn.holmes.rpt.base.protocol.Message;
import cn.holmes.rpt.base.protocol.MessageType;
import cn.holmes.rpt.base.utils.Constants.Client;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;

import java.net.InetSocketAddress;
import java.util.Objects;

public class DataExecutor implements MessageExecutor {

    @Override
    public MessageType getMessageType() {
        return MessageType.TYPE_DATA;
    }

    @Override
    public void execute(ChannelHandlerContext context, Message message) {
        Channel localChannel = context.channel().attr(Client.LOCAL).get();
        if (Objects.isNull(localChannel)) {
            return;
        }
        ByteBuf data = message.hasDataBuf() ? message.getDataBuf().retain() : Unpooled.EMPTY_BUFFER;
        InetSocketAddress udpTarget = localChannel.attr(Client.UDP_TARGET).get();
        if (udpTarget != null) {
            // UDP: 直接转发ByteBuf，零拷贝
            localChannel.writeAndFlush(new DatagramPacket(data, udpTarget));
            return;
        }
        // TCP/HTTP: 直接写ByteBuf，零拷贝
        localChannel.writeAndFlush(data);
    }
}
