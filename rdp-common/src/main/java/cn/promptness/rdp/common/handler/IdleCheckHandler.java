package cn.promptness.rdp.common.handler;

import cn.promptness.rdp.common.protocol.Message;
import cn.promptness.rdp.common.protocol.MessageType;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;

public class IdleCheckHandler extends IdleStateHandler {

    public IdleCheckHandler() {
        super(60, 40, 1200);
    }

    @Override
    protected void channelIdle(ChannelHandlerContext ctx, IdleStateEvent stateEvent) throws Exception {
        if (IdleStateEvent.FIRST_WRITER_IDLE_STATE_EVENT == stateEvent) {
            Message proxyMessage = new Message();
            proxyMessage.setType(MessageType.TYPE_KEEPALIVE);
            ctx.channel().writeAndFlush(proxyMessage);
        } else if (IdleStateEvent.FIRST_READER_IDLE_STATE_EVENT == stateEvent) {
            ctx.channel().close();
        }
        super.channelIdle(ctx, stateEvent);
    }
}
