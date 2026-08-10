package com.ss.zlm4j.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 视频拼接任务的资源上限。
 */
@Data
@ConfigurationProperties(prefix = "simple-secret.zlm4j.video-stack")
public class VideoStackValidationProperties {

    /** 最大画布像素数。 */
    private long maxPixels = 16_777_216L;

    /** 最大单边尺寸。 */
    private int maxDimension = 8192;

    /** 最大网格数量。 */
    private int maxCells = 256;

    /** 最大窗口数量。 */
    private int maxWindows = 64;
}
