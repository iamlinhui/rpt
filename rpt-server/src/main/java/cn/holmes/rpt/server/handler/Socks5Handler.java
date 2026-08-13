package cn.holmes.rpt.server.handler;

import cn.holmes.rpt.base.config.RemoteConfig;
import cn.holmes.rpt.base.utils.Constants.Server;
import cn.holmes.rpt.base.utils.Target;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.socksx.v5.*;
import io.netty.handler.timeout.ReadTimeoutException;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class Socks5Handler extends SimpleChannelInboundHandler<Socks5Message> {

    private static final Logger logger = LoggerFactory.getLogger(Socks5Handler.class);

    private static final long HANDSHAKE_TIMEOUT_SECONDS = 10;

    private static final String NAME_TIMEOUT = "socksTimeout";
    private static final String NAME_INIT_DECODER = "socksInitDecoder";
    private static final String NAME_AUTH_DECODER = "socksAuthDecoder";
    private static final String NAME_CMD_DECODER = "socksCmdDecoder";
    private static final String NAME_ENCODER = "socksMessageEncoder";

    private final Channel serverChannel;
    private final RemoteConfig remoteConfig;
    private final boolean requireAuth;

    /**
     * 握手成功后置位，阻止解码器对拼接进来的应用数据再次按命令帧解析
     */
    private boolean done;

    public Socks5Handler(Channel serverChannel, RemoteConfig remoteConfig) {
        this.serverChannel = serverChannel;
        this.remoteConfig = remoteConfig;
        this.requireAuth = remoteConfig != null && remoteConfig.getToken() != null && !remoteConfig.getToken().isEmpty();
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        // ReadTimeout → InitDecoder → Encoder → Socks5Handler
        ctx.pipeline().addBefore(ctx.name(), NAME_TIMEOUT, new ReadTimeoutHandler(HANDSHAKE_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        ctx.pipeline().addBefore(ctx.name(), NAME_INIT_DECODER, new Socks5InitialRequestDecoder());
        ctx.pipeline().addBefore(ctx.name(), NAME_ENCODER, Socks5ServerEncoder.DEFAULT);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Socks5Message msg) {
        if (done) {
            return;
        }
        if (msg instanceof Socks5InitialRequest) {
            handleInit(ctx, (Socks5InitialRequest) msg);
        } else if (msg instanceof Socks5PasswordAuthRequest) {
            handleAuth(ctx, (Socks5PasswordAuthRequest) msg);
        } else if (msg instanceof Socks5CommandRequest) {
            handleCmd(ctx, (Socks5CommandRequest) msg);
        } else {
            ctx.close();
        }
    }

    private void handleInit(ChannelHandlerContext ctx, Socks5InitialRequest req) {
        if (requireAuth) {
            if (!req.authMethods().contains(Socks5AuthMethod.PASSWORD)) {
                logger.info("socks5 客户端未提供 PASSWORD 方式, remote={}", ctx.channel().remoteAddress());
                failAndClose(ctx, new DefaultSocks5InitialResponse(Socks5AuthMethod.UNACCEPTED));
                return;
            }
            ctx.writeAndFlush(new DefaultSocks5InitialResponse(Socks5AuthMethod.PASSWORD));
            ctx.pipeline().replace(NAME_INIT_DECODER, NAME_AUTH_DECODER, new Socks5PasswordAuthRequestDecoder());
        } else {
            ctx.writeAndFlush(new DefaultSocks5InitialResponse(Socks5AuthMethod.NO_AUTH));
            ctx.pipeline().replace(NAME_INIT_DECODER, NAME_CMD_DECODER, new Socks5CommandRequestDecoder());
        }
    }

    private void handleAuth(ChannelHandlerContext ctx, Socks5PasswordAuthRequest req) {
        boolean ok = checkCredential(req.username(), req.password());
        ctx.writeAndFlush(new DefaultSocks5PasswordAuthResponse(ok ? Socks5PasswordAuthStatus.SUCCESS : Socks5PasswordAuthStatus.FAILURE));
        if (!ok) {
            logger.info("socks5 账密认证失败, user={}, remote={}", req.username(), ctx.channel().remoteAddress());
            ctx.close();
            return;
        }
        ctx.pipeline().replace(NAME_AUTH_DECODER, NAME_CMD_DECODER, new Socks5CommandRequestDecoder());
    }

    private void handleCmd(ChannelHandlerContext ctx, Socks5CommandRequest req) {
        if (req.type() != Socks5CommandType.CONNECT) {
            logger.info("socks5 不支持的命令 {}, remote={}", req.type(), ctx.channel().remoteAddress());
            failAndClose(ctx, new DefaultSocks5CommandResponse(Socks5CommandStatus.COMMAND_UNSUPPORTED, Socks5AddressType.IPv4));
            return;
        }
        String host = req.dstAddr();
        int port = req.dstPort();
        if (host == null || host.isEmpty() || port <= 0 || port > 65535) {
            logger.info("socks5 非法目标 host={} port={}, remote={}", host, port, ctx.channel().remoteAddress());
            failAndClose(ctx, new DefaultSocks5CommandResponse(Socks5CommandStatus.FAILURE, Socks5AddressType.IPv4));
            return;
        }
        done = true;
        ctx.writeAndFlush(new DefaultSocks5CommandResponse(Socks5CommandStatus.SUCCESS, Socks5AddressType.IPv4)).addListener((ChannelFutureListener) future -> {
            if (!future.isSuccess()) {
                ctx.close();
                return;
            }
            Channel ch = ctx.channel();
            ch.attr(Server.DYNAMIC_TARGET).set(new Target(host, port));
            logger.debug("socks5 握手成功, target={}:{}", host, port);
            ctx.pipeline().remove(NAME_CMD_DECODER);
            ctx.pipeline().remove(NAME_ENCODER);
            ctx.pipeline().remove(NAME_TIMEOUT);
            ctx.pipeline().remove(this);
            ctx.pipeline().addLast(new TcpHandler(serverChannel, remoteConfig));
        });
    }

    private void failAndClose(ChannelHandlerContext ctx, Socks5Message resp) {
        ctx.writeAndFlush(resp).addListener(ChannelFutureListener.CLOSE);
    }

    private boolean checkCredential(String user, String pass) {
        String token = remoteConfig.getToken();
        if (token == null) {
            return false;
        }
        int colon = token.indexOf(':');
        if (colon <= 0 || colon == token.length() - 1) {
            return false;
        }
        return token.substring(0, colon).equals(user) && token.substring(colon + 1).equals(pass);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (cause instanceof ReadTimeoutException) {
            logger.info("socks5 握手超时关闭, remote={}", ctx.channel().remoteAddress());
        } else {
            logger.info("socks5 握手异常, remote={}, {}", ctx.channel().remoteAddress(), cause.getMessage());
        }
        ctx.close();
    }
}
