package cn.holmes.rpt.server.executor;

import cn.holmes.rpt.base.config.ProxyType;
import cn.holmes.rpt.base.executor.MessageExecutor;
import cn.holmes.rpt.base.protocol.Message;
import cn.holmes.rpt.base.protocol.MessageType;
import cn.holmes.rpt.base.utils.Constants.Server;
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
        Channel localChannel = context.channel().attr(Server.LOCAL).get();
        if (Objects.isNull(localChannel)) {
            return;
        }
        byte[] data = Optional.ofNullable(message.getData()).orElse(EmptyArrays.EMPTY_BYTES);
        ProxyType proxyType = localChannel.attr(Server.PROXY_TYPE).get();
        Channel proxyChannel = context.channel();
        if (Objects.equals(ProxyType.UDP, proxyType)) {
            InetSocketAddress udpSender = proxyChannel.attr(Server.UDP_SENDER).get();
            if (udpSender == null) {
                return;
            }
            ByteBuf buf = localChannel.alloc().buffer(data.length);
            buf.writeBytes(data);
            localChannel.writeAndFlush(new DatagramPacket(buf, udpSender));
            return;
        }
        localChannel.writeAndFlush(data);
    }
}
