package cn.holmes.rpt.client.executor;

import cn.holmes.rpt.base.executor.MessageExecutor;
import cn.holmes.rpt.base.protocol.Message;
import cn.holmes.rpt.base.protocol.MessageType;
import cn.holmes.rpt.base.utils.Constants.Client;
import cn.holmes.rpt.client.cache.ProxyChannelCache;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;

import java.net.InetSocketAddress;
import java.util.Objects;

public class DisconnectedExecutor implements MessageExecutor {

    @Override
    public MessageType getMessageType() {
        return MessageType.TYPE_DISCONNECTED;
    }

    @Override
    public void execute(ChannelHandlerContext context, Message message) {
        Channel localChannel = context.channel().attr(Client.LOCAL).getAndSet(null);
        if (Objects.isNull(localChannel)) {
            ProxyChannelCache.put(context.channel());
            return;
        }
        localChannel.attr(Client.PROXY).set(null);
        InetSocketAddress udpTarget = localChannel.attr(Client.UDP_TARGET).getAndSet(null);
        if (udpTarget != null) {
            localChannel.close();
            ProxyChannelCache.put(context.channel());
            return;
        }
        localChannel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        ProxyChannelCache.put(context.channel());
    }
}
