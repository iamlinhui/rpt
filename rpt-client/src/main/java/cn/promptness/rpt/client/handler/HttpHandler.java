package cn.promptness.rpt.client.handler;

import cn.promptness.rpt.base.coder.HttpEncoder;
import cn.promptness.rpt.base.config.Config;
import cn.promptness.rpt.base.config.RemoteConfig;
import cn.promptness.rpt.base.utils.Constants;
import cn.promptness.rpt.base.utils.StringUtils;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpVersion;

import java.util.List;
import java.util.Map;

public class HttpHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    /**
     * requestChannelId --> localHttpChannel
     */
    private final Map<String, Channel> localHttpChannelMap;

    public HttpHandler(Map<String, Channel> localHttpChannelMap) {
        this.localHttpChannelMap = localHttpChannelMap;
    }


    @Override
    protected void channelRead0(ChannelHandlerContext context, FullHttpRequest fullHttpRequest) throws Exception {

        String requestChannelId = fullHttpRequest.headers().get(Constants.REQUEST_CHANNEL_ID);
        Channel localHttpChannel = localHttpChannelMap.get(requestChannelId);
        if (localHttpChannel == null) {
            return;
        }
        fullHttpRequest.headers().remove(Constants.REQUEST_CHANNEL_ID);

        String hostAndPort = fullHttpRequest.headers().get(HttpHeaderNames.HOST);
        String domain = hostAndPort.split(":")[0];

        RemoteConfig httpConfig = Config.getClientConfig().getHttpConfig(domain);
        if (httpConfig == null) {
            return;
        }
        String oldHost = fullHttpRequest.headers().get(HttpHeaderNames.HOST);
        String referer = fullHttpRequest.headers().get(HttpHeaderNames.REFERER);
        if (StringUtils.hasText(referer)) {
            String newReferer = referer.replace(oldHost, httpConfig.getLocalIp());
            fullHttpRequest.headers().set(HttpHeaderNames.REFERER, newReferer);
        }

        //设置host为请求服务器地址
        fullHttpRequest.headers().set(HttpHeaderNames.HOST, httpConfig.getLocalIp() + ":" + httpConfig.getLocalPort());
        fullHttpRequest.setProtocolVersion(HttpVersion.HTTP_1_1);
        fullHttpRequest.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);

        List<Object> encode = HttpEncoder.encode(context, fullHttpRequest);
        for (Object obj : encode) {
            localHttpChannel.writeAndFlush(obj);
        }
    }

}
