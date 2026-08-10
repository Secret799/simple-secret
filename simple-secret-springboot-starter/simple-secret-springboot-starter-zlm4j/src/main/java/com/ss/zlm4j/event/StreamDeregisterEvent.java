package com.ss.zlm4j.event;

import com.ss.zlm4j.domain.MediaSourceDomain;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 流代理注销事件
 *
 * @author junpzx
 * @since 2024/8/2
 */
@Getter
public class StreamDeregisterEvent extends ApplicationEvent {
    private final MediaSourceDomain mediaSource;

    public StreamDeregisterEvent(MediaSourceDomain mediaSource) {
        super(mediaSource);
        this.mediaSource = mediaSource;
    }
}