package cn.promptness.rpt.base.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.internal.EmptyArrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class ByteIdleCheckHandler extends IdleStateHandler {

    private static final Logger logger = LoggerFactory.getLogger(ByteIdleCheckHandler.class);

    public ByteIdleCheckHandler(int readerIdleTimeSeconds, int writerIdleTimeSeconds, int allIdleTimeSeconds) {
        super(readerIdleTimeSeconds, writerIdleTimeSeconds, allIdleTimeSeconds, TimeUnit.SECONDS);
    }

    @Override
    protected void channelIdle(ChannelHandlerContext ctx, IdleStateEvent evt) throws Exception {
        if (IdleStateEvent.FIRST_WRITER_IDLE_STATE_EVENT == evt) {
            logger.debug("{}秒未传输数据,发送心跳包", TimeUnit.MILLISECONDS.toSeconds(super.getWriterIdleTimeInMillis()));
            ctx.channel().writeAndFlush(EmptyArrays.EMPTY_BYTES);
        }
        super.channelIdle(ctx, evt);
    }

}
