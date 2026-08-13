package com.ss.camerazlm;

/**
 * 供适配会话隔离 EasyMedia H.264 publisher 的窄边界。
 *
 * @author junpzx
 * @since 2026-08-13
 */
interface H264StreamPublisher {

    /**
     * 按到达顺序推送 H.264 Annex-B 数据。
     *
     * @param app ZLM 应用名
     * @param stream ZLM 流名
     * @param data H.264 Annex-B 数据
     * @return 推送完成后 publisher 仍保留的字节数
     * @throws InterruptedException 推送线程在背压等待时被中断
     */
    int push(String app, String stream, byte[] data) throws InterruptedException;

    /**
     * 停止指定 ZLM 流。
     *
     * @param app ZLM 应用名
     * @param stream ZLM 流名
     */
    void stop(String app, String stream);
}
