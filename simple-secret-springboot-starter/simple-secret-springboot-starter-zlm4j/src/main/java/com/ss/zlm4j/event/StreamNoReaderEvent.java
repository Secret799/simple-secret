package com.ss.zlm4j.event;

import com.ss.zlm4j.domain.MediaSourceDomain;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 流无人观看事件
 *
 * @author JunPzx
 * @since 2024/8/6 14:08
 */
@Getter
public class StreamNoReaderEvent extends ApplicationEvent {
    private final MediaSourceDomain mediaSource;

    /**
     * 创建并初始化实例。
     *
     * @param mediaSource ZLMediaKit 媒体源
     */
    public StreamNoReaderEvent(MediaSourceDomain mediaSource) {
        super(mediaSource);
        this.mediaSource = mediaSource;
    }
}
