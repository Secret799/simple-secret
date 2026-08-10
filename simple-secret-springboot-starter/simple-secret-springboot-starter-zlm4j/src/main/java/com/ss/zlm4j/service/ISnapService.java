package com.ss.zlm4j.service;

/**
 * 快照服务
 *
 * @author JunPzx
 * @since 2025/8/20 16:48
 */
public interface ISnapService {

    /**
     * 获取截图
     *
     * @param url 需要截图的地址
     * @return base64文件内容
     */
    String snapToBase64(String url);


}
