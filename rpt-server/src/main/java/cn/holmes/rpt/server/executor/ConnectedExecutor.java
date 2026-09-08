package cn.holmes.rpt.server.executor;

import cn.holmes.rpt.base.config.ProxyType;
import cn.holmes.rpt.base.executor.MessageExecutor;
import cn.holmes.rpt.base.protocol.Message;
import cn.holmes.rpt.base.protocol.MessageType;
import cn.holmes.rpt.base.protocol.Meta;
import cn.holmes.rpt.base.utils.Attributes.Server;
import cn.holmes.rpt.base.protocol.ChannelEvent;
import cn.holmes.rpt.server.cache.ServerChannelCache;
import cn.holmes.rpt.server.cache.TrafficStatsCache;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;

import java.util.*;

/**
 * 客户端ACK处理：将到达的共享数据隧道与外部会话绑定。
 */
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
            return;
        }
        String clientKey = serverChannel.attr(Server.CLIENT_KEY).get();
        if (Objects.isNull(clientKey)) {
            return;
        }
        Map<String, Channel> localChannelMap = Optional.ofNullable(serverChannel.attr(Server.CHANNELS).get()).orElse(Collections.emptyMap());
        String channelId = meta.getChannelId();
        Channel localChannel = localChannelMap.get(channelId);
        if (Objects.isNull(localChannel)) {
            // 会话已不存在，通知客户端释放该流
            context.writeAndFlush(new Message(MessageType.TYPE_DISCONNECTED, meta, Unpooled.EMPTY_BUFFER));
            return;
        }
        Channel tunnel = context.channel();
        // 隧道注册(TYPE_TUNNEL)时已建立STREAM_SET并回填SERVER_ID，此处直接绑定会话。若隧道尚未注册回TYPE_DISCONNECTED让客户端重试
        Set<String> streamSet = tunnel.attr(Server.STREAM_SET).get();
        if (Objects.isNull(streamSet)) {
            context.writeAndFlush(new Message(MessageType.TYPE_DISCONNECTED, meta, Unpooled.EMPTY_BUFFER));
            return;
        }
        // ADD失败说明该会话已绑定在其他隧道上
        if (!streamSet.add(channelId)) {
            return;
        }
        TrafficStatsCache.incrementProxyChannels(serverId);
        ProxyType proxyType = localChannel.attr(Server.PROXY_TYPE).get();
        if (Objects.equals(ProxyType.UDP, proxyType)) {
            ChannelEvent fireEvent = new ChannelEvent(channelId, tunnel, getMessageType());
            localChannel.pipeline().fireUserEventTriggered(fireEvent);
            return;
        }
        localChannel.attr(Server.PROXY).set(tunnel);
        localChannel.pipeline().fireUserEventTriggered(proxyType);
    }
}
