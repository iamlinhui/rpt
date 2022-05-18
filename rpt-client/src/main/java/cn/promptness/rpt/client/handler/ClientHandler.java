package cn.promptness.rpt.client.handler;

import cn.promptness.rpt.base.coder.ByteArrayCodec;
import cn.promptness.rpt.base.config.ProxyType;
import cn.promptness.rpt.base.config.RemoteConfig;
import cn.promptness.rpt.base.handler.ByteIdleCheckHandler;
import cn.promptness.rpt.base.protocol.Message;
import cn.promptness.rpt.base.protocol.MessageType;
import cn.promptness.rpt.base.protocol.Meta;
import cn.promptness.rpt.base.utils.Config;
import cn.promptness.rpt.base.utils.Constants;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.util.internal.EmptyArrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 服务器连接处理器
 */
public class ClientHandler extends SimpleChannelInboundHandler<Message> {

    private static final Logger logger = LoggerFactory.getLogger(ClientHandler.class);
    private final EventLoopGroup localGroup = new NioEventLoopGroup();
    private final AtomicBoolean connect;

    public ClientHandler(AtomicBoolean connect) {
        this.connect = connect;
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        for (Channel channel : ctx.channel().attr(Constants.CHANNELS).get().values()) {
            channel.config().setAutoRead(ctx.channel().isWritable());
        }
        super.channelWritabilityChanged(ctx);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        //连接建立成功，发送注册请求
        connect.set(true);
        Message message = new Message();
        message.setType(MessageType.TYPE_REGISTER);
        message.setMeta(new Meta(Config.getClientConfig().getClientKey(), Config.getClientConfig().getConfig()));
        ctx.writeAndFlush(message);
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
                disconnected(context, message);
                break;
            case TYPE_DATA:
                transfer(context, message);
                break;
            case TYPE_KEEPALIVE:
            default:
        }
    }

    private void transfer(ChannelHandlerContext context, Message message) {
        if (message.getData() == null) {
            return;
        }
        String channelId = message.getMeta().getChannelId();
        Channel tcpChannel = context.channel().attr(Constants.CHANNELS).get().get(channelId);
        if (tcpChannel != null) {
            tcpChannel.writeAndFlush(message.getData());
        }
    }

    private void disconnected(ChannelHandlerContext context, Message message) {
        String channelId = message.getMeta().getChannelId();
        Channel tcpChannel = context.channel().attr(Constants.CHANNELS).get().remove(channelId);
        if (tcpChannel != null) {
            tcpChannel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }

    private void connected(ChannelHandlerContext context, Message message) {
        Meta meta = message.getMeta();
        RemoteConfig remoteConfig = meta.getRemoteConfig();
        if (remoteConfig == null) {
            return;
        }
        ProxyType proxyType = remoteConfig.getProxyType();
        if (Objects.equals(ProxyType.HTTP, proxyType)) {
            String domain = remoteConfig.getDomain();
            // 补全配置信息
            RemoteConfig httpConfig = Config.getClientConfig().getHttpConfig(domain);
            if (httpConfig == null) {
                return;
            }
            meta.setRemoteConfigList(Collections.singletonList(httpConfig));
        }
        connectedTcp(context, meta);
    }

    private void connectedTcp(ChannelHandlerContext context, Meta meta) {
        RemoteConfig remoteConfig = meta.getRemoteConfig();
        Bootstrap localBootstrap = new Bootstrap();
        localBootstrap.group(localGroup).channel(NioSocketChannel.class).option(ChannelOption.SO_KEEPALIVE, true).handler(new ChannelInitializer<SocketChannel>() {
            @Override
            public void initChannel(SocketChannel channel) throws Exception {
                channel.pipeline().addLast(new ByteArrayCodec());
                channel.pipeline().addLast(new ChunkedWriteHandler());
                channel.pipeline().addLast(new ByteIdleCheckHandler(0, 30, 0));
                channel.pipeline().addLast(new LocalHandler(context.channel(), meta));
            }
        });
        localBootstrap.connect(remoteConfig.getLocalIp(), remoteConfig.getLocalPort()).addListener((ChannelFutureListener) future -> {
            if (!future.isSuccess()) {
                Message message = new Message();
                message.setType(MessageType.TYPE_DISCONNECTED);
                message.setData(EmptyArrays.EMPTY_BYTES);
                message.setMeta(meta);
                context.channel().writeAndFlush(message);
            }
        });
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        logger.info("客户端-服务端连接中断,{}:{}", Config.getClientConfig().getServerIp(), Config.getClientConfig().getServerPort());
        for (Channel channel : Optional.ofNullable(ctx.channel().attr(Constants.CHANNELS).getAndSet(null)).orElse(Collections.emptyMap()).values()) {
            channel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
        localGroup.shutdownGracefully();
        connect.set(false);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.channel().close();
    }
}
