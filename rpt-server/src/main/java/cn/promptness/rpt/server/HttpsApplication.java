package cn.promptness.rpt.server;

import cn.promptness.rpt.base.config.ServerConfig;
import cn.promptness.rpt.base.utils.Application;
import cn.promptness.rpt.base.utils.Config;
import cn.promptness.rpt.server.handler.RequestHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.stream.ChunkedWriteHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

public class HttpsApplication extends Application<ServerBootstrap> {

    private static final Logger logger = LoggerFactory.getLogger(HttpsApplication.class);

    private final ServerBootstrap httpsBootstrap = new ServerBootstrap();
    private final NioEventLoopGroup serverBossGroup = new NioEventLoopGroup();
    private final NioEventLoopGroup serverWorkerGroup = new NioEventLoopGroup();

    @Override
    public Application<ServerBootstrap> config(String[] args) {
        Config.readServerConfig(args);
        return this;
    }

    @Override
    public Application<ServerBootstrap> buildBootstrap() throws IOException {
        ServerConfig serverConfig = Config.getServerConfig();
        SslContext sslContext = buildHttpsSslContext(serverConfig);
        httpsBootstrap.group(serverBossGroup, serverWorkerGroup).channel(NioServerSocketChannel.class).childOption(ChannelOption.SO_KEEPALIVE, true).childHandler(new ChannelInitializer<SocketChannel>() {

            @Override
            public void initChannel(SocketChannel ch) throws Exception {
                ch.pipeline().addLast(sslContext.newHandler(ch.alloc()));
                ch.pipeline().addLast(new HttpServerCodec());
                ch.pipeline().addLast(new HttpObjectAggregator(8 * 1024 * 1024));
                ch.pipeline().addLast(new ChunkedWriteHandler());
                ch.pipeline().addLast(new RequestHandler());
            }
        });
        return this;
    }

    @Override
    public boolean start(int seconds) throws Exception {
        TimeUnit.SECONDS.sleep(seconds);
        ServerConfig serverConfig = Config.getServerConfig();
        int httpsPort = serverConfig.getHttpsPort();
        if (httpsPort == 0) {
            this.stop();
            return false;
        }
        httpsBootstrap.bind(serverConfig.getServerIp(), serverConfig.getHttpsPort()).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                logger.info("服务端启动成功,本机绑定IP:{},Https端口:{}", serverConfig.getServerIp(), serverConfig.getHttpsPort());
            } else {
                logger.info("服务端启动失败,本机绑定IP:{},Https端口:{},原因:{}", serverConfig.getServerIp(), serverConfig.getHttpsPort(), future.cause().getMessage());
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
        return httpsBootstrap;
    }

    private SslContext buildHttpsSslContext(ServerConfig serverConfig) throws IOException {
        try (InputStream certChainFile = ClassLoader.getSystemResourceAsStream(serverConfig.getDomainCert()); InputStream keyFile = ClassLoader.getSystemResourceAsStream(serverConfig.getDomainKey())) {
            return SslContextBuilder.forServer(certChainFile, keyFile).clientAuth(ClientAuth.NONE).sslProvider(SslProvider.OPENSSL).build();
        }
    }
}
