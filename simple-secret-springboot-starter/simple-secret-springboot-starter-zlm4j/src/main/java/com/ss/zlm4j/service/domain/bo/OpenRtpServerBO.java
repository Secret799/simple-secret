package com.ss.zlm4j.service.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;


/**
 * 开启rtp服务
 *
 * @author junpzx
 * @since 2024/06/12 15:36
 **/
@Data
@Accessors(chain = true)
public class OpenRtpServerBO {


    /**
     * 接收端口，0则为随机端口
     */
    @NotNull(message = "接收端口，0则为随机端口")
    private Integer port;

    /**
     * tcp_mode(0 udp 模式，1 tcp 被动模式, 2 tcp 主动模式。 (兼容enable_tcp 为0/1))
     */
    @NotNull(message = "0 udp 模式，1 tcp 被动模式, 2 tcp 主动模式。 (兼容enable_tcp 为0/1)")
    private Integer tcpMode;

    /**
     * 流id
     */
    @NotBlank(message = "流id不为空")
    private String stream;


}
