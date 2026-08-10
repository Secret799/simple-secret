package com.ss.zlm4j.service.domain.bo;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 获取流列表
 *
 * @author junpzx
 * @since 2024/06/12 15:36
 **/
@Data
@Accessors(chain = true)
public class GetMediaListBO {

    /**
     * app
     */
    private String app;

    /**
     * 流id
     */
    private String stream;

    /**
     * 流的协议
     */
    private String schema;

    /**
     * 流Key(与 app和 stream互斥)
     */
    private String streamKey;
}
