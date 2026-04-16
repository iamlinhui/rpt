package cn.holmes.rpt.desktop.controller;

import cn.holmes.rpt.base.config.ProxyType;
import cn.holmes.rpt.base.config.RemoteConfig;
import cn.holmes.rpt.base.utils.Config;
import cn.holmes.rpt.desktop.cache.ClientConfigCache;
import cn.holmes.rpt.desktop.utils.TooltipUtil;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

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

    @FXML
    public void clearLog() {
        textArea.clear();
    }

    public synchronized void addLog(String message) {
        textArea.appendText(LocalDateTime.now() + "|" + Optional.ofNullable(message).orElse("NULL") + "\n");
    }

    public void addConfig(RemoteConfig remoteConfig) {
        CONFIG.add(remoteConfig);
        refreshTable();
        Config.getClientConfig().setConfig(CONFIG);
    }

    public void initialize() {
        ClientConfigCache.read();
        CONFIG.addAll(Optional.ofNullable(Config.getClientConfig().getConfig()).orElse(new ArrayList<>()));

        addColumn("传输类型", "proxyType", 0.15);

        TableColumn<RemoteConfig, String> localCol = new TableColumn<>("本地映射");
        localCol.prefWidthProperty().bind(tableView.widthProperty().multiply(0.25));
        localCol.setCellValueFactory(data -> {
            RemoteConfig config = data.getValue();
            return new SimpleStringProperty(config.getLocalIp() + ":" + config.getLocalPort());
        });
        tableView.getColumns().add(localCol);

        TableColumn<RemoteConfig, String> remoteCol = new TableColumn<>("暴露映射");
        remoteCol.prefWidthProperty().bind(tableView.widthProperty().multiply(0.35));
        remoteCol.setCellValueFactory(data -> {
            RemoteConfig config = data.getValue();
            if (config.getProxyType() == ProxyType.HTTP) {
                String info = Optional.ofNullable(config.getDomain()).orElse("");
                String tokenVal = config.getToken();
                if (tokenVal != null && !tokenVal.isEmpty()) {
                    info += " (" + tokenVal + ")";
                }
                return new SimpleStringProperty(info);
            }
            return new SimpleStringProperty(":" + config.getRemotePort());
        });
        tableView.getColumns().add(remoteCol);

        addColumn("备注", "description", 0.23);

        refreshTable();

        tableView.setRowFactory(param -> {
            TableRow<RemoteConfig> row = new TableRow<>();
            MenuItem editItem = new MenuItem("编辑");
            MenuItem deleteItem = new MenuItem("删除");
            editItem.setOnAction(event -> update(row));
            deleteItem.setOnAction(event -> remove(row));
            ContextMenu contextMenu = new ContextMenu(editItem, deleteItem);
            row.contextMenuProperty().bind(Bindings.when(row.emptyProperty()).then((ContextMenu) null).otherwise(contextMenu));
            return row;
        });
    }

    private void addColumn(String title, String property, double widthRatio) {
        TableColumn<RemoteConfig, ?> column = new TableColumn<>(title);
        column.prefWidthProperty().bind(tableView.widthProperty().multiply(widthRatio));
        column.setCellValueFactory(new PropertyValueFactory<>(property));
        tableView.getColumns().add(column);
    }

    private void refreshTable() {
        tableView.setItems(FXCollections.observableArrayList(CONFIG));
    }

    private boolean checkStarted() {
        if (MenuController.isStart()) {
            TooltipUtil.show("请先关闭连接!");
            return true;
        }
        return false;
    }

    private void update(TableRow<RemoteConfig> row) {
        RemoteConfig remoteConfig = row.getItem();
        if (remoteConfig == null || checkStarted()) {
            return;
        }
        if (ConfigController.buildDialog("修改", "修改映射配置", remoteConfig) != null) {
            refreshTable();
            Config.getClientConfig().setConfig(CONFIG);
            ClientConfigCache.cache();
        }
    }

    private void remove(TableRow<RemoteConfig> row) {
        RemoteConfig remoteConfig = row.getItem();
        if (remoteConfig == null || checkStarted()) {
            return;
        }
        if (ConfigController.confirmDelete(remoteConfig) && CONFIG.remove(remoteConfig)) {
            refreshTable();
            Config.getClientConfig().setConfig(CONFIG);
            ClientConfigCache.cache();
        }
    }

}
