package cn.promptness.rpt.server.handler;

import cn.promptness.rpt.base.coder.HttpEncoder;
import cn.promptness.rpt.base.config.ClientConfig;
import cn.promptness.rpt.base.config.RemoteConfig;
import cn.promptness.rpt.base.protocol.Message;
import cn.promptness.rpt.base.protocol.MessageType;
import cn.promptness.rpt.base.protocol.ProxyType;
import cn.promptness.rpt.server.cache.ServerChannelCache;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WebSocketHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    private static final Map<String, WebSocketVersion> V = new HashMap() {{
        put(WebSocketVersion.V00.toHttpHeaderValue(), WebSocketVersion.V00);
        put(WebSocketVersion.V07.toHttpHeaderValue(), WebSocketVersion.V07);
        put(WebSocketVersion.V08.toHttpHeaderValue(), WebSocketVersion.V08);
        put(WebSocketVersion.V13.toHttpHeaderValue(), WebSocketVersion.V13);
    }};


    private final String domain;

    private final WebSocketVersion webSocketVersion;

    public WebSocketHandler(String domain, String version) {
        this.domain = domain;
        this.webSocketVersion = V.get(version);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame msg) throws Exception {
        Channel serverChannel = ServerChannelCache.getServerDomainChannelMap().get(domain);
        if (serverChannel == null) {
            return;
        }
        List<Object> encode = HttpEncoder.websocketEncode(webSocketVersion).apply(ctx, msg);
        for (Object obj : encode) {
            ByteBuf buf = (ByteBuf) obj;
            byte[] data = new byte[buf.readableBytes()];
            buf.readBytes(data);
            buf.release();
            send(serverChannel, ctx, domain, MessageType.TYPE_DATA, data);
        }
    }


    private void send(Channel serverChannel, ChannelHandlerContext ctx, String domain, MessageType typeConnect, byte[] data) {
        RemoteConfig remoteConfig = new RemoteConfig();
        remoteConfig.setProxyType(ProxyType.HTTP);
        remoteConfig.setDomain(domain);

        ClientConfig clientConfig = new ClientConfig();
        clientConfig.setConfig(Collections.singletonList(remoteConfig));
        clientConfig.setChannelId(ctx.channel().id().asLongText());

        Message message = new Message();
        message.setClientConfig(clientConfig);
        message.setData(data);
        message.setType(typeConnect);
        serverChannel.writeAndFlush(message);
    }
}
