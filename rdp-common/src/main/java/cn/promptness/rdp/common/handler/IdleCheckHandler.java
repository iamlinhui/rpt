package cn.promptness.rdp.common.handler;

import cn.promptness.rdp.common.protocol.Message;
import cn.promptness.rdp.common.protocol.MessageType;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class IdleCheckHandler extends IdleStateHandler {

    private static final Logger logger = LoggerFactory.getLogger(IdleCheckHandler.class);

    public IdleCheckHandler() {
        super(60, 40, 120, TimeUnit.SECONDS);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        logger.info("发送心跳包");
        Message proxyMessage = new Message();
        proxyMessage.setType(MessageType.TYPE_KEEPALIVE);
        ctx.channel().writeAndFlush(proxyMessage);
        super.userEventTriggered(ctx, evt);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.fireExceptionCaught(cause);
        ctx.channel().close();
    }

}
