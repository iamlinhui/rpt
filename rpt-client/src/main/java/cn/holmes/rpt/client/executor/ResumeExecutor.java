package cn.holmes.rpt.client.executor;

import cn.holmes.rpt.base.executor.MessageExecutor;
import cn.holmes.rpt.base.protocol.Message;
import cn.holmes.rpt.base.protocol.MessageType;
import cn.holmes.rpt.base.utils.Constants.Client;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

import static cn.holmes.rpt.client.executor.PauseExecutor.locate;

/**
 * 通道级背压恢复：收到服务端的 TYPE_RESUME 后恢复该通道的本地连接读取。
 * <p>
 * 服务端外部连接排空积压到低水位时发送 TYPE_RESUME，客户端收到后恢复从本地连接
 * 读取数据。清除 {@link Client#PAUSED} 标记，使隧道级背压可以再次生效。
 */
public class ResumeExecutor implements MessageExecutor {

    private static final Logger logger = LoggerFactory.getLogger(ResumeExecutor.class);

    @Override
    public MessageType getMessageType() {
        return MessageType.TYPE_RESUME;
    }

    @Override
    public void execute(ChannelHandlerContext context, Message message) throws Exception {
        Channel localChannel = locate(context, message);
        if (Objects.isNull(localChannel)) {
            return;
        }
        // 通道级标记无条件清除，但隧道级背压仍在生效时不能恢复读取，否则会顶掉隧道级暂停
        localChannel.attr(Client.PAUSED).set(false);
        Channel tunnel = localChannel.attr(Client.TUNNEL).get();
        if (Objects.isNull(tunnel) || tunnel.isWritable()) {
            localChannel.config().setAutoRead(true);
        }
        logger.debug("[resume] local channel {} resumed by server", message.getMeta().getChannelId());
    }
}
