package com.ss.zlm4j.event;

import com.ss.zlm4j.domain.MediaSourceDomain;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 流代理注册事件
 *
 * @author junpzx
 * @since 2024/8/2
 */
@Getter
public class StreamRegisteredEvent extends ApplicationEvent {
    private final MediaSourceDomain mediaSource;

    /**
     * 创建并初始化实例。
     *
     * @param mediaSource ZLMediaKit 媒体源
     */
    public StreamRegisteredEvent(MediaSourceDomain mediaSource) {
        super(mediaSource);
        this.mediaSource = mediaSource;
    }
}