package cn.promptness.rdp.client;

import cn.promptness.rdp.common.coder.MessageDecoder;
import cn.promptness.rdp.common.coder.MessageEncoder;
import cn.promptness.rdp.common.config.ClientConfig;
import cn.promptness.rdp.common.config.Config;
import cn.promptness.rdp.common.handler.IdleCheckHandler;
import cn.promptness.rdp.handler.ClientHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class ClientApplication {

    private static final Logger logger = LoggerFactory.getLogger(ClientApplication.class);

    public static void main(String[] args) {

        ClientConfig clientConfig = Config.getClientConfig();
        NioEventLoopGroup clientWorkerGroup = new NioEventLoopGroup();

        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(clientWorkerGroup).channel(NioSocketChannel.class).option(ChannelOption.SO_KEEPALIVE, true).handler(new ChannelInitializer<SocketChannel>() {
            @Override
            public void initChannel(SocketChannel ch) throws Exception {
                ch.pipeline().addLast(new IdleStateHandler(60, 40, 120, TimeUnit.SECONDS));
                //固定帧长解码器
                ch.pipeline().addLast(new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE, 0, 4, 0, 4));
                //自定义协议解码器
                ch.pipeline().addLast(new MessageDecoder());
                //自定义协议编码器
                ch.pipeline().addLast(new MessageEncoder());
                //服务器连接处理器
                ch.pipeline().addLast(new ClientHandler(bootstrap, clientWorkerGroup));
                ch.pipeline().addLast(new IdleCheckHandler());
            }
        });
        try {
            bootstrap.connect(clientConfig.getServerIp(), clientConfig.getServerPort()).get();
            logger.info("客户端成功连接服务端IP:{},服务端端口:{}", clientConfig.getServerIp(), clientConfig.getServerPort());
        } catch (InterruptedException | ExecutionException exception) {
            logger.info("客户端失败连接服务端IP:{},服务端端口:{},原因:{}", clientConfig.getServerIp(), clientConfig.getServerPort(), exception.getCause().getMessage());
            clientWorkerGroup.shutdownGracefully();
        }
    }
}
