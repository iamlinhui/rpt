package cn.holmes.rpt.server.coder;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequestEncoder;

import java.util.List;

public class HttpEncoder {

    public static class RequestEncoder extends HttpRequestEncoder {
        @Override
        public void encode(ChannelHandlerContext ctx, Object msg, List<Object> out) throws Exception {
            super.encode(ctx, msg, out);
        }
    }

}
