package cn.promptness.rpt.server.handler;

import cn.promptness.rpt.base.config.ClientConfig;
import cn.promptness.rpt.base.config.Config;
import cn.promptness.rpt.base.config.RemoteConfig;
import cn.promptness.rpt.base.protocol.Message;
import cn.promptness.rpt.base.protocol.MessageType;
import cn.promptness.rpt.base.protocol.ProxyType;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.bytes.ByteArrayDecoder;
import io.netty.handler.codec.bytes.ByteArrayEncoder;
import io.netty.handler.traffic.GlobalTrafficShapingHandler;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

    /**
     * domain --> serverChannel 全局
     */
    private final Map<String, Channel> serverChannelMap;

    /**
     * requestChannelId --> httpChannel 全局
     */
    private final Map<String, Channel> httpChannelMap;

    private final List<String> domainList = new ArrayList<>();

    /**
     * 全局服务器读限速器
     */
    private final GlobalTrafficShapingHandler globalTrafficShapingHandler;
    private String clientKey;

    public ServerHandler(GlobalTrafficShapingHandler globalTrafficShapingHandler, Map<String, Channel> serverChannelMap, Map<String, Channel> httpChannelMap) {
        this.globalTrafficShapingHandler = globalTrafficShapingHandler;
        this.serverChannelMap = serverChannelMap;
        this.httpChannelMap = httpChannelMap;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.channel().close();
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
            Channel remove = serverChannelMap.remove(domain);
            if (remove != null) {
                remove.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
            }
        }
        domainList.clear();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext context, Message message) throws Exception {
        MessageType type = message.getType();
        switch (type) {
            case TYPE_REGISTER:
                register(context, message);
                break;
            case TYPE_DATA:
                transfer(context, message);
                break;
            case TYPE_CONNECTED:
                connected(message);
                break;
            case TYPE_DISCONNECTED:
                disconnected(message);
                break;
            case TYPE_KEEPALIVE:
            default:
        }
    }

    private void connected(Message message) {
        ClientConfig clientConfig = message.getClientConfig();
        RemoteConfig remoteConfig = clientConfig.getConfig().get(0);
        ProxyType proxyType = remoteConfig.getProxyType();
        if (proxyType == null) {
            return;
        }
        String channelId = message.getClientConfig().getChannelId();

        switch (proxyType) {
            case TCP:
                Channel remoteChannel = remoteChannelMap.get(channelId);
                if (remoteChannel != null) {
                    remoteChannel.config().setAutoRead(true);
                }
                break;
            case HTTP:
                Channel httpChannel = httpChannelMap.get(channelId);
                if (httpChannel != null) {
                    httpChannel.config().setAutoRead(true);
                    httpChannel.pipeline().fireUserEventTriggered(ProxyType.HTTP);
                }
                break;
            default:
        }
    }

    private void disconnected(Message message) {
        ClientConfig clientConfig = message.getClientConfig();
        RemoteConfig remoteConfig = message.getClientConfig().getConfig().get(0);
        ProxyType proxyType = remoteConfig.getProxyType();
        if (proxyType == null) {
            return;
        }
        switch (proxyType) {
            case TCP:
                Channel remoteChannel = remoteChannelMap.remove(clientConfig.getChannelId());
                if (remoteChannel == null) {
                    return;
                }
                // 数据发送完成后再关闭连 解决http1.0数据传输问题
                remoteChannel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
                break;
            case HTTP:
                Channel httpChannel = httpChannelMap.remove(clientConfig.getChannelId());
                if (httpChannel == null) {
                    return;
                }
                httpChannel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
                break;
            default:
        }
    }


    private void transfer(ChannelHandlerContext context, Message message) {
        RemoteConfig remoteConfig = message.getClientConfig().getConfig().get(0);
        ProxyType proxyType = remoteConfig.getProxyType();
        if (proxyType == null) {
            return;
        }
        switch (proxyType) {
            case TCP:
                transferTcp(message);
                break;
            case HTTP:
                String channelId = message.getClientConfig().getChannelId();
                Channel channel = httpChannelMap.get(channelId);
                if (channel != null) {
                    ByteBuf buf = context.alloc().buffer(message.getData().length);
                    buf.writeBytes(message.getData());
                    ReferenceCountUtil.release(message.getData());
                    channel.writeAndFlush(buf);
                }
                break;
            default:
        }
    }

    private void transferTcp(Message message) {
        ClientConfig clientConfig = message.getClientConfig();
        Channel channel = remoteChannelMap.get(clientConfig.getChannelId());
        if (channel == null) {
            return;
        }
        channel.writeAndFlush(message.getData());
    }

    private void register(ChannelHandlerContext context, Message message) {
        ClientConfig clientConfig = message.getClientConfig();
        clientKey = clientConfig.getClientKey();
        if (!Config.getServerConfig().getClientKey().contains(clientKey)) {
            logger.info("授权连接失败,clientKey:{}", clientKey);
            Message res = new Message();
            res.setType(MessageType.TYPE_AUTH);
            res.setClientConfig(clientConfig.setConnection(false));
            context.writeAndFlush(res);
            return;
        }
        List<String> remoteResult = new ArrayList<>();
        for (RemoteConfig remoteConfig : clientConfig.getConfig()) {
            ProxyType proxyType = remoteConfig.getProxyType();
            if (proxyType == null) {
                Message res = new Message();
                res.setType(MessageType.TYPE_AUTH);
                res.setClientConfig(clientConfig.setConnection(false));
                context.writeAndFlush(res);
                return;
            }
            switch (proxyType) {
                case TCP:
                    registerTcp(context, remoteResult, remoteConfig);
                    break;
                case HTTP:
                    registerHttp(context, remoteResult, remoteConfig);
                    break;
                default:
            }
        }
        Message res = new Message();
        res.setType(MessageType.TYPE_AUTH);
        res.setClientConfig(clientConfig.setConnection(true).setRemoteResult(remoteResult));
        context.writeAndFlush(res);
    }


    private void registerHttp(ChannelHandlerContext context, List<String> remoteResult, RemoteConfig remoteConfig) {
        serverChannelMap.compute(remoteConfig.getDomain(), (httpDomain, channel) -> {
            if (channel != null) {
                remoteResult.add(String.format("服务端绑定域名重复%s", httpDomain));
                return channel;
            }
            domainList.add(httpDomain);
            remoteResult.add(String.format("服务端绑定域名成功%s", httpDomain));
            return context.channel();
        });
    }

    private void registerTcp(ChannelHandlerContext context, List<String> remoteResult, RemoteConfig remoteConfig) {
        ServerBootstrap remoteBootstrap = new ServerBootstrap();
        remoteBootstrap.group(remoteBossGroup, remoteWorkerGroup).channel(NioServerSocketChannel.class).childOption(ChannelOption.SO_KEEPALIVE, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel channel) throws Exception {
                        channel.pipeline().addLast(globalTrafficShapingHandler);
                        channel.pipeline().addLast(new ByteArrayDecoder());
                        channel.pipeline().addLast(new ByteArrayEncoder());
                        channel.pipeline().addLast(new RemoteHandler(context.channel(), remoteConfig));
                        remoteChannelMap.put(channel.id().asLongText(), channel);
                    }
                });
        try {
            logger.info("服务端开始建立本地端口绑定[{}]", remoteConfig.getRemotePort());
            remoteBootstrap.bind(Config.getServerConfig().getServerIp(), remoteConfig.getRemotePort()).get();
            remoteResult.add(String.format("服务端绑定端口[%s]成功", remoteConfig.getRemotePort()));
        } catch (Exception exception) {
            logger.info("服务端失败建立本地端口绑定[{}], {}", remoteConfig.getRemotePort(), exception.getCause().getMessage());
            remoteResult.add(String.format("服务端绑定端口[%s]失败,原因:%s", remoteConfig.getRemotePort(), exception.getCause().getMessage()));
        }
    }
}
