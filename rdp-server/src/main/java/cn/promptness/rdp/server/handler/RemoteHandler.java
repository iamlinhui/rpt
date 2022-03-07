package cn.promptness.rdp.server.handler;

import cn.promptness.rdp.base.config.ClientConfig;
import cn.promptness.rdp.base.config.Config;
import cn.promptness.rdp.base.config.RemoteConfig;
import cn.promptness.rdp.base.config.ServerConfig;
import cn.promptness.rdp.base.protocol.Message;
import cn.promptness.rdp.base.protocol.MessageType;
import com.google.common.collect.Lists;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 处理服务器接收到的外部请求
 */
public class RemoteHandler extends SimpleChannelInboundHandler<byte[]> {

    private static final Logger logger = LoggerFactory.getLogger(RemoteHandler.class);

    private final Channel channel;
    private final RemoteConfig remoteConfig;

    public RemoteHandler(Channel channel, RemoteConfig remoteConfig) {
        this.channel = channel;
        this.remoteConfig = remoteConfig;
    }


    /**
     * 连接初始化，建立连接
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        logger.info("服务端本地端口[{}]连接成功", remoteConfig.getRemotePort());
        ctx.channel().config().setAutoRead(false);
        send(MessageType.TYPE_CONNECTED, new byte[]{}, ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, byte[] bytes) throws Exception {
        logger.debug("收到来自端口[{}]的数据,大小为:{}字节", remoteConfig.getRemotePort(), bytes.length);
        // 从外部连接接收到的数据 转发到客户端
        send(MessageType.TYPE_DATA, bytes, ctx);
    }

    /**
     * 连接中断
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        logger.info("服务端端口[{}]连接断开", remoteConfig.getRemotePort());
        send(MessageType.TYPE_DISCONNECTED, new byte[]{}, ctx);
    }

    /**
     * 连接异常
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.info("服务端端口[{}]连接中断", remoteConfig.getRemotePort());
        ctx.channel().close();
    }

    /**
     * 发送数据到内网客户端流程封装
     **/
    public void send(MessageType type, byte[] data, ChannelHandlerContext ctx) {

        ServerConfig serverConfig = Config.getServerConfig();
        ClientConfig clientConfig = new ClientConfig();
        clientConfig.setServerIp(serverConfig.getServerIp());
        clientConfig.setServerPort(serverConfig.getServerPort());
        clientConfig.setConfig(Lists.newArrayList(remoteConfig));
        clientConfig.setConnection(true);
        clientConfig.setChannelId(ctx.channel().id().asLongText());

        Message message = new Message();
        message.setType(type);
        message.setClientConfig(clientConfig);
        message.setData(data);

        channel.writeAndFlush(message);
    }
}
