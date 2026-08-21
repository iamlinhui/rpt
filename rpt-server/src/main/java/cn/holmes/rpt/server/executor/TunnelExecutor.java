package cn.holmes.rpt.server.executor;

import cn.holmes.rpt.base.executor.MessageExecutor;
import cn.holmes.rpt.base.protocol.Message;
import cn.holmes.rpt.base.protocol.MessageType;
import cn.holmes.rpt.base.protocol.Meta;
import cn.holmes.rpt.base.utils.Constants.Server;
import cn.holmes.rpt.server.cache.ServerChannelCache;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 共享数据隧道注册处理：客户端在控制通道认证成功后建立n条数据隧道，
 * 每条隧道连接成功后发送TYPE_TUNNEL携带clientKey+serverId完成归属绑定。
 * 隧道本身不承载单会话，所有会话通过meta.channelId路由。
 */
public class TunnelExecutor implements MessageExecutor {

    @Override
    public MessageType getMessageType() {
        return MessageType.TYPE_TUNNEL;
    }

    @Override
    public void execute(ChannelHandlerContext context, Message message) throws Exception {
        Meta meta = message.getMeta();
        if (Objects.isNull(meta)) {
            context.close();
            return;
        }
        Channel serverChannel = ServerChannelCache.getServerChannelMap().get(meta.getServerId());
        if (Objects.isNull(serverChannel)) {
            context.close();
            return;
        }
        // 校验隧道注册者与控制通道的clientKey一致
        String clientKey = serverChannel.attr(Server.CLIENT_KEY).get();
        if (Objects.isNull(clientKey) || !Objects.equals(clientKey, meta.getClientKey())) {
            context.close();
            return;
        }
        Channel tunnel = context.channel();
        tunnel.attr(Server.SERVER_ID).set(meta.getServerId());
        tunnel.attr(Server.STREAM_SET).setIfAbsent(ConcurrentHashMap.newKeySet());
        serverChannel.attr(Server.TUNNEL_SET).setIfAbsent(ConcurrentHashMap.newKeySet());
        serverChannel.attr(Server.TUNNEL_SET).get().add(tunnel);
    }
}
