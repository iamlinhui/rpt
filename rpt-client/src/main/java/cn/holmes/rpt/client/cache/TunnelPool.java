package cn.holmes.rpt.client.cache;

import cn.holmes.rpt.base.config.ClientConfig;
import cn.holmes.rpt.base.protocol.Message;
import cn.holmes.rpt.base.protocol.MessageType;
import cn.holmes.rpt.base.protocol.Meta;
import cn.holmes.rpt.base.utils.Config;
import cn.holmes.rpt.base.utils.Constants.Client;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 共享数据隧道池：控制通道认证成功后建立n条常驻隧道，
 * k个外部会话通过channelId复用这n条隧道（轮询选路）。
 * 隧道断开自动补连，控制通道断开时全部关闭。
 */
public class TunnelPool {

    private static final Logger logger = LoggerFactory.getLogger(TunnelPool.class);

    private TunnelPool() {
    }

    private static final TunnelPool INSTANCE = new TunnelPool();

    /**
     * 补连退避上限（秒）
     */
    private static final int RECONNECT_BACKOFF_LIMIT = 30;

    private final List<Channel> tunnels = new CopyOnWriteArrayList<>();

    private final AtomicInteger roundRobin = new AtomicInteger();

    public static TunnelPool getInstance() {
        return INSTANCE;
    }

    /**
     * 初始化n条共享隧道
     */
    public void init(Channel control, String serverId) {
        int tunnelCount = Math.max(1, Math.min(Config.getClientConfig().getTunnelCount(), 16));
        logger.info("客户端开始建立{}条共享数据隧道", tunnelCount);
        for (int i = 0; i < tunnelCount; i++) {
            connect(control, serverId, 0);
        }
    }

    /**
     * 轮询选择一条活跃隧道
     */
    public Channel pick() {
        int size = tunnels.size();
        if (size == 0) {
            return null;
        }
        for (int i = 0; i < size; i++) {
            Channel tunnel = tunnels.get(Math.floorMod(roundRobin.getAndIncrement(), tunnels.size()));
            if (tunnel.isActive()) {
                return tunnel;
            }
        }
        return null;
    }

    /**
     * 隧道断开回调：移出池并在控制通道存活时自动补连
     */
    public void onTunnelClosed(Channel tunnel) {
        tunnels.remove(tunnel);
        Channel controlChannel = tunnel.attr(Client.CONTROL).getAndSet(null);
        if (Objects.isNull(controlChannel) || !controlChannel.isActive()) {
            return;
        }
        String serverId = controlChannel.id().asLongText();
        controlChannel.eventLoop().schedule(() -> connect(controlChannel, serverId, 3), 3, TimeUnit.SECONDS);
    }

    /**
     * 控制通道断开时关闭所有隧道
     */
    public void closeAll() {
        for (Channel tunnel : tunnels) {
            tunnel.attr(Client.CONTROL).set(null);
            tunnel.close();
        }
        tunnels.clear();
    }

    public int size() {
        return tunnels.size();
    }

    private void connect(Channel control, String serverId, int backoff) {
        if (!control.isActive()) {
            return;
        }
        ClientConfig clientConfig = Config.getClientConfig();
        Bootstrap bootstrap = control.attr(Client.APPLICATION).get().bootstrap();
        bootstrap.connect(clientConfig.getServerIp(), clientConfig.getServerPort()).addListener((ChannelFutureListener) future -> {
            if (!control.isActive()) {
                future.channel().close();
                return;
            }
            if (future.isSuccess()) {
                Channel tunnel = future.channel();
                tunnel.attr(Client.CONTROL).set(control);
                // 首个消息注册隧道归属，服务端据此绑定serverId与clientKey校验
                Message message = new Message();
                message.setType(MessageType.TYPE_TUNNEL);
                message.setMeta(new Meta().setClientKey(clientConfig.getClientKey()).setServerId(serverId));
                tunnel.writeAndFlush(message);
                tunnels.add(tunnel);
                logger.info("客户端共享数据隧道建立成功,当前隧道数:{}", tunnels.size());
            } else {
                logger.info("客户端共享数据隧道建立失败:{},{}秒后重试", future.cause().getMessage(), Math.min(backoff + 3, RECONNECT_BACKOFF_LIMIT));
                control.eventLoop().schedule(() -> connect(control, serverId, Math.min(backoff + 3, RECONNECT_BACKOFF_LIMIT)), Math.min(backoff + 3, RECONNECT_BACKOFF_LIMIT), TimeUnit.SECONDS);
            }
        });
    }
}
