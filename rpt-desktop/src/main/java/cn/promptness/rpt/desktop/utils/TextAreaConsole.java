package cn.promptness.rpt.desktop.utils;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import cn.promptness.rpt.desktop.controller.MainController;
import javafx.application.Platform;

public class TextAreaConsole extends UnsynchronizedAppenderBase<ILoggingEvent> {
    @Override
    protected void append(ILoggingEvent eventObject) {
        Platform.runLater(() -> MainController.INSTANCE.addLog(eventObject.getFormattedMessage()));
    }
}
