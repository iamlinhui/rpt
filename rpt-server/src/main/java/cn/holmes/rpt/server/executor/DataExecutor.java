package cn.holmes.rpt.server.executor;

import cn.holmes.rpt.base.config.ProxyType;
import cn.holmes.rpt.base.config.ServerConfig;
import cn.holmes.rpt.base.executor.MessageExecutor;
import cn.holmes.rpt.base.mux.ChannelBuffer;
import cn.holmes.rpt.base.protocol.Message;
import cn.holmes.rpt.base.protocol.MessageType;
import cn.holmes.rpt.base.protocol.Meta;
import cn.holmes.rpt.base.utils.Config;
import cn.holmes.rpt.base.utils.Constants.Server;
import cn.holmes.rpt.server.cache.ServerChannelCache;
import cn.holmes.rpt.server.cache.TrafficStatsCache;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Objects;

/**
 * 隧道下行数据路由
 */
public class DataExecutor implements MessageExecutor {

    @Override
    public MessageType getMessageType() {
        return MessageType.TYPE_DATA;
    }

    @Override
    public void execute(ChannelHandlerContext context, Message message) throws Exception {
        Meta meta = message.getMeta();
        if (Objects.isNull(meta) || Objects.isNull(meta.getServerId()) || Objects.isNull(meta.getChannelId())) {
            return;
        }
        if (!Objects.equals(context.channel().attr(Server.SERVER_ID).get(), meta.getServerId())) {
            return;
        }
        Channel serverChannel = ServerChannelCache.getServerChannelMap().get(meta.getServerId());
        if (Objects.isNull(serverChannel)) {
            return;
        }
        Map<String, Channel> localChannelMap = serverChannel.attr(Server.CHANNELS).get();
        if (Objects.isNull(localChannelMap)) {
            return;
        }
        Channel localChannel = localChannelMap.get(meta.getChannelId());
        if (Objects.isNull(localChannel)) {
            return;
        }
        ProxyType proxyType = localChannel.attr(Server.PROXY_TYPE).get();
        ByteBuf data = message.hasDataBuf() ? message.getDataBuf().retain() : Unpooled.EMPTY_BUFFER;
        TrafficStatsCache.recordOut(meta.getServerId(), data.readableBytes());
        if (Objects.equals(ProxyType.UDP, proxyType)) {
            // UDP本地通道按端口共享，无法挂per-session缓冲区，直接写出不排队
            Map<String, InetSocketAddress> senders = localChannel.attr(Server.UDP_SENDERS).get();
            InetSocketAddress udpSender = Objects.nonNull(senders) ? senders.get(meta.getChannelId()) : null;
            if (udpSender == null) {
                data.release();
                return;
            }
            localChannel.writeAndFlush(new DatagramPacket(data, udpSender));
            return;
        }
        // TCP/HTTP: 经通道级缓冲区写出，慢的外部连接只堆自己的队列，不再拖停整条隧道
        buffer(localChannel).write(data);
    }

    /**
     * 懒创建该外部连接的通道级缓冲区
     */
    private ChannelBuffer buffer(Channel localChannel) {
        ChannelBuffer buffer = localChannel.attr(Server.BUFFER).get();
        if (Objects.nonNull(buffer)) {
            return buffer;
        }
        ServerConfig config = Config.getServerConfig();
        ChannelBuffer created = new ChannelBuffer(localChannel, config.getHighWater(), config.getLowWater(), config.getCapacity(), this::signal);
        ChannelBuffer previous = localChannel.attr(Server.BUFFER).setIfAbsent(created);
        return Objects.nonNull(previous) ? previous : created;
    }

    /**
     * 向客户端发送该通道的背压信号，隧道与会话标识在回调时实时读取
     */
    private void signal(Channel localChannel, MessageType type) {
        Channel tunnel = localChannel.attr(Server.PROXY).get();
        String channelId = localChannel.attr(Server.CHANNEL_ID).get();
        String serverId = localChannel.attr(Server.SERVER_ID).get();
        if (Objects.isNull(tunnel) || !tunnel.isActive() || Objects.isNull(channelId) || Objects.isNull(serverId)) {
            return;
        }
        Meta meta = new Meta().setChannelId(channelId).setServerId(serverId);
        tunnel.writeAndFlush(new Message(type, meta, Unpooled.EMPTY_BUFFER));
    }
}
