package cn.promptness.rpt.base.coder;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpRequestEncoder;
import io.netty.handler.codec.http.HttpResponseEncoder;

import java.util.ArrayList;
import java.util.List;

public class HttpEncoder {


    public static List<Object> encode(ChannelHandlerContext ctx, FullHttpResponse fullHttpResponse) throws Exception {
        List<Object> out = new ArrayList<>();
        RESPONSE_ENCODER.encode(ctx, fullHttpResponse, out);
        return out;
    }

    public static List<Object> encode(ChannelHandlerContext ctx, FullHttpRequest fullHttpRequest) throws Exception {
        List<Object> out = new ArrayList<>();
        REQUEST_ENCODER.encode(ctx, fullHttpRequest, out);
        return out;
    }

    private static final ResponseEncoder RESPONSE_ENCODER = new ResponseEncoder();
    private static final RequestEncoder REQUEST_ENCODER = new RequestEncoder();

    private static class ResponseEncoder extends HttpResponseEncoder {
        @Override
        protected void encode(ChannelHandlerContext ctx, Object msg, List<Object> out) throws Exception {
            super.encode(ctx, msg, out);
        }
    }

    private static class RequestEncoder extends HttpRequestEncoder {
        @Override
        protected void encode(ChannelHandlerContext ctx, Object msg, List<Object> out) throws Exception {
            super.encode(ctx, msg, out);
        }
    }

}
