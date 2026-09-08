package cn.holmes.rpt.server.executor;

import cn.holmes.rpt.base.config.ProxyType;
import cn.holmes.rpt.base.executor.MessageExecutor;
import cn.holmes.rpt.base.protocol.Message;
import cn.holmes.rpt.base.protocol.MessageType;
import cn.holmes.rpt.base.utils.Attributes.Server;
import cn.holmes.rpt.server.cache.ServerChannelCache;
import cn.holmes.rpt.server.handler.ServerHandler;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;

/**
 * 通道级背压：收到 TYPE_PAUSE 后暂停该通道的外部连接读取。
 * <p>
 * 客户端通道缓冲区达到高水位时发送 TYPE_PAUSE，服务端收到后停止从外部连接
 * 读取数据。外部连接的 TCP 接收窗口随之缩小，背压逐跳传播到最终用户。
 * <p>
 * 与 {@link ServerHandler#channelWritabilityChanged} 隧道级背压的区别：
 * 隧道级暂停整个隧道上所有通道，通道级仅暂停单个通道，互不干扰。
 * 通道级 PAUSE 标记 {@link Server#PAUSED}=true，隧道级恢复时跳过该通道。
 */
public class PauseExecutor implements MessageExecutor {

    private static final Logger logger = LoggerFactory.getLogger(PauseExecutor.class);

    @Override
    public MessageType getMessageType() {
        return MessageType.TYPE_PAUSE;
    }

    @Override
    public void execute(ChannelHandlerContext context, Message message) throws Exception {
        Channel localChannel = locate(message);
        if (Objects.isNull(localChannel)) {
            return;
        }
        // 标记为通道级暂停，防止隧道级恢复时误覆盖
        localChannel.attr(Server.PAUSED).set(true);
        localChannel.config().setAutoRead(false);
        logger.debug("[pause] channel {} paused by backpressure", message.getMeta().getChannelId());
    }

    /**
     * 按 serverId + channelId 定位外部连接。
     * <p>
     * UDP 本地通道是按端口共享的 DatagramChannel，暂停它会连带停掉该端口上所有会话，
     * 因此 UDP 方向不参与通道级背压（数据报直写不排队，也不会积压）。
     */
    static Channel locate(Message message) {
        String serverId = message.getMeta().getServerId();
        String channelId = message.getMeta().getChannelId();
        if (Objects.isNull(serverId) || Objects.isNull(channelId)) {
            return null;
        }
        Channel serverChannel = ServerChannelCache.getServerChannelMap().get(serverId);
        if (Objects.isNull(serverChannel)) {
            return null;
        }
        Map<String, Channel> localChannelMap = serverChannel.attr(Server.CHANNELS).get();
        if (Objects.isNull(localChannelMap)) {
            return null;
        }
        Channel localChannel = localChannelMap.get(channelId);
        if (Objects.isNull(localChannel) || !localChannel.isActive()) {
            return null;
        }
        if (Objects.equals(ProxyType.UDP, localChannel.attr(Server.PROXY_TYPE).get())) {
            return null;
        }
        return localChannel;
    }
}
