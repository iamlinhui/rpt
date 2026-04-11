package cn.holmes.rpt.server.executor;

import cn.holmes.rpt.base.config.ProxyType;
import cn.holmes.rpt.base.config.RemoteConfig;
import cn.holmes.rpt.base.executor.MessageExecutor;
import cn.holmes.rpt.base.protocol.Message;
import cn.holmes.rpt.base.protocol.MessageType;
import cn.holmes.rpt.base.protocol.Meta;
import cn.holmes.rpt.base.utils.Constants;
import cn.holmes.rpt.server.cache.ServerChannelCache;
import cn.holmes.rpt.server.handler.UdpRemoteHandler;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class ConnectedExecutor implements MessageExecutor {

    @Override
    public MessageType getMessageType() {
        return MessageType.TYPE_CONNECTED;
    }

    @Override
    public void execute(ChannelHandlerContext context, Message message) throws Exception {
        Meta meta = message.getMeta();
        RemoteConfig remoteConfig = meta.getRemoteConfig();
        if (remoteConfig == null) {
            context.close();
            return;
        }
        ProxyType proxyType = Optional.ofNullable(remoteConfig.getProxyType()).orElse(ProxyType.TCP);

        String serverId = meta.getServerId();
        Channel serverChannel = ServerChannelCache.getServerChannelMap().get(serverId);
        if (Objects.isNull(serverChannel)) {
            context.close();
            return;
        }
        String clientKey = serverChannel.attr(Constants.Server.CLIENT_KEY).get();
        if (Objects.isNull(clientKey)) {
            context.close();
            return;
        }
        // label the channel with clientKey
        context.channel().attr(Constants.Server.LABEL).set(clientKey);

        Map<String, Channel> localChannelMap = Optional.ofNullable(serverChannel.attr(Constants.CHANNELS).get()).orElse(Collections.emptyMap());
        String channelId = meta.getChannelId();
        Channel localChannel = localChannelMap.get(channelId);
        if (Objects.isNull(localChannel)) {
            return;
        }
        // binding each other
        if (Objects.equals(ProxyType.UDP, proxyType)) {
            // UDP: localChannel是共享的DatagramChannel，设置发送者地址用于响应路由
            context.channel().attr(Constants.LOCAL).set(localChannel);
            UdpRemoteHandler udpHandler = localChannel.pipeline().get(UdpRemoteHandler.class);
            if (udpHandler != null) {
                InetSocketAddress sender = udpHandler.getSenderMap().get(channelId);
                if (sender != null) {
                    context.channel().attr(Constants.UDP_SENDER).set(sender);
                }
                // 绑定代理通道并刷新缓冲的数据
                udpHandler.bindProxy(channelId, context.channel());
            }
        } else {
            localChannel.attr(Constants.PROXY).set(context.channel());
            context.channel().attr(Constants.LOCAL).set(localChannel);
            // 清除可能残留的UDP属性，防止该proxyChannel上次用于UDP后被复用时DataExecutor走错路径
            context.channel().attr(Constants.UDP_SENDER).set(null);
            localChannel.pipeline().fireUserEventTriggered(proxyType);
        }
    }
}
