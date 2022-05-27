package cn.promptness.rpt.client;

import cn.promptness.rpt.base.coder.GzipCodec;
import cn.promptness.rpt.base.coder.MessageCodec;
import cn.promptness.rpt.base.config.ClientConfig;
import cn.promptness.rpt.base.handler.IdleCheckHandler;
import cn.promptness.rpt.base.protocol.Message;
import cn.promptness.rpt.base.protocol.MessageType;
import cn.promptness.rpt.base.protocol.Meta;
import cn.promptness.rpt.base.utils.Application;
import cn.promptness.rpt.base.utils.Config;
import cn.promptness.rpt.base.utils.Constants;
import cn.promptness.rpt.client.handler.ClientHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.stream.ChunkedWriteHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

public class ClientApplication implements Application<Boolean> {

    private static final Logger logger = LoggerFactory.getLogger(ClientApplication.class);
    private final Bootstrap bootstrap = new Bootstrap();

    public static void main(String[] args) throws Exception {
        new ClientApplication().buildBootstrap(new NioEventLoopGroup()).start(0);
    }

    public ClientApplication buildBootstrap(NioEventLoopGroup clientWorkerGroup) throws IOException {
        SslContext sslContext = buildSslContext();
        bootstrap.group(clientWorkerGroup).channel(NioSocketChannel.class).option(ChannelOption.SO_KEEPALIVE, true).handler(new ChannelInitializer<SocketChannel>() {
            @Override
            public void initChannel(SocketChannel ch) throws Exception {
                ch.pipeline().addLast(sslContext.newHandler(ch.alloc()));
                ch.pipeline().addLast(new GzipCodec());
                //固定帧长解码器
                ch.pipeline().addLast(new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE, 0, 4, 0, 4));
                ch.pipeline().addLast(new LengthFieldPrepender(4));
                ch.pipeline().addLast(new ChunkedWriteHandler());
                //自定义协议编解码器
                ch.pipeline().addLast(new MessageCodec());
                ch.pipeline().addLast(new IdleCheckHandler(60, 30, 0));
                //服务器连接处理器
                ch.pipeline().addLast(new ClientHandler());
            }
        });
        return this;
    }

    @Override
    public Boolean start(int seconds) throws Exception {
        TimeUnit.SECONDS.sleep(seconds);
        if (bootstrap.config().group().isShuttingDown() || bootstrap.config().group().isShutdown()) {
            return false;
        }
        ClientConfig clientConfig = Config.getClientConfig();
        logger.info("客户端开始连接服务端IP:{},服务端端口:{}", clientConfig.getServerIp(), clientConfig.getServerPort());
        bootstrap.connect(clientConfig.getServerIp(), clientConfig.getServerPort()).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                future.channel().attr(Constants.Client.APPLICATION).set(this);
                //连接建立成功，发送注册请求
                Message message = new Message();
                message.setType(MessageType.TYPE_REGISTER);
                message.setMeta(new Meta(Config.getClientConfig().getClientKey(), Config.getClientConfig().getConfig()));
                future.channel().writeAndFlush(message);
            } else {
                logger.info("客户端失败连接服务端IP:{},服务端端口:{},原因:{}", clientConfig.getServerIp(), clientConfig.getServerPort(), future.cause().getMessage());
                this.start(1);
            }
        });
        return true;
    }

    @Override
    public Bootstrap bootstrap() {
        return bootstrap;
    }

    private SslContext buildSslContext() throws IOException {
        try (InputStream certChainFile = ClassLoader.getSystemResourceAsStream("client.crt"); InputStream keyFile = ClassLoader.getSystemResourceAsStream("pkcs8_client.key"); InputStream rootFile = ClassLoader.getSystemResourceAsStream("ca.crt")) {
            return SslContextBuilder.forClient().keyManager(certChainFile, keyFile).trustManager(rootFile).sslProvider(SslProvider.OPENSSL).build();
        }
    }
}
