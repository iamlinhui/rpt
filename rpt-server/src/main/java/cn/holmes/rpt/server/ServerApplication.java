package cn.holmes.rpt.server;

import cn.holmes.rpt.base.coder.MessageCodec;
import cn.holmes.rpt.base.config.ServerConfig;
import cn.holmes.rpt.base.handler.IdleCheckHandler;
import cn.holmes.rpt.base.utils.Application;
import cn.holmes.rpt.base.utils.Config;
import cn.holmes.rpt.server.handler.IpFilterRuleHandler;
import cn.holmes.rpt.server.handler.ServerHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.ipfilter.RuleBasedIpFilter;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class ServerApplication extends Application<ServerBootstrap> {

    private static final Logger logger = LoggerFactory.getLogger(ServerApplication.class);

    private final ServerBootstrap bootstrap = new ServerBootstrap();
    private final NioEventLoopGroup serverBossGroup = new NioEventLoopGroup();
    private final NioEventLoopGroup serverWorkerGroup = new NioEventLoopGroup();
    private final RuleBasedIpFilter ruleBasedIpFilter = new RuleBasedIpFilter(IpFilterRuleHandler.getInstance());

    public static void main(String[] args) throws Exception {
        Application.run(args, new ServerApplication(), new HttpApplication(), new HttpsApplication());
    }

    @Override
    public Application<ServerBootstrap> config(String[] args) {
        Config.readServerConfig(args);
        return this;
    }

    @Override
    public Application<ServerBootstrap> buildBootstrap() throws IOException {
        SslContext sslContext = buildServerSslContext();
        bootstrap.group(serverBossGroup, serverWorkerGroup).channel(NioServerSocketChannel.class).childHandler(new ChannelInitializer<SocketChannel>() {

            @Override
            public void initChannel(SocketChannel ch) throws Exception {
                if (Config.getServerConfig().ipFilter()) {
                    ch.pipeline().addLast(ruleBasedIpFilter);
                }
                ch.pipeline().addLast(sslContext.newHandler(ch.alloc()));
                // 固定帧长解码器
                ch.pipeline().addLast(new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE, 0, 4, 0, 4));
                ch.pipeline().addLast(new LengthFieldPrepender(4));
                // 自定义协议编解码器
                ch.pipeline().addLast(new MessageCodec());
                ch.pipeline().addLast(new IdleCheckHandler(60, 40, 0));
                // 代理客户端连接代理服务器处理器
                ch.pipeline().addLast(new ServerHandler());
            }
        });
        return this;
    }

    @Override
    public boolean start(int seconds) throws Exception {
        TimeUnit.SECONDS.sleep(seconds);
        ServerConfig serverConfig = Config.getServerConfig();
        bootstrap.bind(serverConfig.getServerIp(), serverConfig.getServerPort()).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                logger.info("服务端启动成功,本机绑定IP:{},服务端口:{}", serverConfig.getServerIp(), serverConfig.getServerPort());
            } else {
                logger.info("服务端启动失败,本机绑定IP:{},服务端口:{},原因:{}", serverConfig.getServerIp(), serverConfig.getServerPort(), future.cause().getMessage());
                this.stop();
            }
        });
        return true;
    }

    @Override
    public void stop() {
        serverBossGroup.shutdownGracefully();
        serverWorkerGroup.shutdownGracefully();
    }

    @Override
    public ServerBootstrap bootstrap() {
        return bootstrap;
    }

    private static SslContext buildServerSslContext() throws IOException {
        ServerConfig serverConfig = Config.getServerConfig();
        String serverCaPath = Optional.ofNullable(serverConfig.getServerCaPath()).orElse("ca.crt");
        String serverCertPath = Optional.ofNullable(serverConfig.getServerCertPath()).orElse("server.crt");
        String serverKeyPath = Optional.ofNullable(serverConfig.getServerKeyPath()).orElse("pkcs8_server.key");
        try (InputStream caFile = ClassLoader.getSystemResourceAsStream(serverCaPath);
             InputStream certFile = ClassLoader.getSystemResourceAsStream(serverCertPath);
             InputStream keyFile = ClassLoader.getSystemResourceAsStream(serverKeyPath)) {
            return SslContextBuilder.forServer(certFile, keyFile).trustManager(caFile).clientAuth(ClientAuth.REQUIRE).sslProvider(SslProvider.OPENSSL).build();
        }
    }
}
