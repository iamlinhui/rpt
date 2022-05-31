package cn.promptness.rpt.client.executor;

import cn.promptness.rpt.base.executor.MessageExecutor;
import cn.promptness.rpt.base.protocol.Message;
import cn.promptness.rpt.base.protocol.MessageType;
import cn.promptness.rpt.base.utils.Constants;
import cn.promptness.rpt.client.cache.ProxyChannelCache;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.internal.EmptyArrays;

import java.util.Objects;

public class DisconnectedExecutor implements MessageExecutor {
    @Override
    public MessageType getMessageType() {
        return MessageType.TYPE_DISCONNECTED;
    }

    @Override
    public void execute(ChannelHandlerContext context, Message message) {
        Channel localChannel = context.channel().attr(Constants.LOCAL).getAndSet(null);
        if (Objects.nonNull(localChannel)) {
            localChannel.attr(Constants.PROXY).set(null);
            localChannel.writeAndFlush(EmptyArrays.EMPTY_BYTES).addListener(ChannelFutureListener.CLOSE);
        }
        ProxyChannelCache.put(context.channel());
    }
}
