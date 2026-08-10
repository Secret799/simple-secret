package com.ss.easymedia.core.handler.app;

import com.aizuda.zlm4j.structure.MK_MEDIA_SOURCE;
import com.ss.easymedia.core.handler.AbstractAppHandler;
import com.ss.easymedia.core.handler.app.noreader.StreamNoReaderAppHandler;
import com.ss.zlm4j.domain.MediaSourceDomain;
import com.ss.zlm4j.handler.StreamNoReaderHandler;
import com.ss.zlm4j.helper.ZlmMediaHelper;

/**
 * ems 流无人观看 app分发器
 *
 * @author JunPzx
 * @since 2025/8/21 16:00
 */
public class EmsStreamNoReaderAppDispatcher extends AbstractAppHandler<StreamNoReaderAppHandler> implements StreamNoReaderHandler {

    @Override
    public void handle(MK_MEDIA_SOURCE sender) {
        MediaSourceDomain mediaSource = ZlmMediaHelper.Assembler.getMediaSource(sender);
        doSomething(mediaSource.getApp(), handler -> handler.handle(sender));
    }
}
