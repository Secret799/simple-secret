package com.ss.easymedia.core.handler.app;

import com.aizuda.zlm4j.structure.MK_MEDIA_INFO;
import com.aizuda.zlm4j.structure.MK_SOCK_INFO;
import com.ss.easymedia.core.handler.AbstractAppHandler;
import com.ss.easymedia.core.handler.app.nofound.StreamNoFoundAppHandler;
import com.ss.zlm4j.domain.MediaInfoDomain;
import com.ss.zlm4j.handler.StreamNoFoundHandler;
import com.ss.zlm4j.helper.ZlmMediaHelper;

/**
 * ems 流未找到 app分发器
 *
 * @author JunPzx
 * @since 2025/8/21 16:00
 */
public class EmsStreamNoFoundAppDispatcher extends AbstractAppHandler<StreamNoFoundAppHandler> implements StreamNoFoundHandler {

    @Override
    public int handle(MK_MEDIA_INFO urlInfo, MK_SOCK_INFO sender) {
        MediaInfoDomain mediaInfo = ZlmMediaHelper.Assembler.getMediaInfo(urlInfo);
        return doSomething(mediaInfo.getApp(),
                handler -> handler.handle(urlInfo, sender), 0);
    }
}
