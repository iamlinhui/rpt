package cn.holmes.rpt.server.handler;

import cn.holmes.rpt.base.utils.Config;
import cn.holmes.rpt.base.utils.Constants;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;

public class RedirectHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.close();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest msg) throws Exception {
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.MOVED_PERMANENTLY);
        HttpHeaders headers = response.headers();
        headers.set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
        String host = Constants.COLON.split(msg.headers().get(HttpHeaderNames.HOST))[0] + Constants.COLON + Config.getServerConfig().getHttpsPort();
        headers.set(HttpHeaderNames.LOCATION, HttpScheme.HTTPS + "://" + host + msg.uri());
        ChannelFuture future = ctx.writeAndFlush(response);
        if (!HttpUtil.isKeepAlive(msg)) {
            future.addListener(ChannelFutureListener.CLOSE);
        }
    }
}
