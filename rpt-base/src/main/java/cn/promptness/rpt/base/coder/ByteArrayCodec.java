package cn.promptness.rpt.base.coder;

import io.netty.channel.CombinedChannelDuplexHandler;
import io.netty.handler.codec.bytes.ByteArrayDecoder;
import io.netty.handler.codec.bytes.ByteArrayEncoder;

public class ByteArrayCodec extends CombinedChannelDuplexHandler<ByteArrayDecoder, ByteArrayEncoder> {

    public ByteArrayCodec() {
        super(new ByteArrayDecoder(), new ByteArrayEncoder());
    }
}
