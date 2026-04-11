package cn.holmes.rpt.client.cache;

import cn.holmes.rpt.base.config.ClientConfig;
import cn.holmes.rpt.base.protocol.Meta;
import cn.holmes.rpt.base.utils.Config;
import cn.holmes.rpt.base.utils.Constants;
import cn.holmes.rpt.base.utils.Listener;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

public class ProxyChannelCache {

    private static final Logger logger = LoggerFactory.getLogger(ProxyChannelCache.class);

    private static final Integer MAX_QUEUE_LIMIT = 128;

    private static final Queue<Channel> PROXY_CHANNEL_QUEUE = new LinkedBlockingQueue<>(MAX_QUEUE_LIMIT);

    public static void get(Channel serverChannel, Meta meta, Listener<Meta> listener) {
        // 循环排空失效连接，找到第一个可用的
        Channel proxyChannel;
        while ((proxyChannel = PROXY_CHANNEL_QUEUE.poll()) != null) {
            if (proxyChannel.isActive()) {
                listener.success(serverChannel, proxyChannel, meta);
                return;
            }
            proxyChannel.close();
        }
        // 池中无可用连接，新建代理连接
        ClientConfig clientConfig = Config.getClientConfig();
        Bootstrap bootstrap = serverChannel.attr(Constants.Client.APPLICATION).get().bootstrap();
        bootstrap.connect(clientConfig.getServerIp(), clientConfig.getServerPort()).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                listener.success(serverChannel, future.channel(), meta);
            } else {
                listener.fail(serverChannel, meta);
            }
        });
    }

    public static void put(Channel proxyChannel) {
        if (!proxyChannel.isActive()) {
            proxyChannel.close();
            return;
        }
        proxyChannel.config().setAutoRead(true);
        proxyChannel.attr(Constants.LOCAL).set(null);
        if (!PROXY_CHANNEL_QUEUE.offer(proxyChannel)) {
            proxyChannel.close();
        }
        logger.debug("连接池闲置连接数:{}个", PROXY_CHANNEL_QUEUE.size());
    }

    public static void delete(Channel proxyChannel) {
        PROXY_CHANNEL_QUEUE.remove(proxyChannel);
    }

}
