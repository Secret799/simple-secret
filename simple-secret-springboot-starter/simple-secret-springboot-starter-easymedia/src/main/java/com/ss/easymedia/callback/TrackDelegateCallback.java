package com.ss.easymedia.callback;

import com.ss.zlm4j.domain.MediaSourceDomain;
import com.ss.zlm4j.domain.TrackDomain;

import java.util.Set;

/**
 * 轨道监听回调(可用于sei获取)
 *
 * @author JunPzx
 * @since 2025/12/18 15:40
 */
public interface TrackDelegateCallback {

    /**
     * 获取当前回调支持的媒体源协议。
     *
     * @return 支持的 ZLM schema 集合
     */
    default Set<String> supportedSchemas() {
        return Set.of("ts");
    }

    /**
     * 处理媒体源注册事件。
     *
     * @param mediaSource 媒体源信息
     */
    default void onMediaSourceRegistered(MediaSourceDomain mediaSource) {
    }

    /**
     * 处理媒体源注销事件。
     *
     * @param mediaSource 媒体源信息
     */
    default void onMediaSourceDeregistered(MediaSourceDomain mediaSource) {
    }

    /**
     * 轨道监听回调
     *
     * @param mediaSourceDomain 视频流信息
     * @param trackDomain       视频轨道信息
     * @param tackDelegateInfo  轨道帧信息
     */
    void callback(MediaSourceDomain mediaSourceDomain, TrackDomain trackDomain, TackDelegateInfo tackDelegateInfo);


    class TackDelegateInfo {
        /**
         * 数据长度
         */
        private Long dataLength;

        /**
         * 数据
         */
        private byte[] data;

        /**
         * 数据前缀长度
         */
        private Long dataPrefixSize;

        /**
         * 获取解码时间戳，单位毫秒
         */
        private Long dts;

        /**
         * 获取显示时间戳，单位毫秒
         */
        private Long pts;

        /**
         * 获取帧flag，请参考 MK_FRAME_FLAG
         */
        private Long flag;

        public Long getDataLength() {
            return dataLength;
        }

        public TackDelegateInfo setDataLength(Long dataLength) {
            this.dataLength = dataLength;
            return this;
        }

        public byte[] getData() {
            return data;
        }

        public TackDelegateInfo setData(byte[] data) {
            this.data = data;
            return this;
        }

        public Long getDataPrefixSize() {
            return dataPrefixSize;
        }

        public TackDelegateInfo setDataPrefixSize(Long dataPrefixSize) {
            this.dataPrefixSize = dataPrefixSize;
            return this;
        }

        public Long getDts() {
            return dts;
        }

        public TackDelegateInfo setDts(Long dts) {
            this.dts = dts;
            return this;
        }

        public Long getPts() {
            return pts;
        }

        public TackDelegateInfo setPts(Long pts) {
            this.pts = pts;
            return this;
        }

        public Long getFlag() {
            return flag;
        }

        public TackDelegateInfo setFlag(Long flag) {
            this.flag = flag;
            return this;
        }
    }
}
