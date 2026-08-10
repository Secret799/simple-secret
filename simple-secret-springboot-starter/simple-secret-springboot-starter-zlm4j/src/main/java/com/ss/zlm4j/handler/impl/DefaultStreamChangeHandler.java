package com.ss.zlm4j.handler.impl;

import com.ss.zlm4j.domain.MediaSourceDomain;
import com.ss.zlm4j.handler.StreamChangeHandler;
import lombok.extern.slf4j.Slf4j;

/**
 * 默认注册或反注册MediaSource事件处理器
 *
 * @author JunPzx
 * @since 2025/8/21 15:15
 */
@Slf4j
public class DefaultStreamChangeHandler extends AbstractCallbackHandler implements StreamChangeHandler {

    @Override
    public void handleRegister(MediaSourceDomain mediaSource) {
        log.info("【SimpleSecretZLMediaKit】 流注册成功,app:{},stream:{},scheme:{}", mediaSource.getApp(),
                mediaSource.getStream(), mediaSource.getSchema());
    }

    @Override
    public void handleDeregister(MediaSourceDomain mediaSource) {
        log.info("【SimpleSecretZLMediaKit】 流注销成功,app:{},stream:{},scheme:{}", mediaSource.getApp(),
                mediaSource.getStream(), mediaSource.getSchema());
    }
}
