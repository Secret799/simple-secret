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
