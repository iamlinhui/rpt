package cn.promptness.rpt.client.cache;

import cn.promptness.rpt.base.config.ClientConfig;
import cn.promptness.rpt.base.utils.Config;
import cn.promptness.rpt.base.utils.Constants;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class ProxyChannelCache {

    private static final Logger logger = LoggerFactory.getLogger(ProxyChannelCache.class);

    private static final Integer MAX_QUEUE_LIMIT = 128;

    private static final Queue<Channel> PROXY_CHANNEL_QUEUE = new LinkedBlockingQueue<>();

    public static void get(Bootstrap bootstrap, Consumer<Channel> success, Supplier<ChannelFuture> fail) {
        Channel proxyChannel = PROXY_CHANNEL_QUEUE.poll();
        if (proxyChannel != null && proxyChannel.isActive()) {
            success.accept(proxyChannel);
            return;
        }
        ClientConfig clientConfig = Config.getClientConfig();
        bootstrap.connect(clientConfig.getServerIp(), clientConfig.getServerPort()).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                success.accept(future.channel());
            } else {
                future.channel().close();
                fail.get();
            }
        });

    }

    public static void put(Channel proxyChannel) {
        if (PROXY_CHANNEL_QUEUE.size() > MAX_QUEUE_LIMIT) {
            proxyChannel.close();
            return;
        }
        if (proxyChannel.isActive()) {
            proxyChannel.config().setAutoRead(true);
            proxyChannel.attr(Constants.LOCAL).set(null);
            PROXY_CHANNEL_QUEUE.offer(proxyChannel);
        }
        logger.debug("连接池闲置连接数:{}个", PROXY_CHANNEL_QUEUE.size());
    }

    public static void delete(Channel proxyChannel) {
        PROXY_CHANNEL_QUEUE.remove(proxyChannel);
    }

}
