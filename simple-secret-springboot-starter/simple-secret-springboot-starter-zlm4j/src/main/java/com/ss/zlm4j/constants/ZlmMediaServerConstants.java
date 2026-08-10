package com.ss.zlm4j.constants;

import org.slf4j.helpers.MessageFormatter;

/**
 * zlm4j 媒体服务常量
 *
 * @author JunPzx
 * @since 2024/6/12 14:14
 */
public interface ZlmMediaServerConstants {
    /**
     * 默认vhost
     */
    String DEFAULT_VHOST = "__defaultVhost__";

    /**
     * 播放模板常量
     */
    interface PlayUrlTemplate {
        /**
         * rtmp播放地址
         */
        String RTMP = "rtmp://{}:{}/{}/{}";
        /**
         * rtsp播放地址
         */
        String RTSP = "rtsp://{}:{}/{}/{}";
        /**
         * flv 播放地址
         */
        String FLV = "{}://{}:{}/{}/{}.live.flv";
        /**
         * ts 播放地址
         */
        String TS = "{}://{}:{}/{}/{}.live.ts";
        /**
         * hls播放地址
         */
        String HLS = " {}://{}:{}/{}/{}/hls.m3u8";
        /**
         * fmp4播放地址
         */
        String HTTP_FMP4 = "{}://{}:{}/{}/{}.live.mp4";

        /**
         * 格式化播放地址
         *
         * @param template  播放模板
         * @param agreement 协议(ws/wss/http/https)
         * @param ip        ip地址
         * @param port      端口
         * @param app       应用名称
         * @param stream    流名称
         * @return 播放地址
         */
        default String format(String template, String agreement, String ip, String port, String app, String stream) {
            return MessageFormatter.basicArrayFormat(template, new Object[]{agreement, ip, port, app, stream});
        }

        /**
         * 格式化播放地址
         *
         * @param template 播放模板
         * @param ip       ip地址
         * @param port     端口
         * @param app      应用名称
         * @param stream   流名称
         * @return 播放地址
         */
        default String format(String template, String ip, String port, String app, String stream) {
            return format(template, null, ip, port, app, stream);
        }
    }

    /**
     * 推流模板常量
     */
    interface PublishUrlTemplate {
        /**
         * rtmp 推流模板
         */
        String RTMP = "rtmp://{}:{}/{}/{}";

        /**
         * rtsp 推流模板
         */
        String RTSP = "rtsp://{}:{}/{}/{}";

        /**
         * srt 推流模板
         */
        String SRT = "srt://{}:{}?streamid=#!::r={}/{},m=publish";


        /**
         * 格式化播放地址
         *
         * @param template 播放模板
         * @param ip       ip地址
         * @param port     端口
         * @param app      应用名称
         * @param stream   流名称
         * @return 播放地址
         */
        default String format(String template, String ip, String port, String app, String stream) {
            return MessageFormatter.basicArrayFormat(template, new Object[]{ip, port, app, stream});
        }
    }
}
