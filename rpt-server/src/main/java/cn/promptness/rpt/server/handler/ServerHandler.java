package cn.promptness.rpt.server.handler;

import cn.promptness.rpt.base.coder.ByteArrayCodec;
import cn.promptness.rpt.base.config.ProxyType;
import cn.promptness.rpt.base.config.RemoteConfig;
import cn.promptness.rpt.base.config.ServerToken;
import cn.promptness.rpt.base.protocol.Message;
import cn.promptness.rpt.base.protocol.MessageType;
import cn.promptness.rpt.base.protocol.Meta;
import cn.promptness.rpt.base.utils.Config;
import cn.promptness.rpt.base.utils.Constants;
import cn.promptness.rpt.base.utils.StringUtils;
import cn.promptness.rpt.server.cache.ServerChannelCache;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.util.internal.EmptyArrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * 处理服务器接收到的客户端连接
 */
public class ServerHandler extends SimpleChannelInboundHandler<Message> {

    private static final Logger logger = LoggerFactory.getLogger(ServerHandler.class);
    private final EventLoopGroup remoteBossGroup = new NioEventLoopGroup();
    private final EventLoopGroup remoteWorkerGroup = new NioEventLoopGroup();
    private final List<String> domainList = new CopyOnWriteArrayList<>();

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.channel().close();
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        for (Channel channel : ctx.channel().attr(Constants.CHANNELS).get().values()) {
            channel.config().setAutoRead(ctx.channel().isWritable());
        }
        super.channelWritabilityChanged(ctx);
    }

    /**
     * 连接中断
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        String clientKey = ctx.channel().attr(Constants.CLIENT_KEY).getAndSet(null);
        logger.info("服务端-客户端连接中断,{}", clientKey == null ? "未知连接" : clientKey);
        for (Channel channel : Optional.ofNullable(ctx.channel().attr(Constants.CHANNELS).getAndSet(null)).orElse(Collections.emptyMap()).values()) {
            channel.close();
        }
        for (String domain : domainList) {
            ServerChannelCache.getServerDomainChannelMap().remove(domain);
            ServerChannelCache.getServerDomainToken().remove(domain);
        }
        // 取消监听的端口 否则第二次连接时无法再次绑定端口
        remoteBossGroup.shutdownGracefully();
        remoteWorkerGroup.shutdownGracefully();
    }


    @Override
    protected void channelRead0(ChannelHandlerContext context, Message message) throws Exception {
        MessageType type = message.getType();
        Map<String, Channel> channelMap = context.channel().attr(Constants.CHANNELS).get();
        switch (type) {
            case TYPE_REGISTER:
                register(context, message);
                break;
            case TYPE_DATA:
                dispatch(message, channelMap::get, (proxyType, channel) -> channel.writeAndFlush(Optional.ofNullable(message.getData()).orElse(EmptyArrays.EMPTY_BYTES)));
                break;
            case TYPE_CONNECTED:
                dispatch(message, channelMap::get, (proxyType, channel) -> channel.pipeline().fireUserEventTriggered(proxyType));
                break;
            case TYPE_DISCONNECTED:
                // 数据发送完成后再关闭连
                dispatch(message, channelMap::remove, (proxyType, channel) -> channel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE));
                break;
            case TYPE_KEEPALIVE:
            default:
        }
    }

    private void register(ChannelHandlerContext context, Message message) throws InterruptedException {
        Meta meta = message.getMeta();
        if (!Config.getServerConfig().authorize(meta.getClientKey())) {
            logger.info("授权失败,客户端使用的秘钥:{}", meta.getClientKey());
            Message res = new Message();
            res.setType(MessageType.TYPE_AUTH);
            res.setMeta(meta.setConnection(false).setRemoteResult(Collections.singletonList("秘钥授权失败")));
            context.writeAndFlush(res).addListener(ChannelFutureListener.CLOSE);
            return;
        }
        fillRemoteResult(context, meta);
        Message res = new Message();
        res.setType(MessageType.TYPE_AUTH);
        res.setMeta(meta);
        ChannelFuture channelFuture = context.writeAndFlush(res);
        if (!meta.isConnection()) {
            channelFuture.addListener(ChannelFutureListener.CLOSE);
            return;
        }
        context.channel().attr(Constants.CLIENT_KEY).set(meta.getClientKey());
        context.channel().attr(Constants.CHANNELS).set(new ConcurrentHashMap<>(1024));
    }

    private void fillRemoteResult(ChannelHandlerContext context, Meta meta) throws InterruptedException {
        List<String> remoteResult = new ArrayList<>();
        meta.setConnection(true).setRemoteResult(remoteResult);
        List<RemoteConfig> remoteConfigList = Optional.ofNullable(meta.getRemoteConfigList()).orElse(Collections.emptyList());
        if (remoteConfigList.isEmpty()) {
            return;
        }
        CountDownLatch countDownLatch = new CountDownLatch(remoteConfigList.size());
        for (RemoteConfig remoteConfig : remoteConfigList) {
            ProxyType proxyType = Optional.ofNullable(remoteConfig.getProxyType()).orElse(ProxyType.TCP);
            switch (proxyType) {
                case TCP:
                    registerTcp(context, meta, remoteConfig, countDownLatch);
                    break;
                case HTTP:
                    registerHttp(context, meta, remoteConfig, countDownLatch);
                    break;
                default:
            }
        }
        countDownLatch.await();
    }

    private void registerHttp(ChannelHandlerContext context, Meta meta, RemoteConfig remoteConfig, CountDownLatch countDownLatch) {
        if (!StringUtils.hasText(remoteConfig.getDomain())) {
            meta.setConnection(false).addRemoteResult(String.format("服务端绑定域名[%s]不合法", remoteConfig.getDomain()));
            countDownLatch.countDown();
            return;
        }
        logger.info("服务端开始绑定域名[{}]", remoteConfig.getDomain());
        ServerChannelCache.getServerDomainChannelMap().compute(remoteConfig.getDomain(), (domain, channel) -> {
            if (channel != null) {
                meta.setConnection(false).addRemoteResult(String.format("服务端绑定域名[%s]重复", domain));
                return channel;
            }
            domainList.add(domain);
            if (StringUtils.hasText(remoteConfig.getToken())) {
                ServerChannelCache.getServerDomainToken().put(domain, remoteConfig.getToken());
            }
            meta.addRemoteResult(String.format("服务端绑定域名[%s]成功", domain));
            return context.channel();
        });
        countDownLatch.countDown();
    }

    private void registerTcp(ChannelHandlerContext context, Meta meta, RemoteConfig remoteConfig, CountDownLatch countDownLatch) {
        if (remoteConfig.getRemotePort() == 0 || remoteConfig.getRemotePort() == Config.getServerConfig().getServerPort() || remoteConfig.getRemotePort() == Config.getServerConfig().getHttpPort() || remoteConfig.getRemotePort() == Config.getServerConfig().getHttpsPort()) {
            meta.setConnection(false).addRemoteResult(String.format("需要绑定的端口[%s]不合法", remoteConfig.getRemotePort()));
            countDownLatch.countDown();
            return;
        }
        ServerToken serverToken = Config.getServerConfig().getServerToken(meta.getClientKey());
        if (!serverToken.authorize(remoteConfig.getRemotePort())) {
            meta.setConnection(false).addRemoteResult(String.format("需要绑定的端口[%s]范围不合法", remoteConfig.getRemotePort()));
            countDownLatch.countDown();
            return;
        }
        ServerBootstrap remoteBootstrap = new ServerBootstrap();
        remoteBootstrap.group(remoteBossGroup, remoteWorkerGroup).channel(NioServerSocketChannel.class).childOption(ChannelOption.SO_KEEPALIVE, true).childHandler(new ChannelInitializer<SocketChannel>() {
            @Override
            public void initChannel(SocketChannel channel) throws Exception {
                channel.pipeline().addLast(new ByteArrayCodec());
                channel.pipeline().addLast(new ChunkedWriteHandler());
                channel.pipeline().addLast(new RemoteHandler(context.channel(), remoteConfig));
            }
        });

        logger.info("服务端开始建立本地端口绑定[{}]", remoteConfig.getRemotePort());
        remoteBootstrap.bind(Config.getServerConfig().getServerIp(), remoteConfig.getRemotePort()).addListener((ChannelFutureListener) channelFuture -> {
            if (channelFuture.isSuccess()) {
                meta.addRemoteResult(String.format("服务端绑定端口[%s]成功", remoteConfig.getRemotePort()));
            } else {
                logger.info("服务端失败建立本地端口绑定[{}], {}", remoteConfig.getRemotePort(), channelFuture.cause().getMessage());
                meta.setConnection(false).addRemoteResult(String.format("服务端绑定端口[%s]失败,原因:%s", remoteConfig.getRemotePort(), channelFuture.cause().getMessage()));
            }
            countDownLatch.countDown();
        });
    }

    private void dispatch(Message message, Function<String, Channel> function, BiConsumer<ProxyType, Channel> consumer) {
        Meta meta = message.getMeta();
        RemoteConfig remoteConfig = meta.getRemoteConfig();
        if (remoteConfig == null) {
            return;
        }
        ProxyType proxyType = Optional.ofNullable(remoteConfig.getProxyType()).orElse(ProxyType.TCP);
        String channelId = meta.getChannelId();
        Channel channel = function.apply(channelId);
        if (channel == null) {
            return;
        }
        consumer.accept(proxyType, channel);
    }
}
