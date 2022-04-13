package cn.promptness.rpt.client.handler;

import cn.promptness.rpt.base.config.ClientConfig;
import cn.promptness.rpt.base.config.Config;
import cn.promptness.rpt.base.config.RemoteConfig;
import cn.promptness.rpt.base.protocol.Message;
import cn.promptness.rpt.base.protocol.MessageType;
import cn.promptness.rpt.base.protocol.ProxyType;
import cn.promptness.rpt.client.handler.cache.ClientChannelCache;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.bytes.ByteArrayDecoder;
import io.netty.handler.codec.bytes.ByteArrayEncoder;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseDecoder;
import io.netty.util.internal.EmptyArrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

/**
 * 服务器连接处理器
 */
public class ClientHandler extends SimpleChannelInboundHandler<Message> {

    private static final Logger logger = LoggerFactory.getLogger(ClientHandler.class);
    private final EventLoopGroup localGroup = new NioEventLoopGroup();

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        //连接建立成功，发送注册请求
        Message message = new Message();
        message.setType(MessageType.TYPE_REGISTER);
        message.setClientConfig(Config.getClientConfig());
        ctx.writeAndFlush(message);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext context, Message message) throws Exception {
        switch (message.getType()) {
            case TYPE_AUTH:
                boolean connection = message.getClientConfig().isConnection();
                ClientChannelCache.setConnect(connection);
                if (connection) {
                    logger.info("授权连接成功,clientKey:{}", message.getClientConfig().getClientKey());
                    for (String remoteResult : message.getClientConfig().getRemoteResult()) {
                        logger.info(remoteResult);
                    }
                } else {
                    logger.info("授权连接失败,clientKey:{}", message.getClientConfig().getClientKey());
                }
                break;
            case TYPE_CONNECTED:
                // 外部请求进入，开始与内网建立连接
                connected(context, message);
                break;
            case TYPE_DISCONNECTED:
                disconnected(message);
                break;
            case TYPE_DATA:
                transfer(context, message);
                break;
            case TYPE_KEEPALIVE:
            default:
        }
    }

    private void transfer(ChannelHandlerContext context, Message message) {
        if (message.getData() == null || message.getData().length <= 0) {
            return;
        }
        ClientConfig clientConfig = message.getClientConfig();

        ProxyType proxyType = clientConfig.getConfig().get(0).getProxyType();
        if (proxyType == null) {
            return;
        }
        switch (proxyType) {
            // 将数据转发到对应内网服务器
            case TCP:
                Channel tcpChannel = ClientChannelCache.getLocalTcpChannelMap().get(clientConfig.getChannelId());
                if (tcpChannel != null) {
                    tcpChannel.writeAndFlush(message.getData());
                }
                break;
            case HTTP:
                // 向下传递
                byte[] data = message.getData();
                ByteBuf buffer = context.alloc().buffer(data.length);
                buffer.writeBytes(data);
                context.fireChannelRead(buffer);
                break;
            default:
        }
    }

    private void disconnected(Message message) {
        ClientConfig clientConfig = message.getClientConfig();
        ProxyType proxyType = clientConfig.getConfig().get(0).getProxyType();
        if (proxyType == null) {
            return;
        }
        switch (proxyType) {
            // 将数据转发到对应内网服务器
            case TCP:
                Channel tcpChannel = ClientChannelCache.getLocalTcpChannelMap().remove(clientConfig.getChannelId());
                if (tcpChannel != null) {
                    tcpChannel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
                }
                break;
            case HTTP:
                Channel httpChannel = ClientChannelCache.getLocalHttpChannelMap().remove(clientConfig.getChannelId());
                if (httpChannel != null) {
                    httpChannel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
                }
                break;
            default:
        }
    }

    private void connected(ChannelHandlerContext context, Message message) {

        ClientConfig clientConfig = message.getClientConfig();
        List<RemoteConfig> remoteConfigList = clientConfig.getConfig();
        if (remoteConfigList == null || remoteConfigList.isEmpty()) {
            return;
        }
        ProxyType proxyType = remoteConfigList.get(0).getProxyType();
        if (proxyType == null) {
            return;
        }
        // 外部请求进入，开始与内网建立连接
        switch (proxyType) {
            case TCP:
                connectedTcp(context, clientConfig);
                break;
            case HTTP:
                String domain = clientConfig.getConfig().get(0).getDomain();
                RemoteConfig httpConfig = Config.getClientConfig().getHttpConfig(domain);
                if (httpConfig == null) {
                    break;
                }
                connectedHttp(context, httpConfig, clientConfig.getChannelId());
                break;
            default:
        }
    }


    private void connectedTcp(ChannelHandlerContext context, ClientConfig clientConfig) {
        RemoteConfig remoteConfig = clientConfig.getConfig().get(0);
        Bootstrap localBootstrap = new Bootstrap();
        localBootstrap.group(localGroup).channel(NioSocketChannel.class).option(ChannelOption.SO_KEEPALIVE, true).handler(new ChannelInitializer<SocketChannel>() {
            @Override
            public void initChannel(SocketChannel channel) throws Exception {
                channel.pipeline().addLast(new ByteArrayDecoder());
                channel.pipeline().addLast(new ByteArrayEncoder());
                channel.pipeline().addLast(new LocalHandler(context.channel(), clientConfig));
            }
        });
        localBootstrap.connect(remoteConfig.getLocalIp(), remoteConfig.getLocalPort()).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                ClientChannelCache.getLocalTcpChannelMap().put(clientConfig.getChannelId(), future.channel());
            } else {
                Message message = new Message();
                message.setType(MessageType.TYPE_DISCONNECTED);
                message.setData(EmptyArrays.EMPTY_BYTES);
                message.setClientConfig(clientConfig);
                context.channel().writeAndFlush(message);
            }
        });
    }

    private void connectedHttp(ChannelHandlerContext context, RemoteConfig httpConfig, String channelId) {
        ClientConfig clientConfig = new ClientConfig();
        clientConfig.setConfig(Collections.singletonList(httpConfig));
        clientConfig.setChannelId(channelId);
        Bootstrap localBootstrap = new Bootstrap();
        localBootstrap.group(localGroup).channel(NioSocketChannel.class).option(ChannelOption.SO_KEEPALIVE, true).handler(new ChannelInitializer<SocketChannel>() {
            @Override
            public void initChannel(SocketChannel channel) throws Exception {
                channel.pipeline().addLast(new HttpResponseDecoder());
                channel.pipeline().addLast(new HttpObjectAggregator(8 * 1024 * 1024));
                channel.pipeline().addLast(new ReceiveHandler(context.channel(), clientConfig));
            }
        });
        localBootstrap.connect(httpConfig.getLocalIp(), httpConfig.getLocalPort()).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                ClientChannelCache.getLocalHttpChannelMap().put(clientConfig.getChannelId(), future.channel());
            } else {
                Message message = new Message();
                message.setType(MessageType.TYPE_DISCONNECTED);
                message.setClientConfig(clientConfig);
                message.setData(EmptyArrays.EMPTY_BYTES);
                context.channel().writeAndFlush(message);
            }
        });
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        logger.info("客户端-服务端连接中断,{}:{}", Config.getClientConfig().getServerIp(), Config.getClientConfig().getServerPort());
        for (Channel channel : ClientChannelCache.getLocalTcpChannelMap().values()) {
            channel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
        ClientChannelCache.getLocalTcpChannelMap().clear();
        for (Channel channel : ClientChannelCache.getLocalHttpChannelMap().values()) {
            channel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
        ClientChannelCache.getLocalHttpChannelMap().clear();
        localGroup.shutdownGracefully();
        ClientChannelCache.setConnect(false);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.channel().close();
    }
}
