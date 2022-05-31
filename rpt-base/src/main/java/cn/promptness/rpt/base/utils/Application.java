package cn.promptness.rpt.base.utils;


import java.io.IOException;

public interface Application<B> {

    Application<B> config(String[] args);

    Application<B> buildBootstrap() throws IOException;

    boolean start(int seconds) throws Exception;

    void stop();

    B bootstrap();

    static void run(String[] args, Application<?>... applications) throws Exception {
        for (Application<?> application : applications) {
            application.config(args).buildBootstrap().start(0);
        }
    }
}