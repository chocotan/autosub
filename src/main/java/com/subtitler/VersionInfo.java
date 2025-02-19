package com.subtitler;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class VersionInfo {
    private static final Properties versionProps = new Properties();
    private static final String VERSION;
    private static final String BUILD_TIME;
    private static final String COPYRIGHT = "南通深空网络科技有限公司 版权所有";
    
    static {
        try (InputStream is = VersionInfo.class.getResourceAsStream("/version.properties")) {
            if (is != null) {
                versionProps.load(is);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        VERSION = versionProps.getProperty("version", "未知版本");
        BUILD_TIME = versionProps.getProperty("buildTime", "未知时间");
    }
    
    public static String getVersion() {
        return VERSION;
    }
    
    public static String getBuildTime() {
        return BUILD_TIME;
    }
    
    public static String getFullVersionInfo() {
        return String.format("视频字幕打轴工具\n" +
                           "版本：%s\n" +
                           "构建时间：%s\n" +
                           "\n" +
                           "© %d %s\n" +
                           "保留所有权利。", 
                           VERSION, 
                           BUILD_TIME,
                           java.time.Year.now().getValue(),
                           COPYRIGHT);
    }
} 