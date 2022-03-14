package cn.promptness.rpt.client.handler;

import cn.promptness.rpt.base.config.ClientConfig;
import cn.promptness.rpt.base.config.Config;
import cn.promptness.rpt.base.config.RemoteConfig;
import cn.promptness.rpt.base.protocol.Message;
import cn.promptness.rpt.base.protocol.MessageType;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 服务器连接处理器
 */
public class ClientHandler extends SimpleChannelInboundHandler<Message> {

    private static final Logger logger = LoggerFactory.getLogger(ClientHandler.class);
    private final EventLoopGroup localGroup = new NioEventLoopGroup();
    /**
     * remoteChannelId --> localChannel
     */
    private final Map<String, Channel> localChannelMap = new ConcurrentHashMap<>();
    private final Bootstrap clientBootstrap;
    private final NioEventLoopGroup clientWorkerGroup;
    private final AtomicBoolean connected = new AtomicBoolean(false);

    public ClientHandler(Bootstrap clientBootstrap, NioEventLoopGroup clientWorkerGroup) {
        this.clientBootstrap = clientBootstrap;
        this.clientWorkerGroup = clientWorkerGroup;
    }

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
                connected.set(message.getClientConfig().isConnection());
                if (connected.get()) {
                    logger.info("授权连接成功,clientKey:{}", message.getClientConfig().getClientKey());
                    for (String remoteResult : message.getClientConfig().getRemoteResult()) {
                        logger.info(remoteResult);
                    }
                } else {
                    logger.info("授权连接失败,clientKey:{}", message.getClientConfig().getClientKey());
                    clientWorkerGroup.shutdownGracefully();
                }
                break;
            case TYPE_CONNECTED:
                // 外部请求进入，开始与内网建立连接
                connected(context, message);
                break;
            case TYPE_DISCONNECTED:
                disconnected(message);
                break;
            case TYPE_DATA:
                transfer(message);
                break;
            case TYPE_KEEPALIVE:
            default:
        }
    }

    private void transfer(Message message) {
        if (message.getData() == null || message.getData().length <= 0) {
            return;
        }
        ClientConfig clientConfig = message.getClientConfig();
        Channel channel = localChannelMap.get(clientConfig.getChannelId());
        if (channel != null) {
            // 将数据转发到对应内网服务器
            channel.writeAndFlush(message.getData());
        }
    }

    private void disconnected(Message message) {
        ClientConfig clientConfig = message.getClientConfig();
        Channel channel = localChannelMap.remove(clientConfig.getChannelId());
        if (channel != null) {
            channel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }

    private void connected(ChannelHandlerContext context, Message message) {
        List<RemoteConfig> remoteConfigList = message.getClientConfig().getConfig();
        if (remoteConfigList == null || remoteConfigList.isEmpty()) {
            return;
        }
        RemoteConfig remoteConfig = remoteConfigList.get(0);
        if (remoteConfig.getLocalPort() == 0) {
            return;
        }
        Bootstrap localBootstrap = new Bootstrap();
        localBootstrap.group(localGroup).channel(NioSocketChannel.class).option(ChannelOption.SO_KEEPALIVE, true).handler(new ChannelInitializer<SocketChannel>() {
            @Override
            public void initChannel(SocketChannel channel) throws Exception {
                channel.pipeline().addLast(new ByteArrayDecoder());
                channel.pipeline().addLast(new ByteArrayEncoder());
                channel.pipeline().addLast(new LocalHandler(context.channel(), message.getClientConfig()));
                localChannelMap.put(message.getClientConfig().getChannelId(), channel);
            }
        });
        try {
            logger.info("客户端开始建立本地连接,{}:{}", remoteConfig.getLocalIp(), remoteConfig.getLocalPort());
            localBootstrap.connect(remoteConfig.getLocalIp(), remoteConfig.getLocalPort()).get();
        } catch (Exception exception) {
            logger.error("客户端建立本地连接失败,{}:{},{}", remoteConfig.getLocalIp(), remoteConfig.getLocalPort(), exception.getCause().getMessage());
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        logger.info("客户端-服务端连接中断,{}:{}", Config.getClientConfig().getServerIp(), Config.getClientConfig().getServerPort());
        for (Channel channel : localChannelMap.values()) {
            channel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
        localChannelMap.clear();
        localGroup.shutdownGracefully();
        retryConnect();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.channel().close();
    }

    private void retryConnect() throws Exception {
        if (!connected.get()) {
            return;
        }
        ClientConfig clientConfig = Config.getClientConfig();
        int count = 0;
        while (count >= 0) {
            try {
                logger.info("客户端第{}次重试开始连接服务端IP:{},服务端端口:{}", ++count, clientConfig.getServerIp(), clientConfig.getServerPort());
                clientBootstrap.connect(clientConfig.getServerIp(), clientConfig.getServerPort()).get(15, TimeUnit.SECONDS);
                break;
            } catch (Exception exception) {
                logger.info("客户端第{}次重试失败连接服务端IP:{},服务端端口:{},原因:{}", count, clientConfig.getServerIp(), clientConfig.getServerPort(), exception.getCause().getMessage());
                TimeUnit.MINUTES.sleep(1);
            }
        }
    }
}
