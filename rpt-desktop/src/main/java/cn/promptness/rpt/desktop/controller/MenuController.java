package cn.promptness.rpt.desktop.controller;

import cn.promptness.rpt.base.config.RemoteConfig;
import cn.promptness.rpt.base.utils.Config;
import cn.promptness.rpt.base.utils.Constants;
import cn.promptness.rpt.client.ClientApplication;
import cn.promptness.rpt.desktop.utils.ProgressUtil;
import cn.promptness.rpt.desktop.utils.SystemTrayUtil;
import cn.promptness.rpt.desktop.utils.TooltipUtil;
import io.netty.channel.nio.NioEventLoopGroup;
import javafx.application.Platform;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.MenuItem;

import java.util.concurrent.ArrayBlockingQueue;

public class MenuController {

    private static final ArrayBlockingQueue<NioEventLoopGroup> QUEUE = new ArrayBlockingQueue<>(1);

    @FXML
    public MenuItem startText;
    @FXML
    public MenuItem configText;
    @FXML
    public MenuItem addText;


    public void initialize() {

    }

    @FXML
    public void about() {
        Alert alert = new Alert(Alert.AlertType.NONE);
        alert.setTitle(Constants.TITLE);
        alert.setHeaderText("关于");
        alert.setContentText(String.format(" Version %s %n Powered By Lynn %n https://github.com/iamlinhui/rpt", Constants.VERSION));
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
    public void start() {
        Service<Void> service = new Service<Void>() {
            @Override
            protected Task<Void> createTask() {
                return new Task<Void>() {
                    @Override
                    protected Void call() throws Exception {
                        if (QUEUE.isEmpty()) {
                            if (connect()) {
                                Platform.runLater(() -> TooltipUtil.show("开启成功!"));
                            } else {
                                Platform.runLater(() -> TooltipUtil.show("开启失败!"));
                            }
                        } else {
                            if (stop()) {
                                Platform.runLater(() -> TooltipUtil.show("关闭成功!"));
                            } else {
                                Platform.runLater(() -> TooltipUtil.show("关闭失败!"));
                            }
                        }
                        return null;
                    }
                };
            }
        };
        service.setOnSucceeded(event -> {
            startText.setText(isStart() ? "关闭" : "开启");
            MainController.INSTANCE.tableView.setDisable(isStart());
            configText.setDisable(isStart());
            addText.setDisable(isStart());
        });
        ProgressUtil.of(SystemTrayUtil.getPrimaryStage(), service).show();
    }

    public static boolean isStart() {
        return !QUEUE.isEmpty();
    }

    private static boolean stop() {
        if (!QUEUE.isEmpty()) {
            synchronized (QUEUE) {
                if (!QUEUE.isEmpty()) {
                    NioEventLoopGroup nioEventLoopGroup = QUEUE.poll();
                    if (nioEventLoopGroup == null) {
                        return false;
                    }
                    nioEventLoopGroup.shutdownGracefully();
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean connect() {
        if (QUEUE.isEmpty()) {
            synchronized (QUEUE) {
                if (QUEUE.isEmpty()) {
                    NioEventLoopGroup nioEventLoopGroup = new NioEventLoopGroup();
                    try {
                        boolean start = new ClientApplication().buildBootstrap(nioEventLoopGroup).get();
                        if (!start) {
                            nioEventLoopGroup.shutdownGracefully();
                            return false;
                        }
                        if (!QUEUE.offer(nioEventLoopGroup)) {
                            nioEventLoopGroup.shutdownGracefully();
                        } else {
                            return true;
                        }
                    } catch (Exception e) {
                        nioEventLoopGroup.shutdownGracefully();
                    }
                }
            }
        }
        return false;
    }
}
