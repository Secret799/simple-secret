package com.ss.zlm4j.service.domain.bo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 流查询参数
 *
 * @author junpzx
 * @since 2024/06/12 15:36
 **/
@Data
@Accessors(chain = true)
public class MediaQueryBO {

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
     * 流的协议
     */
    @NotBlank(message = "流的协议不为空")
    private String schema;

}
