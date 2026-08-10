package com.ss.zlm4j.handler.impl;

import com.aizuda.zlm4j.core.ZLMApi;
import com.aizuda.zlm4j.structure.*;
import com.ss.zlm4j.config.properties.ZlmMediaProperties;
import com.ss.zlm4j.handler.StreamPublishHandler;
import com.ss.zlm4j.helper.ZlmMediaHelper;
import lombok.extern.slf4j.Slf4j;

/**
 * 推流回调 作用域处理器
 *
 * @author JunPzx
 * @since 2024/8/8 18:32
 */
@Slf4j
public class DefaultStreamPublishHandler extends AbstractCallbackHandler implements StreamPublishHandler {

    private static final String ANONYMOUS_PUBLISH_DISABLED = "ANONYMOUS_PUBLISH_DISABLED";

    private final ZlmMediaProperties properties;
    private final ZLMApi zlmApi;

    public DefaultStreamPublishHandler() {
        this(null, null);
    }

    /**
     * 创建默认推流鉴权处理器。
     *
     * @param properties ZLM 配置
     * @param zlmApi     原生 API
     */
    public DefaultStreamPublishHandler(ZlmMediaProperties properties, ZLMApi zlmApi) {
        this.properties = properties;
        this.zlmApi = zlmApi;
    }

    @Override
    public void handle(MK_MEDIA_INFO urlInfo, MK_PUBLISH_AUTH_INVOKER invoker, MK_SOCK_INFO sender) {
        ZLMApi api = api();
        if (!Boolean.TRUE.equals(properties().getAllowAnonymousPublish())) {
            api.mk_publish_auth_invoker_do2(invoker, ANONYMOUS_PUBLISH_DISABLED, null);
            return;
        }
        // 判断系统中是否存在该流,防止推流重复导致系统宕机
        MK_MEDIA_SOURCE mediaSource = getMediaSource(api, urlInfo);
        if (mediaSource != null) {
            log.info("【SimpleSecretZLMediaKit】 流已存在,请勿重复推流");
            api.mk_publish_auth_invoker_do2(invoker, "STREAM_ALREADY_EXISTS", null);
            return;
        }
        // 这里拿到访问路径后(例如rtmp://xxxx/xxx/xxx?token=xxxx其中?后面就是拿到的参数)的参数
        // String param = ZlmMediaHelper.getZlmApi().mk_media_info_get_params(urlInfo);
        // err_msg返回 空字符串表示鉴权成功 否则鉴权失败提示
        // ZlmMediaHelper.getZlmApi().mk_publish_auth_invoker_do(invoker, "", 1, 0);
        MK_INI option = api.mk_ini_create();
        try {
            ZlmMediaHelper.Configurator.setConfig(api, option, properties());
            api.mk_publish_auth_invoker_do2(invoker, "", option);
        } finally {
            api.mk_ini_release(option);
        }
    }

    private MK_MEDIA_SOURCE getMediaSource(ZLMApi api, MK_MEDIA_INFO mediaInfo) {
        return api.mk_media_source_find2(
                api.mk_media_info_get_schema(mediaInfo),
                api.mk_media_info_get_vhost(mediaInfo),
                api.mk_media_info_get_app(mediaInfo),
                api.mk_media_info_get_stream(mediaInfo),
                0);
    }

    private ZlmMediaProperties properties() {
        return properties != null ? properties : ZlmMediaHelper.getContext().getDefaultProperties();
    }

    private ZLMApi api() {
        return zlmApi != null ? zlmApi : ZlmMediaHelper.getZlmApi();
    }
}
