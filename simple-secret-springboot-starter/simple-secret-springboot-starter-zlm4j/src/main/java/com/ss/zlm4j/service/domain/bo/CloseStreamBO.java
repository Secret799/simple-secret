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

    @NotBlank(message = "app不为空")
    private String app;

    @NotBlank(message = "流id不为空")
    private String stream;

    @NotNull(message = "是否强制关闭不为空")
    private Integer force;

    @NotBlank(message = "流的协议不为空")
    private String schema;
}
