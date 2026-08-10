package com.ss.easymedia.core.handler.app.noreader;

import com.ss.easymedia.core.constants.EasyMediaConstants;
import com.ss.zlm4j.domain.MediaSourceDomain;
import com.ss.zlm4j.handler.impl.AbstractCallbackHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 视频点播流作用域无人观看监听器
 *
 * @author JunPzx
 * @since 2024/8/9 11:23
 */
@Component
public class NoReaderDibblingScopeHandler extends AbstractCallbackHandler implements StreamNoReaderAppHandler {

    private static final Logger log = LoggerFactory.getLogger(NoReaderDibblingScopeHandler.class);

    @Override
    public String app() {
        return EasyMediaConstants.Scope.DIBBLING_APP;
    }

    @Override
    public void handle(MediaSourceDomain mediaSourceDomain) {
        log.info("{}作用域无人观看不会自动关闭流", app());
    }

}
