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
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Pair;

import java.util.Objects;
import java.util.regex.Pattern;

public class ConfigController {

    private static final int FIELD_WIDTH = 300;
    private static final int LABEL_WIDTH = 70;
    private static final Pattern DIGIT_PATTERN = Pattern.compile("\\d*");
    private static final Pattern NON_DIGIT_PATTERN = Pattern.compile("\\D");

    public static RemoteConfig buildDialog(String confirm, String headerText, RemoteConfig remoteConfig) {
        Pair<ButtonType, Dialog<ButtonType>> pair = createDialog(confirm, headerText);
        Dialog<ButtonType> dialog = pair.getValue();

        ComboBox<ProxyType> proxyType = new ComboBox<>(FXCollections.observableArrayList(ProxyType.values()));
        proxyType.setPrefWidth(FIELD_WIDTH);
        TextField localIp = createTextField(remoteConfig.getLocalIp());
        TextField localPort = createPortField(remoteConfig.getLocalPort());
        TextField domain = createTextField(remoteConfig.getDomain());
        TextField token = createTextField(remoteConfig.getToken());
        TextField remotePort = createPortField(remoteConfig.getRemotePort());
        TextField description = createTextField(remoteConfig.getDescription());

        HBox domainRow = createRow("暴露域名", domain);
        HBox tokenRow = createRow("访问账户", token);
        HBox remotePortRow = createRow("暴露端口", remotePort);

        VBox form = createForm(createRow("传输类型", proxyType), createRow("本地地址", localIp), createRow("本地端口", localPort), domainRow, tokenRow, remotePortRow, createRow("备注", description));

        dialog.getDialogPane().setContent(form);
        Button confirmButton = (Button) dialog.getDialogPane().lookupButton(pair.getKey());

        Runnable validate = () -> {
            ProxyType type = proxyType.getValue();
            boolean isHttp = type == ProxyType.HTTP;
            boolean isTcpOrUdp = type == ProxyType.TCP || type == ProxyType.UDP;

            setNodeVisible(domainRow, isHttp);
            setNodeVisible(tokenRow, isHttp);
            setNodeVisible(remotePortRow, isTcpOrUdp);
            resizeDialog(dialog);

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

        if (!Objects.equals(dialog.showAndWait().orElse(null), pair.getKey())) {
            return null;
        }
        remoteConfig.setProxyType(proxyType.getValue());
        remoteConfig.setLocalIp(localIp.getText());
        remoteConfig.setLocalPort(parsePort(localPort.getText()));
        remoteConfig.setDomain(domain.getText());
        remoteConfig.setToken(token.getText());
        remoteConfig.setRemotePort(parsePort(remotePort.getText()));
        remoteConfig.setDescription(description.getText());
        return remoteConfig;
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

    public static ClientConfig buildDialog(String confirm, String headerText, ClientConfig clientConfig) {
        Pair<ButtonType, Dialog<ButtonType>> pair = createDialog(confirm, headerText);

        TextField serverIp = createTextField(clientConfig.getServerIp());
        TextField serverPort = createPortField(clientConfig.getServerPort());
        TextField clientKey = createTextField(clientConfig.getClientKey());

        VBox form = createForm(createRow("服务端地址", serverIp), createRow("服务端端口", serverPort), createRow("连接秘钥", clientKey));

        pair.getValue().getDialogPane().setContent(form);

        if (!Objects.equals(pair.getValue().showAndWait().orElse(null), pair.getKey())) {
            return null;
        }
        clientConfig.setServerIp(serverIp.getText());
        clientConfig.setServerPort(parsePort(serverPort.getText()));
        clientConfig.setClientKey(clientKey.getText());
        return clientConfig;
    }

    // ==================== Private Helpers ====================

    private static Pair<ButtonType, Dialog<ButtonType>> createDialog(String confirm, String headerText) {
        ButtonType buttonType = new ButtonType(confirm);
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(Constants.Desktop.TITLE);
        dialog.setHeaderText(headerText);
        dialog.initOwner(SystemTrayUtil.getPrimaryStage());
        dialog.getDialogPane().getButtonTypes().add(buttonType);
        return new Pair<>(buttonType, dialog);
    }

    private static VBox createForm(Node... rows) {
        VBox form = new VBox(10);
        form.setPadding(new Insets(20));
        form.getChildren().addAll(rows);
        return form;
    }

    private static HBox createRow(String labelText, Node field) {
        Label label = new Label(labelText);
        label.setMinWidth(LABEL_WIDTH);
        label.setPrefWidth(LABEL_WIDTH);
        HBox row = new HBox(10, label, field);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private static TextField createTextField(String value) {
        TextField field = new TextField(value);
        field.setPrefWidth(FIELD_WIDTH);
        return field;
    }

    private static TextField createPortField(int value) {
        TextField field = createTextField(String.valueOf(value));
        field.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!DIGIT_PATTERN.matcher(newVal).matches()) {
                field.setText(NON_DIGIT_PATTERN.matcher(newVal).replaceAll(""));
            }
        });
        return field;
    }

    private static int parsePort(String text) {
        return StringUtils.hasText(text) ? Integer.parseInt(text) : 0;
    }

    private static void setNodeVisible(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }

    private static void resizeDialog(Dialog<?> dialog) {
        dialog.getDialogPane().requestLayout();
        if (dialog.getDialogPane().getScene() != null) {
            dialog.getDialogPane().getScene().getWindow().sizeToScene();
        }
    }
}
