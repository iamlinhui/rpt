package cn.promptness.rpt.base.coder;

import io.netty.channel.CombinedChannelDuplexHandler;

public class MessageCodec extends CombinedChannelDuplexHandler<MessageDecoder, MessageEncoder> {

    public MessageCodec() {
        super(new MessageDecoder(),new MessageEncoder());
    }
}
