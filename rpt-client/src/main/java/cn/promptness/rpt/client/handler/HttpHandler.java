package cn.promptness.rpt.client.handler;

import cn.promptness.rpt.base.coder.HttpEncoder;
import cn.promptness.rpt.base.utils.Constants;
import cn.promptness.rpt.client.handler.cache.ClientChannelCache;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpVersion;

import java.util.ArrayList;
import java.util.List;

public class HttpHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private final HttpEncoder.RequestEncoder requestEncoder = new HttpEncoder.RequestEncoder();

    @Override
    protected void channelRead0(ChannelHandlerContext context, FullHttpRequest fullHttpRequest) throws Exception {

        String requestChannelId = fullHttpRequest.headers().get(Constants.REQUEST_CHANNEL_ID);
        if (requestChannelId == null) {
            return;
        }
        Channel localHttpChannel = ClientChannelCache.getLocalHttpChannelMap().get(requestChannelId);
        if (localHttpChannel == null) {
            return;
        }
        fullHttpRequest.headers().remove(Constants.REQUEST_CHANNEL_ID);
        fullHttpRequest.setProtocolVersion(HttpVersion.HTTP_1_1);
        fullHttpRequest.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);

        List<Object> encode = new ArrayList<>();
        requestEncoder.encode(context, fullHttpRequest, encode);
        for (Object obj : encode) {
            localHttpChannel.writeAndFlush(obj);
        }
    }

}
