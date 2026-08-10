package com.ss.zlm4j.handler;

import com.aizuda.zlm4j.structure.MK_MEDIA_SOURCE;
import com.ss.zlm4j.domain.MediaSourceDomain;
import com.ss.zlm4j.helper.ZlmMediaHelper;

/**
 * Zlm 无人观看作用域处理器
 *
 * @author JunPzx
 * @since 2024/8/8 18:32
 */
public interface StreamNoReaderHandler {

    /**
     * 处理无人观看
     *
     * @param mediaSourceDomain 媒体源
     */
    default void handle(MediaSourceDomain mediaSourceDomain) {
    }

    /**
     * 处理无人观看（如果需要关闭媒体或者其他操作，请实现本方法，通过{@code ZlmMediaHelper.getZlmApi().mk_media_source_close(mkMediaSource,0)}）
     *
     * @param sender 数据源
     */
    default void handle(MK_MEDIA_SOURCE sender) {
        handle(ZlmMediaHelper.Assembler.getMediaSource(sender));
    }
}
