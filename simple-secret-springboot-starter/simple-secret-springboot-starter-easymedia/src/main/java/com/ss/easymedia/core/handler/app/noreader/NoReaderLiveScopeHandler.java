package com.ss.easymedia.core.handler.app.noreader;

import com.aizuda.zlm4j.structure.MK_MEDIA_SOURCE;
import com.ss.easymedia.core.constants.EasyMediaConstants;
import com.ss.zlm4j.handler.impl.AbstractCallbackHandler;
import com.ss.zlm4j.helper.ZlmMediaHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Live作用域无人观看监听器(
 * )
 *
 * @author JunPzx
 * @since 2024/8/9 11:23
 */
@Component
public class NoReaderLiveScopeHandler extends AbstractCallbackHandler implements StreamNoReaderAppHandler {

    private static final Logger log = LoggerFactory.getLogger(NoReaderLiveScopeHandler.class);

    @Override
    public String app() {
        return EasyMediaConstants.Scope.LIVE_APP;
    }

    @Override
    public void handle(MK_MEDIA_SOURCE mkMediaSource) {
        log.info("{}作用域无人观看监听器", app());
        // 无人观看时候可以调用下面的实现关流 不调用就代表不关流 需要配置protocol.auto_close 为 0 这里才会有回调
        ZlmMediaHelper.getZlmApi().mk_media_source_close(mkMediaSource, 0);
        log.info("{}作用域强制关闭无人观看流成功", app());
    }
}
