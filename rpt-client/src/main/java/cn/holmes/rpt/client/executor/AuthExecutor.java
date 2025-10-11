package cn.holmes.rpt.client.executor;

import cn.holmes.rpt.base.executor.MessageExecutor;
import cn.holmes.rpt.base.protocol.Message;
import cn.holmes.rpt.base.protocol.MessageType;
import cn.holmes.rpt.base.utils.Constants;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class AuthExecutor implements MessageExecutor {

    private final Map<String, Channel> channelMap = new ConcurrentHashMap<>(1024);

    @Override
    public MessageType getMessageType() {
        return MessageType.TYPE_AUTH;
    }

    @Override
    public void execute(ChannelHandlerContext context, Message message) {
        for (String remoteResult : Optional.ofNullable(message.getMeta().getRemoteResult()).orElse(Collections.emptyList())) {
            logger.info(remoteResult);
        }
        boolean connection = message.getMeta().isConnection();
        if (connection) {
            logger.info("连接成功,当前秘钥:{}", message.getMeta().getClientKey());
            context.channel().attr(Constants.CHANNELS).setIfAbsent(channelMap);
        } else {
            logger.info("连接失败,当前秘钥:{}", message.getMeta().getClientKey());
        }
    }
}
