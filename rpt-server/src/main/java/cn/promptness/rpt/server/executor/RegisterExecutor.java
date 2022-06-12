package cn.promptness.rpt.server.executor;

import cn.promptness.rpt.base.coder.ByteArrayCodec;
import cn.promptness.rpt.base.config.ProxyType;
import cn.promptness.rpt.base.config.RemoteConfig;
import cn.promptness.rpt.base.config.ServerToken;
import cn.promptness.rpt.base.executor.MessageExecutor;
import cn.promptness.rpt.base.protocol.Message;
import cn.promptness.rpt.base.protocol.MessageType;
import cn.promptness.rpt.base.protocol.Meta;
import cn.promptness.rpt.base.utils.Config;
import cn.promptness.rpt.base.utils.Constants;
import cn.promptness.rpt.base.utils.StringUtils;
import cn.promptness.rpt.server.cache.ServerChannelCache;
import cn.promptness.rpt.server.handler.IpFilterRuleHandler;
import cn.promptness.rpt.server.handler.RemoteHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.ipfilter.RuleBasedIpFilter;
import io.netty.handler.stream.ChunkedWriteHandler;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;

public class RegisterExecutor implements MessageExecutor {

    private final RuleBasedIpFilter ruleBasedIpFilter = new RuleBasedIpFilter(new IpFilterRuleHandler());

    @Override
    public MessageType getMessageType() {
        return MessageType.TYPE_REGISTER;
    }

    @Override
    public void execute(ChannelHandlerContext context, Message message) throws Exception {

        Meta meta = message.getMeta();
        // 存在client_key为设置为null的情况 在断线时的标识判断
        context.channel().attr(Constants.Server.CLIENT_KEY).set(String.valueOf(meta.getClientKey()));

        if (!Config.getServerConfig().authorize(meta.getClientKey())) {
            logger.info("授权失败,客户端使用的秘钥:{}", meta.getClientKey());
            Message res = new Message();
            res.setType(MessageType.TYPE_AUTH);
            res.setMeta(meta.setConnection(false).setRemoteResult(Collections.singletonList("秘钥授权失败")));
            context.writeAndFlush(res).addListener(ChannelFutureListener.CLOSE);
            return;
        }
        this.fillRemoteResult(context, meta);
        Message res = new Message();
        res.setType(MessageType.TYPE_AUTH);
        res.setMeta(meta);
        ChannelFuture channelFuture = context.writeAndFlush(res);
        if (!meta.isConnection()) {
            channelFuture.addListener(ChannelFutureListener.CLOSE);
            return;
        }
        logger.info("授权注册成功,客户端使用的秘钥:{}", meta.getClientKey());
        ServerChannelCache.getServerChannelMap().put(context.channel().id().asLongText(), context.channel());
        context.channel().attr(Constants.CHANNELS).set(new ConcurrentHashMap<>(1024));
    }

    private void fillRemoteResult(ChannelHandlerContext context, Meta meta) throws Exception {
        meta.setConnection(true).setRemoteResult(new CopyOnWriteArrayList<>());
        List<RemoteConfig> remoteConfigList = Optional.ofNullable(meta.getRemoteConfigList()).orElse(Collections.emptyList());
        if (remoteConfigList.isEmpty()) {
            return;
        }
        CountDownLatch countDownLatch = new CountDownLatch(remoteConfigList.size());
        for (RemoteConfig remoteConfig : remoteConfigList) {
            ProxyType proxyType = Optional.ofNullable(remoteConfig.getProxyType()).orElse(ProxyType.TCP);
            switch (proxyType) {
                case TCP:
                    // lazy
                    if (Objects.isNull(context.channel().attr(Constants.Server.REMOTE_BOSS_GROUP).get()) && Objects.isNull(context.channel().attr(Constants.Server.REMOTE_WORKER_GROUP).get())) {
                        context.channel().attr(Constants.Server.REMOTE_BOSS_GROUP).set(new NioEventLoopGroup());
                        context.channel().attr(Constants.Server.REMOTE_WORKER_GROUP).set(new NioEventLoopGroup());
                    }
                    registerTcp(context, meta, remoteConfig, countDownLatch);
                    break;
                case HTTP:
                    if (Objects.isNull(context.channel().attr(Constants.Server.DOMAIN).get())) {
                        context.channel().attr(Constants.Server.DOMAIN).set(new CopyOnWriteArrayList<>());
                    }
                    registerHttp(context, meta, remoteConfig, countDownLatch);
                    break;
                default:
            }
        }
        countDownLatch.await();
    }

    private void registerHttp(ChannelHandlerContext context, Meta meta, RemoteConfig remoteConfig, CountDownLatch countDownLatch) {
        if (Config.getServerConfig().getHttpPort() == 0 && Config.getServerConfig().getHttpsPort() == 0) {
            meta.setConnection(false).addRemoteResult("服务端未开启HTTP穿透功能");
            countDownLatch.countDown();
            return;
        }
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
            context.channel().attr(Constants.Server.DOMAIN).get().add(domain);
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
        remoteBootstrap.group(context.channel().attr(Constants.Server.REMOTE_BOSS_GROUP).get(), context.channel().attr(Constants.Server.REMOTE_WORKER_GROUP).get()).channel(NioServerSocketChannel.class).childOption(ChannelOption.SO_KEEPALIVE, true).childHandler(new ChannelInitializer<SocketChannel>() {
            @Override
            public void initChannel(SocketChannel channel) throws Exception {
                if (Config.getServerConfig().ipFilter()) {
                    channel.pipeline().addLast(ruleBasedIpFilter);
                }
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
}
