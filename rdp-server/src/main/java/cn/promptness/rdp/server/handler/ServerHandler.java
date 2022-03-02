package cn.promptness.rdp.server.handler;

import cn.promptness.rdp.common.config.ClientConfig;
import cn.promptness.rdp.common.config.Config;
import cn.promptness.rdp.common.config.RemoteConfig;
import cn.promptness.rdp.common.protocol.Message;
import cn.promptness.rdp.common.protocol.MessageType;
import com.google.common.collect.Maps;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.bytes.ByteArrayDecoder;
import io.netty.handler.codec.bytes.ByteArrayEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * 处理服务器接收到的客户端连接
 */
public class ServerHandler extends SimpleChannelInboundHandler<Message> {

    private static final Logger logger = LoggerFactory.getLogger(ServerHandler.class);

    private static final EventLoopGroup BOSS_GROUP = new NioEventLoopGroup();
    private static final EventLoopGroup WORKER_GROUP = new NioEventLoopGroup();

    private final Map<Integer, Channel> channelMap = Maps.newConcurrentMap();

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.channel().close();
        BOSS_GROUP.shutdownGracefully();
        WORKER_GROUP.shutdownGracefully();
    }

    /**
     * 连接中断
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        ctx.channel().close();
        for (Channel channel : channelMap.values()) {
            channel.close();
        }
        channelMap.clear();
        // 取消监听的端口 否则第二次连接时无法再次绑定端口
        BOSS_GROUP.shutdownGracefully();
        WORKER_GROUP.shutdownGracefully();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext context, Message message) throws Exception {

        MessageType type = message.getType();

        switch (type) {
            case TYPE_REGISTER:
                register(context, message);
                break;
            case TYPE_DATA:
                transfer(message);
                break;
            case TYPE_DISCONNECTED:
                disconnected(message);
                break;
            case TYPE_KEEPALIVE:
            default:
        }

    }

    private void disconnected(Message message) {
        ClientConfig clientConfig = message.getClientConfig();
        if (clientConfig.getConfig().isEmpty()) {
            return;
        }
        Channel channel = channelMap.get(clientConfig.getConfig().get(0).getRemotePort());
        if (channel == null) {
            return;
        }
        channel.close();
    }

    private void transfer(Message message) {
        ClientConfig clientConfig = message.getClientConfig();
        String clientKey = clientConfig.getClientKey();
        if (message.getData() == null || message.getData().length == 0 || !Config.getServerConfig().getClientKey().contains(clientKey)) {
            return;
        }
        if (clientConfig.getConfig().isEmpty()) {
            return;
        }
        Channel channel = channelMap.get(clientConfig.getConfig().get(0).getRemotePort());
        if (channel == null) {
            return;
        }
        channel.writeAndFlush(message.getData());
    }

    private void register(ChannelHandlerContext context, Message message) throws InterruptedException, ExecutionException {
        ClientConfig clientConfig = message.getClientConfig();
        String clientKey = clientConfig.getClientKey();
        if (!Config.getServerConfig().getClientKey().contains(clientKey)) {
            clientConfig.setConnection(false);
            Message res = new Message();
            res.setType(MessageType.TYPE_AUTH);
            res.setClientConfig(clientConfig);
            context.writeAndFlush(res);
            return;
        }
        for (RemoteConfig remoteConfig : clientConfig.getConfig()) {
            ServerBootstrap remoteBootstrap = new ServerBootstrap();
            remoteBootstrap.group(BOSS_GROUP, WORKER_GROUP).channel(NioServerSocketChannel.class).childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel channel) throws Exception {
                            channel.pipeline().addLast(new ByteArrayDecoder());
                            channel.pipeline().addLast(new ByteArrayEncoder());
                            channel.pipeline().addLast(new RemoteHandler(context.channel(), remoteConfig, clientKey));
                            channelMap.put(remoteConfig.getRemotePort(), channel);
                        }
                    });
            logger.info("服务端开始建立本地端口绑定[{}]", remoteConfig.getRemotePort());
            remoteBootstrap.bind(Config.getServerConfig().getServerIp(), remoteConfig.getRemotePort()).get();
        }
        clientConfig.setConnection(true);
        Message res = new Message();
        res.setType(MessageType.TYPE_AUTH);
        res.setClientConfig(clientConfig);
        context.writeAndFlush(res);
    }
}