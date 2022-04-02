package cn.promptness.rpt.desktop.cache;

import cn.promptness.rpt.base.config.ClientConfig;
import cn.promptness.rpt.base.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;

public class ClientCache {

    private static final Logger log = LoggerFactory.getLogger(ClientCache.class);

    private static final String CONFIG_FILE = "client.dat";

    public static void read() {
        File account = new File(CONFIG_FILE);
        if (account.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(account))) {
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
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(account))) {
            oos.writeObject(Config.getClientConfig());
            oos.writeObject(null);
            oos.flush();
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }
}
