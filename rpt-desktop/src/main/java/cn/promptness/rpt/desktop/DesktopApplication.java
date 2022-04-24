package cn.promptness.rpt.desktop;

import cn.promptness.rpt.desktop.cache.ClientConfigCache;
import cn.promptness.rpt.desktop.utils.Constants;
import cn.promptness.rpt.desktop.utils.SystemTrayUtil;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.awt.*;
import java.util.Objects;

public class DesktopApplication extends Application {


    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "false");
        if (!SystemTray.isSupported()) {
            System.exit(1);
        }
        Application.launch(DesktopApplication.class, args);
    }

    @Override
    public void init() throws Exception {
        ClientConfigCache.read();
        Runtime.getRuntime().addShutdownHook(new Thread(ClientConfigCache::cache));
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        SystemTrayUtil.systemTray(primaryStage, Constants.TITLE);
        Parent root = new FXMLLoader(this.getClass().getResource("/fxml/main.fxml")).load();
        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        scene.getStylesheets().add(Objects.requireNonNull(this.getClass().getResource("/css/light_theme.css")).toExternalForm());
        primaryStage.setTitle(Constants.TITLE);
        primaryStage.getIcons().add(new Image("/icon.png"));
        primaryStage.setScene(scene);
        primaryStage.setResizable(true);
        primaryStage.show();
    }
}
