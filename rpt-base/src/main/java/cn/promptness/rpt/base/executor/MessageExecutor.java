package cn.promptness.rpt.base.executor;

import cn.promptness.rpt.base.protocol.Message;
import cn.promptness.rpt.base.protocol.MessageType;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface MessageExecutor {

    Logger logger = LoggerFactory.getLogger(MessageExecutor.class);

    MessageType getMessageType();

    void execute(ChannelHandlerContext context, Message message) throws Exception;
}
