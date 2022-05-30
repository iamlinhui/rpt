package cn.promptness.rpt.base.utils;


import io.netty.bootstrap.AbstractBootstrap;

import java.io.IOException;

public interface Application<R> {

    Application<R> config(String[] args);

    Application<R> buildBootstrap() throws IOException;

    R start(int value) throws Exception;

    AbstractBootstrap<?, ?> bootstrap();
}