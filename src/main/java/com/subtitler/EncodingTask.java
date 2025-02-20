package com.subtitler;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.IntegerProperty;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicBoolean;

public class EncodingTask {
    private final StringProperty filename = new SimpleStringProperty();
    private final StringProperty status = new SimpleStringProperty();
    private final IntegerProperty progress = new SimpleIntegerProperty(0);
    private final StringProperty startTime = new SimpleStringProperty();
    private final StringProperty endTime = new SimpleStringProperty();
    private String errorMessage;
    private Process ffmpegProcess;
    private final AtomicBoolean isCancelled = new AtomicBoolean(false);
    private Thread encodingThread;

    public EncodingTask(String filename) {
        this.filename.set(filename);
        this.status.set("等待开始");
        this.startTime.set(LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
    }

    public String getFilename() { return filename.get(); }
    public StringProperty filenameProperty() { return filename; }

    public String getStatus() { return status.get(); }
    public StringProperty statusProperty() { return status; }
    public void setStatus(String status) { this.status.set(status); }

    public int getProgress() { return progress.get(); }
    public IntegerProperty progressProperty() { return progress; }
    public void setProgress(int progress) { this.progress.set(progress); }

    public String getStartTime() { return startTime.get(); }
    public String getEndTime() { return endTime.get(); }
    public void setEndTime(String endTime) { this.endTime.set(endTime); }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public void setFfmpegProcess(Process process) {
        this.ffmpegProcess = process;
    }

    public void setEncodingThread(Thread thread) {
        this.encodingThread = thread;
    }

    public boolean isCancelled() {
        return isCancelled.get();
    }

    public void cancel() {
        isCancelled.set(true);
        if (ffmpegProcess != null) {
            ffmpegProcess.destroy();
        }
        if (encodingThread != null) {
            encodingThread.interrupt();
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(startTime.get()).append(" ");
        sb.append(filename.get());
        if (progress.get() < 100 && "正在编码".equals(status.get())) {
            sb.append(" (").append(progress.get()).append("%)");
        }
        sb.append(" - ").append(status.get());
        if (errorMessage != null) {
            sb.append(" [").append(errorMessage).append("]");
        }
        if (endTime.get() != null) {
            sb.append(" 完成时间: ").append(endTime.get());
        }
        return sb.toString();
    }
} 