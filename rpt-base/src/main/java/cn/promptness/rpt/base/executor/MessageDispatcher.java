package cn.promptness.rpt.base.executor;

import cn.promptness.rpt.base.protocol.Message;
import cn.promptness.rpt.base.protocol.MessageType;
import io.netty.channel.ChannelHandlerContext;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;

public class MessageDispatcher {

    private static final Map<MessageType, MessageExecutor> MESSAGE_EXECUTOR_MAP = new HashMap<>();

    static {
        ServiceLoader<MessageExecutor> executors = ServiceLoader.load(MessageExecutor.class);
        for (MessageExecutor executor : executors) {
            MessageExecutor messageExecutor = MESSAGE_EXECUTOR_MAP.put(executor.getMessageType(), executor);
            if (messageExecutor != null) {
                throw new RuntimeException(String.format("%s Message Executor Register Repeat", messageExecutor.getMessageType().name()));
            }
        }
    }

    public void handle(ChannelHandlerContext context, Message message) throws Exception {
        MessageExecutor messageExecutor = MESSAGE_EXECUTOR_MAP.get(message.getType());
        if (messageExecutor == null) {
            return;
        }
        messageExecutor.execute(context, message);
    }
}
