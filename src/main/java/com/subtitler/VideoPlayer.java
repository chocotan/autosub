package com.subtitler;

import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer;
import uk.co.caprica.vlcj.factory.MediaPlayerFactory;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.application.Platform;
import java.util.concurrent.atomic.AtomicBoolean;
import uk.co.caprica.vlcj.javafx.videosurface.ImageViewVideoSurface;

public class VideoPlayer {
    private final ImageView imageView;
    private final AtomicBoolean isPlaying = new AtomicBoolean(false);
    private double currentTimeSeconds = 0;
    private double durationSeconds = 0;
    private VideoPlayerCallback callback;
    private volatile boolean isUpdatingSlider = false;
    private MediaPlayerFactory mediaPlayerFactory;
    private EmbeddedMediaPlayer mediaPlayer;
    private Pane videoPane;

    public VideoPlayer(ImageView imageView) {
        this.imageView = imageView;
        initializePlayer();
    }

    private void initializePlayer() {
        // 创建 VLC 媒体播放器工厂和播放器，禁用字幕自动检测
        String[] args = {"--no-sub-autodetect-file", "--no-video-title-show"};
        mediaPlayerFactory = new MediaPlayerFactory(args);
        mediaPlayer = mediaPlayerFactory.mediaPlayers().newEmbeddedMediaPlayer();
        
        // 创建 JavaFX 视频表面并设置给播放器
        ImageViewVideoSurface videoSurface = new ImageViewVideoSurface(imageView);
        mediaPlayer.videoSurface().set(videoSurface);
        
        // 设置时间变化监听器
        mediaPlayer.events().addMediaPlayerEventListener(new uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter() {
            @Override
            public void timeChanged(uk.co.caprica.vlcj.player.base.MediaPlayer mediaPlayer, long newTime) {
                currentTimeSeconds = newTime / 1000.0;
                if (callback != null && !isUpdatingSlider) {
                    Platform.runLater(() -> {
                        callback.onTimeChanged(currentTimeSeconds);
                    });
                }
            }

            @Override
            public void lengthChanged(uk.co.caprica.vlcj.player.base.MediaPlayer mediaPlayer, long newLength) {
                durationSeconds = newLength / 1000.0;
                if (callback != null) {
                    Platform.runLater(() -> {
                        callback.onDurationChanged(durationSeconds);
                    });
                }
            }

            @Override
            public void finished(uk.co.caprica.vlcj.player.base.MediaPlayer mediaPlayer) {
                isPlaying.set(false);
            }

            @Override
            public void error(uk.co.caprica.vlcj.player.base.MediaPlayer mediaPlayer) {
                isPlaying.set(false);
            }
        });
    }

    public void setCallback(VideoPlayerCallback callback) {
        this.callback = callback;
    }

    public void openVideo(String filePath) throws Exception {
        if (mediaPlayer != null) {
            stop();
        }

        // 打开视频文件
        mediaPlayer.media().play(filePath);
        
        // 禁用字幕
        mediaPlayer.subpictures().setTrack(-1);
        
        // 获取视频信息
        currentTimeSeconds = 0;
        durationSeconds = mediaPlayer.status().length() / 1000.0;
        
        // 设置ImageView的尺寸
        Platform.runLater(() -> {
            int videoWidth = (int)mediaPlayer.video().videoDimension().getWidth();
            int videoHeight = (int)mediaPlayer.video().videoDimension().getHeight();
            double aspectRatio = (double) videoWidth / videoHeight;
            imageView.setFitWidth(800);
            imageView.setFitHeight(800 / aspectRatio);
            imageView.setPreserveRatio(true);
        });
        
        if (callback != null) {
            callback.onDurationChanged(durationSeconds);
            callback.onTimeChanged(currentTimeSeconds);
        }
    }

    public void play() {
        if (mediaPlayer == null || isPlaying.get()) return;
        
        mediaPlayer.controls().play();
        isPlaying.set(true);
    }

    public void pause() {
        if (mediaPlayer == null || !isPlaying.get()) return;
        
        mediaPlayer.controls().pause();
        isPlaying.set(false);
    }

    public void stop() {
        if (mediaPlayer == null) return;
        
        mediaPlayer.controls().stop();
        isPlaying.set(false);
        currentTimeSeconds = 0;
        if (callback != null) {
            Platform.runLater(() -> callback.onTimeChanged(currentTimeSeconds));
        }
    }

    public void seek(double seconds) {
        if (mediaPlayer == null) return;
        
        isUpdatingSlider = true;
        currentTimeSeconds = seconds;
        mediaPlayer.controls().setTime((long)(seconds * 1000));
        
        // 等待一小段时间确保时间已经更新
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            // 忽略中断异常
        }
        
        // 强制更新当前时间
        currentTimeSeconds = mediaPlayer.status().time() / 1000.0;
        
        if (callback != null) {
            Platform.runLater(() -> {
                callback.onTimeChanged(currentTimeSeconds);
                isUpdatingSlider = false;
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

    public void setPlaybackSpeed(double speed) {
        if (mediaPlayer != null) {
            mediaPlayer.controls().setRate((float)speed);
        }
    }

    public void dispose() {
        if (mediaPlayer != null) {
            stop();
            mediaPlayer.release();
        }
        if (mediaPlayerFactory != null) {
            mediaPlayerFactory.release();
        }
    }

    public interface VideoPlayerCallback {
        void onTimeChanged(double currentTimeSeconds);
        void onDurationChanged(double durationSeconds);
    }
} 