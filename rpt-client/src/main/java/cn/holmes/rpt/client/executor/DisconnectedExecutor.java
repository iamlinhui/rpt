package cn.holmes.rpt.client.executor;

import cn.holmes.rpt.base.executor.MessageExecutor;
import cn.holmes.rpt.base.protocol.Message;
import cn.holmes.rpt.base.protocol.MessageType;
import cn.holmes.rpt.base.protocol.Meta;
import cn.holmes.rpt.base.utils.Constants.Client;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;


public class DisconnectedExecutor implements MessageExecutor {

    @Override
    public MessageType getMessageType() {
        return MessageType.TYPE_DISCONNECTED;
    }

    @Override
    public void execute(ChannelHandlerContext context, Message message) {
        String channelId = Optional.ofNullable(message.getMeta()).map(Meta::getChannelId).orElse(null);
        if (channelId == null) {
            return;
        }
        Channel controlChannel = Optional.ofNullable(context.channel().attr(Client.CONTROL).get()).orElse(context.channel());
        Map<String, Channel> channelMap = controlChannel.attr(Client.CHANNELS).get();
        Channel localChannel = Objects.nonNull(channelMap) ? channelMap.remove(channelId) : null;
        if (Objects.isNull(localChannel)) {
            return;
        }
        Optional.ofNullable(localChannel.attr(Client.TUNNEL).get()).flatMap(tunnel -> Optional.ofNullable(tunnel.attr(Client.STREAM_SET).get())).ifPresent(streamSet -> streamSet.remove(channelId));
        InetSocketAddress udpTarget = localChannel.attr(Client.UDP_TARGET).getAndSet(null);
        if (udpTarget != null) {
            localChannel.close();
            return;
        }
        localChannel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
    }
}
