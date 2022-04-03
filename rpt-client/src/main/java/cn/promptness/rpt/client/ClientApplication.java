package cn.promptness.rpt.client;

import cn.promptness.rpt.base.coder.MessageDecoder;
import cn.promptness.rpt.base.coder.MessageEncoder;
import cn.promptness.rpt.base.config.ClientConfig;
import cn.promptness.rpt.base.config.Config;
import cn.promptness.rpt.base.handler.IdleCheckHandler;
import cn.promptness.rpt.base.utils.ScheduledThreadFactory;
import cn.promptness.rpt.client.handler.ClientHandler;
import cn.promptness.rpt.client.handler.HttpHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.stream.ChunkedWriteHandler;
import javafx.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class ClientApplication {

    private static final Logger logger = LoggerFactory.getLogger(ClientApplication.class);

    private static final Map<String, Channel> LOCAL_HTTP_CHANNEL_MAP = new ConcurrentHashMap<>();

    private static final ScheduledThreadPoolExecutor EXECUTOR = new ScheduledThreadPoolExecutor(1, ScheduledThreadFactory.create("client", false));

    private static final ArrayBlockingQueue<Pair<NioEventLoopGroup, ScheduledFuture<?>>> QUEUE = new ArrayBlockingQueue<>(1);

    public static void main(String[] args) throws SSLException {
        InputStream certChainFile = ClassLoader.getSystemResourceAsStream("client.crt");
        InputStream keyFile = ClassLoader.getSystemResourceAsStream("pkcs8_client.key");
        InputStream rootFile = ClassLoader.getSystemResourceAsStream("ca.crt");
        SslContext sslContext = SslContextBuilder.forClient().keyManager(certChainFile, keyFile).trustManager(rootFile).sslProvider(SslProvider.OPENSSL).build();

        NioEventLoopGroup clientWorkerGroup = new NioEventLoopGroup();
        Bootstrap bootstrap = new Bootstrap();
        AtomicBoolean connect = new AtomicBoolean(false);
        bootstrap.group(clientWorkerGroup).channel(NioSocketChannel.class).option(ChannelOption.SO_KEEPALIVE, true).handler(new ChannelInitializer<SocketChannel>() {
            @Override
            public void initChannel(SocketChannel ch) throws Exception {
                ch.pipeline().addLast(sslContext.newHandler(ch.alloc()));
                //固定帧长解码器
                ch.pipeline().addLast(new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE, 0, 4, 0, 4));
                ch.pipeline().addLast(new LengthFieldPrepender(4));
                //自定义协议解码器
                ch.pipeline().addLast(new MessageDecoder());
                //自定义协议编码器
                ch.pipeline().addLast(new MessageEncoder());
                ch.pipeline().addLast(new IdleCheckHandler(60, 30, 0));
                //服务器连接处理器
                ch.pipeline().addLast(new ClientHandler(connect, LOCAL_HTTP_CHANNEL_MAP));
                ch.pipeline().addLast(new HttpRequestDecoder());
                ch.pipeline().addLast(new HttpObjectAggregator(8 * 1024 * 1024));
                ch.pipeline().addLast(new ChunkedWriteHandler());
                ch.pipeline().addLast(new HttpHandler(LOCAL_HTTP_CHANNEL_MAP));
            }
        });
        Pair<NioEventLoopGroup, ScheduledFuture<?>> pair = getPair(clientWorkerGroup, bootstrap, connect);
        if (!QUEUE.offer(pair)) {
            pair.getValue().cancel(true);
            clientWorkerGroup.shutdownGracefully();
        }
    }

    private static Pair<NioEventLoopGroup, ScheduledFuture<?>> getPair(NioEventLoopGroup clientWorkerGroup, Bootstrap bootstrap, AtomicBoolean connect) {
        ClientConfig clientConfig = Config.getClientConfig();
        return new Pair<>(clientWorkerGroup, EXECUTOR.scheduleAtFixedRate(() -> {
            if (connect.get()) {
                return;
            }
            synchronized (Object.class) {
                if (connect.get()) {
                    return;
                }
                try {
                    bootstrap.connect(clientConfig.getServerIp(), clientConfig.getServerPort()).sync();
                    logger.info("客户端成功连接服务端IP:{},服务端端口:{}", clientConfig.getServerIp(), clientConfig.getServerPort());
                } catch (Exception exception) {
                    logger.info("客户端失败连接服务端IP:{},服务端端口:{},原因:{}", clientConfig.getServerIp(), clientConfig.getServerPort(), exception.getCause().getMessage());
                }
            }
        }, 0, 1, TimeUnit.MINUTES));
    }

    public static void stop() {
        Pair<NioEventLoopGroup, ScheduledFuture<?>> pair = QUEUE.poll();
        if (pair == null) {
            return;
        }
        pair.getValue().cancel(true);
        pair.getKey().shutdownGracefully();
    }

    public static boolean isStart() {
        return !QUEUE.isEmpty();
    }
}
