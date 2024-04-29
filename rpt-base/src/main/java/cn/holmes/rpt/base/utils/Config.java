package cn.holmes.rpt.base.utils;

import cn.holmes.rpt.base.config.ClientConfig;
import cn.holmes.rpt.base.config.ServerConfig;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Optional;

public class Config {

    private static final Logger logger = LoggerFactory.getLogger(Config.class);

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

    public static void setServerConfig(ServerConfig config) {
        serverConfig = config;
    }

    public static void readServerConfig(String[] args) {
        Optional.ofNullable(readConfig(args, ServerConfig.class)).ifPresent(Config::setServerConfig);
    }

    public static void readClientConfig(String[] args) {
        Optional.ofNullable(readConfig(args, ClientConfig.class)).ifPresent(Config::setClientConfig);
    }

    private static <T> T readConfig(String[] args, Class<T> clazz) {
        try {
            Option build = Option.builder().option("c").longOpt("config").hasArg(true).desc("yaml config path").required(false).build();
            DefaultParser parser = DefaultParser.builder().build();
            String configPath = parser.parse(new Options().addOption(build), args).getOptionValue("c");
            if (!StringUtils.hasText(configPath)) {
                return null;
            }
            try (InputStream resource = Files.newInputStream(new File(configPath).toPath())) {
                return new Yaml().loadAs(resource, clazz);
            } catch (Exception e) {
                logger.error("解析配置文件{}异常", configPath);
            }
        } catch (Exception e) {
            logger.error("解析启动参数异常");
        }
        return null;
    }
}
