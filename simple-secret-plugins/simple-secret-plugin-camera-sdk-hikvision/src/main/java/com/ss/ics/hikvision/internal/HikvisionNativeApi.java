package com.ss.ics.hikvision.internal;

import com.ss.ics.hikvision.internal.model.HikvisionFileSearchCondition;
import com.ss.ics.hikvision.internal.model.HikvisionFileSearchResult;
import com.ss.ics.hikvision.internal.model.HikvisionNativeLoginResult;
import com.ss.ics.domain.LoginDomain;

/**
 * 驱动内部使用的最小海康原生 SDK 适配接口。
 *
 * @author junpzx
 * @since 2026-08-12
 */
public interface HikvisionNativeApi {

    /**
     * 初始化进程级 SDK。
     *
     * @return 初始化是否成功
     */
    boolean initialize();

    /**
     * 清理进程级 SDK。
     *
     * @return 清理是否成功
     */
    boolean cleanup();

    /**
     * 获取最近一次原生错误码。
     *
     * @return 原生错误码
     */
    int lastError();

    /**
     * 登录设备。
     *
     * @param login 登录参数
     * @return 原生登录结果
     */
    default HikvisionNativeLoginResult login(LoginDomain login) {
        throw new UnsupportedOperationException("login is not implemented");
    }

    /**
     * 注销设备会话。
     *
     * @param userId 原生用户编号
     * @return 注销是否成功
     */
    default boolean logout(int userId) {
        throw new UnsupportedOperationException("logout is not implemented");
    }

    /**
     * 执行原生 PTZ 控制。
     *
     * @param userId 原生用户编号
     * @param channel 原生通道号
     * @param command 厂商命令值
     * @param stop 是否停止命令
     * @param speed 厂商速度值
     * @return 命令是否成功
     */
    default boolean ptzControl(int userId, int channel, int command, int stop, int speed) {
        throw new UnsupportedOperationException("PTZ control is not implemented");
    }

    /**
     * 启动录像文件查询。
     *
     * @param userId 原生用户编号
     * @param condition 查询条件
     * @return 原生查询句柄，失败时由实现返回负值
     */
    default long findFiles(int userId, HikvisionFileSearchCondition condition) {
        throw new UnsupportedOperationException("file search is not implemented");
    }

    /**
     * 获取下一条录像文件结果。
     *
     * @param findHandle 原生查询句柄
     * @return 查询状态和录像时间段
     */
    default HikvisionFileSearchResult findNextFile(long findHandle) {
        throw new UnsupportedOperationException("file search is not implemented");
    }

    /**
     * 关闭录像文件查询。
     *
     * @param findHandle 原生查询句柄
     * @return 关闭是否成功
     */
    default boolean closeFind(long findHandle) {
        throw new UnsupportedOperationException("file search is not implemented");
    }
}
