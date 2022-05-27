package cn.promptness.rpt.client.cache;

import cn.promptness.rpt.base.config.ClientConfig;
import cn.promptness.rpt.base.utils.Config;
import cn.promptness.rpt.base.utils.Constants;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;

import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class ProxyChannelCache {

    private static final Integer MAX_QUEUE_LIMIT = 128;

    private static final Queue<Channel> PROXY_CHANNEL_QUEUE = new LinkedBlockingQueue<>();

    public static void get(Bootstrap bootstrap, Consumer<Channel> success, Supplier<ChannelFuture> fail) {
        Channel proxyChannel = PROXY_CHANNEL_QUEUE.poll();
        if (proxyChannel != null) {
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

    public static void release(Channel proxyChannel) {
        if (PROXY_CHANNEL_QUEUE.size() > MAX_QUEUE_LIMIT) {
            proxyChannel.close();
            return;
        }
        proxyChannel.config().setAutoRead(true);
        proxyChannel.attr(Constants.LOCAL).set(null);
        PROXY_CHANNEL_QUEUE.offer(proxyChannel);
    }

    public static void delete(Channel proxyChannel) {
        PROXY_CHANNEL_QUEUE.remove(proxyChannel);
    }

}
