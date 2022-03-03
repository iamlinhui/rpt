package cn.promptness.rdp.common.handler;

import cn.promptness.rdp.common.protocol.Message;
import cn.promptness.rdp.common.protocol.MessageType;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IdleCheckHandler extends ChannelInboundHandlerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(IdleCheckHandler.class);

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
