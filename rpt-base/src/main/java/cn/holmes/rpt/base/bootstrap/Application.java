package cn.holmes.rpt.base.bootstrap;


import java.io.IOException;

public abstract class Application<B> {

    public Application() {
        Runtime.getRuntime().addShutdownHook(new Thread(this::stop));
    }

    public Application<B> config(String[] args) {
        return this;
    }

    public abstract Application<B> build() throws IOException;

    public abstract void start(int seconds);

    public abstract void stop();

    public abstract B bootstrap();

    public static void run(String[] args, Application<?>... applications) throws IOException {
        for (Application<?> app : applications) {
            app.config(args).build().start(0);
        }
    }
}
