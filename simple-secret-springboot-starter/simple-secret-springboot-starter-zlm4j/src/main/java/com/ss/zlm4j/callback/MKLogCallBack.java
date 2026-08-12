package com.ss.zlm4j.callback;

import com.aizuda.zlm4j.callback.IMKLogCallBack;
import com.sun.jna.CallbackThreadInitializer;
import com.sun.jna.Native;
import lombok.extern.slf4j.Slf4j;

/**
 * 日志输出广播
 *
 * @author junpzx
 * @since 2024-06-12 13:49
 */
@Slf4j
public class MKLogCallBack implements IMKLogCallBack {

    /**
     * 创建并初始化实例。
     */
    public MKLogCallBack() {
        //回调使用同一个线程
        Native.setCallbackThreadInitializer(this, new CallbackThreadInitializer(true, false, "MediaServerLogThread"));
    }

    /**
     * 日志输出广播
     *
     * @param level    日志级别
     * @param file     源文件名
     * @param line     源文件行
     * @param function 源文件函数名
     * @param message  日志内容
     */
    @Override
    public void invoke(int level, String file, int line, String function, String message) {
        log.info("【SimpleSecretZLMediaKit】 日志输出事件 回调开始");
        switch (level) {
            case 0:
                log.trace("【SimpleSecretZLMediaKit】{}", message);
                break;
            case 1:
                log.debug("【SimpleSecretZLMediaKit】{}", message);
                break;
            case 3:
                log.warn("【SimpleSecretZLMediaKit】{}", message);
                break;
            case 4:
                log.error("【SimpleSecretZLMediaKit】{}", message);
                break;
            case 2:
            default:
                log.info("【SimpleSecretZLMediaKit】{}", message);
                break;
        }
        log.info("【SimpleSecretZLMediaKit】 日志输出事件 回调结束");
    }
}