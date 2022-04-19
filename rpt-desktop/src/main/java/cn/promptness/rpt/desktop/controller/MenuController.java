package cn.promptness.rpt.desktop.controller;

import cn.promptness.rpt.base.utils.Config;
import cn.promptness.rpt.base.config.RemoteConfig;
import cn.promptness.rpt.base.utils.Constants;
import cn.promptness.rpt.client.ClientApplication;
import cn.promptness.rpt.desktop.utils.SystemTrayUtil;
import cn.promptness.rpt.desktop.utils.TooltipUtil;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.MenuItem;

import javax.net.ssl.SSLException;

public class MenuController {


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
        if (ClientApplication.isStart()) {
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
        if (ClientApplication.isStart()) {
            ClientApplication.stop();
            startText.setText("开启");
            TooltipUtil.show("关闭成功!");
        } else {
            ClientApplication.main(new String[0]);
            startText.setText("关闭");
            TooltipUtil.show("开启成功!");
        }
    }
}
