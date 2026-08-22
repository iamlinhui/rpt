package cn.holmes.rpt.server.executor;

import cn.holmes.rpt.base.config.ProxyType;
import cn.holmes.rpt.base.executor.MessageExecutor;
import cn.holmes.rpt.base.protocol.Message;
import cn.holmes.rpt.base.protocol.MessageType;
import cn.holmes.rpt.base.protocol.Meta;
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
            Map<String, InetSocketAddress> senders = localChannel.attr(Server.UDP_SENDERS).get();
            InetSocketAddress udpSender = Objects.nonNull(senders) ? senders.get(meta.getChannelId()) : null;
            if (udpSender == null) {
                data.release();
                return;
            }
            localChannel.writeAndFlush(new DatagramPacket(data, udpSender));
            return;
        }
        // TCP/HTTP: 直接写ByteBuf，零拷贝
        localChannel.writeAndFlush(data);
    }
}
