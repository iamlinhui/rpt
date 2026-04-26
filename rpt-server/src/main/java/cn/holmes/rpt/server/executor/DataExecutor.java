package cn.holmes.rpt.server.executor;

import cn.holmes.rpt.base.config.ProxyType;
import cn.holmes.rpt.base.executor.MessageExecutor;
import cn.holmes.rpt.base.protocol.Message;
import cn.holmes.rpt.base.protocol.MessageType;
import cn.holmes.rpt.base.utils.Constants.Server;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;

import java.net.InetSocketAddress;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        ProxyType proxyType = localChannel.attr(Server.PROXY_TYPE).get();
        Channel proxyChannel = context.channel();
        ByteBuf data = message.hasDataBuf() ? message.getDataBuf().retain() : Unpooled.EMPTY_BUFFER;
        if (Objects.equals(ProxyType.UDP, proxyType)) {
            InetSocketAddress udpSender = proxyChannel.attr(Server.UDP_SENDER).get();
            if (udpSender == null) {
                data.release();
                return;
            }
            localChannel.writeAndFlush(new DatagramPacket(data, udpSender));
            return;
        }
        // TCP/HTTP: 直接写ByteBuf，零拷贝
        localChannel.writeAndFlush(data);
    }
}
