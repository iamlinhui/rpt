package cn.promptness.rpt.base.utils;


import io.netty.bootstrap.AbstractBootstrap;

import java.io.IOException;

public interface Application {

    Application config(String[] args);

    Application buildBootstrap() throws IOException;

    boolean start(int seconds) throws Exception;

    void stop();

    AbstractBootstrap<?, ?> bootstrap();
}