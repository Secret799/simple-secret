package com.ss.zlm4j.callback;

import java.util.Optional;

import com.aizuda.zlm4j.callback.IMKPlayCallBack;
import com.aizuda.zlm4j.structure.MK_AUTH_INVOKER;
import com.aizuda.zlm4j.structure.MK_MEDIA_INFO;
import com.aizuda.zlm4j.structure.MK_SOCK_INFO;
import com.ss.zlm4j.handler.StreamPlayHandler;
import com.sun.jna.CallbackThreadInitializer;
import com.sun.jna.Native;
import lombok.extern.slf4j.Slf4j;

/**
 * 播放rtsp/rtmp/http-flv/hls事件广播，通过该事件控制播放鉴权
 *
 * @author junpzx
 * @since 2023/11/23
 **/
@Slf4j
public class MKPlayCallBack implements IMKPlayCallBack {

    private final StreamPlayHandler handler;


    /**
     * 创建并初始化实例。
     *
     * @param handler 消息处理
     */
    public MKPlayCallBack(StreamPlayHandler handler) {
        //回调使用同一个线程
        Native.setCallbackThreadInitializer(this, new CallbackThreadInitializer(true, false, "MediaPlayThread"));
        this.handler = handler;
    }

    /**
     * 播放rtsp/rtmp/http-flv/hls事件广播，通过该事件控制播放鉴权
     *
     * @param urlInfo 播放url相关信息
     * @param invoker 执行invoker返回鉴权结果
     * @param sender  播放客户端相关信息
     * @see "mk_auth_invoker_do"
     */
    @Override
    public void invoke(MK_MEDIA_INFO urlInfo, MK_AUTH_INVOKER invoker, MK_SOCK_INFO sender) {
        log.info("【SimpleSecretZLMediaKit】 播放rtsp/rtmp/http-flv/hls事件 回调开始");
        try {
            Optional.ofNullable(handler).ifPresent(t -> t.handle(urlInfo, invoker, sender));
            // 这里拿到访问路径后(例如http://xxxx/xxx/xxx.live.flv?token=xxxx其中?后面就是拿到的参数)的参数
            // err_msg返回 空字符串表示鉴权成功 否则鉴权失败提示
            // String param = ZLM_API.mk_media_info_get_params(url_info);
            // ZlmMediaHelper.getZlmApi().mk_auth_invoker_do(invoker, "");
        } catch (Exception e) {
            log.error("【SimpleSecretZLMediaKit】 播放rtsp/rtmp/http-flv/hls事件 回调处理器发生异常", e);
        }
        log.info("【SimpleSecretZLMediaKit】 播放rtsp/rtmp/http-flv/hls事件 回调结束");
    }
}
