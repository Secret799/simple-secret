package com.ss.zlm4j.service;

import com.ss.zlm4j.service.domain.bo.TranscodeBO;

/**
 * 转码服务
 *
 * @author JunPzx
 * @since 2025/8/20 17:59
 */
public interface ITranscodeService {

    /**
     * 转码
     *
     * @param param 转码参数
     */
    void transcode(TranscodeBO param);

    /**
     * 停止转码
     *
     * @param stream 流ID
     */
    void stopTranscode(String stream);


}
