package cn.promptness.rpt.desktop.cache;

import cn.promptness.rpt.base.config.ClientConfig;
import cn.promptness.rpt.base.serialize.api.ObjectInputStream;
import cn.promptness.rpt.base.serialize.api.ObjectOutputStream;
import cn.promptness.rpt.base.serialize.api.SerializationType;
import cn.promptness.rpt.base.serialize.api.SerializeFactory;
import cn.promptness.rpt.base.utils.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Optional;

public class ClientConfigCache {

    private ClientConfigCache() {
    }

    private static final Logger logger = LoggerFactory.getLogger(ClientConfigCache.class);

    private static final File CONFIG_FILE = new File("client.json");

    public static void read() {
        if (CONFIG_FILE.exists()) {
            try (ObjectInputStream ois = SerializeFactory.getSerialization(SerializationType.JSON).deserialize(Files.newInputStream(CONFIG_FILE.toPath()))) {
                Optional.ofNullable(ois.readObject(ClientConfig.class)).ifPresent(Config::setClientConfig);
            } catch (Exception e) {
                logger.error("解析缓存配置文件异常");
            }
        }
    }

    public static void cache() {
        try (ObjectOutputStream oos = SerializeFactory.getSerialization(SerializationType.JSON).serialize(Files.newOutputStream(CONFIG_FILE.toPath()))) {
            oos.writeObject(Config.getClientConfig());
            oos.flush();
        } catch (IOException e) {
            logger.error(e.getMessage());
        }
    }
}
