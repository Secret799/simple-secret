package com.ss.zlm4j.callback;

import java.util.Optional;

import com.aizuda.zlm4j.callback.IMKHttpRequestCallBack;
import com.aizuda.zlm4j.structure.MK_HTTP_RESPONSE_INVOKER;
import com.aizuda.zlm4j.structure.MK_PARSER;
import com.aizuda.zlm4j.structure.MK_SOCK_INFO;
import com.ss.zlm4j.handler.HttpRequestHandler;
import com.sun.jna.ptr.IntByReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 收到http api请求广播(包括GET/POST)
 *
 * @author junpzx
 * @since 2024-06-12 13:49
 */
@Slf4j
@RequiredArgsConstructor
public class MKHttpRequestCallBack implements IMKHttpRequestCallBack {

    private final HttpRequestHandler handler;

    /**
     * 收到http api请求广播(包括GET/POST)
     *
     * @param parser   http请求内容对象
     * @param invoker  执行该invoker返回http回复
     * @param consumed 置1则说明我们要处理该事件
     * @param sender   http客户端相关信息
     */
    @Override
    public void invoke(MK_PARSER parser, MK_HTTP_RESPONSE_INVOKER invoker, IntByReference consumed, MK_SOCK_INFO sender) {
        log.info("【SimpleSecretZLMediaKit】 收到http api请求（包括Get/Post）事件 回调开始");
        try {
            Optional.ofNullable(handler).ifPresentOrElse(
                    t -> t.handle(parser, invoker, consumed, sender),
                    () -> consumed.setValue(0));
        } catch (Exception e) {
            log.error("【SimpleSecretZLMediaKit】HTTP访问文件或者目录事件 回调处理器发生异常", e);
        }
        log.info("【SimpleSecretZLMediaKit】 收到http api请求（包括Get/Post）事件 回调结束");
    }
}
