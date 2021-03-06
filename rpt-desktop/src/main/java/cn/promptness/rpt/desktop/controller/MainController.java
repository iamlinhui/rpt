package cn.promptness.rpt.desktop.controller;

import cn.promptness.rpt.base.config.RemoteConfig;
import cn.promptness.rpt.base.utils.Config;
import cn.promptness.rpt.desktop.cache.ClientConfigCache;
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
        textArea.appendText(Optional.ofNullable(message).orElse("NULL"));
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
        ClientConfigCache.read();
        ReadOnlyDoubleProperty widthProperty = tableView.widthProperty();
        CONFIG.addAll(Optional.ofNullable(Config.getClientConfig().getConfig()).orElse(new ArrayList<>()));
        TableColumn<RemoteConfig, String> proxyType = new TableColumn<>("????????????");
        proxyType.prefWidthProperty().bind(widthProperty.multiply(.14));
        proxyType.setCellValueFactory(new PropertyValueFactory<>("proxyType"));

        TableColumn<RemoteConfig, String> localIp = new TableColumn<>("????????????");
        localIp.prefWidthProperty().bind(widthProperty.multiply(.14));
        localIp.setCellValueFactory(new PropertyValueFactory<>("localIp"));

        TableColumn<RemoteConfig, Integer> localPort = new TableColumn<>("????????????");
        localPort.prefWidthProperty().bind(widthProperty.multiply(.14));
        localPort.setCellValueFactory(new PropertyValueFactory<>("localPort"));

        TableColumn<RemoteConfig, String> domain = new TableColumn<>("????????????");
        domain.prefWidthProperty().bind(widthProperty.multiply(.14));
        domain.setCellValueFactory(new PropertyValueFactory<>("domain"));

        TableColumn<RemoteConfig, String> token = new TableColumn<>("????????????");
        token.prefWidthProperty().bind(widthProperty.multiply(.14));
        token.setCellValueFactory(new PropertyValueFactory<>("token"));

        TableColumn<RemoteConfig, Integer> remotePort = new TableColumn<>("????????????");
        remotePort.prefWidthProperty().bind(widthProperty.multiply(.14));
        remotePort.setCellValueFactory(new PropertyValueFactory<>("remotePort"));

        TableColumn<RemoteConfig, String> description = new TableColumn<>("??????");
        description.prefWidthProperty().bind(widthProperty.multiply(.14));
        description.setCellValueFactory(new PropertyValueFactory<>("description"));

        tableView.getColumns().addAll(proxyType, localIp, localPort, domain, token, remotePort, description);

        tableView.setItems(FXCollections.observableArrayList(CONFIG));

        tableView.setRowFactory(param -> {
            TableRow<RemoteConfig> remoteConfigTableRow = new TableRow<>();
            remoteConfigTableRow.setOnMousePressed(event -> {
                MouseButton button = event.getButton();
                //??????????????????
                if (button == MouseButton.PRIMARY && event.getClickCount() == MouseEvent.BUTTON2) {
                    update(remoteConfigTableRow);
                }
                // ????????????
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
            TooltipUtil.show("??????????????????!");
            return;
        }
        RemoteConfig result = ConfigController.buildDialog("??????", "??????????????????", remoteConfig);
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
            TooltipUtil.show("??????????????????!");
            return;
        }
        RemoteConfig result = ConfigController.buildDialog("??????", "??????????????????", remoteConfig);
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
