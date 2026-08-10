package com.ss.zlm4j.service.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * rtp服务
 *
 * @author lidaofu
 * @since 2023/3/30
 **/
@Data
public class RtpServerVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1;

    /**
     * 接收端口，0则为随机端口
     */
    private Integer port;

    /**
     * 流id
     */
    private String stream;


}
