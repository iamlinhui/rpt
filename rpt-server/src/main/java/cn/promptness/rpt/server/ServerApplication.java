package cn.promptness.rpt.server;

import cn.promptness.rpt.base.coder.GzipCodec;
import cn.promptness.rpt.base.coder.MessageCodec;
import cn.promptness.rpt.base.config.ServerConfig;
import cn.promptness.rpt.base.handler.IdleCheckHandler;
import cn.promptness.rpt.base.utils.Application;
import cn.promptness.rpt.base.utils.Config;
import cn.promptness.rpt.server.handler.RedirectHandler;
import cn.promptness.rpt.server.handler.RequestHandler;
import cn.promptness.rpt.server.handler.ServerHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
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

public class ServerApplication implements Application<Boolean> {

    private static final Logger logger = LoggerFactory.getLogger(ServerApplication.class);

    private final ServerBootstrap bootstrap = new ServerBootstrap();
    private final NioEventLoopGroup serverBossGroup = new NioEventLoopGroup();
    private final NioEventLoopGroup serverWorkerGroup = new NioEventLoopGroup();

    public static void main(String[] args) throws Exception {
        new ServerApplication().config(args).buildBootstrap().start(0);
    }

    @Override
    public Application<Boolean> config(String[] args) {
        Config.readServerConfig(args);
        return this;
    }

    @Override
    public Application<Boolean> buildBootstrap() throws IOException {
        SslContext sslContext = buildServerSslContext();
        bootstrap.group(serverBossGroup, serverWorkerGroup).channel(NioServerSocketChannel.class).childHandler(new ChannelInitializer<SocketChannel>() {

            @Override
            public void initChannel(SocketChannel ch) throws Exception {
                ch.pipeline().addLast(sslContext.newHandler(ch.alloc()));
                ch.pipeline().addLast(new GzipCodec());
                // 固定帧长解码器
                ch.pipeline().addLast(new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE, 0, 4, 0, 4));
                ch.pipeline().addLast(new LengthFieldPrepender(4));
                ch.pipeline().addLast(new ChunkedWriteHandler());
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
    public Boolean start(int seconds) throws Exception {
        TimeUnit.SECONDS.sleep(seconds);
        if (serverBossGroup.isShuttingDown() || serverBossGroup.isShutdown()) {
            return false;
        }
        ServerConfig serverConfig = Config.getServerConfig();
        bootstrap.bind(serverConfig.getServerIp(), serverConfig.getServerPort()).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                logger.info("服务端启动成功,本机绑定IP:{},服务端口:{}", serverConfig.getServerIp(), serverConfig.getServerPort());
                this.startHttp();
            } else {
                logger.info("服务端启动失败,本机绑定IP:{},服务端口:{},原因:{}", serverConfig.getServerIp(), serverConfig.getServerPort(), future.cause().getMessage());
                serverBossGroup.shutdownGracefully();
                serverWorkerGroup.shutdownGracefully();
            }
        });
        return true;
    }

    private void startHttp() {
        ServerConfig serverConfig = Config.getServerConfig();
        int httpPort = serverConfig.getHttpPort();
        if (httpPort == 0) {
            return;
        }
        ServerBootstrap httpBootstrap = new ServerBootstrap();
        httpBootstrap.group(serverBossGroup, serverWorkerGroup).channel(NioServerSocketChannel.class).childOption(ChannelOption.SO_KEEPALIVE, true).childHandler(new ChannelInitializer<SocketChannel>() {

            @Override
            public void initChannel(SocketChannel ch) throws Exception {
                ch.pipeline().addLast(new HttpServerCodec());
                ch.pipeline().addLast(new HttpObjectAggregator(8 * 1024 * 1024));
                ch.pipeline().addLast(new ChunkedWriteHandler());
                ch.pipeline().addLast(serverConfig.getHttpsPort() == 0 ? new RequestHandler() : new RedirectHandler());
            }
        });

        httpBootstrap.bind(serverConfig.getServerIp(), serverConfig.getHttpPort()).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                logger.info("服务端启动成功,本机绑定IP:{},Http端口:{}", serverConfig.getServerIp(), serverConfig.getHttpPort());
                startHttps();
            } else {
                logger.info("服务端启动失败,本机绑定IP:{},Http端口:{},原因:{}", serverConfig.getServerIp(), serverConfig.getHttpPort(), future.cause().getMessage());
                serverBossGroup.shutdownGracefully();
                serverWorkerGroup.shutdownGracefully();
            }
        });
    }

    private void startHttps() throws IOException {
        ServerConfig serverConfig = Config.getServerConfig();
        int httpsPort = serverConfig.getHttpsPort();
        if (httpsPort == 0) {
            return;
        }
        ServerBootstrap httpsBootstrap = new ServerBootstrap();
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
        httpsBootstrap.bind(serverConfig.getServerIp(), serverConfig.getHttpsPort()).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                logger.info("服务端启动成功,本机绑定IP:{},Https端口:{}", serverConfig.getServerIp(), serverConfig.getHttpsPort());
            } else {
                logger.info("服务端启动失败,本机绑定IP:{},Https端口:{},原因:{}", serverConfig.getServerIp(), serverConfig.getHttpsPort(), future.cause().getMessage());
                serverBossGroup.shutdownGracefully();
                serverWorkerGroup.shutdownGracefully();
            }
        });
    }

    private SslContext buildHttpsSslContext(ServerConfig serverConfig) throws IOException {
        try (InputStream certChainFile = ClassLoader.getSystemResourceAsStream(serverConfig.getDomainCert()); InputStream keyFile = ClassLoader.getSystemResourceAsStream(serverConfig.getDomainKey())) {
            return SslContextBuilder.forServer(certChainFile, keyFile).clientAuth(ClientAuth.NONE).sslProvider(SslProvider.OPENSSL).build();
        }
    }

    private static SslContext buildServerSslContext() throws IOException {
        try (InputStream certChainFile = ClassLoader.getSystemResourceAsStream("server.crt"); InputStream keyFile = ClassLoader.getSystemResourceAsStream("pkcs8_server.key"); InputStream rootFile = ClassLoader.getSystemResourceAsStream("ca.crt")) {
            return SslContextBuilder.forServer(certChainFile, keyFile).trustManager(rootFile).clientAuth(ClientAuth.REQUIRE).sslProvider(SslProvider.OPENSSL).build();
        }
    }

    @Override
    public ServerBootstrap bootstrap() {
        return bootstrap;
    }
}
