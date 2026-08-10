package com.ss.zlm4j.service.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;


/**
 * 录像状态
 *
 * @author junpzx
 * @since 2024/06/12 15:36
 **/
@Data
@Accessors(chain = true)
public class RecordStatusBO {


    /**
     * app
     */
    @NotBlank(message = "app不为空")
    private String app;

    /**
     * 流id
     */
    @NotBlank(message = "流id不为空")
    private String stream;

    /**
     * 录像类型(0为hls，1为mp4,2:hls-fmp4,3:http-fmp4,4:http-ts 当0时需要开启配置分片持久化)
     */
    @NotNull(message = "录像类型不为空")
    private Integer type;

}
