package cn.holmes.rpt.client.executor;

import cn.holmes.rpt.base.config.ClientConfig;
import cn.holmes.rpt.base.executor.MessageExecutor;
import cn.holmes.rpt.base.mux.ChannelBuffer;
import cn.holmes.rpt.base.protocol.Message;
import cn.holmes.rpt.base.protocol.MessageType;
import cn.holmes.rpt.base.protocol.Meta;
import cn.holmes.rpt.base.config.ConfigHolder;
import cn.holmes.rpt.base.utils.Attributes.Client;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class DataExecutor implements MessageExecutor {

    @Override
    public MessageType getMessageType() {
        return MessageType.TYPE_DATA;
    }

    @Override
    public void execute(ChannelHandlerContext context, Message message) {
        String channelId = Optional.ofNullable(message.getMeta()).map(Meta::getChannelId).orElse(null);
        if (Objects.isNull(channelId)) {
            return;
        }
        // 消息可能到达共享隧道，CHANNELS表挂在控制通道上
        Channel control = Optional.ofNullable(context.channel().attr(Client.CONTROL).get()).orElse(context.channel());
        Map<String, Channel> channelMap = control.attr(Client.CHANNELS).get();
        Channel localChannel = Objects.nonNull(channelMap) ? channelMap.get(channelId) : null;
        if (Objects.isNull(localChannel)) {
            return;
        }
        ByteBuf data = message.hasDataBuf() ? message.getDataBuf().retain() : Unpooled.EMPTY_BUFFER;
        InetSocketAddress udpTarget = localChannel.attr(Client.UDP_TARGET).get();
        if (udpTarget != null) {
            // UDP: 数据报排队没有意义，直接转发，零拷贝
            localChannel.writeAndFlush(new DatagramPacket(data, udpTarget));
            return;
        }
        // TCP/HTTP: 经通道级缓冲区写出，慢的本地服务只堆自己的队列，不再拖停整条隧道
        buffer(localChannel).write(data);
    }

    /**
     * 懒创建该本地连接的通道级缓冲区
     */
    private ChannelBuffer buffer(Channel localChannel) {
        ChannelBuffer buffer = localChannel.attr(Client.BUFFER).get();
        if (Objects.nonNull(buffer)) {
            return buffer;
        }
        ClientConfig config = ConfigHolder.getClientConfig();
        ChannelBuffer created = new ChannelBuffer(localChannel, config.getHighWater(), config.getLowWater(), config.getCapacity(), this::signal);
        ChannelBuffer previous = localChannel.attr(Client.BUFFER).setIfAbsent(created);
        return Objects.nonNull(previous) ? previous : created;
    }

    /**
     * 向服务端发送该通道的背压信号，隧道与会话标识在回调时实时读取
     */
    private void signal(Channel localChannel, MessageType type) {
        Channel tunnel = localChannel.attr(Client.TUNNEL).get();
        String channelId = localChannel.attr(Client.CHANNEL_ID).get();
        String serverId = localChannel.attr(Client.SERVER_ID).get();
        if (Objects.isNull(tunnel) || !tunnel.isActive() || Objects.isNull(channelId) || Objects.isNull(serverId)) {
            return;
        }
        Meta meta = new Meta().setChannelId(channelId).setServerId(serverId);
        tunnel.writeAndFlush(new Message(type, meta, Unpooled.EMPTY_BUFFER));
    }
}
