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
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

public class MainController {

    public static volatile MainController INSTANCE;

    private static final List<RemoteConfig> CONFIG = new CopyOnWriteArrayList<>();

    @FXML
    public TableView<RemoteConfig> tableView;
    @FXML
    public TextArea textArea;

    public MainController() {
        if (INSTANCE == null) {
            synchronized (MainController.class) {
                if (INSTANCE == null) {
                    INSTANCE = this;
                }
            }
        }
    }

    // ==================== FXML Lifecycle ====================

    public void initialize() {
        ClientConfigCache.read();
        List<RemoteConfig> saved = Config.getClientConfig().getConfig();
        if (saved != null) {
            CONFIG.addAll(saved);
        }
        initColumns();
        initRowFactory();
        refreshTable();
    }

    @FXML
    public void clearLog() {
        textArea.clear();
    }

    // ==================== Public API ====================

    public synchronized void addLog(String message) {
        textArea.appendText(LocalDateTime.now() + "|" + (message != null ? message : "NULL") + "\n");
    }

    public void addConfig(RemoteConfig remoteConfig) {
        CONFIG.add(remoteConfig);
        refreshTable();
        Config.getClientConfig().setConfig(CONFIG);
    }

    // ==================== Private Helpers ====================

    private void initColumns() {
        addSimpleColumn("传输类型", "proxyType", 0.15);

        TableColumn<RemoteConfig, String> localCol = new TableColumn<>("本地映射");
        localCol.prefWidthProperty().bind(tableView.widthProperty().multiply(0.25));
        localCol.setCellValueFactory(data -> {
            RemoteConfig config = data.getValue();
            return new SimpleStringProperty(config.getLocalIp() + ":" + config.getLocalPort());
        });
        tableView.getColumns().add(localCol);

        TableColumn<RemoteConfig, String> remoteCol = new TableColumn<>("暴露映射");
        remoteCol.prefWidthProperty().bind(tableView.widthProperty().multiply(0.35));
        remoteCol.setCellValueFactory(data -> new SimpleStringProperty(formatRemoteMapping(data.getValue())));
        tableView.getColumns().add(remoteCol);

        addSimpleColumn("备注", "description", 0.23);
    }

    private void initRowFactory() {
        tableView.setRowFactory(param -> {
            TableRow<RemoteConfig> row = new TableRow<>();
            MenuItem editItem = new MenuItem("编辑");
            MenuItem deleteItem = new MenuItem("删除");
            editItem.setOnAction(event -> updateConfig(row));
            deleteItem.setOnAction(event -> removeConfig(row));
            ContextMenu contextMenu = new ContextMenu(editItem, deleteItem);
            row.contextMenuProperty().bind(Bindings.when(row.emptyProperty()).then((ContextMenu) null).otherwise(contextMenu));
            return row;
        });
    }

    private void addSimpleColumn(String title, String property, double widthRatio) {
        TableColumn<RemoteConfig, ?> column = new TableColumn<>(title);
        column.prefWidthProperty().bind(tableView.widthProperty().multiply(widthRatio));
        column.setCellValueFactory(new PropertyValueFactory<>(property));
        tableView.getColumns().add(column);
    }

    private String formatRemoteMapping(RemoteConfig config) {
        if (config.getProxyType() == ProxyType.HTTP) {
            String info = Optional.ofNullable(config.getDomain()).orElse("");
            String tokenVal = config.getToken();
            if (tokenVal != null && !tokenVal.isEmpty()) {
                info += " (" + tokenVal + ")";
            }
            return info;
        }
        return ":" + config.getRemotePort();
    }

    private void refreshTable() {
        tableView.setItems(FXCollections.observableArrayList(CONFIG));
    }

    private void saveAndRefresh() {
        refreshTable();
        Config.getClientConfig().setConfig(CONFIG);
        ClientConfigCache.cache();
    }

    private boolean checkStarted() {
        if (MenuController.isStart()) {
            TooltipUtil.show("请先关闭连接!");
            return true;
        }
        return false;
    }

    private void updateConfig(TableRow<RemoteConfig> row) {
        RemoteConfig remoteConfig = row.getItem();
        if (remoteConfig == null || checkStarted()) {
            return;
        }
        if (ConfigController.buildDialog("修改", "修改映射配置", remoteConfig) != null) {
            saveAndRefresh();
        }
    }

    private void removeConfig(TableRow<RemoteConfig> row) {
        RemoteConfig remoteConfig = row.getItem();
        if (remoteConfig == null || checkStarted()) {
            return;
        }
        if (ConfigController.confirmDelete(remoteConfig) && CONFIG.remove(remoteConfig)) {
            saveAndRefresh();
        }
    }
}
