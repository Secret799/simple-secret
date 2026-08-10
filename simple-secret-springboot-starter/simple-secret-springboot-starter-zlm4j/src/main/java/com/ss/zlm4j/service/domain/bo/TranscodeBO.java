package com.ss.zlm4j.service.domain.bo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;

/**
 * 转码业务对象
 *
 * @author JunPzx
 * @since 2025/8/20 17:59
 */
@Data
@Accessors(chain = true)
public class TranscodeBO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1;

    /**
     * url(rtmp协议只支持H264)
     */
    @NotBlank(message = "url不为空")
    private String url;

    /**
     * 转码后推的app
     */
    @NotBlank(message = "app不为空")
    private String app;

    /**
     * 是否开启音频
     */
    private Boolean enableAudio = true;

    /**
     * 转码后推的stream
     */
    @NotBlank(message = "stream不为空")
    private String stream;

    /**
     * 修改分辨率宽，不需要则置为空
     */
    private Integer scaleWidth = 0;

    /**
     * 修改分辨率高，不需要则置为空
     */
    private Integer scaleHeight = 0;

}
