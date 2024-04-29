package cn.holmes.rpt.base.coder;

import io.netty.channel.CombinedChannelDuplexHandler;
import io.netty.handler.codec.compression.ZlibCodecFactory;
import io.netty.handler.codec.compression.ZlibDecoder;
import io.netty.handler.codec.compression.ZlibEncoder;
import io.netty.handler.codec.compression.ZlibWrapper;

public class GzipCodec extends CombinedChannelDuplexHandler<ZlibDecoder, ZlibEncoder> {

    public GzipCodec() {
        super(ZlibCodecFactory.newZlibDecoder(ZlibWrapper.GZIP), ZlibCodecFactory.newZlibEncoder(ZlibWrapper.GZIP));
    }
}
