package com.subtitler;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.stage.FileChooser;
import java.io.File;
import javafx.scene.media.Media;
import javafx.util.Duration;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.ArrayList;
import javafx.application.Platform;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import javafx.scene.control.Slider;
import javafx.beans.value.ChangeListener;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import java.util.Map;
import java.util.HashMap;

public class MainViewController {
    @FXML private MediaView mediaView;
    @FXML private Label timeLabel;
    @FXML private TableView<Subtitle> subtitleTable;
    @FXML private TableColumn<Subtitle, String> startTimeColumn;
    @FXML private TableColumn<Subtitle, String> endTimeColumn;
    @FXML private TableColumn<Subtitle, String> contentColumn;
    
    private MediaPlayer mediaPlayer;
    private File currentVideoFile;
    @FXML private ProgressBar progressBar;
    private ObservableList<Subtitle> subtitles = FXCollections.observableArrayList();
    @FXML private Slider timeSlider;
    @FXML private TextArea subtitleInput;
    
    @FXML
    public void initialize() {
        // 初始化表格列
        startTimeColumn.setCellValueFactory(data -> data.getValue().startTimeProperty());
        endTimeColumn.setCellValueFactory(data -> data.getValue().endTimeProperty());
        contentColumn.setCellValueFactory(data -> data.getValue().contentProperty());
        
        // 修改表格点击事件
        subtitleTable.setOnMouseClicked(event -> {
            if (event.getClickCount() == 1) {
                Subtitle selected = subtitleTable.getSelectionModel().getSelectedItem();
                if (selected != null && mediaPlayer != null) {
                    double currentTime = mediaPlayer.getCurrentTime().toSeconds();
                    double cellWidth = contentColumn.getWidth();
                    double startColumnX = 0;
                    double endColumnX = startTimeColumn.getWidth() + endTimeColumn.getWidth();
                    
                    if (event.getX() < startTimeColumn.getWidth()) {
                        selected.setStartTime(formatTimeWithMillis(currentTime));
                        // 更新左侧文本区域
                        updateTextAreaFromSubtitles();
                        // 强制更新表格显示
                        subtitleTable.refresh();
                    } else if (event.getX() < endColumnX && event.getX() > startTimeColumn.getWidth()) {
                        selected.setEndTime(formatTimeWithMillis(currentTime));
                        // 更新左侧文本区域
                        updateTextAreaFromSubtitles();
                        // 强制更新表格显示
                        subtitleTable.refresh();
                    }
                }
            }
        });
        
        subtitleTable.setItems(subtitles);
        
        // 初始化时显示带毫秒的时间
        timeLabel.setText("00:00:00,000");
        
        // 初始化时间滑块
        timeSlider.setMin(0);
        timeSlider.setMax(100);
        timeSlider.setValue(0);
        
        // 修改滑块拖动监听
        timeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (mediaPlayer != null && timeSlider.isValueChanging()) {
                mediaPlayer.pause(); // 拖动时暂停播放
                double duration = mediaPlayer.getTotalDuration().toSeconds();
                double time = duration * (newVal.doubleValue() / 100.0);
                mediaPlayer.seek(Duration.seconds(time));
            }
        });
        
        // 添加滑块释放事件监听
        timeSlider.setOnMouseReleased(event -> {
            if (mediaPlayer != null) {
                if (mediaPlayer.getStatus() != MediaPlayer.Status.PAUSED) {
                    mediaPlayer.play(); // 如果之前是播放状态，恢复播放
                }
            }
        });
        
        // 创建并保存文本变化监听器
        textChangeListener = (observable, oldValue, newValue) -> updateSubtitles();
        subtitleInput.textProperty().addListener(textChangeListener);
    }
    
    @FXML
    private void openVideo() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("视频文件", "*.mp4", "*.avi", "*.mkv")
        );
        File file = fileChooser.showOpenDialog(null);
        if (file != null) {
            currentVideoFile = file;
            Media media = new Media(file.toURI().toString());
            if (mediaPlayer != null) {
                mediaPlayer.dispose();
            }
            mediaPlayer = new MediaPlayer(media);
            mediaView.setMediaPlayer(mediaPlayer);
            
            // 修改时间更新监听器，显示毫秒
            mediaPlayer.currentTimeProperty().addListener((obs, oldTime, newTime) -> {
                if (newTime != null) {
                    Platform.runLater(() -> {
                        Duration duration = mediaPlayer.getTotalDuration();
                        timeLabel.setText(String.format("%s / %s",
                            formatTimeWithMillis(newTime.toSeconds()),
                            formatTimeWithMillis(duration.toSeconds())));
                        
                        // 只在非拖动状态更新滑块位置
                        if (!timeSlider.isValueChanging()) {
                            double progress = newTime.toSeconds() / duration.toSeconds();
                            timeSlider.setValue(progress * 100.0);
                        }
                    });
                }
            });
            
            // 添加播放状态监听
            mediaPlayer.statusProperty().addListener((obs, oldStatus, newStatus) -> {
                if (newStatus == MediaPlayer.Status.STOPPED || 
                    newStatus == MediaPlayer.Status.PAUSED) {
                    // 可以在这里更新UI，比如更改播放按钮的图标
                }
            });
            
            // 修改视频结束时的处理
            mediaPlayer.setOnEndOfMedia(() -> {
                mediaPlayer.pause(); // 只暂停播放，不回到开始位置
            });
        }
    }
    
    @FXML
    public void togglePlay() {
        if (mediaPlayer == null) return;
        
        if (mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING) {
            mediaPlayer.pause();
        } else {
            mediaPlayer.play();
        }
    }
    
    @FXML
    private void exportSubtitles() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("SRT字幕", "*.srt")
        );
        File file = fileChooser.showSaveDialog(null);
        if (file != null) {
            try {
                StringBuilder srt = new StringBuilder();
                int index = 1;
                for (Subtitle subtitle : subtitles) {
                    // 检查时间戳是否有效
                    if (subtitle.getStartTime() == null || subtitle.getEndTime() == null || 
                        subtitle.getStartTime().isEmpty() || subtitle.getEndTime().isEmpty()) {
                        showError("导出失败", "第 " + index + " 条字幕的时间戳不完整");
                        return;
                    }
                    
                    srt.append(index++).append("\n");
                    srt.append(subtitle.getStartTime())
                       .append(" --> ")
                       .append(subtitle.getEndTime())
                       .append("\n");
                    srt.append(subtitle.getContent().trim()).append("\n\n");
                }
                Files.write(file.toPath(), srt.toString().getBytes("UTF-8"));
                showInfo("导出成功", "字幕已成功导出为SRT格式");
            } catch (Exception e) {
                showError("导出失败", "无法导出字幕文件: " + e.getMessage());
            }
        }
    }
    
    @FXML
    private void encodeVideo() {
        if (currentVideoFile == null) {
            showError("错误", "请先打开视频文件");
            return;
        }
        
        TextInputDialog dialog = new TextInputDialog("2000");
        dialog.setTitle("设置码率");
        dialog.setHeaderText("请输入目标码率（kbps）");
        dialog.showAndWait().ifPresent(bitrate -> {
            progressBar.setProgress(0);
            progressBar.setVisible(true);
            
            String outputPath = currentVideoFile.getParent() + "/output_" + 
                              currentVideoFile.getName();
            
            String command = String.format("ffmpeg -i \"%s\" -b:v %sk \"%s\"", 
                currentVideoFile.getPath(), bitrate, outputPath);
                
            new Thread(() -> {
                try {
                    Process process = Runtime.getRuntime().exec(command);
                    
                    // 读取FFmpeg输出以更新进度
                    BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getErrorStream())
                    );
                    
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.contains("time=")) {
                            // 解析FFmpeg输出的时间信息来更新进度
                            updateProgress(line);
                        }
                    }
                    
                    int exitCode = process.waitFor();
                    if (exitCode == 0) {
                        Platform.runLater(() -> {
                            progressBar.setProgress(1.0);
                            showInfo("编码完成", "视频编码已完成");
                        });
                    } else {
                        throw new Exception("FFmpeg返回错误代码: " + exitCode);
                    }
                    
                } catch (Exception e) {
                    Platform.runLater(() -> {
                        progressBar.setVisible(false);
                        showError("编码失败", "视频编码失败: " + e.getMessage());
                    });
                }
            }).start();
        });
    }
    
    private void updateProgress(String ffmpegOutput) {
        try {
            // 从FFmpeg输出中提取时间信息
            int timeIndex = ffmpegOutput.indexOf("time=");
            if (timeIndex > 0) {
                String time = ffmpegOutput.substring(timeIndex + 5, timeIndex + 13);
                String[] timeParts = time.split(":");
                double seconds = Double.parseDouble(timeParts[0]) * 3600 + 
                               Double.parseDouble(timeParts[1]) * 60 + 
                               Double.parseDouble(timeParts[2]);
                
                // 获取视频总时长
                double duration = mediaPlayer.getTotalDuration().toSeconds();
                double progress = seconds / duration;
                
                Platform.runLater(() -> progressBar.setProgress(progress));
            }
        } catch (Exception e) {
            // 忽略解析错误
        }
    }
    
    @FXML
    private void updateSubtitles() {
        String[] lines = subtitleInput.getText().split("\n");
        
        // 创建新的字幕列表
        ObservableList<Subtitle> newSubtitles = FXCollections.observableArrayList();
        
        // 获取当前选中的行
        int selectedIndex = subtitleTable.getSelectionModel().getSelectedIndex();
        
        // 处理每一行
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (!line.isEmpty()) {
                Subtitle subtitle = new Subtitle(line);
                
                // 如果行号在现有字幕范围内，复用时间信息
                if (i < subtitles.size()) {
                    Subtitle existing = subtitles.get(i);
                    subtitle.setStartTime(existing.getStartTime());
                    subtitle.setEndTime(existing.getEndTime());
                }
                
                newSubtitles.add(subtitle);
            }
        }
        
        // 更新字幕列表
        subtitles.clear();
        subtitles.addAll(newSubtitles);
        
        // 恢复选中状态
        if (selectedIndex >= 0 && selectedIndex < subtitles.size()) {
            subtitleTable.getSelectionModel().select(selectedIndex);
        }
    }
    
    @FXML
    private void setStartTime() {
        if (mediaPlayer == null) return;
        Subtitle selected = subtitleTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            double currentTime = mediaPlayer.getCurrentTime().toSeconds();
            String timeStr = formatTimeWithMillis(currentTime);
            selected.setStartTime(timeStr);
            // 更新文本区域
            updateTextAreaFromSubtitles();
            // 强制更新表格显示
            subtitleTable.refresh();
        }
    }
    
    @FXML
    private void setEndTime() {
        if (mediaPlayer == null) return;
        Subtitle selected = subtitleTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            double currentTime = mediaPlayer.getCurrentTime().toSeconds();
            String timeStr = formatTimeWithMillis(currentTime);
            selected.setEndTime(timeStr);
            // 更新文本区域
            updateTextAreaFromSubtitles();
            // 强制更新表格显示
            subtitleTable.refresh();
        }
    }
    
    @FXML
    private void importSrtSubtitles() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("SRT字幕", "*.srt")
        );
        File file = fileChooser.showOpenDialog(null);
        if (file != null) {
            try {
                List<String> lines = Files.readAllLines(file.toPath());
                subtitles.clear();
                
                StringBuilder content = new StringBuilder();
                String startTime = null;
                String endTime = null;
                
                for (String line : lines) {
                    line = line.trim();
                    if (line.isEmpty()) {
                        if (content.length() > 0) {
                            Subtitle subtitle = new Subtitle(content.toString().trim());
                            if (startTime != null) {
                                subtitle.setStartTime(convertSrtTimeToNormal(startTime));
                            }
                            if (endTime != null) {
                                subtitle.setEndTime(convertSrtTimeToNormal(endTime));
                            }
                            subtitles.add(subtitle);
                            content.setLength(0);
                            startTime = null;
                            endTime = null;
                        }
                    } else if (line.contains("-->")) {
                        String[] times = line.split("-->");
                        startTime = times[0].trim();
                        endTime = times[1].trim();
                    } else if (!line.matches("\\d+")) { // 不是序号行
                        content.append(line).append("\n");
                    }
                }
                
                // 更新文本区域
                updateTextArea();
                
            } catch (Exception e) {
                showError("导入失败", "无法导入SRT字幕: " + e.getMessage());
            }
        }
    }
    
    @FXML
    private void importTxtSubtitles() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("文本文件", "*.txt")
        );
        File file = fileChooser.showOpenDialog(null);
        if (file != null) {
            try {
                String content = Files.readString(file.toPath());
                subtitleInput.setText(content);
                updateSubtitles();
            } catch (Exception e) {
                showError("导入失败", "无法导入文本文件: " + e.getMessage());
            }
        }
    }
    
    private void updateTextArea() {
        StringBuilder text = new StringBuilder();
        for (Subtitle subtitle : subtitles) {
            text.append(subtitle.getContent()).append("\n");
        }
        subtitleInput.setText(text.toString());
    }
    
    private String convertSrtTimeToNormal(String srtTime) {
        // 保持SRT格式不变，不再截断毫秒部分
        return srtTime;
    }
    
    private void showError(String title, String content) {
        Alert alert = new Alert(AlertType.ERROR);
        alert.setTitle(title);
        alert.setContentText(content);
        alert.showAndWait();
    }
    
    private void showInfo(String title, String content) {
        Alert alert = new Alert(AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setContentText(content);
        alert.showAndWait();
    }
    
    private String formatTimeWithMillis(double seconds) {
        int hours = (int) (seconds / 3600);
        int minutes = (int) ((seconds % 3600) / 60);
        int secs = (int) (seconds % 60);
        int millis = (int) ((seconds - Math.floor(seconds)) * 1000);
        return String.format("%02d:%02d:%02d,%03d", hours, minutes, secs, millis);
    }
    
    @FXML
    private void showAbout() {
        Alert alert = new Alert(AlertType.INFORMATION);
        alert.setTitle("关于");
        alert.setHeaderText(null);
        alert.setContentText(VersionInfo.getFullVersionInfo());
        
        // 设置对话框样式
        DialogPane dialogPane = alert.getDialogPane();
        dialogPane.setStyle("-fx-font-family: 'Microsoft YaHei';");
        
        // 添加应用图标（如果有的话）
        // Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();
        // stage.getIcons().add(new Image(getClass().getResourceAsStream("/icons/app.png")));
        
        alert.showAndWait();
    }
    
    @FXML
    private void insertRowAbove() {
        int selectedIndex = subtitleTable.getSelectionModel().getSelectedIndex();
        if (selectedIndex >= 0) {
            Subtitle newSubtitle = new Subtitle("[在此输入字幕文本]");
            subtitles.add(selectedIndex, newSubtitle);
            updateTextAreaFromSubtitles();
            subtitleTable.getSelectionModel().select(selectedIndex);
        }
    }
    
    @FXML
    private void insertRowBelow() {
        int selectedIndex = subtitleTable.getSelectionModel().getSelectedIndex();
        if (selectedIndex >= 0) {
            Subtitle newSubtitle = new Subtitle("");
            subtitles.add(selectedIndex + 1, newSubtitle);
            updateTextAreaFromSubtitles();
            subtitleTable.getSelectionModel().select(selectedIndex + 1);
        }
    }
    
    @FXML
    private void deleteCurrentRow() {
        int selectedIndex = subtitleTable.getSelectionModel().getSelectedIndex();
        if (selectedIndex >= 0) {
            subtitles.remove(selectedIndex);
            updateTextAreaFromSubtitles();
            if (selectedIndex < subtitles.size()) {
                subtitleTable.getSelectionModel().select(selectedIndex);
            } else if (!subtitles.isEmpty()) {
                subtitleTable.getSelectionModel().select(subtitles.size() - 1);
            }
        }
    }
    
    @FXML
    private void deleteRowsBelow() {
        int selectedIndex = subtitleTable.getSelectionModel().getSelectedIndex();
        if (selectedIndex >= 0 && selectedIndex < subtitles.size() - 1) {
            subtitles.remove(selectedIndex + 1, subtitles.size());
            updateTextAreaFromSubtitles();
            subtitleTable.getSelectionModel().select(selectedIndex);
        }
    }
    
    // 更新左侧文本区域的内容
    private void updateTextAreaFromSubtitles() {
        StringBuilder text = new StringBuilder();
        for (Subtitle subtitle : subtitles) {
            text.append(subtitle.toRawText()).append("\n");
        }
        // 阻止触发updateSubtitles
        subtitleInput.textProperty().removeListener(textChangeListener);
        subtitleInput.setText(text.toString());
        subtitleInput.textProperty().addListener(textChangeListener);
    }
    
    // 添加文本变化监听器作为字段
    private ChangeListener<String> textChangeListener;
} 