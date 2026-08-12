package com.ss.ics.dahua.internal;

import com.ss.ics.dahua.DahuaPoint;
import com.ss.ics.dahua.internal.model.DahuaNativeLoginResult;
import com.ss.ics.dahua.internal.model.DahuaNativeRadiometryRecord;
import com.ss.ics.dahua.internal.model.DahuaNativeRegionTemperature;
import com.ss.ics.dahua.internal.model.DahuaNativeSearchStart;
import com.ss.ics.dahua.internal.model.DahuaNativeTemperatureSummary;
import com.ss.ics.domain.LoginDomain;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 驱动内部使用的最小大华原生 SDK 适配接口。
 *
 * @author junpzx
 * @since 2026-08-12
 */
public interface DahuaNativeApi {

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
    default DahuaNativeLoginResult login(LoginDomain login) {
        throw new UnsupportedOperationException("login is not implemented");
    }

    /**
     * 注销设备会话。
     *
     * @param userId 原生用户句柄
     * @return 注销是否成功
     */
    default boolean logout(long userId) {
        throw new UnsupportedOperationException("logout is not implemented");
    }

    /**
     * 执行原生 PTZ 控制。
     *
     * @param userId 原生用户句柄
     * @param channel 原生通道号
     * @param command 厂商命令值
     * @param param1 厂商参数一
     * @param param2 厂商参数二
     * @param param3 厂商参数三
     * @param stop 是否停止命令
     * @return 命令是否成功
     */
    default boolean ptzControl(
            long userId, int channel, int command,
            int param1, int param2, int param3, int stop) {
        throw new UnsupportedOperationException("PTZ control is not implemented");
    }

    /**
     * 启动实时预览。
     *
     * @param userId 原生用户句柄
     * @param channel 原生通道号
     * @param streamType 码流类型
     * @param callback 码流回调
     * @return 原生预览句柄，失败时由实现返回无效值
     */
    default long startPreview(
            long userId, int channel, int streamType, DahuaNativeStreamCallback callback) {
        throw new UnsupportedOperationException("real-time preview is not implemented");
    }

    /**
     * 停止实时预览。
     *
     * @param previewHandle 原生预览句柄
     * @return 停止是否成功
     */
    default boolean stopPreview(long previewHandle) {
        throw new UnsupportedOperationException("real-time preview is not implemented");
    }

    /**
     * 订阅热成像数据。
     *
     * @param userId 原生用户句柄
     * @param channel 原生通道号
     * @param callback 热成像回调
     * @return 原生订阅句柄，失败时由实现返回无效值
     */
    default long attachRadiometry(
            long userId, int channel, DahuaNativeThermalCallback callback) {
        throw new UnsupportedOperationException("radiometry is not implemented");
    }

    /**
     * 取消热成像订阅。
     *
     * @param subscriptionHandle 原生订阅句柄
     * @return 取消是否成功
     */
    default boolean detachRadiometry(long subscriptionHandle) {
        throw new UnsupportedOperationException("radiometry is not implemented");
    }

    /**
     * 触发获取热成像数据。
     *
     * @param userId 原生用户句柄
     * @param channel 原生通道号
     * @return 厂商获取状态
     */
    default int fetchRadiometry(long userId, int channel) {
        throw new UnsupportedOperationException("radiometry is not implemented");
    }

    /**
     * 查询点温度。
     *
     * @param userId 原生用户句柄
     * @param channel 原生通道号
     * @param x 横坐标
     * @param y 纵坐标
     * @return 温度统计快照
     */
    default DahuaNativeTemperatureSummary queryPointTemperature(
            long userId, int channel, int x, int y) {
        throw new UnsupportedOperationException("radiometry is not implemented");
    }

    /**
     * 查询预置规则温度。
     *
     * @param userId 原生用户句柄
     * @param channel 原生通道号
     * @param presetId 预置点编号
     * @param ruleId 规则编号
     * @param meterType 测温类型
     * @return 温度统计快照
     */
    default DahuaNativeTemperatureSummary queryItemTemperature(
            long userId, int channel, int presetId, int ruleId, int meterType) {
        throw new UnsupportedOperationException("radiometry is not implemented");
    }

    /**
     * 查询多边形区域温度。
     *
     * @param userId 原生用户句柄
     * @param channel 原生通道号
     * @param points 区域坐标点
     * @return 区域温度快照
     */
    default DahuaNativeRegionTemperature queryRegionTemperature(
            long userId, int channel, List<DahuaPoint> points) {
        throw new UnsupportedOperationException("radiometry is not implemented");
    }

    /**
     * 启动历史测温查询。
     *
     * @param userId 原生用户句柄
     * @param channel 原生通道号
     * @param meterType 测温类型
     * @param period 查询周期
     * @param begin 开始时间
     * @param end 结束时间
     * @return 查询句柄和结果总数
     */
    default DahuaNativeSearchStart startRadiometrySearch(
            long userId, int channel, int meterType, int period,
            LocalDateTime begin, LocalDateTime end) {
        throw new UnsupportedOperationException("radiometry search is not implemented");
    }

    /**
     * 获取一页历史测温结果。
     *
     * @param userId 原生用户句柄
     * @param finderHandle 原生查询句柄
     * @param offset 结果偏移量
     * @param count 本页数量
     * @return 历史测温记录
     */
    default List<DahuaNativeRadiometryRecord> findRadiometryPage(
            long userId, int finderHandle, int offset, int count) {
        throw new UnsupportedOperationException("radiometry search is not implemented");
    }

    /**
     * 关闭历史测温查询。
     *
     * @param userId 原生用户句柄
     * @param finderHandle 原生查询句柄
     * @return 关闭是否成功
     */
    default boolean stopRadiometrySearch(long userId, int finderHandle) {
        throw new UnsupportedOperationException("radiometry search is not implemented");
    }
}
