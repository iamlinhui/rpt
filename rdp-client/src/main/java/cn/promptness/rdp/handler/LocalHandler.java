package cn.promptness.rdp.handler;

import cn.promptness.rdp.common.config.ClientConfig;
import cn.promptness.rdp.common.config.Config;
import cn.promptness.rdp.common.config.RemoteConfig;
import cn.promptness.rdp.common.protocol.Message;
import cn.promptness.rdp.common.protocol.MessageType;
import com.google.common.collect.Lists;
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
    private final RemoteConfig remoteConfig;

    public LocalHandler(Channel channel, RemoteConfig remoteConfig) {
        this.channel = channel;
        this.remoteConfig = remoteConfig;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        logger.info("客户端建立本地连接成功,本地绑定IP:{},本地绑定端口:{}", remoteConfig.getLocalIp(), remoteConfig.getLocalPort());
    }

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, byte[] bytes) throws Exception {
        logger.info("收到本地{}:{}的数据,数据量为:{}字节", remoteConfig.getLocalIp(), remoteConfig.getLocalPort(), bytes.length);
        Message message = new Message();
        message.setType(MessageType.TYPE_DATA);
        message.setClientConfig(getLocalClientConfig());
        message.setData(bytes);
        //收到内网服务器响应后返回给服务器端
        channel.writeAndFlush(message);
    }


    /**
     * 连接断开
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        logger.info("客户端本地连接断开,本地绑定IP:{},本地绑定端口:{}", remoteConfig.getLocalIp(), remoteConfig.getLocalPort());
        Message message = new Message();
        message.setType(MessageType.TYPE_DISCONNECTED);
        message.setClientConfig(getLocalClientConfig());
        channel.writeAndFlush(message);
    }

    /**
     * 连接异常
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.info("客户端本地连接中断,本地绑定IP:{},本地绑定端口:{}", remoteConfig.getLocalIp(), remoteConfig.getLocalPort());
        ctx.channel().close();
    }

    private ClientConfig getLocalClientConfig() {
        ClientConfig clientConfig = new ClientConfig();
        clientConfig.setServerIp(Config.getClientConfig().getServerIp());
        clientConfig.setServerPort(Config.getClientConfig().getServerPort());
        clientConfig.setClientKey(Config.getClientConfig().getClientKey());
        clientConfig.setConfig(Lists.newArrayList(remoteConfig));
        clientConfig.setConnection(true);
        return clientConfig;
    }

}
