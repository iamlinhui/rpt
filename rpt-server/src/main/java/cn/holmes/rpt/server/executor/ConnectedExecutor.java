package cn.holmes.rpt.server.executor;

import cn.holmes.rpt.base.config.ProxyType;
import cn.holmes.rpt.base.config.RemoteConfig;
import cn.holmes.rpt.base.executor.MessageExecutor;
import cn.holmes.rpt.base.protocol.Message;
import cn.holmes.rpt.base.protocol.MessageType;
import cn.holmes.rpt.base.protocol.Meta;
import cn.holmes.rpt.base.utils.Constants.Server;
import cn.holmes.rpt.base.utils.FireEvent;
import cn.holmes.rpt.server.cache.ServerChannelCache;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;

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
        String serverId = meta.getServerId();
        Channel serverChannel = ServerChannelCache.getServerChannelMap().get(serverId);
        if (Objects.isNull(serverChannel)) {
            context.close();
            return;
        }
        String clientKey = serverChannel.attr(Server.CLIENT_KEY).get();
        if (Objects.isNull(clientKey)) {
            context.close();
            return;
        }
        Map<String, Channel> localChannelMap = Optional.ofNullable(serverChannel.attr(Server.CHANNELS).get()).orElse(Collections.emptyMap());
        String channelId = meta.getChannelId();
        Channel localChannel = localChannelMap.get(channelId);
        if (Objects.isNull(localChannel)) {
            return;
        }
        // binding each other
        Channel proxyChannel = context.channel();
        ProxyType proxyType = localChannel.attr(Server.PROXY_TYPE).get();
        if (Objects.equals(ProxyType.UDP, proxyType)) {
            proxyChannel.attr(Server.LOCAL).set(localChannel);
            FireEvent fireEvent = new FireEvent(channelId, proxyChannel, getMessageType());
            localChannel.pipeline().fireUserEventTriggered(fireEvent);
            return;
        }
        localChannel.attr(Server.PROXY).set(proxyChannel);
        proxyChannel.attr(Server.LOCAL).set(localChannel);
        localChannel.pipeline().fireUserEventTriggered(proxyType);
    }
}
