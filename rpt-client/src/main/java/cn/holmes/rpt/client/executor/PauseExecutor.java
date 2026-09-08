package cn.holmes.rpt.client.executor;

import cn.holmes.rpt.base.executor.MessageExecutor;
import cn.holmes.rpt.base.protocol.Message;
import cn.holmes.rpt.base.protocol.MessageType;
import cn.holmes.rpt.base.utils.Attributes.Client;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 通道级背压：收到服务端的 TYPE_PAUSE 后暂停该通道的本地连接读取。
 * <p>
 * 服务端外部连接写入慢导致通道缓冲区达到高水位时发送 TYPE_PAUSE，
 * 客户端收到后停止从本地连接读取数据，背压逐跳传播到本地服务。
 * <p>
 * 与 {@code ClientHandler#channelWritabilityChanged} 隧道级背压的区别：
 * 隧道级暂停整个隧道上所有通道，通道级仅暂停单个通道，互不干扰。
 * 通道级 PAUSE 标记 {@link Client#PAUSED}=true，隧道级恢复时跳过该通道。
 */
public class PauseExecutor implements MessageExecutor {

    private static final Logger logger = LoggerFactory.getLogger(PauseExecutor.class);

    @Override
    public MessageType getMessageType() {
        return MessageType.TYPE_PAUSE;
    }

    @Override
    public void execute(ChannelHandlerContext context, Message message) throws Exception {
        Channel localChannel = locate(context, message);
        if (Objects.isNull(localChannel)) {
            return;
        }
        // 标记为通道级暂停，防止隧道级恢复时误覆盖
        localChannel.attr(Client.PAUSED).set(true);
        localChannel.config().setAutoRead(false);
        logger.debug("[pause] local channel {} paused by server backpressure", message.getMeta().getChannelId());
    }

    /**
     * 按 channelId 定位本地连接。
     * <p>
     * UDP 方向数据报直写不排队，服务端不会为其发背压信号，此处一并排除。
     */
    static Channel locate(ChannelHandlerContext context, Message message) {
        String channelId = Objects.nonNull(message.getMeta()) ? message.getMeta().getChannelId() : null;
        if (Objects.isNull(channelId)) {
            return null;
        }
        // 消息可能到达共享隧道，CHANNELS表挂在控制通道上
        Channel control = Optional.ofNullable(context.channel().attr(Client.CONTROL).get()).orElse(context.channel());
        Map<String, Channel> channelMap = control.attr(Client.CHANNELS).get();
        if (Objects.isNull(channelMap)) {
            return null;
        }
        Channel localChannel = channelMap.get(channelId);
        if (Objects.isNull(localChannel) || !localChannel.isActive()) {
            return null;
        }
        if (Objects.nonNull(localChannel.attr(Client.UDP_TARGET).get())) {
            return null;
        }
        return localChannel;
    }
}
