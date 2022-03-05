package cn.promptness.rdp.server;

import cn.promptness.rdp.base.coder.MessageDecoder;
import cn.promptness.rdp.base.coder.MessageEncoder;
import cn.promptness.rdp.base.config.Config;
import cn.promptness.rdp.base.config.ServerConfig;
import cn.promptness.rdp.base.handler.IdleCheckHandler;
import cn.promptness.rdp.server.handler.ServerHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.traffic.GlobalTrafficShapingHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;
import java.io.InputStream;

public class ServerApplication {

    private static final Logger logger = LoggerFactory.getLogger(ServerApplication.class);

    public static void main(String[] args) throws SSLException {

        ServerConfig serverConfig = Config.getServerConfig();
        InputStream certChainFile = ClassLoader.getSystemResourceAsStream("server.crt");
        InputStream keyFile = ClassLoader.getSystemResourceAsStream("pkcs8_server.key");
        InputStream rootFile = ClassLoader.getSystemResourceAsStream("ca.crt");
        SslContext sslContext = SslContextBuilder.forServer(certChainFile, keyFile).trustManager(rootFile).clientAuth(ClientAuth.REQUIRE).sslProvider(SslProvider.OPENSSL).build();

        NioEventLoopGroup serverBossGroup = new NioEventLoopGroup();
        NioEventLoopGroup serverWorkerGroup = new NioEventLoopGroup();

        ServerBootstrap bootstrap = new ServerBootstrap();
        // 服务上行带宽8Mbps 限制数据流入速度1m/s
        GlobalTrafficShapingHandler globalTrafficShapingHandler = new GlobalTrafficShapingHandler(serverBossGroup, 0, serverConfig.getServerLimit());
        bootstrap.group(serverBossGroup, serverWorkerGroup).channel(NioServerSocketChannel.class).childHandler(new ChannelInitializer<SocketChannel>() {

            @Override
            public void initChannel(SocketChannel ch) throws Exception {
                ch.pipeline().addLast(sslContext.newHandler(ch.alloc()));
                ch.pipeline().addLast(globalTrafficShapingHandler);
                // 固定帧长解码器
                ch.pipeline().addLast(new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE, 0, 4, 0, 4));
                // 最大16M
                ch.pipeline().addLast(new LengthFieldPrepender(4));
                // 自定义协议解码器
                ch.pipeline().addLast(new MessageDecoder());
                // 自定义协议编码器
                ch.pipeline().addLast(new MessageEncoder());
                ch.pipeline().addLast(new IdleCheckHandler(60, 40, 0));
                // 代理客户端连接代理服务器处理器
                ch.pipeline().addLast(new ServerHandler());
            }
        });

        try {
            bootstrap.bind(serverConfig.getServerIp(), serverConfig.getServerPort()).get();
            logger.info("服务端启动成功,本机绑定IP:{},服务端口:{}", serverConfig.getServerIp(), serverConfig.getServerPort());
        } catch (Exception exception) {
            logger.info("服务端启动失败,本机绑定IP:{},服务端口:{},原因:{}", serverConfig.getServerIp(), serverConfig.getServerPort(), exception.getCause().getMessage());
            serverBossGroup.shutdownGracefully();
            serverWorkerGroup.shutdownGracefully();
        }
    }
}
