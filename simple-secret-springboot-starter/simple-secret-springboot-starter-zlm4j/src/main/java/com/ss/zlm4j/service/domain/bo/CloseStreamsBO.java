package com.ss.zlm4j.service.domain.bo;

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
public class CloseStreamsBO {

    /**
     * app
     */
    private String app;

    /**
     * 流id
     */
    private String stream;

    /**
     * 是否强制关闭
     */
    private Integer force = 1;

    /**
     * 流的协议
     */
    private String schema;
}
