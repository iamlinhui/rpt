package cn.promptness.rdp.client;

import cn.promptness.rdp.base.coder.MessageDecoder;
import cn.promptness.rdp.base.coder.MessageEncoder;
import cn.promptness.rdp.base.config.ClientConfig;
import cn.promptness.rdp.base.config.Config;
import cn.promptness.rdp.base.handler.IdleCheckHandler;
import cn.promptness.rdp.handler.ClientHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientApplication {

    private static final Logger logger = LoggerFactory.getLogger(ClientApplication.class);

    public static void main(String[] args) {
        ClientConfig clientConfig = Config.getClientConfig();
        NioEventLoopGroup clientWorkerGroup = new NioEventLoopGroup();

        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(clientWorkerGroup).channel(NioSocketChannel.class).option(ChannelOption.SO_KEEPALIVE, true).handler(new ChannelInitializer<SocketChannel>() {
            @Override
            public void initChannel(SocketChannel ch) throws Exception {
                //固定帧长解码器
                ch.pipeline().addLast(new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE, 0, 4, 0, 4));
                //自定义协议解码器
                ch.pipeline().addLast(new MessageDecoder());
                //自定义协议编码器
                ch.pipeline().addLast(new MessageEncoder());
                ch.pipeline().addLast(new IdleCheckHandler(60, 30, 0));
                //服务器连接处理器
                ch.pipeline().addLast(new ClientHandler(bootstrap, clientWorkerGroup));
            }
        });
        try {
            bootstrap.connect(clientConfig.getServerIp(), clientConfig.getServerPort()).get();
            logger.info("客户端成功连接服务端IP:{},服务端端口:{}", clientConfig.getServerIp(), clientConfig.getServerPort());
        } catch (Exception exception) {
            logger.info("客户端失败连接服务端IP:{},服务端端口:{},原因:{}", clientConfig.getServerIp(), clientConfig.getServerPort(), exception.getCause().getMessage());
            clientWorkerGroup.shutdownGracefully();
        }
    }
}
