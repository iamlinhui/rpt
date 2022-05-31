package cn.promptness.rpt.server;

import cn.promptness.rpt.base.config.ServerConfig;
import cn.promptness.rpt.base.utils.Application;
import cn.promptness.rpt.base.utils.Config;
import cn.promptness.rpt.server.handler.RedirectHandler;
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
import io.netty.handler.stream.ChunkedWriteHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class HttpApplication implements Application<ServerBootstrap> {

    private static final Logger logger = LoggerFactory.getLogger(HttpApplication.class);

    private final ServerBootstrap httpBootstrap = new ServerBootstrap();
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
        httpBootstrap.group(serverBossGroup, serverWorkerGroup).channel(NioServerSocketChannel.class).childOption(ChannelOption.SO_KEEPALIVE, true).childHandler(new ChannelInitializer<SocketChannel>() {

            @Override
            public void initChannel(SocketChannel ch) throws Exception {
                ch.pipeline().addLast(new HttpServerCodec());
                ch.pipeline().addLast(new HttpObjectAggregator(8 * 1024 * 1024));
                ch.pipeline().addLast(new ChunkedWriteHandler());
                ch.pipeline().addLast(serverConfig.getHttpsPort() == 0 ? new RequestHandler() : new RedirectHandler());
            }
        });
        return this;
    }

    @Override
    public boolean start(int seconds) throws Exception {
        TimeUnit.SECONDS.sleep(seconds);
        ServerConfig serverConfig = Config.getServerConfig();
        int httpPort = serverConfig.getHttpPort();
        if (httpPort == 0) {
            this.stop();
            return false;
        }
        httpBootstrap.bind(serverConfig.getServerIp(), serverConfig.getHttpPort()).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                logger.info("服务端启动成功,本机绑定IP:{},Http端口:{}", serverConfig.getServerIp(), serverConfig.getHttpPort());
            } else {
                logger.info("服务端启动失败,本机绑定IP:{},Http端口:{},原因:{}", serverConfig.getServerIp(), serverConfig.getHttpPort(), future.cause().getMessage());
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
        return httpBootstrap;
    }
}
