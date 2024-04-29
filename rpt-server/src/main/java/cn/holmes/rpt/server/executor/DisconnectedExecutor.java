package cn.holmes.rpt.server.executor;

import cn.holmes.rpt.base.executor.MessageExecutor;
import cn.holmes.rpt.base.protocol.Message;
import cn.holmes.rpt.base.protocol.MessageType;
import cn.holmes.rpt.base.utils.Constants;
import cn.holmes.rpt.server.cache.ServerChannelCache;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.internal.EmptyArrays;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class DisconnectedExecutor implements MessageExecutor {

    @Override
    public MessageType getMessageType() {
        return MessageType.TYPE_DISCONNECTED;
    }

    @Override
    public void execute(ChannelHandlerContext context, Message message) throws Exception {
        String serverId = message.getMeta().getServerId();
        Channel serverChannel = ServerChannelCache.getServerChannelMap().get(serverId);
        if (Objects.isNull(serverChannel)) {
            return;
        }
        Map<String, Channel> localChannelMap = Optional.ofNullable(serverChannel.attr(Constants.CHANNELS).get()).orElse(Collections.emptyMap());
        String channelId = message.getMeta().getChannelId();
        Channel localChannel = localChannelMap.get(channelId);
        if (Objects.isNull(localChannel)) {
            return;
        }
        localChannel.writeAndFlush(EmptyArrays.EMPTY_BYTES).addListener(ChannelFutureListener.CLOSE);
    }
}
