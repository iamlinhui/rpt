package cn.promptness.rpt.base.coder;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequestEncoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.codec.http.websocketx.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HttpEncoder {

    public static class ResponseEncoder extends HttpResponseEncoder {
        @Override
        public void encode(ChannelHandlerContext ctx, Object msg, List<Object> out) throws Exception {
            super.encode(ctx, msg, out);
        }
    }

    public static BiFunctionException<ChannelHandlerContext, WebSocketFrame, List<Object>> websocketEncode(WebSocketVersion webSocketVersion) {
        return webSocketEncoderMap.get(webSocketVersion);
    }

    public interface BiFunctionException<T, U, R> {
        R apply(T t, U u) throws Exception;
    }

    private static final Map<WebSocketVersion, BiFunctionException<ChannelHandlerContext, WebSocketFrame, List<Object>>> webSocketEncoderMap = new HashMap() {{
        put(WebSocketVersion.V00, new BiFunctionException<ChannelHandlerContext, WebSocketFrame, List<Object>>() {
            @Override
            public List<Object> apply(ChannelHandlerContext channelHandlerContext, WebSocketFrame webSocketFrame) throws Exception {
                List<Object> objects = new ArrayList<>();
                new WebSocket00FrameEncoder() {
                    @Override
                    public void encode(ChannelHandlerContext ctx, WebSocketFrame msg, List<Object> out) throws Exception {
                        super.encode(ctx, msg, out);
                    }
                }.encode(channelHandlerContext, webSocketFrame, objects);
                return objects;
            }
        });
        put(WebSocketVersion.V07, new BiFunctionException<ChannelHandlerContext, WebSocketFrame, List<Object>>() {
            @Override
            public List<Object> apply(ChannelHandlerContext channelHandlerContext, WebSocketFrame webSocketFrame) throws Exception {
                List<Object> objects = new ArrayList<>();
                new WebSocket07FrameEncoder(false) {
                    @Override
                    public void encode(ChannelHandlerContext ctx, WebSocketFrame msg, List<Object> out) throws Exception {
                        super.encode(ctx, msg, out);
                    }
                }.encode(channelHandlerContext, webSocketFrame, objects);
                return objects;
            }
        });
        put(WebSocketVersion.V08, new BiFunctionException<ChannelHandlerContext, WebSocketFrame, List<Object>>() {
            @Override
            public List<Object> apply(ChannelHandlerContext channelHandlerContext, WebSocketFrame webSocketFrame) throws Exception {
                List<Object> objects = new ArrayList<>();
                new WebSocket08FrameEncoder(false) {
                    @Override
                    public void encode(ChannelHandlerContext ctx, WebSocketFrame msg, List<Object> out) throws Exception {
                        super.encode(ctx, msg, out);
                    }
                }.encode(channelHandlerContext, webSocketFrame, objects);
                return objects;
            }
        });
        put(WebSocketVersion.V13, new BiFunctionException<ChannelHandlerContext, WebSocketFrame, List<Object>>() {
            @Override
            public List<Object> apply(ChannelHandlerContext channelHandlerContext, WebSocketFrame webSocketFrame) throws Exception {
                List<Object> objects = new ArrayList<>();
                new WebSocket13FrameEncoder(false) {
                    @Override
                    public void encode(ChannelHandlerContext ctx, WebSocketFrame msg, List<Object> out) throws Exception {
                        super.encode(ctx, msg, out);
                    }
                }.encode(channelHandlerContext, webSocketFrame, objects);
                return objects;
            }
        });
    }};

    public static class WebSocketEncoder extends WebSocket00FrameEncoder {
        @Override
        public void encode(ChannelHandlerContext ctx, WebSocketFrame msg, List<Object> out) throws Exception {
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
