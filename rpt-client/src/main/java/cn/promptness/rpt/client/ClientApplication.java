package cn.promptness.rpt.client;

import cn.promptness.rpt.base.coder.MessageDecoder;
import cn.promptness.rpt.base.coder.MessageEncoder;
import cn.promptness.rpt.base.config.ClientConfig;
import cn.promptness.rpt.base.config.Config;
import cn.promptness.rpt.base.handler.IdleCheckHandler;
import cn.promptness.rpt.client.handler.ClientHandler;
import io.netty.bootstrap.Bootstrap;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;
import java.io.InputStream;

public class ClientApplication {

    private static final Logger logger = LoggerFactory.getLogger(ClientApplication.class);

    public static void main(String[] args) throws SSLException {
        ClientConfig clientConfig = Config.getClientConfig();
        InputStream certChainFile = ClassLoader.getSystemResourceAsStream("client.crt");
        InputStream keyFile = ClassLoader.getSystemResourceAsStream("pkcs8_client.key");
        InputStream rootFile = ClassLoader.getSystemResourceAsStream("ca.crt");
        SslContext sslContext = SslContextBuilder.forClient().keyManager(certChainFile, keyFile).trustManager(rootFile).sslProvider(SslProvider.OPENSSL).build();

        NioEventLoopGroup clientWorkerGroup = new NioEventLoopGroup();
        Bootstrap bootstrap = new Bootstrap();
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
                ch.pipeline().addLast(new ClientHandler(bootstrap, clientWorkerGroup));
            }
        });
        try {
            bootstrap.connect(clientConfig.getServerIp(), clientConfig.getServerPort()).get();
            logger.info("客户端成功连接服务端IP:{},服务端端口:{}", clientConfig.getServerIp(), clientConfig.getServerPort());
        } catch (Exception exception) {
            logger.info("客户端失败连接服务端IP:{},服务端端口:{},原因:{}", clientConfig.getServerIp(), clientConfig.getServerPort(), exception.getCause().getMessage());
            clientWorkerGroup.shutdownGracefully();
        }
    }
}
