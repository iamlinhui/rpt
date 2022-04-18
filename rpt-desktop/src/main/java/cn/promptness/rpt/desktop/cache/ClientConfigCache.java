package cn.promptness.rpt.desktop.cache;

import cn.promptness.rpt.base.config.ClientConfig;
import cn.promptness.rpt.base.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;

public class ClientConfigCache {

    private ClientConfigCache() {
    }

    private static final Logger log = LoggerFactory.getLogger(ClientConfigCache.class);

    private static final String CONFIG_FILE = "client.dat";

    public static void read() {
        File account = new File(CONFIG_FILE);
        if (account.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(Files.newInputStream(account.toPath()))) {
                Object object;
                while ((object = ois.readObject()) != null) {
                    ClientConfig clientConfig = (ClientConfig) object;
                    Config.setClientConfig(clientConfig);
                }
            } catch (IOException | ClassNotFoundException e) {
                log.error(e.getMessage());
            }
        }
    }

    public static void cache() {
        File account = new File(CONFIG_FILE);
        try (ObjectOutputStream oos = new ObjectOutputStream(Files.newOutputStream(account.toPath()))) {
            oos.writeObject(Config.getClientConfig());
            oos.writeObject(null);
            oos.flush();
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }
}
