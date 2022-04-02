package cn.promptness.rpt.desktop.controller;

import cn.promptness.rpt.base.config.ClientConfig;
import cn.promptness.rpt.base.config.Config;
import cn.promptness.rpt.base.config.RemoteConfig;
import cn.promptness.rpt.base.utils.Constants;
import cn.promptness.rpt.base.utils.StringUtils;
import cn.promptness.rpt.client.ClientApplication;
import cn.promptness.rpt.desktop.utils.SystemTrayUtil;
import cn.promptness.rpt.desktop.utils.TooltipUtil;
import io.netty.channel.nio.NioEventLoopGroup;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.text.Text;
import javafx.util.Pair;

import javax.net.ssl.SSLException;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;

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
        alert.setContentText("Version 2.0.0\nPowered By Lynn\nhttps://github.com/iamlinhui/rpt");
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
        RemoteConfig remoteConfig = ConfigController.buildDialog("确定", "新增映射配置", new RemoteConfig());
        if (remoteConfig != null) {
            MainController.addConfig(remoteConfig);
        }
    }

    @FXML
    public void close() {
        System.exit(0);
    }

    @FXML
    public void account() {

        ClientConfig clientConfig = Config.getClientConfig();

        ButtonType cancel = new ButtonType("确定");

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(Constants.TITLE);
        dialog.setHeaderText("连接配置");
        dialog.initOwner(SystemTrayUtil.getPrimaryStage());
        dialog.getDialogPane().getButtonTypes().add(cancel);


        TextField serverIp = new TextField(clientConfig.getServerIp());
        TextField serverPort = new TextField(String.valueOf(clientConfig.getServerPort()));
        serverPort.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue.matches("\\d*")) {
                serverPort.setText(newValue.replaceAll("[^\\d]", ""));
            }
        });
        TextField clientKey = new TextField(clientConfig.getClientKey());

        GridPane grid = new GridPane();
        grid.setMinWidth(300);
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        grid.add(new Text("服务端地址"), 0, 0);
        grid.add(serverIp, 1, 0);

        grid.add(new Text("服务端端口"), 0, 1);
        grid.add(serverPort, 1, 1);

        grid.add(new Text("连接秘钥"), 0, 2);
        grid.add(clientKey, 1, 2);

        dialog.getDialogPane().setContent(grid);

        ButtonType buttonType = dialog.showAndWait().orElse(null);
        if (Objects.equals(cancel, buttonType)) {
            clientConfig.setServerIp(serverIp.getText());
            clientConfig.setServerPort(StringUtils.hasText(serverPort.getText()) ? Integer.parseInt(serverPort.getText()) : 0);
            clientConfig.setClientKey(clientKey.getText());
        }

    }

    @FXML
    public void start() throws SSLException {
        Pair<NioEventLoopGroup, ScheduledFuture<?>> pair = ClientApplication.getPair();
        if (pair == null) {
            ClientApplication.main(new String[0]);
            startText.setText("关闭");
            TooltipUtil.show("开启成功!");
        } else {
            pair.getValue().cancel(true);
            pair.getKey().shutdownGracefully();
            ClientApplication.clear();
            startText.setText("开启");
            TooltipUtil.show("关闭成功!");
        }
    }
}
