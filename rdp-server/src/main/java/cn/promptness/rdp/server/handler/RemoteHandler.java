package cn.promptness.rdp.server.handler;

import cn.promptness.rdp.common.config.ClientConfig;
import cn.promptness.rdp.common.config.Config;
import cn.promptness.rdp.common.config.RemoteConfig;
import cn.promptness.rdp.common.config.ServerConfig;
import cn.promptness.rdp.common.protocol.Message;
import cn.promptness.rdp.common.protocol.MessageType;
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
    private final String clientKey;

    public RemoteHandler(Channel channel, RemoteConfig remoteConfig, String clientKey) {
        this.channel = channel;
        this.remoteConfig = remoteConfig;
        this.clientKey = clientKey;
    }


    /**
     * 连接初始化，建立连接
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        logger.info("服务端建立本地连接成功,绑定端口[{}]", remoteConfig.getRemotePort());
        send(MessageType.TYPE_CONNECTED, new byte[]{});
    }

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, byte[] bytes) throws Exception {
        logger.info("收到来自端口[{}]的数据,大小为:{}字节", remoteConfig.getRemotePort(), bytes.length);
        // 从外部连接接收到的数据 转发到客户端
        send(MessageType.TYPE_DATA, bytes);
    }

    /**
     * 连接中断
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        logger.info("服务端端口[{}]连接断开", remoteConfig.getRemotePort());
        send(MessageType.TYPE_DISCONNECTED, new byte[]{});
    }

    /**
     * 发送数据到内网客户端流程封装
     **/
    public void send(MessageType type, byte[] data) {

        ServerConfig serverConfig = Config.getServerConfig();
        ClientConfig clientConfig = new ClientConfig();
        clientConfig.setServerIp(serverConfig.getServerIp());
        clientConfig.setServerPort(serverConfig.getServerPort());
        clientConfig.setClientKey(clientKey);
        clientConfig.setConfig(Lists.newArrayList(remoteConfig));
        clientConfig.setConnection(true);

        Message message = new Message();
        message.setType(type);
        message.setClientConfig(clientConfig);
        message.setData(data);

        channel.writeAndFlush(message);
    }
}
