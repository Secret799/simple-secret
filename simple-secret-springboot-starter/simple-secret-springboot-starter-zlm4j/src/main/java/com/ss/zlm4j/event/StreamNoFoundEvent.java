package com.ss.zlm4j.event;

import com.ss.zlm4j.domain.MediaInfoDomain;
import com.ss.zlm4j.domain.SocketInfoDomain;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 未找到流后会广播该事件
 *
 * @author JunPzx
 * @since 2025/9/23 09:46
 */
@Getter
public class StreamNoFoundEvent extends ApplicationEvent {

    private final MediaInfoDomain mediaInfo;
    private final SocketInfoDomain socketInfo;


    public StreamNoFoundEvent(MediaInfoDomain mediaInfo, SocketInfoDomain socketInfo) {
        super(mediaInfo);
        this.mediaInfo = mediaInfo;
        this.socketInfo = socketInfo;
    }


}
