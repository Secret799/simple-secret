package com.ss.zlm4j.handler.register;

import com.ss.zlm4j.context.ZlmCallbackHandlerContext;
import com.ss.zlm4j.handler.impl.*;
import org.springframework.core.annotation.Order;

/**
 * 默认zlm 回调处理 注册器
 *
 * @author JunPzx
 * @since 2025/9/29 10:34
 */
@Order
public class DefaultZlmCallbackHandlerRegister implements ZlmCallbackHandlerRegister {
    @Override
    public void register(ZlmCallbackHandlerContext context) {
        context.setFlowReportHandler(new DefaultFlowReportHandler())
                .setHttpAccessHandler(new DefaultHttpAccessHandler())
                .setHttpBeforeAccessHandler(new DefaultHttpBeforeAccessHandler())
                .setHttpRequestHandler(new DefaultHttpRequestHandler())
                .setRecordMp4Handler(new DefaultRecordMp4Handler())
                .setRecordTsHandler(new DefaultRecordTsHandler())
                .setStreamChangeHandler(new DefaultStreamChangeHandler())
                .setStreamNoFoundHandler(new DefaultStreamNoFoundHandler())
                .setStreamNoReaderHandler(new DefaultStreamNoReaderHandler())
                .setStreamPlayHandler(new DefaultStreamPlayHandler())
                .setStreamPublishHandler(new DefaultStreamPublishHandler());
    }
}
