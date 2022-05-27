package cn.promptness.rpt.client.handler;

import cn.promptness.rpt.base.coder.ByteArrayCodec;
import cn.promptness.rpt.base.config.ProxyType;
import cn.promptness.rpt.base.config.RemoteConfig;
import cn.promptness.rpt.base.protocol.Message;
import cn.promptness.rpt.base.protocol.MessageType;
import cn.promptness.rpt.base.protocol.Meta;
import cn.promptness.rpt.base.utils.Application;
import cn.promptness.rpt.base.utils.Config;
import cn.promptness.rpt.base.utils.Constants;
import cn.promptness.rpt.client.cache.ProxyChannelCache;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.util.internal.EmptyArrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务器连接处理器
 */
public class ClientHandler extends SimpleChannelInboundHandler<Message> {

    private static final Logger logger = LoggerFactory.getLogger(ClientHandler.class);
    private static final EventLoopGroup LOCAL_GROUP = new NioEventLoopGroup();

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        Channel localChannel = ctx.channel().attr(Constants.LOCAL).get();
        if (Objects.nonNull(localChannel)) {
            localChannel.config().setAutoRead(ctx.channel().isWritable());
        }
        super.channelWritabilityChanged(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext context, Message message) throws Exception {
        switch (message.getType()) {
            case TYPE_AUTH:
                for (String remoteResult : Optional.ofNullable(message.getMeta().getRemoteResult()).orElse(Collections.emptyList())) {
                    logger.info(remoteResult);
                }
                boolean connection = message.getMeta().isConnection();
                if (connection) {
                    logger.info("连接成功,当前秘钥:{}", message.getMeta().getClientKey());
                    context.channel().attr(Constants.CHANNELS).set(new ConcurrentHashMap<>(1024));
                } else {
                    logger.info("连接失败,当前秘钥:{}", message.getMeta().getClientKey());
                }
                break;
            case TYPE_CONNECTED:
                // 外部请求进入，开始与内网建立连接
                connected(context, message);
                break;
            case TYPE_DISCONNECTED:
                disconnected(context);
                break;
            case TYPE_DATA:
                transfer(context, message);
                break;
            case TYPE_KEEPALIVE:
            default:
        }
    }

    private void transfer(ChannelHandlerContext context, Message message) {
        Channel localChannel = context.channel().attr(Constants.LOCAL).get();
        if (Objects.nonNull(localChannel)) {
            localChannel.writeAndFlush(Optional.ofNullable(message.getData()).orElse(EmptyArrays.EMPTY_BYTES));
        }
    }

    private void disconnected(ChannelHandlerContext context) {
        Channel localChannel = context.channel().attr(Constants.LOCAL).getAndSet(null);
        if (Objects.nonNull(localChannel)) {
            ProxyChannelCache.release(context.channel());
            localChannel.writeAndFlush(EmptyArrays.EMPTY_BYTES).addListener(ChannelFutureListener.CLOSE);
        }
    }

    private void connected(ChannelHandlerContext context, Message message) {
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
        ProxyChannelCache.get(context.channel().attr(Constants.APPLICATION).get().bootstrap(), proxyChannel -> connectedTcp(context, proxyChannel, meta), () -> context.writeAndFlush(new Message(MessageType.TYPE_DISCONNECTED, meta, EmptyArrays.EMPTY_BYTES)));
    }

    private void connectedTcp(ChannelHandlerContext context, Channel proxyChannel, Meta meta) {
        RemoteConfig remoteConfig = meta.getRemoteConfig();
        Bootstrap localBootstrap = new Bootstrap();
        localBootstrap.group(LOCAL_GROUP).channel(NioSocketChannel.class).option(ChannelOption.SO_KEEPALIVE, true).handler(new ChannelInitializer<SocketChannel>() {
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
                ProxyChannelCache.release(proxyChannel);
                context.writeAndFlush(new Message(MessageType.TYPE_DISCONNECTED, meta, EmptyArrays.EMPTY_BYTES));
            }
        });
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        Application<Boolean> application = ctx.channel().attr(Constants.APPLICATION).getAndSet(null);
        logger.info("客户端-服务端连接中断,{}:{}", Config.getClientConfig().getServerIp(), Config.getClientConfig().getServerPort());
        if (Objects.nonNull(application)) {
            Optional.ofNullable(ctx.channel().attr(Constants.CHANNELS).getAndSet(null)).ifPresent(this::clear);
            return;
        }
        Channel localChannel = ctx.channel().attr(Constants.LOCAL).getAndSet(null);
        if (Objects.nonNull(localChannel)) {
            localChannel.attr(Constants.PROXY).set(null);
            localChannel.writeAndFlush(EmptyArrays.EMPTY_BYTES).addListener(ChannelFutureListener.CLOSE);
        }
        ProxyChannelCache.delete(ctx.channel());
    }

    private void clear(Map<String, Channel> channelMap) {
        for (Channel localChannel : channelMap.values()) {
            localChannel.writeAndFlush(EmptyArrays.EMPTY_BYTES).addListener(ChannelFutureListener.CLOSE);
        }
        channelMap.clear();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.close();
    }
}
