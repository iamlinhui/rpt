package cn.promptness.rpt.desktop.controller;

import cn.promptness.rpt.base.config.RemoteConfig;
import cn.promptness.rpt.base.utils.Config;
import cn.promptness.rpt.base.utils.Constants;
import cn.promptness.rpt.base.utils.Pair;
import cn.promptness.rpt.client.ClientApplication;
import cn.promptness.rpt.desktop.utils.SystemTrayUtil;
import cn.promptness.rpt.desktop.utils.TooltipUtil;
import io.netty.channel.nio.NioEventLoopGroup;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.MenuItem;

import javax.net.ssl.SSLException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ScheduledFuture;

public class MenuController {

    private static final ArrayBlockingQueue<Pair<NioEventLoopGroup, ScheduledFuture<?>>> QUEUE = new ArrayBlockingQueue<>(1);

    @FXML
    public MenuItem startText;


    public void initialize() {

    }

    @FXML
    public void about() {
        Alert alert = new Alert(Alert.AlertType.NONE);
        alert.setTitle(Constants.TITLE);
        alert.setHeaderText("关于");
        alert.setContentText("Version 2.2.0\nPowered By Lynn\nhttps://github.com/iamlinhui/rpt");
        alert.initOwner(SystemTrayUtil.getPrimaryStage());
        alert.getButtonTypes().add(ButtonType.CLOSE);
        alert.showAndWait();
    }

    @FXML
    public void instruction() {
        Alert alert = new Alert(Alert.AlertType.NONE);
        alert.setTitle(Constants.TITLE);
        alert.setHeaderText("使用说明");
        alert.setContentText("1.先填写服务器配置信息\n2.再填写映射配置信息\n3.最后点击开启服务");
        alert.initOwner(SystemTrayUtil.getPrimaryStage());
        alert.getButtonTypes().add(ButtonType.CLOSE);
        alert.showAndWait();
    }

    @FXML
    public void add() {
        if (!QUEUE.isEmpty()) {
            TooltipUtil.show("请先关闭连接!");
            return;
        }
        RemoteConfig remoteConfig = ConfigController.buildDialog("确定", "新增映射配置", new RemoteConfig());
        if (remoteConfig != null) {
            MainController.INSTANCE.addConfig(remoteConfig);
        }
    }

    @FXML
    public void close() {
        System.exit(0);
    }

    @FXML
    public void account() {
        ConfigController.buildDialog("确认", "连接配置", Config.getClientConfig());
    }


    @FXML
    public void start() throws SSLException {
        if (QUEUE.isEmpty()) {
            if (connect()) {
                TooltipUtil.show("开启成功!");
            } else {
                TooltipUtil.show("开启失败!");
            }
        } else {
            if (stop()) {
                TooltipUtil.show("关闭成功!");
            } else {
                TooltipUtil.show("关闭失败!");
            }
        }
        startText.setText(isStart() ? "关闭" : "开启");
    }

    public static boolean isStart() {
        return !QUEUE.isEmpty();
    }

    private static boolean stop() {
        if (!QUEUE.isEmpty()) {
            synchronized (QUEUE) {
                if (!QUEUE.isEmpty()) {
                    Pair<NioEventLoopGroup, ScheduledFuture<?>> pair = QUEUE.poll();
                    if (pair == null) {
                        return false;
                    }
                    pair.getValue().cancel(true);
                    pair.getKey().shutdownGracefully();
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean connect() throws SSLException {
        if (QUEUE.isEmpty()) {
            synchronized (QUEUE) {
                if (QUEUE.isEmpty()) {
                    Pair<NioEventLoopGroup, ScheduledFuture<?>> pair = ClientApplication.start();
                    if (!QUEUE.offer(pair)) {
                        pair.getValue().cancel(true);
                        pair.getKey().shutdownGracefully();
                    } else {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
