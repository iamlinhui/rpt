package cn.promptness.rpt.base.handler;

import cn.promptness.rpt.base.protocol.Message;
import cn.promptness.rpt.base.protocol.MessageType;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class IdleCheckHandler extends IdleStateHandler {

    private static final Logger logger = LoggerFactory.getLogger(IdleCheckHandler.class);

    public IdleCheckHandler(int readerIdleTimeSeconds, int writerIdleTimeSeconds, int allIdleTimeSeconds) {
        super(readerIdleTimeSeconds, writerIdleTimeSeconds, allIdleTimeSeconds, TimeUnit.SECONDS);
    }

    @Override
    protected void channelIdle(ChannelHandlerContext ctx, IdleStateEvent evt) throws Exception {
        if (IdleStateEvent.FIRST_WRITER_IDLE_STATE_EVENT == evt) {
            logger.debug("{}秒未传输数据,发送心跳包", TimeUnit.MILLISECONDS.toSeconds(super.getWriterIdleTimeInMillis()));
            Message proxyMessage = new Message();
            proxyMessage.setType(MessageType.TYPE_KEEPALIVE);
            ctx.channel().writeAndFlush(proxyMessage);
        } else if (IdleStateEvent.FIRST_READER_IDLE_STATE_EVENT == evt) {
            logger.info("{}秒未收到数据,断开连接", TimeUnit.MILLISECONDS.toSeconds(super.getReaderIdleTimeInMillis()));
            ctx.channel().close();
        }
        super.channelIdle(ctx, evt);
    }

}
