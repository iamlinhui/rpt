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

/**
 * 实际内网连接处理器
 */
public class LocalHandler extends SimpleChannelInboundHandler<byte[]> {

    private final Channel channel;
    private final RemoteConfig remoteConfig;

    public LocalHandler(Channel channel, RemoteConfig remoteConfig) {
        this.channel = channel;
        this.remoteConfig = remoteConfig;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, byte[] bytes) throws Exception {
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
