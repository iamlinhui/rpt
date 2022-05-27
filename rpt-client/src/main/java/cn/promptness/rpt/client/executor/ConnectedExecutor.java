package cn.promptness.rpt.client.executor;

import cn.promptness.rpt.base.coder.ByteArrayCodec;
import cn.promptness.rpt.base.config.ProxyType;
import cn.promptness.rpt.base.config.RemoteConfig;
import cn.promptness.rpt.base.executor.MessageExecutor;
import cn.promptness.rpt.base.protocol.Message;
import cn.promptness.rpt.base.protocol.MessageType;
import cn.promptness.rpt.base.protocol.Meta;
import cn.promptness.rpt.base.utils.Config;
import cn.promptness.rpt.base.utils.Constants;
import cn.promptness.rpt.client.cache.ProxyChannelCache;
import cn.promptness.rpt.client.handler.LocalHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.util.internal.EmptyArrays;

import java.util.Collections;
import java.util.Objects;

public class ConnectedExecutor implements MessageExecutor {

    private final EventLoopGroup localGroup = new NioEventLoopGroup();

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
        ProxyChannelCache.get(context.channel().attr(Constants.Client.APPLICATION).get().bootstrap(), proxyChannel -> connectedTcp(context, proxyChannel, meta), () -> context.writeAndFlush(new Message(MessageType.TYPE_DISCONNECTED, meta, EmptyArrays.EMPTY_BYTES)));
    }

    private void connectedTcp(ChannelHandlerContext context, Channel proxyChannel, Meta meta) {
        RemoteConfig remoteConfig = meta.getRemoteConfig();
        Bootstrap localBootstrap = new Bootstrap();
        localBootstrap.group(localGroup).channel(NioSocketChannel.class).option(ChannelOption.SO_KEEPALIVE, true).handler(new ChannelInitializer<SocketChannel>() {
            @Override
            public void initChannel(SocketChannel channel) throws Exception {
                channel.pipeline().addLast(new ByteArrayCodec());
                channel.pipeline().addLast(new ChunkedWriteHandler());
                channel.pipeline().addLast(new LocalHandler(context.channel(), meta));
            }
        });
        localBootstrap.connect(remoteConfig.getLocalIp(), remoteConfig.getLocalPort()).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                future.channel().attr(Constants.PROXY).set(proxyChannel);
                proxyChannel.attr(Constants.LOCAL).set(future.channel());
            } else {
                ProxyChannelCache.put(proxyChannel);
                context.writeAndFlush(new Message(MessageType.TYPE_DISCONNECTED, meta, EmptyArrays.EMPTY_BYTES));
            }
        });
    }
}
