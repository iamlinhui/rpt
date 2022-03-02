package cn.promptness.rdp.server;

import cn.promptness.rdp.common.coder.MessageDecoder;
import cn.promptness.rdp.common.coder.MessageEncoder;
import cn.promptness.rdp.common.config.Config;
import cn.promptness.rdp.common.config.ServerConfig;
import cn.promptness.rdp.server.handler.ServerHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.timeout.IdleStateHandler;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class ServerApplication {

    public static void main(String[] args) throws ExecutionException, InterruptedException {

        ServerConfig serverConfig = Config.getServerConfig();

        NioEventLoopGroup serverBossGroup = new NioEventLoopGroup();
        NioEventLoopGroup serverWorkerGroup = new NioEventLoopGroup();

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(serverBossGroup, serverWorkerGroup).channel(NioServerSocketChannel.class)
                .handler(new LoggingHandler())
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline().addLast(new IdleStateHandler(60, 40, 0, TimeUnit.SECONDS));
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
        bootstrap.bind(serverConfig.getServerIp(), serverConfig.getServerPort()).get();
    }
}
