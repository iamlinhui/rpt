package cn.holmes.rpt.client.executor;

import cn.holmes.rpt.base.config.ClientConfig;
import cn.holmes.rpt.base.executor.MessageExecutor;
import cn.holmes.rpt.base.protocol.Message;
import cn.holmes.rpt.base.protocol.MessageType;
import cn.holmes.rpt.base.utils.Config;
import cn.holmes.rpt.base.utils.Constants;
import cn.holmes.rpt.client.cache.ProxyChannelCache;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
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

    /**
     * 连接池预热数量
     */
    private static final int POOL_WARM_UP_SIZE = 3;

    @Override
    public void execute(ChannelHandlerContext context, Message message) {
        for (String remoteResult : Optional.ofNullable(message.getMeta().getRemoteResult()).orElse(Collections.emptyList())) {
            logger.info(remoteResult);
        }
        boolean connection = message.getMeta().isConnection();
        if (connection) {
            logger.info("连接成功,当前秘钥:{}", message.getMeta().getClientKey());
            context.channel().attr(Constants.CHANNELS).setIfAbsent(channelMap);
            // 预热代理连接池，避免首次请求因TLS握手延迟导致失败
            warmUpProxyPool(context.channel());
        } else {
            logger.info("连接失败,当前秘钥:{}", message.getMeta().getClientKey());
        }
    }

    private void warmUpProxyPool(Channel serverChannel) {
        ClientConfig clientConfig = Config.getClientConfig();
        Bootstrap bootstrap = serverChannel.attr(Constants.Client.APPLICATION).get().bootstrap();
        for (int i = 0; i < POOL_WARM_UP_SIZE; i++) {
            bootstrap.connect(clientConfig.getServerIp(), clientConfig.getServerPort()).addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    ProxyChannelCache.put(future.channel());
                    logger.debug("预热代理连接池成功");
                }
            });
        }
    }
}
