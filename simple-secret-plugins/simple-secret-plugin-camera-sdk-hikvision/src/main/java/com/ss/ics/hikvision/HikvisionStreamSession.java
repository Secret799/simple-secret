package com.ss.ics.hikvision;

import java.util.Objects;

/**
 * 海康实时预览或历史回放会话，关闭时停止取流并释放临时登录。
 *
 * @author junpzx
 * @since 2026-08-12
 */
public final class HikvisionStreamSession implements AutoCloseable {

    /** 海康取流会话类型。 */
    public enum Type {
        /** 实时预览。 */
        REAL_PLAY,
        /** 历史录像回放。 */
        PLAYBACK
    }

    /** 厂商原生播放句柄。 */
    private final long handle;

    /** 取流会话类型。 */
    private final Type type;

    /** 关闭底层资源的动作。 */
    private final Runnable closeAction;

    /** 会话是否已经成功关闭。 */
    private boolean closed;

    /**
     * 创建海康取流会话。
     *
     * @param handle 厂商原生播放句柄
     * @param type 取流会话类型
     * @param closeAction 关闭底层资源的动作
     */
    HikvisionStreamSession(long handle, Type type, Runnable closeAction) {
        this.handle = handle;
        this.type = Objects.requireNonNull(type, "type");
        this.closeAction = Objects.requireNonNull(closeAction, "closeAction");
    }

    /** @return 厂商原生播放句柄 */
    public long handle() {
        return handle;
    }

    /** @return 取流会话类型 */
    public Type type() {
        return type;
    }

    /** @return 会话是否已经成功关闭 */
    public synchronized boolean closed() {
        return closed;
    }

    /** 停止取流并释放临时登录；关闭失败时允许调用方重试。 */
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closeAction.run();
        closed = true;
    }
}
