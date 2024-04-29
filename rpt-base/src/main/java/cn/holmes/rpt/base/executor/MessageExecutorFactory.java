package cn.holmes.rpt.base.executor;

import cn.holmes.rpt.base.protocol.MessageType;

import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

public class MessageExecutorFactory {

    private static final Map<MessageType, MessageExecutor> MESSAGE_EXECUTOR_MAP = new ConcurrentHashMap<>();

    static {
        ServiceLoader<MessageExecutor> executors = ServiceLoader.load(MessageExecutor.class);
        for (MessageExecutor executor : executors) {
            MESSAGE_EXECUTOR_MAP.put(executor.getMessageType(), executor);
        }
    }

    public static MessageExecutor getMessageExecutor(MessageType messageType) {
        return MESSAGE_EXECUTOR_MAP.get(messageType);
    }
}
