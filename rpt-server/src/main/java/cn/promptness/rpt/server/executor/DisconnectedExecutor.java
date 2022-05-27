package cn.promptness.rpt.server.executor;

import cn.promptness.rpt.base.executor.MessageExecutor;
import cn.promptness.rpt.base.protocol.Message;
import cn.promptness.rpt.base.protocol.MessageType;
import cn.promptness.rpt.base.utils.Constants;
import cn.promptness.rpt.server.cache.ServerChannelCache;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.internal.EmptyArrays;

import java.util.Map;

public class DisconnectedExecutor implements MessageExecutor {

    @Override
    public MessageType getMessageType() {
        return MessageType.TYPE_DISCONNECTED;
    }

    @Override
    public void execute(ChannelHandlerContext context, Message message) throws Exception {
        String serverId = message.getMeta().getServerId();
        Map<String, Channel> channelMap = ServerChannelCache.getServerChannelMap().get(serverId).attr(Constants.CHANNELS).get();

        String channelId = message.getMeta().getChannelId();
        Channel localChannel = channelMap.get(channelId);
        if (localChannel == null) {
            return;
        }
        localChannel.writeAndFlush(EmptyArrays.EMPTY_BYTES).addListener(ChannelFutureListener.CLOSE);
    }
}
