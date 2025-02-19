package com.subtitler;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class Subtitle {
    private final StringProperty content = new SimpleStringProperty();
    private final StringProperty startTime = new SimpleStringProperty();
    private final StringProperty endTime = new SimpleStringProperty();
    
    public Subtitle(String rawText) {
        parseRawText(rawText);
    }
    
    private void parseRawText(String rawText) {
        if (rawText.contains(" -> ")) {
            String[] mainParts = rawText.split(" \\| ");
            if (mainParts.length > 0) {
                String timePart = mainParts[0];
                String[] times = timePart.split(" -> ");
                
                if (times.length == 2) {
                    // 完整格式：00:00:05,909 -> 00:00:08,909 | 文本
                    startTime.set(times[0].trim());
                    endTime.set(times[1].trim());
                    content.set(mainParts.length > 1 ? mainParts[1] : "");
                } else if (timePart.startsWith("-> ")) {
                    // 只有结束时间：-> 00:00:08,909 | 文本
                    startTime.set("");
                    endTime.set(times[0].replace("-> ", "").trim());
                    content.set(mainParts.length > 1 ? mainParts[1] : "");
                } else if (timePart.endsWith(" ->")) {
                    // 只有开始时间：00:00:05,909 -> | 文本
                    startTime.set(times[0].trim());
                    endTime.set("");
                    content.set(mainParts.length > 1 ? mainParts[1] : "");
                }
            }
        } else {
            // 纯文本格式：文本
            content.set(rawText);
            startTime.set("");
            endTime.set("");
        }
    }
    
    public String toRawText() {
        String start = startTime.get() == null ? "" : startTime.get();
        String end = endTime.get() == null ? "" : endTime.get();
        
        if (start.isEmpty() && end.isEmpty()) {
            return content.get();
        } else if (start.isEmpty()) {
            return "-> " + end + " | " + content.get();
        } else if (end.isEmpty()) {
            return start + " -> | " + content.get();
        } else {
            return start + " -> " + end + " | " + content.get();
        }
    }
    
    public String getContent() {
        return content.get();
    }
    
    public void setContent(String value) {
        content.set(value);
    }
    
    public StringProperty contentProperty() {
        return content;
    }
    
    public String getStartTime() {
        return startTime.get();
    }
    
    public void setStartTime(String value) {
        startTime.set(value);
    }
    
    public StringProperty startTimeProperty() {
        return startTime;
    }
    
    public String getEndTime() {
        return endTime.get();
    }
    
    public void setEndTime(String value) {
        endTime.set(value);
    }
    
    public StringProperty endTimeProperty() {
        return endTime;
    }
} 