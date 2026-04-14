package cn.holmes.rpt.client.executor;

import cn.holmes.rpt.base.executor.MessageExecutor;
import cn.holmes.rpt.base.protocol.Message;
import cn.holmes.rpt.base.protocol.MessageType;
import cn.holmes.rpt.base.utils.Constants.Client;
import cn.holmes.rpt.client.cache.ProxyChannelCache;
import io.netty.channel.ChannelHandlerContext;

import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class AuthExecutor implements MessageExecutor {

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
            context.channel().attr(Client.CHANNELS).setIfAbsent(new ConcurrentHashMap<>(1024));
            // 预热代理连接池，避免首次请求因TLS握手延迟导致失败
            ProxyChannelCache.init(context.channel());
        } else {
            logger.info("连接失败,当前秘钥:{}", message.getMeta().getClientKey());
        }
    }
}
