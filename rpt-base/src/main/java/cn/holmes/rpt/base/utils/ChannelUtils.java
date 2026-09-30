package cn.holmes.rpt.base.utils;

import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;

public class ChannelUtils {

    /**
     * 以 RST 方式立即关闭连接并丢弃未写出的数据，用于数据流已不完整的场景。
     * FIN 会让接收方把截断的流当成正常结束（无长度标识的协议会把残缺文件当成功），RST 则明确报错。
     */
    public static void reset(Channel channel) {
        if (!channel.isOpen()) {
            return;
        }
        try {
            channel.config().setOption(ChannelOption.SO_LINGER, 0);
        } catch (Exception ignored) {
            // 连接已关闭或传输类型不支持 SO_LINGER，退化为普通关闭
        }
        channel.close();
    }

}
