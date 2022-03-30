package cn.promptness.rpt.client.handler;

import cn.promptness.rpt.base.config.ClientConfig;
import cn.promptness.rpt.base.protocol.Message;
import cn.promptness.rpt.base.protocol.MessageType;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.internal.EmptyArrays;

/**
 * 实际内网连接处理器
 */
public class LocalHandler extends SimpleChannelInboundHandler<byte[]> {

    private final Channel channel;
    private final ClientConfig clientConfig;

    public LocalHandler(Channel channel, ClientConfig clientConfig) {
        this.channel = channel;
        this.clientConfig = clientConfig;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        ctx.channel().config().setAutoRead(false);
        send(MessageType.TYPE_CONNECTED, EmptyArrays.EMPTY_BYTES);
        ctx.channel().config().setAutoRead(true);
    }


    @Override
    protected void channelRead0(ChannelHandlerContext ctx, byte[] bytes) throws Exception {
        send(MessageType.TYPE_DATA, bytes);
    }


    /**
     * 连接断开
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        ctx.channel().config().setAutoRead(true);
        send(MessageType.TYPE_DISCONNECTED, EmptyArrays.EMPTY_BYTES);
    }

    /**
     * 连接异常
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.channel().close();
    }

    private void send(MessageType type, byte[] data) {
        Message message = new Message();
        message.setType(type);
        message.setClientConfig(clientConfig);
        message.setData(data);
        // 收到内网服务器响应后返回给服务器端
        channel.writeAndFlush(message);
    }

}
