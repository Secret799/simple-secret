package com.ss.zlm4j.handler.impl;

import com.aizuda.zlm4j.core.ZLMApi;
import com.aizuda.zlm4j.structure.MK_AUTH_INVOKER;
import com.aizuda.zlm4j.structure.MK_MEDIA_INFO;
import com.aizuda.zlm4j.structure.MK_SOCK_INFO;
import com.ss.zlm4j.config.properties.ZlmMediaProperties;
import com.ss.zlm4j.handler.StreamPlayHandler;
import com.ss.zlm4j.helper.ZlmMediaHelper;

/**
 * 默认播放回调 作用域处理器
 *
 * @author JunPzx
 * @since 2025/8/21 15:39
 */
public class DefaultStreamPlayHandler extends AbstractCallbackHandler implements StreamPlayHandler {

    private static final String ANONYMOUS_PLAY_DISABLED = "ANONYMOUS_PLAY_DISABLED";

    private final ZlmMediaProperties properties;
    private final ZLMApi zlmApi;

    /**
     * 创建并初始化实例。
     */
    public DefaultStreamPlayHandler() {
        this(null, null);
    }

    /**
     * 创建默认播放鉴权处理器。
     *
     * @param properties ZLM 配置
     * @param zlmApi     原生 API
     */
    public DefaultStreamPlayHandler(ZlmMediaProperties properties, ZLMApi zlmApi) {
        this.properties = properties;
        this.zlmApi = zlmApi;
    }

    @Override
    public void handle(MK_MEDIA_INFO urlInfo, MK_AUTH_INVOKER invoker, MK_SOCK_INFO sender) {
        boolean allowAnonymous = Boolean.TRUE.equals(properties().getAllowAnonymousPlay());
        api().mk_auth_invoker_do(invoker, allowAnonymous ? "" : ANONYMOUS_PLAY_DISABLED);
    }

    private ZlmMediaProperties properties() {
        return properties != null ? properties : ZlmMediaHelper.getContext().getDefaultProperties();
    }

    private ZLMApi api() {
        return zlmApi != null ? zlmApi : ZlmMediaHelper.getZlmApi();
    }
}
