package cn.promptness.rpt.server.handler;

import cn.promptness.rpt.base.coder.ByteArrayCodec;
import cn.promptness.rpt.base.config.ProxyType;
import cn.promptness.rpt.base.config.RemoteConfig;
import cn.promptness.rpt.base.handler.ByteIdleCheckHandler;
import cn.promptness.rpt.base.protocol.Message;
import cn.promptness.rpt.base.protocol.MessageType;
import cn.promptness.rpt.base.protocol.Meta;
import cn.promptness.rpt.base.utils.Config;
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
import java.util.function.BiFunction;

/**
 * 处理服务器接收到的客户端连接
 */
public class ServerHandler extends SimpleChannelInboundHandler<Message> {

    private static final Logger logger = LoggerFactory.getLogger(ServerHandler.class);
    /**
     * remoteChannelId --> remoteChannel
     */
    private final Map<String, Channel> remoteChannelMap = new ConcurrentHashMap<>();
    private final EventLoopGroup remoteBossGroup = new NioEventLoopGroup();
    private final EventLoopGroup remoteWorkerGroup = new NioEventLoopGroup();
    private final List<String> domainList = new CopyOnWriteArrayList<>();
    private String clientKey;

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.channel().close();
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        for (Channel channel : remoteChannelMap.values()) {
            channel.config().setAutoRead(ctx.channel().isWritable());
        }
        for (Channel channel : ServerChannelCache.getServerHttpChannelMap().values()) {
            channel.config().setAutoRead(ctx.channel().isWritable());
        }
        super.channelWritabilityChanged(ctx);
    }

    /**
     * 连接中断
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        logger.info("服务端-客户端连接中断,{}", clientKey == null ? "未知连接" : clientKey);
        for (Channel channel : remoteChannelMap.values()) {
            channel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
        remoteChannelMap.clear();
        // 取消监听的端口 否则第二次连接时无法再次绑定端口
        remoteBossGroup.shutdownGracefully();
        remoteWorkerGroup.shutdownGracefully();

        for (String domain : domainList) {
            Channel remove = ServerChannelCache.getServerDomainChannelMap().remove(domain);
            if (remove != null) {
                remove.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
            }
        }
        domainList.clear();
        for (Channel channel : ServerChannelCache.getServerHttpChannelMap().values()) {
            channel.config().setAutoRead(true);
        }
    }


    @Override
    protected void channelRead0(ChannelHandlerContext context, Message message) throws Exception {
        MessageType type = message.getType();
        switch (type) {
            case TYPE_REGISTER:
                register(context, message);
                break;
            case TYPE_DATA:
                dispatch(message, (proxyType, channelId) -> getChannelMap(proxyType).get(channelId), (proxyType, channel) -> channel.writeAndFlush(Optional.ofNullable(message.getData()).orElse(EmptyArrays.EMPTY_BYTES)));
                break;
            case TYPE_CONNECTED:
                dispatch(message, (proxyType, channelId) -> getChannelMap(proxyType).get(channelId), (proxyType, channel) -> channel.pipeline().fireUserEventTriggered(proxyType));
                break;
            case TYPE_DISCONNECTED:
                // 数据发送完成后再关闭连
                dispatch(message, (proxyType, channelId) -> getChannelMap(proxyType).remove(channelId), (proxyType, channel) -> channel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE));
                break;
            case TYPE_KEEPALIVE:
            default:
        }
    }

    private void register(ChannelHandlerContext context, Message message) throws InterruptedException {
        Meta meta = message.getMeta();
        clientKey = meta.getClientKey();
        if (!Config.getServerConfig().getClientKey().contains(clientKey)) {
            logger.info("授权失败,客户端使用的秘钥:{}", clientKey);
            Message res = new Message();
            res.setType(MessageType.TYPE_AUTH);
            res.setMeta(meta.setConnection(false));
            context.writeAndFlush(res).addListener(ChannelFutureListener.CLOSE);
            return;
        }
        List<String> remoteResult = getRemoteResult(context, meta);
        Message res = new Message();
        res.setType(MessageType.TYPE_AUTH);
        res.setMeta(meta.setConnection(true).setRemoteResult(remoteResult));
        context.writeAndFlush(res);
    }

    private List<String> getRemoteResult(ChannelHandlerContext context, Meta meta) throws InterruptedException {
        List<String> remoteResult = new ArrayList<>();
        List<RemoteConfig> remoteConfigList = Optional.ofNullable(meta.getRemoteConfigList()).orElse(Collections.emptyList());
        if (remoteConfigList.isEmpty()) {
            return remoteResult;
        }
        CountDownLatch countDownLatch = new CountDownLatch(remoteConfigList.size());
        for (RemoteConfig remoteConfig : remoteConfigList) {
            ProxyType proxyType = Optional.ofNullable(remoteConfig.getProxyType()).orElse(ProxyType.TCP);
            switch (proxyType) {
                case TCP:
                    registerTcp(context, remoteResult, remoteConfig, countDownLatch);
                    break;
                case HTTP:
                    registerHttp(context, remoteResult, remoteConfig, countDownLatch);
                    break;
                default:
            }
        }
        countDownLatch.await();
        return remoteResult;
    }

    private void registerHttp(ChannelHandlerContext context, List<String> remoteResult, RemoteConfig remoteConfig, CountDownLatch countDownLatch) {
        if (!StringUtils.hasText(remoteConfig.getDomain())) {
            remoteResult.add(String.format("服务端绑定域名[%s]不合法", remoteConfig.getDomain()));
            countDownLatch.countDown();
            return;
        }
        logger.info("服务端开始绑定域名[{}]", remoteConfig.getDomain());
        ServerChannelCache.getServerDomainChannelMap().compute(remoteConfig.getDomain(), (domain, channel) -> {
            if (channel != null) {
                remoteResult.add(String.format("服务端绑定域名[%s]重复", domain));
                return channel;
            }
            domainList.add(domain);
            remoteResult.add(String.format("服务端绑定域名[%s]成功", domain));
            return context.channel();
        });
        countDownLatch.countDown();
    }

    private void registerTcp(ChannelHandlerContext context, List<String> remoteResult, RemoteConfig remoteConfig, CountDownLatch countDownLatch) {
        if (remoteConfig.getRemotePort() == 0 || remoteConfig.getRemotePort() == Config.getServerConfig().getServerPort() || remoteConfig.getRemotePort() == Config.getServerConfig().getHttpPort() || remoteConfig.getRemotePort() == Config.getServerConfig().getHttpsPort()) {
            remoteResult.add(String.format("需要绑定的端口[%s]不合法", remoteConfig.getRemotePort()));
            countDownLatch.countDown();
            return;
        }
        ServerBootstrap remoteBootstrap = new ServerBootstrap();
        remoteBootstrap.group(remoteBossGroup, remoteWorkerGroup).channel(NioServerSocketChannel.class).childOption(ChannelOption.SO_KEEPALIVE, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel channel) throws Exception {
                        channel.pipeline().addLast(new ByteArrayCodec());
                        channel.pipeline().addLast(new ChunkedWriteHandler());
                        channel.pipeline().addLast(new ByteIdleCheckHandler(0, 30, 0));
                        channel.pipeline().addLast(new RemoteHandler(context.channel(), remoteConfig));
                        remoteChannelMap.put(channel.id().asLongText(), channel);
                    }
                });

        logger.info("服务端开始建立本地端口绑定[{}]", remoteConfig.getRemotePort());
        remoteBootstrap.bind(Config.getServerConfig().getServerIp(), remoteConfig.getRemotePort()).addListener((ChannelFutureListener) channelFuture -> {
            if (channelFuture.isSuccess()) {
                remoteResult.add(String.format("服务端绑定端口[%s]成功", remoteConfig.getRemotePort()));
            } else {
                logger.info("服务端失败建立本地端口绑定[{}], {}", remoteConfig.getRemotePort(), channelFuture.cause().getMessage());
                remoteResult.add(String.format("服务端绑定端口[%s]失败,原因:%s", remoteConfig.getRemotePort(), channelFuture.cause().getMessage()));
            }
            countDownLatch.countDown();
        });
    }

    private Map<String, Channel> getChannelMap(ProxyType proxyType) {
        return ProxyType.HTTP.equals(proxyType) ? ServerChannelCache.getServerHttpChannelMap() : remoteChannelMap;
    }

    private void dispatch(Message message, BiFunction<ProxyType, String, Channel> function, BiConsumer<ProxyType, Channel> consumer) {
        Meta meta = message.getMeta();
        RemoteConfig remoteConfig = meta.getRemoteConfig();
        if (remoteConfig == null) {
            return;
        }
        ProxyType proxyType = Optional.ofNullable(remoteConfig.getProxyType()).orElse(ProxyType.TCP);
        String channelId = meta.getChannelId();
        Channel channel = function.apply(proxyType, channelId);
        if (channel == null) {
            return;
        }
        consumer.accept(proxyType, channel);
    }
}
