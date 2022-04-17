package cn.promptness.rpt.client.handler.cache;

import java.util.concurrent.atomic.AtomicBoolean;

public class ClientChannelCache {

    private static final AtomicBoolean CONNECT = new AtomicBoolean(false);

    public static boolean getConnect() {
        return CONNECT.get();
    }

    public static void setConnect(boolean connect) {
        CONNECT.set(connect);
    }
}
