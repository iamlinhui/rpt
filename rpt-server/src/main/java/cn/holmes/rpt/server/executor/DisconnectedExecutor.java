package cn.holmes.rpt.server.executor;

import cn.holmes.rpt.base.config.ProxyType;
import cn.holmes.rpt.base.executor.MessageExecutor;
import cn.holmes.rpt.base.protocol.Message;
import cn.holmes.rpt.base.protocol.MessageType;
import cn.holmes.rpt.base.utils.Constants.Server;
import cn.holmes.rpt.base.utils.FireEvent;
import cn.holmes.rpt.server.cache.ServerChannelCache;
import cn.holmes.rpt.server.cache.TrafficStatsCache;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;

import java.util.*;

/**
 * 客户端主动断开某个会话：按serverId+channelId路由到外部连接并关闭。
 */
public class DisconnectedExecutor implements MessageExecutor {

    @Override
    public MessageType getMessageType() {
        return MessageType.TYPE_DISCONNECTED;
    }

    @Override
    public void execute(ChannelHandlerContext context, Message message) throws Exception {
        String serverId = message.getMeta().getServerId();
        if (!Objects.equals(context.channel().attr(Server.SERVER_ID).get(), serverId)) {
            return;
        }
        Channel serverChannel = ServerChannelCache.getServerChannelMap().get(serverId);
        if (Objects.isNull(serverChannel)) {
            return;
        }
        Map<String, Channel> localChannelMap = Optional.ofNullable(serverChannel.attr(Server.CHANNELS).get()).orElse(Collections.emptyMap());
        String channelId = message.getMeta().getChannelId();
        Channel localChannel = localChannelMap.get(channelId);
        if (Objects.isNull(localChannel)) {
            return;
        }
        // 会话计数与隧道反向索引解绑
        Channel tunnel = context.channel();
        Set<String> streamSet = tunnel.attr(Server.STREAM_SET).get();
        if (Objects.nonNull(streamSet) && streamSet.remove(channelId)) {
            TrafficStatsCache.decrementProxyChannels(serverId);
        }
        ProxyType proxyType = localChannel.attr(Server.PROXY_TYPE).get();
        if (Objects.equals(ProxyType.UDP, proxyType)) {
            FireEvent fireEvent = new FireEvent(channelId, tunnel, getMessageType());
            localChannel.pipeline().fireUserEventTriggered(fireEvent);
            return;
        }
        localChannelMap.remove(channelId);
        // 先清空隧道绑定，避免本地连接关闭时造成环路
        localChannel.attr(Server.PROXY).set(null);
        localChannel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
    }
}
