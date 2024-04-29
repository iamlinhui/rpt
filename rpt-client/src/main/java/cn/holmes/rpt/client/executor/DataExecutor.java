package cn.holmes.rpt.client.executor;

import cn.holmes.rpt.base.executor.MessageExecutor;
import cn.holmes.rpt.base.protocol.Message;
import cn.holmes.rpt.base.protocol.MessageType;
import cn.holmes.rpt.base.utils.Constants;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.internal.EmptyArrays;

import java.util.Objects;
import java.util.Optional;

public class DataExecutor implements MessageExecutor {

    @Override
    public MessageType getMessageType() {
        return MessageType.TYPE_DATA;
    }

    @Override
    public void execute(ChannelHandlerContext context, Message message) {
        Channel localChannel = context.channel().attr(Constants.LOCAL).get();
        if (Objects.nonNull(localChannel)) {
            localChannel.writeAndFlush(Optional.ofNullable(message.getData()).orElse(EmptyArrays.EMPTY_BYTES));
        }
    }
}
