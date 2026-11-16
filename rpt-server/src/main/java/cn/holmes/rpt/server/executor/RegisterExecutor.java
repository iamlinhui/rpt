package cn.holmes.rpt.server.executor;

import cn.holmes.rpt.base.config.ConfigHolder;
import cn.holmes.rpt.base.config.ProxyType;
import cn.holmes.rpt.base.config.RemoteConfig;
import cn.holmes.rpt.base.config.ServerToken;
import cn.holmes.rpt.base.executor.MessageExecutor;
import cn.holmes.rpt.base.protocol.Message;
import cn.holmes.rpt.base.protocol.MessageType;
import cn.holmes.rpt.base.protocol.Meta;
import cn.holmes.rpt.base.utils.Attributes.Server;
import cn.holmes.rpt.base.utils.StringUtils;
import cn.holmes.rpt.server.cache.ServerChannelCache;
import cn.holmes.rpt.server.cache.TrafficStatsCache;
import cn.holmes.rpt.server.cache.UdpSessionQuota;
import cn.holmes.rpt.server.handler.Socks5Handler;
import cn.holmes.rpt.server.handler.TcpHandler;
import cn.holmes.rpt.server.handler.UdpHandler;
import cn.holmes.rpt.server.utils.IpCountryFilter;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.ipfilter.RuleBasedIpFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

public class RegisterExecutor implements MessageExecutor {

    private final Logger logger = LoggerFactory.getLogger(RegisterExecutor.class);

    private final RuleBasedIpFilter ruleBasedIpFilter = new RuleBasedIpFilter(IpCountryFilter.getInstance());

    private final EventLoopGroup remoteBossGroup = new NioEventLoopGroup();

    private final EventLoopGroup remoteWorkerGroup = new NioEventLoopGroup();

    private final UdpSessionQuota udpSessionQuota = new UdpSessionQuota();

    @Override
    public MessageType getMessageType() {
        return MessageType.TYPE_REGISTER;
    }

    @Override
    public void execute(ChannelHandlerContext context, Message message) throws Exception {

        Meta meta = message.getMeta();
        context.channel().attr(Server.CLIENT_KEY).set(String.valueOf(meta.getClientKey()));

        if (!ConfigHolder.getServerConfig().authorize(meta.getClientKey())) {
            logger.info("[register] authorization failed, clientKey: {}", meta.getClientKey());
            Message res = new Message();
            res.setType(MessageType.TYPE_AUTH);
            res.setMeta(meta.setConnection(false).setRemoteResult(Collections.singletonList("Invalid client key")));
            context.writeAndFlush(res).addListener(ChannelFutureListener.CLOSE);
            return;
        }
        context.channel().attr(Server.CHANNELS).setIfAbsent(new ConcurrentHashMap<>(1024));
        fillRemoteResult(context.channel(), meta);
    }

    private void fillRemoteResult(Channel serverChannel, Meta meta) {
        meta.setConnection(true).setServerId(serverChannel.id().asLongText()).setRemoteResult(new CopyOnWriteArrayList<>());
        List<RemoteConfig> remoteConfigList = Optional.ofNullable(meta.getRemoteConfigList()).orElse(Collections.emptyList());
        if (remoteConfigList.isEmpty()) {
            completeRegistration(serverChannel, meta);
            return;
        }
        RegistrationTask registration = new RegistrationTask(serverChannel, meta, remoteConfigList.size());
        for (RemoteConfig remoteConfig : remoteConfigList) {
            ProxyType proxyType = Optional.ofNullable(remoteConfig.getProxyType()).orElse(ProxyType.TCP);
            switch (proxyType) {
                case TCP:
                    serverChannel.attr(Server.TCP_PORT_CHANNEL_FUTURE).setIfAbsent(new ConcurrentHashMap<>());
                    registerTcp(serverChannel, meta, remoteConfig, registration);
                    break;
                case HTTP:
                    serverChannel.attr(Server.DOMAIN).setIfAbsent(new CopyOnWriteArrayList<>());
                    registerHttp(serverChannel, meta, remoteConfig, registration);
                    break;
                case UDP:
                    serverChannel.attr(Server.UDP_PORT_CHANNEL_FUTURE).setIfAbsent(new ConcurrentHashMap<>());
                    registerUdp(serverChannel, meta, remoteConfig, registration);
                    break;
                case SOCKS5:
                    serverChannel.attr(Server.TCP_PORT_CHANNEL_FUTURE).setIfAbsent(new ConcurrentHashMap<>());
                    registerSocks5(serverChannel, meta, remoteConfig, registration);
                    break;
                default:
                    meta.setConnection(false).addRemoteResult("Unsupported proxy type");
                    registration.completeOne();
            }
        }
    }

    private void completeRegistration(Channel serverChannel, Meta meta) {
        if (!serverChannel.isActive()) {
            return;
        }
        Message res = new Message();
        res.setType(MessageType.TYPE_AUTH);
        res.setMeta(meta);
        ChannelFuture channelFuture = serverChannel.writeAndFlush(res);
        if (!meta.isConnection()) {
            channelFuture.addListener(ChannelFutureListener.CLOSE);
            return;
        }
        logger.info("[register] authorization succeeded, clientKey: {}", meta.getClientKey());
        ServerChannelCache.getServerChannelMap().put(serverChannel.id().asLongText(), serverChannel);
        serverChannel.attr(Server.SERVER_ID).set(serverChannel.id().asLongText());
        serverChannel.attr(Server.CHANNELS).setIfAbsent(new ConcurrentHashMap<>(1024));
        serverChannel.attr(Server.CONNECT_TIME).set(System.currentTimeMillis());
        TrafficStatsCache.incrementConnections();
    }

    private void registerHttp(Channel serverChannel, Meta meta, RemoteConfig remoteConfig, RegistrationTask registration) {
        if (ConfigHolder.getServerConfig().getHttpPort() == 0) {
            meta.setConnection(false).addRemoteResult("HTTP tunneling is not enabled on the server");
            registration.completeOne();
            return;
        }
        if (!StringUtils.hasText(remoteConfig.getDomain())) {
            meta.setConnection(false).addRemoteResult(String.format("Invalid domain to bind on server [%s]", remoteConfig.getDomain()));
            registration.completeOne();
            return;
        }
        logger.info("[register] binding domain [{}]", remoteConfig.getDomain());
        // 域名不区分大小写
        ServerChannelCache.getServerDomainChannelMap().compute(remoteConfig.getDomain().toLowerCase(Locale.ROOT), (domain, channel) -> {
            if (channel != null) {
                meta.setConnection(false).addRemoteResult(String.format("Domain [%s] is already bound on server", domain));
                return channel;
            }
            serverChannel.attr(Server.DOMAIN).get().add(domain);
            if (StringUtils.hasText(remoteConfig.getToken())) {
                ServerChannelCache.getServerDomainToken().put(domain, remoteConfig.getToken());
            }
            meta.addRemoteResult(String.format("Domain [%s] bound on server", domain));
            return serverChannel;
        });
        registration.completeOne();
    }

    private void registerTcp(Channel serverChannel, Meta meta, RemoteConfig remoteConfig, RegistrationTask registration) {
        bindTcpPort(serverChannel, meta, remoteConfig, registration, ProxyType.TCP, () -> new TcpHandler(serverChannel, remoteConfig));
    }

    private void registerSocks5(Channel serverChannel, Meta meta, RemoteConfig remoteConfig, RegistrationTask registration) {
        bindTcpPort(serverChannel, meta, remoteConfig, registration, ProxyType.SOCKS5, () -> new Socks5Handler(serverChannel, remoteConfig));
    }

    private void bindTcpPort(Channel serverChannel, Meta meta, RemoteConfig remoteConfig, RegistrationTask registration, ProxyType proxyType, Supplier<ChannelHandler> tailHandlerFactory) {
        if (remoteConfig.getRemotePort() == 0 || remoteConfig.getRemotePort() == ConfigHolder.getServerConfig().getServerPort() || remoteConfig.getRemotePort() == ConfigHolder.getServerConfig().getHttpPort() || remoteConfig.getRemotePort() == ConfigHolder.getServerConfig().getDashboardPort()) {
            meta.setConnection(false).addRemoteResult(String.format("Invalid %s port to bind [%s]", proxyType, remoteConfig.getRemotePort()));
            registration.completeOne();
            return;
        }
        ServerToken serverToken = ConfigHolder.getServerConfig().getServerToken(meta.getClientKey());
        if (!serverToken.authorize(remoteConfig.getRemotePort())) {
            meta.setConnection(false).addRemoteResult(String.format("Out-of-range %s port to bind [%s]", proxyType, remoteConfig.getRemotePort()));
            registration.completeOne();
            return;
        }
        ServerBootstrap remoteBootstrap = new ServerBootstrap();
        // 允许端口复用，解决快速重连时端口未释放的问题
        remoteBootstrap.group(remoteBossGroup, remoteWorkerGroup).channel(NioServerSocketChannel.class).option(ChannelOption.SO_REUSEADDR, true).childOption(ChannelOption.SO_KEEPALIVE, true).childHandler(new ChannelInitializer<SocketChannel>() {
            @Override
            public void initChannel(SocketChannel channel) throws Exception {
                if (ConfigHolder.getServerConfig().ipFilterEnabled()) {
                    channel.pipeline().addLast(ruleBasedIpFilter);
                }
                channel.pipeline().addLast(tailHandlerFactory.get());
            }
        });

        logger.info("[register] binding {} port [{}]", proxyType, remoteConfig.getRemotePort());
        Map<Integer, ChannelFuture> portMap = serverChannel.attr(Server.TCP_PORT_CHANNEL_FUTURE).get();
        remoteBootstrap.bind(ConfigHolder.getServerConfig().getServerIp(), remoteConfig.getRemotePort()).addListener((ChannelFutureListener) channelFuture ->
            registration.completeBind(channelFuture, remoteConfig, proxyType, portMap)
        );
    }

    private void registerUdp(Channel serverChannel, Meta meta, RemoteConfig remoteConfig, RegistrationTask registration) {
        if (remoteConfig.getRemotePort() == 0 || remoteConfig.getRemotePort() == ConfigHolder.getServerConfig().getServerPort() || remoteConfig.getRemotePort() == ConfigHolder.getServerConfig().getHttpPort() || remoteConfig.getRemotePort() == ConfigHolder.getServerConfig().getDashboardPort()) {
            meta.setConnection(false).addRemoteResult(String.format("Invalid UDP port to bind [%s]", remoteConfig.getRemotePort()));
            registration.completeOne();
            return;
        }
        ServerToken serverToken = ConfigHolder.getServerConfig().getServerToken(meta.getClientKey());
        if (!serverToken.authorize(remoteConfig.getRemotePort())) {
            meta.setConnection(false).addRemoteResult(String.format("Out-of-range UDP port to bind [%s]", remoteConfig.getRemotePort()));
            registration.completeOne();
            return;
        }
        Bootstrap udpBootstrap = new Bootstrap();
        udpBootstrap.group(remoteWorkerGroup).channel(NioDatagramChannel.class)
            // 允许端口复用，避免快速重启时绑定失败（注意保证单实例）
            .option(ChannelOption.SO_REUSEADDR, true)
            // 数据报读缓冲固定 64KB：adaptive 在满载下翻倍/收缩会导致大包间歇性截断
            .option(ChannelOption.RCVBUF_ALLOCATOR, new FixedRecvByteBufAllocator(65535))
            .option(ChannelOption.SO_RCVBUF, 8 * 1024 * 1024)
            .option(ChannelOption.SO_SNDBUF, 8 * 1024 * 1024)
            .handler(new ChannelInitializer<DatagramChannel>() {
                @Override
                protected void initChannel(DatagramChannel channel) throws Exception {
                    channel.pipeline().addLast(new UdpHandler(serverChannel, remoteConfig, udpSessionQuota));
                }
            });

        logger.info("[register] binding UDP port [{}]", remoteConfig.getRemotePort());
        Map<Integer, ChannelFuture> portMap = serverChannel.attr(Server.UDP_PORT_CHANNEL_FUTURE).get();
        udpBootstrap.bind(ConfigHolder.getServerConfig().getServerIp(), remoteConfig.getRemotePort()).addListener((ChannelFutureListener) channelFuture ->
            registration.completeBind(channelFuture, remoteConfig, ProxyType.UDP, portMap)
        );
    }

    /**
     * 单次注册的状态只在控制通道eventLoop上访问，bind回调统一切回该线程，避免超时与迟到回调竞争。
     */
    private class RegistrationTask {

        private final Channel serverChannel;

        private final Meta meta;

        private final ScheduledFuture<?> timeoutFuture;

        private int remaining;

        private boolean finished;

        private RegistrationTask(Channel serverChannel, Meta meta, int remaining) {
            this.serverChannel = serverChannel;
            this.meta = meta;
            this.remaining = remaining;
            this.timeoutFuture = serverChannel.eventLoop().schedule(this::timeout, 3, TimeUnit.SECONDS);
        }

        private void completeOne() {
            if (finished) {
                return;
            }
            if (--remaining == 0) {
                finish();
            }
        }

        private void completeBind(ChannelFuture channelFuture, RemoteConfig remoteConfig, ProxyType proxyType, Map<Integer, ChannelFuture> portMap) {
            Runnable completion = () -> {
                if (finished || !serverChannel.isActive()) {
                    channelFuture.channel().close();
                    return;
                }
                if (channelFuture.isSuccess()) {
                    portMap.put(remoteConfig.getRemotePort(), channelFuture);
                    meta.addRemoteResult(String.format("%s port [%s] bound on server", proxyType, remoteConfig.getRemotePort()));
                } else {
                    logger.info("[register] failed to bind {} port [{}]: {}", proxyType, remoteConfig.getRemotePort(), channelFuture.cause().getMessage());
                    meta.setConnection(false).addRemoteResult(String.format("Failed to bind %s port [%s] on server: %s", proxyType, remoteConfig.getRemotePort(), channelFuture.cause().getMessage()));
                }
                completeOne();
            };
            if (serverChannel.eventLoop().inEventLoop()) {
                completion.run();
                return;
            }
            try {
                serverChannel.eventLoop().execute(completion);
            } catch (RejectedExecutionException e) {
                channelFuture.channel().close();
            }
        }

        private void timeout() {
            if (finished) {
                return;
            }
            logger.info("[register] timed out waiting for registration, clientKey: {}", meta.getClientKey());
            meta.setConnection(false).addRemoteResult("Server timed out processing the registration request");
            finish();
        }

        private void finish() {
            finished = true;
            timeoutFuture.cancel(false);
            completeRegistration(serverChannel, meta);
        }
    }
}
