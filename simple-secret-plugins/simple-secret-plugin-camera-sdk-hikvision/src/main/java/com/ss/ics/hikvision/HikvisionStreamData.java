package com.ss.ics.hikvision;

/**
 * 海康实时预览或历史回放返回的码流数据快照。
 *
 * @param handle 原生播放句柄
 * @param dataType 海康 SDK 数据类型
 * @param data 已脱离原生内存生命周期的字节数据
 * @author junpzx
 * @since 2026-08-12
 */
public record HikvisionStreamData(long handle, int dataType, byte[] data) {

    /**
     * 创建防御性复制的数据快照。
     *
     * @param handle 原生播放句柄
     * @param dataType 海康 SDK 数据类型
     * @param data 已脱离原生内存生命周期的字节数据
     */
    public HikvisionStreamData {
        if (data == null) {
            throw new IllegalArgumentException("data must not be null");
        }
        data = data.clone();
    }

    /**
     * 获取码流字节副本，避免调用方修改会话保存的数据。
     *
     * @return 码流字节副本
     */
    @Override
    public byte[] data() {
        return data.clone();
    }
}
