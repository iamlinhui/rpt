package cn.promptness.rdp.server;

import cn.promptness.rdp.common.coder.MessageDecoder;
import cn.promptness.rdp.common.coder.MessageEncoder;
import cn.promptness.rdp.common.config.Config;
import cn.promptness.rdp.common.config.ServerConfig;
import cn.promptness.rdp.common.handler.IdleCheckHandler;
import cn.promptness.rdp.server.handler.ServerHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class ServerApplication {

    private static final Logger logger = LoggerFactory.getLogger(ServerApplication.class);

    public static void main(String[] args) throws InterruptedException {

        ServerConfig serverConfig = Config.getServerConfig();

        NioEventLoopGroup serverBossGroup = new NioEventLoopGroup();
        NioEventLoopGroup serverWorkerGroup = new NioEventLoopGroup();

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(serverBossGroup, serverWorkerGroup).channel(NioServerSocketChannel.class).childHandler(new ChannelInitializer<SocketChannel>() {
            @Override
            public void initChannel(SocketChannel ch) throws Exception {
                ch.pipeline().addLast(new IdleCheckHandler());
                //固定帧长解码器
                ch.pipeline().addLast(new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE, 0, 4, 0, 4));
                //自定义协议解码器
                ch.pipeline().addLast(new MessageDecoder());
                //自定义协议编码器
                ch.pipeline().addLast(new MessageEncoder());
                //代理客户端连接代理服务器处理器
                ch.pipeline().addLast(new ServerHandler());
            }
        });
        bootstrap.bind(serverConfig.getServerIp(), serverConfig.getServerPort()).sync();
        logger.info("服务端启动成功本机绑定IP:{},服务端口:{}", serverConfig.getServerIp(), serverConfig.getServerPort());
    }
}
