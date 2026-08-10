package com.ss.zlm4j.service;

import com.ss.zlm4j.service.domain.bo.VideoStackBO;

/**
 * 视频拼接屏服务
 *
 * @author JunPzx
 * @since 2025/8/20 18:15
 */
public interface IVideoStackService {

    /**
     * 开启拼接屏任务
     *
     * @param param 拼接参数
     */
    void startStack(VideoStackBO param);

    /**
     * 重设拼接屏任务
     *
     * @param param 拼接参数
     */
    void resetStack(VideoStackBO param);

    /**
     * 停止拼接屏任务
     *
     * @param id 任务ID
     */
    void stopStack(String id);


}
