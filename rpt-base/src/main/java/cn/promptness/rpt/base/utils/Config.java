package cn.promptness.rpt.base.utils;

import cn.promptness.rpt.base.config.ClientConfig;
import cn.promptness.rpt.base.config.ServerConfig;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;

public class Config {

    private static ServerConfig serverConfig;
    private static ClientConfig clientConfig;

    static {
        InputStream resource = ClassLoader.getSystemResourceAsStream("server.yml");
        if (resource != null) {
            serverConfig = new Yaml().loadAs(resource, ServerConfig.class);
        }
    }

    static {
        InputStream resource = ClassLoader.getSystemResourceAsStream("client.yml");
        if (resource != null) {
            clientConfig = new Yaml().loadAs(resource, ClientConfig.class);
        }
    }

    public static ClientConfig getClientConfig() {
        return clientConfig;
    }

    public static void setClientConfig(ClientConfig config) {
        clientConfig = config;
    }

    public static ServerConfig getServerConfig() {
        return serverConfig;
    }
}
