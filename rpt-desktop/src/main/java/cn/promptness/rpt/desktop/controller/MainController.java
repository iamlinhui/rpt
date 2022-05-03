package cn.promptness.rpt.desktop.controller;

import cn.promptness.rpt.base.config.RemoteConfig;
import cn.promptness.rpt.base.utils.Config;
import cn.promptness.rpt.desktop.utils.TooltipUtil;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.MouseButton;

import java.awt.event.MouseEvent;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;


public class MainController {

    public static volatile MainController INSTANCE;

    private static final List<RemoteConfig> CONFIG = new CopyOnWriteArrayList<>();

    public MainController() {
        if (INSTANCE == null) {
            synchronized (MainController.class) {
                if (INSTANCE == null) {
                    INSTANCE = this;
                }
            }
        }
    }

    @FXML
    public TableView<RemoteConfig> tableView;
    @FXML
    public TextArea textArea;

    public synchronized void addLog(String message) {
        textArea.appendText(LocalDateTime.now().toString());
        textArea.appendText("|");
        textArea.appendText(message);
        textArea.appendText("\n");
    }

    public void addConfig(RemoteConfig remoteConfig) {
        CONFIG.add(remoteConfig);
        tableView.getItems().clear();
        tableView.setItems(FXCollections.observableArrayList(CONFIG));
        Config.getClientConfig().setConfig(CONFIG);
    }

    @SuppressWarnings("unchecked")
    public void initialize() {
        ReadOnlyDoubleProperty widthProperty = tableView.widthProperty();
        CONFIG.addAll(Optional.ofNullable(Config.getClientConfig().getConfig()).orElse(new ArrayList<>()));
        TableColumn<RemoteConfig, String> proxyType = new TableColumn<>("传输类型");
        proxyType.prefWidthProperty().bind(widthProperty.multiply(.14));
        proxyType.setCellValueFactory(new PropertyValueFactory<>("proxyType"));

        TableColumn<RemoteConfig, String> localIp = new TableColumn<>("本地地址");
        localIp.prefWidthProperty().bind(widthProperty.multiply(.14));
        localIp.setCellValueFactory(new PropertyValueFactory<>("localIp"));

        TableColumn<RemoteConfig, Integer> localPort = new TableColumn<>("本地端口");
        localPort.prefWidthProperty().bind(widthProperty.multiply(.14));
        localPort.setCellValueFactory(new PropertyValueFactory<>("localPort"));

        TableColumn<RemoteConfig, String> domain = new TableColumn<>("暴露域名");
        domain.prefWidthProperty().bind(widthProperty.multiply(.14));
        domain.setCellValueFactory(new PropertyValueFactory<>("domain"));

        TableColumn<RemoteConfig, String> token = new TableColumn<>("访问账户");
        token.prefWidthProperty().bind(widthProperty.multiply(.14));
        token.setCellValueFactory(new PropertyValueFactory<>("token"));

        TableColumn<RemoteConfig, Integer> remotePort = new TableColumn<>("暴露端口");
        remotePort.prefWidthProperty().bind(widthProperty.multiply(.14));
        remotePort.setCellValueFactory(new PropertyValueFactory<>("remotePort"));

        TableColumn<RemoteConfig, String> description = new TableColumn<>("备注");
        description.prefWidthProperty().bind(widthProperty.multiply(.14));
        description.setCellValueFactory(new PropertyValueFactory<>("description"));

        tableView.getColumns().addAll(proxyType, localIp, localPort, domain, token, remotePort, description);

        tableView.setItems(FXCollections.observableArrayList(CONFIG));

        tableView.setRowFactory(param -> {
            TableRow<RemoteConfig> remoteConfigTableRow = new TableRow<>();
            remoteConfigTableRow.setOnMousePressed(event -> {
                MouseButton button = event.getButton();
                //左键双击操作
                if (button == MouseButton.PRIMARY && event.getClickCount() == MouseEvent.BUTTON2) {
                    update(remoteConfigTableRow);
                }
                // 右键点击
                if (button == MouseButton.SECONDARY && event.getClickCount() == MouseEvent.BUTTON1) {
                    remove(remoteConfigTableRow);
                }
            });
            return remoteConfigTableRow;
        });
    }

    private void update(TableRow<RemoteConfig> remoteConfigTableRow) {
        RemoteConfig remoteConfig = remoteConfigTableRow.getItem();
        if (remoteConfig == null) {
            return;
        }
        if (MenuController.isStart()) {
            TooltipUtil.show("请先关闭连接!");
            return;
        }
        RemoteConfig result = ConfigController.buildDialog("修改", "修改映射配置", remoteConfig);
        if (result == null) {
            return;
        }
        tableView.getItems().clear();
        tableView.setItems(FXCollections.observableArrayList(CONFIG));
        Config.getClientConfig().setConfig(CONFIG);
    }

    private void remove(TableRow<RemoteConfig> remoteConfigTableRow) {
        RemoteConfig remoteConfig = remoteConfigTableRow.getItem();
        if (remoteConfig == null) {
            return;
        }
        if (MenuController.isStart()) {
            TooltipUtil.show("请先关闭连接!");
            return;
        }
        RemoteConfig result = ConfigController.buildDialog("删除", "删除映射配置", remoteConfig);
        if (result == null) {
            return;
        }
        if (CONFIG.remove(remoteConfigTableRow.getItem())) {
            tableView.getItems().clear();
            tableView.setItems(FXCollections.observableArrayList(CONFIG));
            Config.getClientConfig().setConfig(CONFIG);
        }
    }

}
