package cn.promptness.rdp.client.handler;

import cn.promptness.rdp.base.config.ClientConfig;
import cn.promptness.rdp.base.config.RemoteConfig;
import cn.promptness.rdp.base.protocol.Message;
import cn.promptness.rdp.base.protocol.MessageType;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 实际内网连接处理器
 */
public class LocalHandler extends SimpleChannelInboundHandler<byte[]> {

    private static final Logger logger = LoggerFactory.getLogger(LocalHandler.class);

    private final Channel channel;
    private final ClientConfig clientConfig;

    public LocalHandler(Channel channel, ClientConfig clientConfig) {
        this.channel = channel;
        this.clientConfig = clientConfig;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        RemoteConfig remoteConfig = clientConfig.getConfig().get(0);
        logger.info("客户端建立本地连接成功,本地绑定IP:{},本地绑定端口:{}", remoteConfig.getLocalIp(), remoteConfig.getLocalPort());
        Message message = new Message();
        message.setType(MessageType.TYPE_CONNECTED);
        message.setClientConfig(clientConfig);
        message.setData(new byte[0]);
        channel.writeAndFlush(message);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, byte[] bytes) throws Exception {
        RemoteConfig remoteConfig = clientConfig.getConfig().get(0);
        logger.debug("收到本地{}:{}的数据,数据量为:{}字节", remoteConfig.getLocalIp(), remoteConfig.getLocalPort(), bytes.length);
        Message message = new Message();
        message.setType(MessageType.TYPE_DATA);
        message.setClientConfig(clientConfig);
        message.setData(bytes);
        // 收到内网服务器响应后返回给服务器端
        channel.writeAndFlush(message);
    }


    /**
     * 连接断开
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        RemoteConfig remoteConfig = clientConfig.getConfig().get(0);
        logger.info("客户端本地连接断开,本地绑定IP:{},本地绑定端口:{}", remoteConfig.getLocalIp(), remoteConfig.getLocalPort());
        Message message = new Message();
        message.setType(MessageType.TYPE_DISCONNECTED);
        message.setClientConfig(clientConfig);
        channel.writeAndFlush(message);
    }

    /**
     * 连接异常
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        RemoteConfig remoteConfig = clientConfig.getConfig().get(0);
        logger.info("客户端本地连接中断,本地绑定IP:{},本地绑定端口:{}", remoteConfig.getLocalIp(), remoteConfig.getLocalPort());
        ctx.channel().close();
    }


}
