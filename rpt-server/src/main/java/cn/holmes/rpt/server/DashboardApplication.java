package cn.holmes.rpt.server;

import cn.holmes.rpt.base.config.ServerConfig;
import cn.holmes.rpt.base.utils.Application;
import cn.holmes.rpt.base.utils.Config;
import cn.holmes.rpt.server.handler.DashboardHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class DashboardApplication extends Application<ServerBootstrap> {

    private static final Logger logger = LoggerFactory.getLogger(DashboardApplication.class);

    private final ServerBootstrap bootstrap = new ServerBootstrap();
    private final EventLoopGroup bossGroup = new NioEventLoopGroup(1);
    private final EventLoopGroup workerGroup = new NioEventLoopGroup(2);

    @Override
    public Application<ServerBootstrap> buildBootstrap() throws IOException {
        bootstrap.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class).childOption(ChannelOption.SO_KEEPALIVE, true).childHandler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) {
                ch.pipeline().addLast(new HttpServerCodec());
                ch.pipeline().addLast(new HttpObjectAggregator(65536));
                ch.pipeline().addLast(new DashboardHandler());
            }
        });
        return this;
    }

    @Override
    public boolean start(int seconds) throws Exception {
        TimeUnit.SECONDS.sleep(seconds);
        ServerConfig serverConfig = Config.getServerConfig();
        int dashboardPort = serverConfig.getDashboardPort();
        if (dashboardPort == 0) {
            this.stop();
            return false;
        }
        bootstrap.bind(serverConfig.getServerIp(), dashboardPort).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                logger.info("Dashboard启动成功,绑定IP:{},端口:{}", serverConfig.getServerIp(), dashboardPort);
            } else {
                logger.info("Dashboard启动失败,绑定IP:{},端口:{},原因:{}", serverConfig.getServerIp(), dashboardPort, future.cause().getMessage());
                this.stop();
            }
        });
        return true;
    }

    @Override
    public void stop() {
        bossGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();
    }

    @Override
    public ServerBootstrap bootstrap() {
        return bootstrap;
    }
}

