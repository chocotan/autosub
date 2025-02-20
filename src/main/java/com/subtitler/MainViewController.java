package com.subtitler;

import com.github.kokorin.jaffree.ffmpeg.FFmpeg;
import com.github.kokorin.jaffree.ffmpeg.OutputListener;
import com.github.kokorin.jaffree.ffmpeg.UrlInput;
import com.github.kokorin.jaffree.ffmpeg.UrlOutput;
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
import javafx.scene.layout.GridPane;
import javafx.geometry.Insets;
import javafx.event.ActionEvent;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ListView;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import javafx.scene.Scene;
import javafx.scene.layout.VBox;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.beans.property.SimpleStringProperty;
import java.util.Stack;
import java.util.stream.Collectors;
import javafx.scene.Node;
import javafx.scene.control.cell.PropertyValueFactory;

public class MainViewController {
    @FXML private ImageView mediaView;
    @FXML private Label timeLabel;
    @FXML private TableView<Subtitle> subtitleTable;
    @FXML private TableColumn<Subtitle, String> startTimeColumn;
    @FXML private TableColumn<Subtitle, String> endTimeColumn;
    @FXML private TableColumn<Subtitle, String> contentColumn;
    @FXML private Label prevSubtitleLabel;
    @FXML private Label currentSubtitleLabel;
    @FXML private Label nextSubtitleLabel;

    private VideoPlayer videoPlayer;
    private File currentVideoFile;
    @FXML private Slider timeSlider;
    @FXML private TextArea subtitleInput;
    private TableView<EncodingTask> taskTable;
    private ObservableList<EncodingTask> tasks = FXCollections.observableArrayList();
    private Stage taskWindow;
    private ObservableList<Subtitle> subtitles = FXCollections.observableArrayList();

    // 添加撤销相关的字段
    private Stack<SubtitleState> undoStack = new Stack<>();
    private static final int MAX_UNDO_STEPS = 50;
    private boolean isUndoing = false;

    // 添加内部类来保存字幕状态
    private static class SubtitleState {
        private final String textContent;
        private final List<SubtitleData> subtitleDataList;

        public SubtitleState(String textContent, List<Subtitle> subtitles) {
            this.textContent = textContent;
            this.subtitleDataList = subtitles.stream()
                .map(s -> new SubtitleData(s.getContent(), s.getStartTime(), s.getEndTime()))
                .collect(Collectors.toList());
        }

        private static class SubtitleData {
            String content;
            String startTime;
            String endTime;

            SubtitleData(String content, String startTime, String endTime) {
                this.content = content;
                this.startTime = startTime;
                this.endTime = endTime;
            }
        }
    }

    private void saveCurrentState() {
        if (!isUndoing) {
            SubtitleState state = new SubtitleState(subtitleInput.getText(), subtitles);
            undoStack.push(state);
            if (undoStack.size() > MAX_UNDO_STEPS) {
                undoStack.remove(0);
            }
        }
    }

    private boolean userScrolling = false;
    private long lastUserScrollTime = 0;
    private static final long SCROLL_TIMEOUT = 2000; // 2秒后恢复自动滚动

    @FXML private ComboBox<String> playbackSpeedComboBox;

    @FXML
    public void initialize() {
        // 初始化倍速选择下拉框
        ObservableList<String> speedOptions = FXCollections.observableArrayList(
            "1.0", "1.25", "1.5", "2.0", "4.0"
        );
        playbackSpeedComboBox.setItems(speedOptions);
        playbackSpeedComboBox.setValue("1.0");

        // 添加倍速播放监听器
        playbackSpeedComboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (videoPlayer != null && newVal != null) {
                double speed = Double.parseDouble(newVal);
                videoPlayer.setPlaybackSpeed(speed);
            }
        });

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
                        // 保存当前状态
                        saveCurrentState();

                        selected.setStartTime(formatTimeWithMillis(currentTime));
                        // 更新左侧文本区域
                        updateTextAreaFromSubtitles();
                        // 强制更新表格显示
                        subtitleTable.refresh();
                    } else if (event.getX() < endColumnX && event.getX() > startTimeColumn.getWidth()) {
                        // 保存当前状态
                        saveCurrentState();

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

                // 更新字幕预览
                updateSubtitlePreview(currentTimeSeconds);
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

                // 立即更新视频位置
                videoPlayer.seek(time);

                timeLabel.setText(String.format("%s / %s",
                    formatTimeWithMillis(time),
                    formatTimeWithMillis(duration)));

                // 存储播放状态
                timeSlider.setUserData(wasPlaying ? 1.0 : 0.0);
            }
        });

        // 修改滑块拖动监听
        timeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (videoPlayer != null && timeSlider.isValueChanging()) {
                double duration = videoPlayer.getDuration();
                double time = duration * (newVal.doubleValue() / 100.0);

                // 立即更新视频位置
                videoPlayer.seek(time);

                timeLabel.setText(String.format("%s / %s",
                    formatTimeWithMillis(time),
                    formatTimeWithMillis(duration)));
            }
        });

        // 修改滑块释放事件监听
        timeSlider.setOnMouseReleased(event -> {
            if (videoPlayer != null) {
                // 获取之前的播放状态
                Object data = timeSlider.getUserData();
                if (data instanceof Double && ((Double) data) > 0) {
                    videoPlayer.play();
                }
            }
        });

        // 修改文本变化监听器
        textChangeListener = (observable, oldValue, newValue) -> {
            if (!isUndoing && oldValue != null && !oldValue.equals(newValue)) {
                updateSubtitles(); // updateSubtitles 方法中已经包含了状态保存
            }
        };
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
                        case F1:
                            togglePlay();
                            event.consume();
                            break;
                        case SPACE:
                            togglePlay();
                            event.consume();
                            break;
                        case LEFT:
                            if (event.isControlDown() && event.isShiftDown()) {
                                seekBackward5s();
                            } else if (event.isControlDown()) {
                                seekBackward1s();
                            } else {
                                seekBackward05s();
                            }
                            event.consume();
                            break;
                        case RIGHT:
                            if (event.isControlDown() && event.isShiftDown()) {
                                seekForward5s();
                            } else if (event.isControlDown()) {
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

        // 添加滚动监听器
        subtitleTable.setOnScrollStarted(event -> {
            userScrolling = true;
            lastUserScrollTime = System.currentTimeMillis();
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

        // 创建自定义对话框
        Dialog<Map<String, String>> dialog = new Dialog<>();
        dialog.setTitle("视频编码设置");
        dialog.setHeaderText("请设置编码参数");

        // 设置按钮
        ButtonType okButton = new ButtonType("确定", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButton = new ButtonType("取消", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(okButton, cancelButton);

        // 创建表单布局
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        // 码率输入框
        TextField bitrateField = new TextField("2000");
        bitrateField.setPromptText("输入码率(kbps)");

        // 硬件加速选择
        ComboBox<String> hwaccelBox = new ComboBox<>();
        hwaccelBox.getItems().addAll(
            "不使用硬件加速",
            "NVIDIA (nvenc)",
            "Intel QSV",
            "AMD AMF",
            "Apple VideoToolbox"
        );
        hwaccelBox.setValue("不使用硬件加速");

        // 编码格式选择
        ComboBox<String> codecBox = new ComboBox<>();
        codecBox.getItems().addAll("H.264", "H.265");
        codecBox.setValue("H.264");

        // 添加控件到表单
        grid.add(new Label("码率(kbps):"), 0, 0);
        grid.add(bitrateField, 1, 0);
        grid.add(new Label("硬件加速:"), 0, 1);
        grid.add(hwaccelBox, 1, 1);
        grid.add(new Label("编码格式:"), 0, 2);
        grid.add(codecBox, 1, 2);

        dialog.getDialogPane().setContent(grid);

        // 转换结果
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == okButton) {
                Map<String, String> settings = new HashMap<>();
                settings.put("bitrate", bitrateField.getText());
                settings.put("hwaccel", hwaccelBox.getValue());
                settings.put("codec", codecBox.getValue());
                return settings;
            }
            return null;
        });

        // 显示对话框并处理结果
        dialog.showAndWait().ifPresent(settings -> {
            String outputPath = currentVideoFile.getParent() + "/output_" +
                              currentVideoFile.getName();

            // 根据硬件加速选项和编码格式选择编码器
            String encoder;
            String hwaccel = settings.get("hwaccel");
            String codec = settings.get("codec");

            switch (hwaccel) {
                case "NVIDIA (nvenc)":
                    encoder = "H.264".equals(codec) ? "h264_nvenc" : "hevc_nvenc";
                    break;
                case "Intel QSV":
                    encoder = "H.264".equals(codec) ? "h264_qsv" : "hevc_qsv";
                    break;
                case "AMD AMF":
                    encoder = "H.264".equals(codec) ? "h264_amf" : "hevc_amf";
                    break;
                case "Apple VideoToolbox":
                    encoder = "H.264".equals(codec) ? "h264_videotoolbox" : "hevc_videotoolbox";
                    break;
                default:
                    encoder = "H.264".equals(codec) ? "libx264" : "libx265";
                    break;
            }

            String bitrate = settings.get("bitrate");

            // 添加编码任务
            addEncodingTask(outputPath, encoder, bitrate);
        });
    }

    @FXML
    private void updateSubtitles() {
        // 保存当前状态
        saveCurrentState();

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
            // 保存当前状态
            saveCurrentState();

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
            // 保存当前状态
            saveCurrentState();

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
                boolean isReadingContent = false;

                for (String line : lines) {
                    line = line.trim();

                    if (line.isEmpty()) {
                        if (content.length() > 0) {
                            // 处理一个完整的字幕块
                            String subtitleText = content.toString().trim();
                            Subtitle subtitle = new Subtitle(subtitleText);
                            if (startTime != null) {
                                subtitle.setStartTime(convertSrtTimeToNormal(startTime));
                            }
                            if (endTime != null) {
                                subtitle.setEndTime(convertSrtTimeToNormal(endTime));
                            }
                            subtitles.add(subtitle);

                            // 重置状态
                            content.setLength(0);
                            startTime = null;
                            endTime = null;
                            isReadingContent = false;
                        }
                    } else if (line.contains("-->")) {
                        String[] times = line.split("-->");
                        startTime = times[0].trim();
                        endTime = times[1].trim();
                        isReadingContent = true;
                    } else if (isReadingContent) {
                        if (content.length() > 0) {
                            content.append("\n");
                        }
                        content.append(line);
                    }
                    // 完全忽略序号行
                }

                // 处理最后一条字幕（如果有的话）
                if (content.length() > 0) {
                    String subtitleText = content.toString().trim();
                    Subtitle subtitle = new Subtitle(subtitleText);
                    if (startTime != null) {
                        subtitle.setStartTime(convertSrtTimeToNormal(startTime));
                    }
                    if (endTime != null) {
                        subtitle.setEndTime(convertSrtTimeToNormal(endTime));
                    }
                    subtitles.add(subtitle);
                }

                // 更新左侧文本区域
                updateTextAreaFromSubtitles();

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

    @FXML
    private void seekForward5s() {
        if (videoPlayer != null) {
            double newTime = Math.min(videoPlayer.getCurrentTime() + 5.0,
                                    videoPlayer.getDuration());
            videoPlayer.seek(newTime);
        }
    }

    @FXML
    private void seekBackward5s() {
        if (videoPlayer != null) {
            double newTime = Math.max(videoPlayer.getCurrentTime() - 5.0, 0);
            videoPlayer.seek(newTime);
        }
    }

    // 添加一个打开文件目录的辅助方法
    private void openFileDirectory(String filePath) {
        try {
            File directory = new File(filePath).getParentFile();
            if (directory != null && directory.exists()) {
                String os = System.getProperty("os.name").toLowerCase();
                ProcessBuilder builder;
                if (os.contains("win")) {
                    builder = new ProcessBuilder("explorer.exe", directory.getAbsolutePath());
                } else if (os.contains("mac")) {
                    builder = new ProcessBuilder("open", directory.getAbsolutePath());
                } else {
                    builder = new ProcessBuilder("xdg-open", directory.getAbsolutePath());
                }
                builder.start();
            }
        } catch (Exception e) {
            showError("打开目录失败", "无法打开文件目录: " + e.getMessage());
        }
    }

    @FXML
    private void showEncodingTasks() {
        if (taskWindow == null) {
            taskWindow = new Stage();
            taskWindow.initStyle(StageStyle.UTILITY);
            taskWindow.setTitle("编码任务");
            taskWindow.setAlwaysOnTop(true);

            // 创建TableView和列
            taskTable = new TableView<>(tasks);

            // 开始时间列
            TableColumn<EncodingTask, String> startTimeCol = new TableColumn<>("开始时间");
            startTimeCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getStartTime()));
            startTimeCol.setPrefWidth(80);

            // 源文件列
            TableColumn<EncodingTask, String> sourceFileCol = new TableColumn<>("源文件");
            sourceFileCol.setCellValueFactory(data -> data.getValue().filenameProperty());
            sourceFileCol.setPrefWidth(200);

            // 输出文件列
            TableColumn<EncodingTask, String> outputFileCol = new TableColumn<>("输出文件");
            outputFileCol.setCellValueFactory(data -> {
                String filename = data.getValue().getFilename();
                return new SimpleStringProperty(new File(filename).getName());
            });
            outputFileCol.setPrefWidth(200);

            // 状态列（包含进度）
            TableColumn<EncodingTask, String> statusCol = new TableColumn<>("状态");
            statusCol.setCellValueFactory(data -> {
                EncodingTask task = data.getValue();
                return new SimpleStringProperty() {
                    {
                        // 初始绑定
                        setValue(getStatusText(task));

                        // 监听状态和进度变化
                        task.statusProperty().addListener((obs, old, newVal) ->
                            setValue(getStatusText(task)));
                        task.progressProperty().addListener((obs, old, newVal) ->
                            setValue(getStatusText(task)));
                    }
                };
            });
            statusCol.setPrefWidth(150);

            // 完成时间列
            TableColumn<EncodingTask, String> endTimeCol = new TableColumn<>("完成时间");
            endTimeCol.setCellValueFactory(data -> {
                String endTime = data.getValue().getEndTime();
                return new SimpleStringProperty(endTime != null ? endTime : "");
            });
            endTimeCol.setPrefWidth(80);

            // 操作列
            TableColumn<EncodingTask, Void> actionCol = new TableColumn<>("操作");
            actionCol.setCellFactory(param -> new TableCell<>() {
                private final Button cancelButton = new Button("取消");
                {
                    cancelButton.setOnAction(event -> {
                        EncodingTask task = getTableView().getItems().get(getIndex());
                        if ("正在编码".equals(task.getStatus())) {
                            task.cancel();
                        }
                    });
                }

                @Override
                protected void updateItem(Void item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty) {
                        setGraphic(null);
                    } else {
                        EncodingTask task = getTableView().getItems().get(getIndex());
                        if ("正在编码".equals(task.getStatus())) {
                            setGraphic(cancelButton);
                        } else {
                            setGraphic(null);
                        }
                    }
                }
            });
            actionCol.setPrefWidth(80);

            taskTable.getColumns().addAll(
                startTimeCol, sourceFileCol, outputFileCol, statusCol, endTimeCol, actionCol
            );

            // 添加右键菜单
            ContextMenu contextMenu = new ContextMenu();
            MenuItem clearItem = new MenuItem("清空已完成任务");
            clearItem.setOnAction(e -> {
                tasks.removeIf(task ->
                    "编码完成".equals(task.getStatus()) ||
                    "编码失败".equals(task.getStatus()) ||
                    "已取消".equals(task.getStatus())
                );
            });
            MenuItem openFolderItem = new MenuItem("打开输出目录");
            openFolderItem.setOnAction(e -> {
                EncodingTask selectedTask = taskTable.getSelectionModel().getSelectedItem();
                if (selectedTask != null) {
                    openFileDirectory(selectedTask.getFilename());
                }
            });
            contextMenu.getItems().addAll(clearItem, openFolderItem);
            taskTable.setContextMenu(contextMenu);

            VBox root = new VBox(10);
            root.setPadding(new Insets(10));
            root.getChildren().add(taskTable);

            Scene scene = new Scene(root);
            taskWindow.setScene(scene);
            taskWindow.setWidth(750);  // 设置合适的窗口大小
            taskWindow.setHeight(400);

            taskWindow.setOnCloseRequest(event -> {
                event.consume();
                taskWindow.hide();
            });
        }

        if (!taskWindow.isShowing()) {
            if (mediaView.getScene().getWindow() != null) {
                Window mainWindow = mediaView.getScene().getWindow();
                taskWindow.setX(mainWindow.getX() + mainWindow.getWidth() - taskWindow.getWidth());
                taskWindow.setY(mainWindow.getY());
            }
            taskWindow.show();
        }
    }

    private void addEncodingTask(String filename, String encoder, String bitrate) {
        EncodingTask task = new EncodingTask(filename);
        Platform.runLater(() -> {
            tasks.add(task);
            int taskIndex = tasks.size() - 1;

            // 如果窗口没显示，则显示窗口
            if (taskWindow == null || !taskWindow.isShowing()) {
                showEncodingTasks();
            }

            task.setStatus("正在编码");

            // 获取视频总时长（毫秒）
            long totalDurationMillis = (long)(videoPlayer.getDuration() * 1000);

            Thread encodingThread = new Thread(() -> {
                try {
                    // 构建 FFmpeg 命令
                    List<String> command = new ArrayList<>();
                    command.add("ffmpeg");
                    command.add("-i");
                    command.add(currentVideoFile.getAbsolutePath());
                    command.add("-c:v");
                    command.add(encoder);
                    command.add("-b:v");
                    command.add(bitrate + "k");
                    command.add("-c:a");
                    command.add("copy");
                    command.add("-y");
                    command.add(filename);

                    // 创建进程
                    ProcessBuilder processBuilder = new ProcessBuilder(command);
                    processBuilder.redirectErrorStream(true);
                    Process process = processBuilder.start();
                    task.setFfmpegProcess(process);

                    // 读取输出并更新进度
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (task.isCancelled()) {
                                process.destroy();
                                break;
                            }

                            // 解析进度信息
                            if (line.contains("time=")) {
                                try {
                                    String timeStr = line.split("time=")[1].split(" ")[1];
                                    String[] parts = timeStr.split(":");
                                    long timeMillis = (long)((Long.parseLong(parts[0]) * 3600 +
                                                     Long.parseLong(parts[1]) * 60 +
                                                     Double.parseDouble(parts[2])) * 1000);

                                    if (timeMillis > 0 && totalDurationMillis > 0) {
                                        int percentage = (int) ((timeMillis * 100) / totalDurationMillis);
                                        percentage = Math.min(percentage, 100);
                                        int finalPercentage = percentage;
                                        Platform.runLater(() -> {
                                            task.setProgress(finalPercentage);
                                            taskTable.refresh();
                                        });
                                    }
                                } catch (Exception e) {
                                    // 忽略解析错误
                                }
                            }
                        }
                    }

                    // 等待编码完成
                    int exitCode = process.waitFor();

                    if (task.isCancelled()) {
                        Platform.runLater(() -> {
                            task.setStatus("已取消");
                            task.setEndTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
                        });
                        return;
                    }

                    if (exitCode == 0) {
                        Platform.runLater(() -> {
                            task.setStatus("编码完成");
                            task.setProgress(100);
                            task.setEndTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));

                            // 显示完成对话框
                            Alert alert = new Alert(AlertType.INFORMATION,
                                "视频编码已完成，是否打开输出目录？",
                                ButtonType.YES, ButtonType.NO);
                            alert.setTitle("编码完成");
                            alert.showAndWait().ifPresent(response -> {
                                if (response == ButtonType.YES) {
                                    openFileDirectory(filename);
                                }
                            });
                        });
                    } else {
                        throw new RuntimeException("FFmpeg process exited with code " + exitCode);
                    }
                } catch (Exception e) {
                    if (!task.isCancelled()) {
                        Platform.runLater(() -> {
                            task.setStatus("编码失败");
                            task.setErrorMessage(e.getMessage());
                            task.setEndTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
                            showError("编码失败", "视频编码失败: " + e.getMessage());
                        });
                    }
                }
            });

            task.setEncodingThread(encodingThread);
            encodingThread.start();
        });
    }

    // 添加辅助方法来生成状态文本
    private String getStatusText(EncodingTask task) {
        String status = task.getStatus();
        if ("正在编码".equals(status)) {
            return status + " (" + task.getProgress() + "%)";
        }
        if ("编码失败".equals(status) && task.getErrorMessage() != null) {
            return status + " - " + task.getErrorMessage();
        }
        return status;
    }

    @FXML
    private void undo() {
        if (!undoStack.isEmpty()) {
            isUndoing = true;
            try {
                SubtitleState previousState = undoStack.pop();

                // 恢复文本内容
                subtitleInput.setText(previousState.textContent);

                // 恢复字幕列表
                subtitles.clear();
                for (SubtitleState.SubtitleData data : previousState.subtitleDataList) {
                    Subtitle subtitle = new Subtitle(data.content);
                    subtitle.setStartTime(data.startTime);
                    subtitle.setEndTime(data.endTime);
                    subtitles.add(subtitle);
                }

                // 刷新表格显示
                subtitleTable.refresh();
            } finally {
                isUndoing = false;
            }
        }
    }

    private void updateSubtitlePreview(double currentTime) {
        if (subtitles.isEmpty()) {
            prevSubtitleLabel.setText("");
            currentSubtitleLabel.setText("");
            nextSubtitleLabel.setText("");
            return;
        }

        int currentIndex = -1;
        int prevIndex = -1;
        int nextIndex = -1;

        // 查找当前字幕
        for (int i = 0; i < subtitles.size(); i++) {
            Subtitle subtitle = subtitles.get(i);
            if (isTimeInSubtitle(currentTime, subtitle)) {
                currentIndex = i;
                break;
            } else if (getTimeInSeconds(subtitle.getStartTime()) > currentTime) {
                nextIndex = i;
                break;
            }
        }

        // 如果没有找到当前字幕，但找到了下一个字幕
        if (currentIndex == -1 && nextIndex != -1) {
            // 查找上一个字幕
            for (int i = nextIndex - 1; i >= 0; i--) {
                Subtitle subtitle = subtitles.get(i);
                if (!subtitle.getEndTime().isEmpty()) {
                    prevIndex = i;
                    break;
                }
            }
        }
        // 如果找到了当前字幕
        else if (currentIndex != -1) {
            prevIndex = currentIndex - 1;
            nextIndex = currentIndex + 1;
        }
        // 如果都没找到，查找下一个字幕
        else {
            for (int i = 0; i < subtitles.size(); i++) {
                Subtitle subtitle = subtitles.get(i);
                if (!subtitle.getStartTime().isEmpty() &&
                    getTimeInSeconds(subtitle.getStartTime()) > currentTime) {
                    nextIndex = i;
                    break;
                }
            }
            // 查找上一个字幕
            if (nextIndex == -1) {
                for (int i = subtitles.size() - 1; i >= 0; i--) {
                    Subtitle subtitle = subtitles.get(i);
                    if (!subtitle.getEndTime().isEmpty() &&
                        getTimeInSeconds(subtitle.getEndTime()) <= currentTime) {
                        prevIndex = i;
                        break;
                    }
                }
            }
        }

        // 更新显示
        prevSubtitleLabel.setText(prevIndex >= 0 ? formatSubtitlePreview(subtitles.get(prevIndex)) : "");
        currentSubtitleLabel.setText(currentIndex >= 0 ? formatSubtitlePreview(subtitles.get(currentIndex)) : "");
        nextSubtitleLabel.setText(nextIndex >= 0 && nextIndex < subtitles.size() ?
                                formatSubtitlePreview(subtitles.get(nextIndex)) : "");

        // 更新表格选中项
        if (currentIndex >= 0) {
            subtitleTable.getSelectionModel().select(currentIndex);

            // 检查是否应该自动滚动
            long now = System.currentTimeMillis();
            if (!userScrolling || (now - lastUserScrollTime > SCROLL_TIMEOUT)) {
                userScrolling = false;

                // 检查当前项是否在可视范围内
                int firstVisible = getFirstVisibleIndex();
                int lastVisible = getLastVisibleIndex();

                if (currentIndex < firstVisible || currentIndex > lastVisible) {
                    subtitleTable.scrollTo(currentIndex);
                }
            }
        }
    }

    private boolean isTimeInSubtitle(double currentTime, Subtitle subtitle) {
        if (subtitle.getStartTime().isEmpty() || subtitle.getEndTime().isEmpty()) {
            return false;
        }
        double startTime = getTimeInSeconds(subtitle.getStartTime());
        double endTime = getTimeInSeconds(subtitle.getEndTime());
        return currentTime >= startTime && currentTime <= endTime;
    }

    private double getTimeInSeconds(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) {
            return 0;
        }
        String[] parts = timeStr.split("[:,]");
        if (parts.length != 4) {
            return 0;
        }
        int hours = Integer.parseInt(parts[0]);
        int minutes = Integer.parseInt(parts[1]);
        int seconds = Integer.parseInt(parts[2]);
        int millis = Integer.parseInt(parts[3]);
        return hours * 3600 + minutes * 60 + seconds + millis / 1000.0;
    }

    private String formatSubtitlePreview(Subtitle subtitle) {
        StringBuilder sb = new StringBuilder();
        if (!subtitle.getStartTime().isEmpty()) {
            sb.append(subtitle.getStartTime());
        }
        if (!subtitle.getEndTime().isEmpty()) {
            sb.append(" -> ").append(subtitle.getEndTime());
        }
        if (sb.length() > 0) {
            sb.append(" | ");
        }
        sb.append(subtitle.getContent());
        return sb.toString();
    }

    // 获取第一个可见项的索引
    private int getFirstVisibleIndex() {
        int index = subtitleTable.getSelectionModel().getSelectedIndex();
        for (int i = index; i >= 0; i--) {
            TableCell<?, ?> cell = getTableCell(i);
            if (cell != null && cell.isVisible()) {
                return i;
            }
        }
        return 0;
    }

    // 获取最后一个可见项的索引
    private int getLastVisibleIndex() {
        int index = subtitleTable.getSelectionModel().getSelectedIndex();
        int size = subtitleTable.getItems().size();
        for (int i = index; i < size; i++) {
            TableCell<?, ?> cell = getTableCell(i);
            if (cell != null && cell.isVisible()) {
                return i;
            }
        }
        return size - 1;
    }

    // 获取指定索引的单元格
    private TableCell<?, ?> getTableCell(int row) {
        if (subtitleTable.getColumns().isEmpty()) return null;
        TableColumn<?, ?> column = subtitleTable.getColumns().get(0);
        if (column.getCellData(row) == null) return null;

        for (Node node : subtitleTable.lookupAll(".table-cell")) {
            if (node instanceof TableCell) {
                TableCell<?, ?> cell = (TableCell<?, ?>) node;
                if (cell.getTableRow() != null && cell.getTableRow().getIndex() == row) {
                    return cell;
                }
            }
        }
        return null;
    }
}
