package cn.holmes.rpt.desktop.controller;

import cn.holmes.rpt.base.config.RemoteConfig;
import cn.holmes.rpt.base.utils.Config;
import cn.holmes.rpt.base.utils.Constants;
import cn.holmes.rpt.client.ClientApplication;
import cn.holmes.rpt.desktop.utils.ProgressUtil;
import cn.holmes.rpt.desktop.utils.SystemTrayUtil;
import cn.holmes.rpt.desktop.utils.TooltipUtil;
import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.MenuItem;

import java.util.concurrent.atomic.AtomicReference;

public class MenuController {

    private static final AtomicReference<ClientApplication> CLIENT_REF = new AtomicReference<>();

    @FXML
    public MenuItem startText;
    @FXML
    public MenuItem configText;
    @FXML
    public MenuItem addText;

    @FXML
    public void about() {
        final Hyperlink hyperlink = new Hyperlink("https://github.com/iamlinhui/rpt");
        hyperlink.setOnAction(t -> {
            HostServices hostServices = (HostServices) SystemTrayUtil.getPrimaryStage().getProperties().get("hostServices");
            hostServices.showDocument(hyperlink.getText());
        });
        Alert alert = createAlert("关于");
        alert.setGraphic(hyperlink);
        alert.setContentText(String.format("Version %s %nPowered By Lynn", Constants.Desktop.VERSION));
        alert.showAndWait();
    }

    @FXML
    public void instruction() {
        Alert alert = createAlert("使用说明");
        alert.setContentText("1.先填写服务器配置信息\n2.再填写映射配置信息\n3.最后点击开启服务");
        alert.showAndWait();
    }

    @FXML
    public void add() {
        if (isStart()) {
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
    public void start() {
        Service<Boolean> service = new Service<Boolean>() {
            @Override
            protected Task<Boolean> createTask() {
                return new Task<Boolean>() {
                    @Override
                    protected Boolean call() {
                        return isStart() ? stop() : connect();
                    }
                };
            }
        };
        service.setOnSucceeded(event -> {
            boolean success = service.getValue();
            Platform.runLater(() -> TooltipUtil.show(success ? (isStart() ? "开启成功!" : "关闭成功!") : (isStart() ? "关闭失败!" : "开启失败!")));
            boolean started = isStart();
            startText.setText(started ? "关闭" : "开启");
            MainController.INSTANCE.tableView.setDisable(started);
            configText.setDisable(started);
            addText.setDisable(started);
        });
        ProgressUtil.of(SystemTrayUtil.getPrimaryStage(), service).show();
    }

    public static boolean isStart() {
        return CLIENT_REF.get() != null;
    }

    private static boolean stop() {
        ClientApplication app = CLIENT_REF.getAndSet(null);
        if (app == null) {
            return false;
        }
        app.stop();
        return true;
    }

    private static boolean connect() {
        ClientApplication app = new ClientApplication();
        try {
            if (!app.buildBootstrap().start(0)) {
                app.stop();
                return false;
            }
            if (!CLIENT_REF.compareAndSet(null, app)) {
                app.stop();
                return false;
            }
            return true;
        } catch (Exception e) {
            app.stop();
            return false;
        }
    }

    private Alert createAlert(String header) {
        Alert alert = new Alert(Alert.AlertType.NONE);
        alert.setTitle(Constants.Desktop.TITLE);
        alert.setHeaderText(header);
        alert.initOwner(SystemTrayUtil.getPrimaryStage());
        alert.getButtonTypes().add(ButtonType.CLOSE);
        return alert;
    }
}
