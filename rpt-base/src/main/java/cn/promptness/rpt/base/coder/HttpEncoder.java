package cn.promptness.rpt.base.coder;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpRequestEncoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class HttpEncoder {

    private static final Logger logger = LoggerFactory.getLogger(HttpEncoder.class);

    public static List<Object> encode(ChannelHandlerContext ctx, FullHttpResponse fullHttpResponse) throws Exception {
        List<Object> out = new ArrayList<>();
        try {
            new ResponseEncoder().encode(ctx, fullHttpResponse, out);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return out;
    }

    public static List<Object> encode(ChannelHandlerContext ctx, FullHttpRequest fullHttpRequest) {
        List<Object> out = new ArrayList<>();
        try {
            new RequestEncoder().encode(ctx, fullHttpRequest, out);
            return out;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return out;
    }

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
