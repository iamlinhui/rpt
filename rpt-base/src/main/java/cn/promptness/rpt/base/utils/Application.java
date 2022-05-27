package cn.promptness.rpt.base.utils;


import io.netty.bootstrap.Bootstrap;

public interface Application<R> {
    R start(int value) throws Exception;

    Bootstrap bootstrap();
}