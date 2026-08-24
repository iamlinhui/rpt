package cn.holmes.rpt.server.executor;

import cn.holmes.rpt.base.executor.MessageExecutor;
import cn.holmes.rpt.base.protocol.Message;
import cn.holmes.rpt.base.protocol.MessageType;
import cn.holmes.rpt.base.utils.Constants.Server;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

import static cn.holmes.rpt.server.executor.PauseExecutor.locate;

/**
 * 通道级背压恢复：收到 TYPE_RESUME 后恢复该通道的外部连接读取。
 * <p>
 * 客户端通道缓冲区排空到低水位时发送 TYPE_RESUME，服务端收到后恢复从外部连接
 * 读取数据。清除 {@link Server#PAUSED} 标记，使隧道级背压可以再次生效。
 */
public class ResumeExecutor implements MessageExecutor {

    private static final Logger logger = LoggerFactory.getLogger(ResumeExecutor.class);

    @Override
    public MessageType getMessageType() {
        return MessageType.TYPE_RESUME;
    }

    @Override
    public void execute(ChannelHandlerContext context, Message message) throws Exception {
        Channel localChannel = locate(message);
        if (Objects.isNull(localChannel)) {
            return;
        }
        // 通道级标记无条件清除，但隧道级背压仍在生效时不能恢复读取，否则会顶掉隧道级暂停
        localChannel.attr(Server.PAUSED).set(false);
        Channel tunnel = localChannel.attr(Server.PROXY).get();
        if (Objects.isNull(tunnel) || tunnel.isWritable()) {
            localChannel.config().setAutoRead(true);
        }
        logger.debug("[resume] channel {} resumed", message.getMeta().getChannelId());
    }
}
