package com.subtitler;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import java.io.File;
import javafx.scene.image.ImageView;
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
import com.subtitler.VideoPlayer;

public class MainViewController {
    @FXML private ImageView mediaView;
    @FXML private Label timeLabel;
    @FXML private TableView<Subtitle> subtitleTable;
    @FXML private TableColumn<Subtitle, String> startTimeColumn;
    @FXML private TableColumn<Subtitle, String> endTimeColumn;
    @FXML private TableColumn<Subtitle, String> contentColumn;

    private VideoPlayer videoPlayer;
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

        // 设置内容列为可编辑
        contentColumn.setCellFactory(column -> {
            TableCell<Subtitle, String> cell = new TableCell<Subtitle, String>() {
                private TextField textField;
                
                @Override
                public void startEdit() {
                    super.startEdit();
                    if (textField == null) {
                        createTextField();
                    }
                    setText(null);
                    setGraphic(textField);
                    textField.selectAll();
                }
                
                @Override
                public void cancelEdit() {
                    super.cancelEdit();
                    setText(getItem());
                    setGraphic(null);
                }
                
                @Override
                public void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty) {
                        setText(null);
                        setGraphic(null);
                    } else {
                        if (isEditing()) {
                            if (textField != null) {
                                textField.setText(getString());
                            }
                            setText(null);
                            setGraphic(textField);
                        } else {
                            setText(getString());
                            setGraphic(null);
                        }
                    }
                }
                
                private void createTextField() {
                    textField = new TextField(getString());
                    textField.setMinWidth(this.getWidth() - this.getGraphicTextGap() * 2);
                    textField.setOnAction(e -> commitEdit(textField.getText()));
                    textField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
                        if (!isNowFocused) {
                            commitEdit(textField.getText());
                        }
                    });
                }
                
                private String getString() {
                    return getItem() == null ? "" : getItem();
                }
            };
            
            // 双击开始编辑
            cell.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !cell.isEmpty()) {
                    cell.startEdit();
                }
            });
            
            return cell;
        });
        
        // 设置表格为可编辑
        subtitleTable.setEditable(true);
        
        // 添加编辑完成的监听器
        contentColumn.setOnEditCommit(event -> {
            Subtitle subtitle = event.getRowValue();
            subtitle.setContent(event.getNewValue());
            // 更新左侧文本区域
            updateTextAreaFromSubtitles();
        });

        // 修改表格点击事件
        subtitleTable.setOnMouseClicked(event -> {
            if (event.getClickCount() == 1) {
                Subtitle selected = subtitleTable.getSelectionModel().getSelectedItem();
                if (selected != null && videoPlayer != null) {
                    double currentTime = videoPlayer.getCurrentTime();
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

        // 初始化视频播放器
        videoPlayer = new VideoPlayer(mediaView);
        videoPlayer.setCallback(new VideoPlayer.VideoPlayerCallback() {
            @Override
            public void onTimeChanged(double currentTimeSeconds) {
                timeLabel.setText(String.format("%s / %s",
                    formatTimeWithMillis(currentTimeSeconds),
                    formatTimeWithMillis(videoPlayer.getDuration())));

                // 只在非拖动状态更新滑块位置
                if (!timeSlider.isValueChanging()) {
                    double progress = currentTimeSeconds / videoPlayer.getDuration() * 100.0;
                    // 避免频繁的小数点差异导致的更新
                    if (Math.abs(timeSlider.getValue() - progress) > 0.1) {
                        timeSlider.setValue(progress);
                    }
                }
            }

            @Override
            public void onDurationChanged(double durationSeconds) {
                // 可以在这里处理视频总时长变化
            }
        });

        // 修改滑块事件监听
        timeSlider.setOnMousePressed(event -> {
            if (videoPlayer != null) {
                // 记录播放状态
                boolean wasPlaying = videoPlayer.isPlaying();
                if(wasPlaying) {
                    videoPlayer.pause();
                }
                
                // 计算点击位置对应的时间
                double duration = videoPlayer.getDuration();
                double time = duration * (timeSlider.getValue() / 100.0);
                timeLabel.setText(String.format("%s / %s",
                    formatTimeWithMillis(time),
                    formatTimeWithMillis(duration)));
                
                // 存储播放状态和目标时间
                timeSlider.setUserData(new double[]{wasPlaying ? 1.0 : 0.0, time});
            }
        });

        // 修改滑块拖动监听
        timeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (videoPlayer != null && timeSlider.isValueChanging()) {
                // 只更新时间标签和图片
                double duration = videoPlayer.getDuration();
                double time = duration * (newVal.doubleValue() / 100.0);
                timeLabel.setText(String.format("%s / %s",
                    formatTimeWithMillis(time),
                    formatTimeWithMillis(duration)));
                
                // 更新存储的目标时间
                double[] data = (double[]) timeSlider.getUserData();
                if (data != null) {
                    data[1] = time;
                    timeSlider.setUserData(data);
                    // 在拖动过程中也更新图片
                    videoPlayer.seek(time);
                }
            }
        });

        // 修改滑块释放事件监听
        timeSlider.setOnMouseReleased(event -> {
            if (videoPlayer != null) {
                // 获取存储的状态和时间
                double[] data = (double[]) timeSlider.getUserData();
                if (data != null) {
                    // 根据之前的状态决定是否恢复播放
                    if (data[0] > 0) {
                        videoPlayer.play();
                    }
                }
            }
        });

        // 创建并保存文本变化监听器
        textChangeListener = (observable, oldValue, newValue) -> updateSubtitles();
        subtitleInput.textProperty().addListener(textChangeListener);

        // 修改键盘事件监听器
        Platform.runLater(() -> {
            if (mediaView.getScene() != null) {
                mediaView.getScene().setOnKeyPressed(event -> {
                    // 如果焦点在文本区域或其他需要输入的控件，不触发快捷键
                    if (event.getTarget() instanceof TextInputControl) {
                        return;
                    }

                    switch (event.getCode()) {
                        case SPACE:
                            togglePlay();
                            event.consume();
                            break;
                        case LEFT:
                            if (event.isControlDown()) {
                                seekBackward1s();
                            } else {
                                seekBackward05s();
                            }
                            event.consume();
                            break;
                        case RIGHT:
                            if (event.isControlDown()) {
                                seekForward1s();
                            } else {
                                seekForward05s();
                            }
                            event.consume();
                            break;
                    }
                });
            }
        });

        // 为字幕表格添加键盘事件监听
        subtitleTable.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.SPACE) {
                togglePlay();
                event.consume();
            }
        });
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
            try {
                videoPlayer.openVideo(file.getAbsolutePath());
            } catch (Exception e) {
                showError("打开视频失败", e.getMessage());
            }
        }
    }

    @FXML
    public void togglePlay() {
        if (videoPlayer == null) return;

        if (videoPlayer.isPlaying()) {
            videoPlayer.pause();
        } else {
            videoPlayer.play();
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
                double duration = videoPlayer.getDuration();
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
        if (videoPlayer == null) return;
        Subtitle selected = subtitleTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            double currentTime = videoPlayer.getCurrentTime();
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
        if (videoPlayer == null) return;
        Subtitle selected = subtitleTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            double currentTime = videoPlayer.getCurrentTime();
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

    // 添加前进后退的方法
    @FXML
    private void seekForward1s() {
        if (videoPlayer != null) {
            double newTime = Math.min(videoPlayer.getCurrentTime() + 1.0, 
                                    videoPlayer.getDuration());
            videoPlayer.seek(newTime);
        }
    }

    @FXML
    private void seekForward05s() {
        if (videoPlayer != null) {
            double newTime = Math.min(videoPlayer.getCurrentTime() + 0.5, 
                                    videoPlayer.getDuration());
            videoPlayer.seek(newTime);
        }
    }

    @FXML
    private void seekBackward1s() {
        if (videoPlayer != null) {
            double newTime = Math.max(videoPlayer.getCurrentTime() - 1.0, 0);
            videoPlayer.seek(newTime);
        }
    }

    @FXML
    private void seekBackward05s() {
        if (videoPlayer != null) {
            double newTime = Math.max(videoPlayer.getCurrentTime() - 0.5, 0);
            videoPlayer.seek(newTime);
        }
    }
}
