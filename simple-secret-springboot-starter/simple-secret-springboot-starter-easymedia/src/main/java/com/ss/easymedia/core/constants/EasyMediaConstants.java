package com.ss.easymedia.core.constants;

/**
 * ZLMedia APP 常量
 *
 * @author JunPzx
 * @since 2024/7/25 14:04
 */
public interface EasyMediaConstants {

    /**
     * 作用域
     */
    interface Scope {
        /**
         * 直播流(在无人观看的情况下会自动关闭，无人观看的配置：{@link com.ss.zlm4j.config.properties.ZlmMediaProperties#getStreamNoneReaderDelayMs})
         */
        String LIVE_APP = "live";

        /**
         * 外部视频文件点播流(不会自动关闭,一直重复循环推流)
         */
        String DIBBLING_APP = "dibbling";

        /**
         * 默认点播流地址（读取指定文件夹下的文件进行点播,自定义app时不要使用）
         */
        String RECORD_APP = "record";

    }
}
