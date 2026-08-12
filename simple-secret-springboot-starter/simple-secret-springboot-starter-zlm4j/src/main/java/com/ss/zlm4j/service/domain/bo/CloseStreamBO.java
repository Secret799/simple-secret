package com.ss.zlm4j.service.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 关闭流请求参数
 *
 * @author junpzx
 * @since 2024/06/12 15:36
 **/
@Data
@Accessors(chain = true)
public class CloseStreamBO {

    /**
     * 媒体应用名。
     */
    @NotBlank(message = "app不为空")
    private String app;

    /**
     * 媒体流标识。
     */
    @NotBlank(message = "流id不为空")
    private String stream;

    /**
     * 是否强制执行。
     */
    @NotNull(message = "是否强制关闭不为空")
    private Integer force;

    /**
     * 数据结构定义。
     */
    @NotBlank(message = "流的协议不为空")
    private String schema;
}
