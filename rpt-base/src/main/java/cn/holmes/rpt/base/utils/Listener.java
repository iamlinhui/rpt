package cn.holmes.rpt.base.utils;

import io.netty.channel.Channel;

public interface Listener<T> {

    void success(Channel serverChannel, Channel proxyChannel, T t);

    void fail(Channel serverChannel, T t);
}
