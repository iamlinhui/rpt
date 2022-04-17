package cn.promptness.rpt.base.coder;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequestEncoder;
import io.netty.handler.codec.http.HttpResponseEncoder;

import java.util.List;

public class HttpEncoder {

    public static class ResponseEncoder extends HttpResponseEncoder {
        @Override
        public void encode(ChannelHandlerContext ctx, Object msg, List<Object> out) throws Exception {
            super.encode(ctx, msg, out);
        }
    }

    public static class RequestEncoder extends HttpRequestEncoder {
        @Override
        public void encode(ChannelHandlerContext ctx, Object msg, List<Object> out) throws Exception {
            super.encode(ctx, msg, out);
        }
    }

}
