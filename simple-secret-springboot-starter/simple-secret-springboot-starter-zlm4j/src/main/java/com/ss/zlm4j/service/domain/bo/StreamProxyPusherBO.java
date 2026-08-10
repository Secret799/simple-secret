package com.ss.zlm4j.service.domain.bo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 推流代理参数
 *
 * @author junpzx
 * @since 2025/8/26
 */
@Data
public class StreamProxyPusherBO {

    /**
     * app
     */
    @NotBlank(message = "app不为空")
    private String app;

    /**
     * 流ID
     */
    @NotBlank(message = "流id不为空")
    private String stream;

    /**
     * 流协议
     */
    @NotBlank(message = "流的协议不为空")
    private String schema;

    /**
     * 流代理地址
     */
    @NotBlank(message = "推流代理流地址不为空")
    private String url;

    /**
     * rtsp推流时，推流方式
     * <p>
     * 0：tcp，1：udp，2：组播
     */
    private Integer rtpType = 0;

    /**
     * 推流代理超时时间，单位秒
     */
    private Integer timeoutSec;
}
