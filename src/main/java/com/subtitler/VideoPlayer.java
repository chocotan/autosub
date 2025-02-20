package com.subtitler;

import org.bytedeco.javacv.*;
import org.bytedeco.ffmpeg.global.avcodec;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.image.PixelWriter;
import javafx.application.Platform;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class VideoPlayer {
    private FFmpegFrameGrabber grabber;
    private final ImageView imageView;
    private final AtomicBoolean isPlaying = new AtomicBoolean(false);
    private double currentTimeSeconds = 0;
    private double durationSeconds = 0;
    private ScheduledExecutorService playbackThread;
    private VideoPlayerCallback callback;
    private volatile boolean isUpdatingSlider = false;
    private double lastTimeSeconds = 0;
    private ScheduledExecutorService seekDelayExecutor;
    private static final long SEEK_DELAY_MS = 200; // 200毫秒的延迟

    public VideoPlayer(ImageView imageView) {
        this.imageView = imageView;
    }

    public void setCallback(VideoPlayerCallback callback) {
        this.callback = callback;
    }

    public void openVideo(String filePath) throws Exception {
        if (grabber != null) {
            stop();
            grabber.close();
        }

        grabber = new FFmpegFrameGrabber(filePath);
        grabber.start();
        
        // 获取视频信息
        durationSeconds = grabber.getLengthInTime() / 1000000.0;
        currentTimeSeconds = 0;
        lastTimeSeconds = 0;
        
        // 设置ImageView的尺寸
        double aspectRatio = (double) grabber.getImageWidth() / grabber.getImageHeight();
        imageView.setFitWidth(800); // 设置固定宽度
        imageView.setFitHeight(800 / aspectRatio);
        
        // 确保显示第一帧
        Frame frame = grabber.grabImage();
        if (frame != null) {
            showFrame(frame);
        }
        
        if (callback != null) {
            callback.onDurationChanged(durationSeconds);
            callback.onTimeChanged(currentTimeSeconds);
        }
    }

    public void play() {
        if (grabber == null || isPlaying.get()) return;
        
        isPlaying.set(true);
        playbackThread = Executors.newSingleThreadScheduledExecutor();
        playbackThread.scheduleAtFixedRate(() -> {
            try {
                if (!isPlaying.get()) return;
                
                Frame frame = grabber.grab();
                if (frame == null) {
                    stop();
                    return;
                }
                
                double newTimeSeconds = grabber.getTimestamp() / 1000000.0;
                if (newTimeSeconds >= lastTimeSeconds) {
                    currentTimeSeconds = newTimeSeconds;
                    lastTimeSeconds = currentTimeSeconds;
                }
                
                showFrame(frame);
                
                if (callback != null && !isUpdatingSlider) {
                    Platform.runLater(() -> {
                        isUpdatingSlider = true;
                        callback.onTimeChanged(currentTimeSeconds);
                        isUpdatingSlider = false;
                    });
                }
                
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 0, 33, TimeUnit.MILLISECONDS); // ~30fps
    }

    public void pause() {
        isPlaying.set(false);
        if (playbackThread != null) {
            playbackThread.shutdown();
        }
    }

    public void stop() {
        pause();
        try {
            if (grabber != null) {
                grabber.setTimestamp(0);
                currentTimeSeconds = 0;
                lastTimeSeconds = 0;
                if (callback != null) {
                    Platform.runLater(() -> callback.onTimeChanged(currentTimeSeconds));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void seek(double seconds) {
        try {
            if (grabber != null) {
                isUpdatingSlider = true;
                currentTimeSeconds = seconds;
                lastTimeSeconds = seconds;
                
                // 取消之前的延迟seek任务
                if (seekDelayExecutor != null) {
                    seekDelayExecutor.shutdownNow();
                }
                
                // 创建新的延迟seek任务
                seekDelayExecutor = Executors.newSingleThreadScheduledExecutor();
                seekDelayExecutor.schedule(() -> {
                    try {
                        grabber.setTimestamp((long)(seconds * 1000000));
                        
                        // 尝试多次获取帧直到获得有效的视频帧
                        Frame frame = null;
                        for (int i = 0; i < 10 && frame == null; i++) {
                            frame = grabber.grabImage();
                        }
                        
                        if (frame != null) {
                            showFrame(frame);
                        } else {
                            // 如果获取不到视频帧，尝试普通grab
                            frame = grabber.grab();
                            if (frame != null) {
                                showFrame(frame);
                            }
                        }
                        
                        if (callback != null) {
                            Platform.runLater(() -> {
                                callback.onTimeChanged(currentTimeSeconds);
                                isUpdatingSlider = false;
                            });
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        isUpdatingSlider = false;
                    } finally {
                        seekDelayExecutor.shutdown();
                    }
                }, SEEK_DELAY_MS, TimeUnit.MILLISECONDS);
                
            }
        } catch (Exception e) {
            e.printStackTrace();
            isUpdatingSlider = false;
        }
    }

    private void showFrame(Frame frame) {
        if (frame != null && frame.image != null) {
            Java2DFrameConverter converter = new Java2DFrameConverter();
            BufferedImage image = converter.convert(frame);
            Platform.runLater(() -> {
                WritableImage writableImage = new WritableImage(image.getWidth(), image.getHeight());
                PixelWriter pixelWriter = writableImage.getPixelWriter();
                
                for (int x = 0; x < image.getWidth(); x++) {
                    for (int y = 0; y < image.getHeight(); y++) {
                        pixelWriter.setArgb(x, y, image.getRGB(x, y));
                    }
                }
                
                imageView.setImage(writableImage);
            });
        }
    }

    public double getCurrentTime() {
        return currentTimeSeconds;
    }

    public double getDuration() {
        return durationSeconds;
    }

    public boolean isPlaying() {
        return isPlaying.get();
    }

    public void dispose() {
        try {
            stop();
            if (seekDelayExecutor != null) {
                seekDelayExecutor.shutdownNow();
            }
            if (grabber != null) {
                grabber.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public interface VideoPlayerCallback {
        void onTimeChanged(double currentTimeSeconds);
        void onDurationChanged(double durationSeconds);
    }
} 