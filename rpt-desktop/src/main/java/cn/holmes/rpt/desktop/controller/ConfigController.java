package cn.holmes.rpt.desktop.controller;

import cn.holmes.rpt.base.config.ClientConfig;
import cn.holmes.rpt.base.config.ProxyType;
import cn.holmes.rpt.base.config.RemoteConfig;
import cn.holmes.rpt.base.utils.Constants;
import cn.holmes.rpt.base.utils.StringUtils;
import cn.holmes.rpt.desktop.utils.SystemTrayUtil;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.util.Pair;

import java.util.Objects;
import java.util.regex.Pattern;

public class ConfigController {

    private static final Pattern CHECK_REGEX = Pattern.compile("\\d*");
    private static final Pattern REPLACE_REGEX = Pattern.compile("\\D");

    public static Pair<ButtonType, Dialog<ButtonType>> buildDialog(String confirm, String headerTex) {
        ButtonType buttonType = new ButtonType(confirm);
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(Constants.Desktop.TITLE);
        dialog.setHeaderText(headerTex);
        dialog.initOwner(SystemTrayUtil.getPrimaryStage());
        dialog.getDialogPane().getButtonTypes().add(buttonType);
        return new Pair<>(buttonType, dialog);
    }

    public static RemoteConfig buildDialog(String confirm, String headerTex, RemoteConfig remoteConfig) {
        Pair<ButtonType, Dialog<ButtonType>> pair = buildDialog(confirm, headerTex);
        Dialog<ButtonType> dialog = pair.getValue();

        ComboBox<ProxyType> proxyType = new ComboBox<>(FXCollections.observableArrayList(ProxyType.values()));
        proxyType.setPrefWidth(300);
        TextField localIp = new TextField(remoteConfig.getLocalIp());
        localIp.setPrefWidth(300);
        TextField localPort = new TextField(String.valueOf(remoteConfig.getLocalPort()));
        localPort.setPrefWidth(300);
        addPortFilter(localPort);
        TextField domain = new TextField(remoteConfig.getDomain());
        domain.setPrefWidth(300);
        TextField token = new TextField(remoteConfig.getToken());
        token.setPrefWidth(300);
        TextField remotePort = new TextField(String.valueOf(remoteConfig.getRemotePort()));
        remotePort.setPrefWidth(300);
        addPortFilter(remotePort);
        TextField description = new TextField(remoteConfig.getDescription());
        description.setPrefWidth(300);

        HBox domainRow = createRow("暴露域名", domain);
        HBox tokenRow = createRow("访问账户", token);
        HBox remotePortRow = createRow("暴露端口", remotePort);

        VBox form = new VBox(10);
        form.setPadding(new Insets(20));
        form.getChildren().addAll(
                createRow("传输类型", proxyType),
                createRow("本地地址", localIp),
                createRow("本地端口", localPort),
                domainRow,
                tokenRow,
                remotePortRow,
                createRow("备注", description)
        );

        dialog.getDialogPane().setContent(form);
        Button confirmButton = (Button) dialog.getDialogPane().lookupButton(pair.getKey());

        Runnable validate = () -> {
            ProxyType type = proxyType.getValue();
            boolean isHttp = type == ProxyType.HTTP;
            boolean isTcpOrUdp = type == ProxyType.TCP || type == ProxyType.UDP;

            setNodeVisible(domainRow, isHttp);
            setNodeVisible(tokenRow, isHttp);
            setNodeVisible(remotePortRow, isTcpOrUdp);

            dialog.getDialogPane().requestLayout();
            if (dialog.getDialogPane().getScene() != null) {
                dialog.getDialogPane().getScene().getWindow().sizeToScene();
            }

            if (type == null) {
                confirmButton.setDisable(true);
                return;
            }
            boolean valid = StringUtils.hasText(localIp.getText()) && StringUtils.hasText(localPort.getText());
            if (isHttp) {
                valid = valid && StringUtils.hasText(domain.getText());
            }
            if (isTcpOrUdp) {
                valid = valid && StringUtils.hasText(remotePort.getText());
            }
            confirmButton.setDisable(!valid);
        };

        proxyType.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == ProxyType.TCP || newVal == ProxyType.UDP) {
                domain.setText("");
                token.setText("");
            } else if (newVal == ProxyType.HTTP) {
                remotePort.setText("");
            }
            validate.run();
        });
        localIp.textProperty().addListener((obs, o, n) -> validate.run());
        localPort.textProperty().addListener((obs, o, n) -> validate.run());
        domain.textProperty().addListener((obs, o, n) -> validate.run());
        remotePort.textProperty().addListener((obs, o, n) -> validate.run());

        proxyType.setValue(remoteConfig.getProxyType());
        validate.run();

        ButtonType buttonType = dialog.showAndWait().orElse(null);
        if (Objects.equals(buttonType, pair.getKey())) {
            remoteConfig.setProxyType(proxyType.getValue());
            remoteConfig.setLocalIp(localIp.getText());
            remoteConfig.setLocalPort(StringUtils.hasText(localPort.getText()) ? Integer.parseInt(localPort.getText()) : 0);
            remoteConfig.setDomain(domain.getText());
            remoteConfig.setToken(token.getText());
            remoteConfig.setRemotePort(StringUtils.hasText(remotePort.getText()) ? Integer.parseInt(remotePort.getText()) : 0);
            remoteConfig.setDescription(description.getText());
            return remoteConfig;
        }
        return null;
    }

    public static boolean confirmDelete(RemoteConfig remoteConfig) {
        StringBuilder sb = new StringBuilder();
        sb.append("传输类型: ").append(remoteConfig.getProxyType()).append("\n");
        sb.append("本地映射: ").append(remoteConfig.getLocalIp()).append(":").append(remoteConfig.getLocalPort()).append("\n");
        if (Objects.equals(ProxyType.HTTP, remoteConfig.getProxyType())) {
            sb.append("暴露域名: ").append(remoteConfig.getDomain()).append("\n");
            if (StringUtils.hasText(remoteConfig.getToken())) {
                sb.append("访问账户: ").append(remoteConfig.getToken()).append("\n");
            }
        } else {
            sb.append("暴露端口: ").append(remoteConfig.getRemotePort()).append("\n");
        }
        if (StringUtils.hasText(remoteConfig.getDescription())) {
            sb.append("备注: ").append(remoteConfig.getDescription()).append("\n");
        }
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("删除");
        alert.setHeaderText("确定删除?");
        alert.setContentText(sb.toString());
        alert.initOwner(SystemTrayUtil.getPrimaryStage());
        return Objects.equals(ButtonType.OK, alert.showAndWait().orElse(null));
    }

    public static ClientConfig buildDialog(String confirm, String headerTex, ClientConfig clientConfig) {

        Pair<ButtonType, Dialog<ButtonType>> pair = buildDialog(confirm, headerTex);

        TextField serverIp = new TextField(clientConfig.getServerIp());
        TextField serverPort = new TextField(String.valueOf(clientConfig.getServerPort()));
        addPortFilter(serverPort);
        TextField clientKey = new TextField(clientConfig.getClientKey());
        clientKey.setPrefWidth(300);

        GridPane grid = new GridPane();
        grid.setMinWidth(300);
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        grid.add(new Text("服务端地址"), 0, 0);
        grid.add(serverIp, 1, 0);

        grid.add(new Text("服务端端口"), 0, 1);
        grid.add(serverPort, 1, 1);

        grid.add(new Text("连接秘钥"), 0, 3);
        grid.add(clientKey, 1, 3);

        pair.getValue().getDialogPane().setContent(grid);

        ButtonType buttonType = pair.getValue().showAndWait().orElse(null);
        if (Objects.equals(pair.getKey(), buttonType)) {
            clientConfig.setServerIp(serverIp.getText());
            clientConfig.setServerPort(StringUtils.hasText(serverPort.getText()) ? Integer.parseInt(serverPort.getText()) : 0);
            clientConfig.setClientKey(clientKey.getText());
            return clientConfig;
        }
        return null;
    }

    private static HBox createRow(String labelText, Node field) {
        Label label = new Label(labelText);
        label.setMinWidth(70);
        label.setPrefWidth(70);
        HBox row = new HBox(10, label, field);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private static void setNodeVisible(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }

    private static void addPortFilter(TextField field) {
        field.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!CHECK_REGEX.matcher(newValue).matches()) {
                field.setText(REPLACE_REGEX.matcher(newValue).replaceAll(""));
            }
        });
    }
}
