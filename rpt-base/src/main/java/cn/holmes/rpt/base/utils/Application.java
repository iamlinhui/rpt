package cn.holmes.rpt.base.utils;


import java.io.IOException;

public abstract class Application<B> {

    public Application() {
        Runtime.getRuntime().addShutdownHook(new Thread(this::stop));
    }

    public Application<B> config(String[] args) {
        return this;
    }

    public abstract Application<B> buildBootstrap() throws IOException;

    public abstract boolean start(int seconds) throws Exception;

    public abstract void stop();

    public abstract B bootstrap();

    public static void run(String[] args, Application<?>... applications) throws Exception {
        for (Application<?> application : applications) {
            application.config(args).buildBootstrap().start(0);
        }
    }
}
