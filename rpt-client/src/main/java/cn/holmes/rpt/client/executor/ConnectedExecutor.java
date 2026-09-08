package cn.holmes.rpt.client.executor;

import cn.holmes.rpt.base.config.ProxyType;
import cn.holmes.rpt.base.config.RemoteConfig;
import cn.holmes.rpt.base.executor.MessageExecutor;
import cn.holmes.rpt.base.protocol.Message;
import cn.holmes.rpt.base.protocol.MessageType;
import cn.holmes.rpt.base.protocol.Meta;
import cn.holmes.rpt.base.config.ConfigHolder;
import cn.holmes.rpt.client.cache.TunnelPool;
import cn.holmes.rpt.client.handler.TcpHandler;
import cn.holmes.rpt.client.handler.UdpHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.util.Objects;

public class ConnectedExecutor implements MessageExecutor {

    private static final EventLoopGroup LOOP_GROUP = new NioEventLoopGroup();

    @Override
    public MessageType getMessageType() {
        return MessageType.TYPE_CONNECTED;
    }

    @Override
    public void execute(ChannelHandlerContext context, Message message) {
        Meta meta = message.getMeta();
        RemoteConfig remoteConfig = meta.getRemoteConfig();
        if (Objects.isNull(remoteConfig)) {
            return;
        }
        if (Objects.equals(ProxyType.HTTP, remoteConfig.getProxyType())) {
            String domain = remoteConfig.getDomain();
            // 补全配置信息
            RemoteConfig httpConfig = ConfigHolder.getClientConfig().getHttpConfig(domain);
            if (Objects.isNull(httpConfig)) {
                return;
            }
            meta.setRemoteConfig(httpConfig);
        }
        // 轮询选取共享隧道
        Channel tunnel = TunnelPool.getInstance().pick();
        if (Objects.isNull(tunnel)) {
            context.channel().writeAndFlush(new Message(MessageType.TYPE_DISCONNECTED, meta, Unpooled.EMPTY_BUFFER));
            return;
        }
        Channel control = context.channel();
        if (Objects.equals(ProxyType.UDP, remoteConfig.getProxyType())) {
            connectUdp(control, tunnel, meta);
        } else {
            connectTcp(control, tunnel, meta);
        }
    }

    private void connectTcp(Channel control, Channel tunnel, Meta meta) {
        RemoteConfig remoteConfig = meta.getRemoteConfig();
        Bootstrap localBootstrap = new Bootstrap();
        localBootstrap.group(LOOP_GROUP).channel(NioSocketChannel.class).option(ChannelOption.SO_KEEPALIVE, true).handler(new ChannelInitializer<SocketChannel>() {
            @Override
            public void initChannel(SocketChannel channel) throws Exception {
                channel.pipeline().addLast(new TcpHandler(control, tunnel, meta));
            }
        });
        localBootstrap.connect(remoteConfig.getLocalIp(), remoteConfig.getLocalPort()).addListener((ChannelFutureListener) future -> {
            if (!future.isSuccess()) {
                control.writeAndFlush(new Message(MessageType.TYPE_DISCONNECTED, meta, Unpooled.EMPTY_BUFFER));
            }
        });
    }

    private void connectUdp(Channel control, Channel tunnel, Meta meta) {
        Bootstrap udpBootstrap = new Bootstrap();
        udpBootstrap.group(LOOP_GROUP).channel(NioDatagramChannel.class).handler(new ChannelInitializer<DatagramChannel>() {
            @Override
            protected void initChannel(DatagramChannel channel) throws Exception {
                channel.pipeline().addLast(new UdpHandler(control, tunnel, meta));
            }
        });
        // UDP不需要connect，只需要bind到一个随机本地端口
        udpBootstrap.bind(0).addListener((ChannelFutureListener) future -> {
            if (!future.isSuccess()) {
                control.writeAndFlush(new Message(MessageType.TYPE_DISCONNECTED, meta, Unpooled.EMPTY_BUFFER));
            }
        });
    }
}
