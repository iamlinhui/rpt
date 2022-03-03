package cn.promptness.rdp.handler;

import cn.promptness.rdp.common.config.Config;
import cn.promptness.rdp.common.config.RemoteConfig;
import cn.promptness.rdp.common.protocol.Message;
import cn.promptness.rdp.common.protocol.MessageType;
import com.google.common.collect.Maps;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.bytes.ByteArrayDecoder;
import io.netty.handler.codec.bytes.ByteArrayEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * 服务器连接处理器
 */
public class ClientHandler extends SimpleChannelInboundHandler<Message> {

    private static final Logger logger = LoggerFactory.getLogger(ClientHandler.class);

    private final EventLoopGroup localGroup = new NioEventLoopGroup();

    private final Map<Integer, Channel> channelMap = Maps.newConcurrentMap();

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        //连接建立成功，发送注册请求
        Message message = new Message();
        message.setType(MessageType.TYPE_REGISTER);
        message.setClientConfig(Config.getClientConfig());
        ctx.writeAndFlush(message);
    }


    @Override
    protected void channelRead0(ChannelHandlerContext context, Message message) throws Exception {
        switch (message.getType()) {
            case TYPE_AUTH:
                boolean connection = message.getClientConfig().isConnection();
                if (connection) {
                    logger.info("授权连接成功,clientKey:{}", message.getClientConfig().getClientKey());
                } else {
                    logger.info("授权连接失败,clientKey:{}", message.getClientConfig().getClientKey());
                }
                break;
            case TYPE_CONNECTED:
                // 外部请求进入，开始与内网建立连接
                connected(context, message);
                break;
            case TYPE_DISCONNECTED:
                disconnected(message);
                break;
            case TYPE_KEEPALIVE:
                //心跳，不做处理
                break;
            case TYPE_DATA:
                transfer(message);
                break;
            default:
        }
    }

    private void transfer(Message message) {
        if (message.getData() == null || message.getData().length <= 0) {
            return;
        }

        List<RemoteConfig> remoteConfigList = message.getClientConfig().getConfig();
        if (remoteConfigList == null || remoteConfigList.isEmpty()) {
            return;
        }
        RemoteConfig remoteConfig = remoteConfigList.get(0);
        Channel channel = channelMap.get(remoteConfig.getLocalPort());
        if (channel != null) {
            //将数据转发到对应内网服务器
            channel.writeAndFlush(message.getData());
        }
    }

    private void disconnected(Message message) {
        List<RemoteConfig> remoteConfigList = message.getClientConfig().getConfig();
        if (remoteConfigList == null || remoteConfigList.isEmpty()) {
            return;
        }
        RemoteConfig remoteConfig = remoteConfigList.get(0);
        Channel channel = channelMap.get(remoteConfig.getLocalPort());
        if (channel != null) {
            channel.close();
            channelMap.remove(remoteConfig.getLocalPort());
        }
    }

    private void connected(ChannelHandlerContext context, Message message) {
        List<RemoteConfig> remoteConfigList = message.getClientConfig().getConfig();
        if (remoteConfigList == null || remoteConfigList.isEmpty()) {
            return;
        }
        RemoteConfig remoteConfig = remoteConfigList.get(0);
        Bootstrap localBootstrap = new Bootstrap();
        localBootstrap.group(localGroup).channel(NioSocketChannel.class).option(ChannelOption.SO_KEEPALIVE, true).handler(new ChannelInitializer<SocketChannel>() {
            @Override
            public void initChannel(SocketChannel channel) throws Exception {
                channel.pipeline().addLast(new ByteArrayDecoder());
                channel.pipeline().addLast(new ByteArrayEncoder());
                channel.pipeline().addLast(new LocalHandler(context.channel(), remoteConfig));
                channelMap.put(remoteConfig.getLocalPort(), channel);
            }
        });
        try {
            logger.info("客户端开始建立本地连接,本地绑定IP:{},本地绑定端口:{}", remoteConfig.getLocalIp(), remoteConfig.getLocalPort());
            localBootstrap.connect(remoteConfig.getLocalIp(), remoteConfig.getLocalPort()).get();
        } catch (InterruptedException | ExecutionException exception) {
            logger.error("客户端建立本地连接失败,本地绑定IP:{},本地绑定端口:{},{}", remoteConfig.getLocalIp(), remoteConfig.getLocalPort(), exception.getCause().getMessage());
            Channel channel = channelMap.remove(remoteConfig.getLocalPort());
            if (channel != null) {
                channel.close();
            }
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        logger.info("客户端-服务端连接中断{}:{}", Config.getClientConfig().getServerIp(), Config.getClientConfig().getServerPort());
        for (Channel channel : channelMap.values()) {
            channel.close();
        }
        channelMap.clear();
        localGroup.shutdownGracefully();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.channel().close();
    }


}
