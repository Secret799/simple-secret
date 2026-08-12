package com.ss.zlm4j.event;

import com.ss.zlm4j.domain.RecordInfoDomain;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 录制分片文件成功事件
 *
 * @author JunPzx
 * @since 2025/8/20 14:46
 */
@Getter
public class RecordShardingFileEvent extends ApplicationEvent {
    /**
     * 录制信息
     */
    private final RecordInfoDomain recordInfoDomain;
    /**
     * 录制来源
     */
    private final RecordSource recordSource;

    /**
     * 创建并初始化实例。
     *
     * @param recordSource 录像事件源对象
     * @param recordInfoDomain 录像分片信息
     */
    public RecordShardingFileEvent(RecordSource recordSource, RecordInfoDomain recordInfoDomain) {
        super(recordSource);
        this.recordSource = recordSource;
        this.recordInfoDomain = recordInfoDomain;
    }

    /**
     * 录制来源
     */
    public enum RecordSource {
        MP4,

        TS
    }
}
