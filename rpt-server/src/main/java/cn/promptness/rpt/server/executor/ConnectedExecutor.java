package cn.promptness.rpt.server.executor;

import cn.promptness.rpt.base.config.ProxyType;
import cn.promptness.rpt.base.config.RemoteConfig;
import cn.promptness.rpt.base.executor.MessageExecutor;
import cn.promptness.rpt.base.protocol.Message;
import cn.promptness.rpt.base.protocol.MessageType;
import cn.promptness.rpt.base.protocol.Meta;
import cn.promptness.rpt.base.utils.Constants;
import cn.promptness.rpt.server.cache.ServerChannelCache;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;

import java.util.Map;
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
            return;
        }
        ProxyType proxyType = Optional.ofNullable(remoteConfig.getProxyType()).orElse(ProxyType.TCP);

        String serverId = meta.getServerId();
        Map<String, Channel> channelMap = ServerChannelCache.getServerChannelMap().get(serverId).attr(Constants.CHANNELS).get();

        String channelId = meta.getChannelId();
        Channel localChannel = channelMap.get(channelId);
        if (localChannel == null) {
            return;
        }
        // binding each other
        localChannel.attr(Constants.PROXY).set(context.channel());
        context.channel().attr(Constants.LOCAL).set(localChannel);
        context.channel().attr(Constants.Server.LABEL).set(Void.class);
        localChannel.pipeline().fireUserEventTriggered(proxyType);
    }
}
