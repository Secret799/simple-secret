package com.ss.dji.camera.diagnostic;

import com.ss.dji.camera.parser.SeiParseResult;
import com.ss.dji.camera.parser.VideoCodec;
import com.ss.easymedia.callback.TrackDelegateCallback.TackDelegateInfo;
import com.ss.zlm4j.domain.MediaSourceDomain;

/**
 * 接收 DJI SEI 诊断生命周期和帧解析结果。
 *
 * <p>实现必须快速返回；媒体帧回调不会等待耗时的网络或磁盘操作。</p>
 */
public interface DjiSeiEventListener {

    /** 媒体流已经注册。 */
    default void onStreamRegistered(MediaSourceDomain source) {
    }

    /** 一个视频帧已经完成有界 SEI 解析。 */
    default void onFrame(MediaSourceDomain source, TackDelegateInfo frame,
                         VideoCodec codec, SeiParseResult result) {
    }

    /** 媒体流已经注销。 */
    default void onStreamDeregistered(MediaSourceDomain source) {
    }
}
