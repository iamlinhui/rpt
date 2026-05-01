package cn.holmes.rpt.base.executor;

import cn.holmes.rpt.base.protocol.Message;
import cn.holmes.rpt.base.protocol.MessageType;
import io.netty.channel.ChannelHandlerContext;

public interface MessageExecutor {

    MessageType getMessageType();

    void execute(ChannelHandlerContext context, Message message) throws Exception;
}
