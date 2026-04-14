package cn.holmes.rpt.client.executor;

import cn.holmes.rpt.base.coder.ByteArrayCodec;
import cn.holmes.rpt.base.config.ProxyType;
import cn.holmes.rpt.base.config.RemoteConfig;
import cn.holmes.rpt.base.executor.MessageExecutor;
import cn.holmes.rpt.base.protocol.Message;
import cn.holmes.rpt.base.protocol.MessageType;
import cn.holmes.rpt.base.protocol.Meta;
import cn.holmes.rpt.base.utils.Config;
import cn.holmes.rpt.base.utils.Constants.Client;
import cn.holmes.rpt.base.utils.Listener;
import cn.holmes.rpt.client.cache.ProxyChannelCache;
import cn.holmes.rpt.client.handler.TcpHandler;
import cn.holmes.rpt.client.handler.UdpHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.util.internal.EmptyArrays;

import java.util.Collections;
import java.util.Objects;

public class ConnectedExecutor implements MessageExecutor, Listener<Meta> {

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
        ProxyType proxyType = remoteConfig.getProxyType();
        if (Objects.equals(ProxyType.HTTP, proxyType)) {
            String domain = remoteConfig.getDomain();
            // 补全配置信息
            RemoteConfig httpConfig = Config.getClientConfig().getHttpConfig(domain);
            if (Objects.isNull(httpConfig)) {
                return;
            }
            meta.setRemoteConfigList(Collections.singletonList(httpConfig));
        }
        // 绑定代理连接
        ProxyChannelCache.get(context.channel(), meta, this);
    }

    @Override
    public void fail(Channel serverChannel, Meta meta) {
        serverChannel.writeAndFlush(new Message(MessageType.TYPE_DISCONNECTED, meta, EmptyArrays.EMPTY_BYTES));
    }

    @Override
    public void success(Channel serverChannel, Channel proxyChannel, Meta meta) {
        RemoteConfig remoteConfig = meta.getRemoteConfig();
        if (Objects.equals(ProxyType.UDP, remoteConfig.getProxyType())) {
            connectUdp(serverChannel, proxyChannel, meta);
        } else {
            connectTcp(serverChannel, proxyChannel, meta);
        }
    }

    private void connectTcp(Channel serverChannel, Channel proxyChannel, Meta meta) {
        RemoteConfig remoteConfig = meta.getRemoteConfig();
        Bootstrap localBootstrap = new Bootstrap();
        localBootstrap.group(LOOP_GROUP).channel(NioSocketChannel.class).option(ChannelOption.SO_KEEPALIVE, true).handler(new ChannelInitializer<SocketChannel>() {
            @Override
            public void initChannel(SocketChannel channel) throws Exception {
                channel.pipeline().addLast(new ByteArrayCodec());
                channel.pipeline().addLast(new ChunkedWriteHandler());
                channel.pipeline().addLast(new TcpHandler(serverChannel, meta));
            }
        });
        localBootstrap.connect(remoteConfig.getLocalIp(), remoteConfig.getLocalPort()).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                future.channel().attr(Client.PROXY).set(proxyChannel);
                proxyChannel.attr(Client.LOCAL).set(future.channel());
            } else {
                ProxyChannelCache.put(proxyChannel);
                serverChannel.writeAndFlush(new Message(MessageType.TYPE_DISCONNECTED, meta, EmptyArrays.EMPTY_BYTES));
            }
        });
    }

    private void connectUdp(Channel serverChannel, Channel proxyChannel, Meta meta) {
        Bootstrap udpBootstrap = new Bootstrap();
        udpBootstrap.group(LOOP_GROUP).channel(NioDatagramChannel.class).handler(new ChannelInitializer<DatagramChannel>() {
            @Override
            protected void initChannel(DatagramChannel channel) throws Exception {
                channel.pipeline().addLast(new UdpHandler(serverChannel, meta));
            }
        });
        // UDP不需要connect，只需要bind到一个随机本地端口
        udpBootstrap.bind(0).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                proxyChannel.attr(Client.LOCAL).set(future.channel());
                future.channel().attr(Client.PROXY).set(proxyChannel);
            } else {
                ProxyChannelCache.put(proxyChannel);
                serverChannel.writeAndFlush(new Message(MessageType.TYPE_DISCONNECTED, meta, EmptyArrays.EMPTY_BYTES));
            }
        });
    }

}
