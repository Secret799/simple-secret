package com.ss.ics.dahua.internal.jna;

import com.ss.ics.dahua.DahuaJnaStructures;
import com.ss.ics.dahua.DahuaNativeLibrary;

import com.sun.jna.Library;
import com.sun.jna.Pointer;

/**
 * 本阶段使用的大华网络 SDK 原生函数。
 *
 * <p>方法名称和参数顺序必须与厂商 C API 保持一致。</p>
 *
 * @author junpzx
 * @since 2026-08-12
 */
interface DahuaNetSdkLibrary extends DahuaNativeLibrary, Library {

    /**
     * @param callback 断线回调
     * @param user 用户上下文
     * @return 初始化是否成功
     */
    boolean CLIENT_Init(DisconnectCallback callback, Pointer user);

    /**
     * @param callback 重连回调
     * @param user 用户上下文
     */
    void CLIENT_SetAutoReconnect(ReconnectCallback callback, Pointer user);

    /**
     * @param waitTimeMillis 连接等待毫秒数
     * @param tryTimes 尝试次数
     */
    void CLIENT_SetConnectTime(int waitTimeMillis, int tryTimes);

    /**
     * @param networkParam 网络参数
     */
    void CLIENT_SetNetworkParam(DahuaJnaStructures.NetworkParam networkParam);

    /**
     * @param input 登录输入
     * @param output 登录输出
     * @return 原生登录句柄
     */
    DahuaJnaStructures.DahuaLong CLIENT_LoginWithHighLevelSecurity(
            DahuaJnaStructures.HighSecurityLoginInput input,
            DahuaJnaStructures.HighSecurityLoginOutput output);

    /**
     * @param loginId 原生登录句柄
     * @return 注销是否成功
     */
    boolean CLIENT_Logout(DahuaJnaStructures.DahuaLong loginId);

    /**
     * @param loginId 原生登录句柄
     * @param channel 原生通道号
     * @param command PTZ 命令
     * @param param1 厂商参数一
     * @param param2 厂商参数二
     * @param param3 厂商参数三
     * @param stop 是否停止命令
     * @return 控制是否成功
     */
    boolean CLIENT_DHPTZControlEx(
            DahuaJnaStructures.DahuaLong loginId, int channel, int command,
            int param1, int param2, int param3, int stop);

    /**
     * @param loginId 原生登录句柄
     * @param input 预览输入
     * @param output 预览输出
     * @param timeoutMillis 超时毫秒数
     * @return 原生预览句柄
     */
    DahuaJnaStructures.DahuaLong CLIENT_RealPlayByDataType(
            DahuaJnaStructures.DahuaLong loginId,
            DahuaJnaStructures.RealPlayInput input,
            DahuaJnaStructures.RealPlayOutput output,
            int timeoutMillis);

    /**
     * @param previewHandle 原生预览句柄
     * @return 停止是否成功
     */
    boolean CLIENT_StopRealPlayEx(DahuaJnaStructures.DahuaLong previewHandle);

    /**
     * @param loginId 原生登录句柄
     * @param input 订阅输入
     * @param output 订阅输出
     * @param timeoutMillis 超时毫秒数
     * @return 原生订阅句柄
     */
    DahuaJnaStructures.DahuaLong CLIENT_RadiometryAttach(
            DahuaJnaStructures.DahuaLong loginId,
            DahuaJnaStructures.RadiometryAttachInput input,
            DahuaJnaStructures.RadiometryAttachOutput output,
            int timeoutMillis);

    /**
     * @param subscriptionHandle 原生订阅句柄
     * @return 取消订阅是否成功
     */
    boolean CLIENT_RadiometryDetach(DahuaJnaStructures.DahuaLong subscriptionHandle);

    /**
     * @param loginId 原生登录句柄
     * @param input 获取输入
     * @param output 获取输出
     * @param timeoutMillis 超时毫秒数
     * @return 获取是否成功
     */
    boolean CLIENT_RadiometryFetch(
            DahuaJnaStructures.DahuaLong loginId,
            DahuaJnaStructures.RadiometryFetchInput input,
            DahuaJnaStructures.RadiometryFetchOutput output,
            int timeoutMillis);

    /**
     * @param data 原生热图数据
     * @param grayscale 灰度输出缓冲区
     * @param temperatures 温度输出缓冲区
     * @return 解析是否成功
     */
    boolean CLIENT_RadiometryDataParse(
            DahuaJnaStructures.ThermalData data,
            short[] grayscale,
            float[] temperatures);

    /**
     * @param loginId 原生登录句柄
     * @param queryType 查询类型
     * @param input 查询输入
     * @param output 查询输出
     * @param reserved 保留参数
     * @param timeoutMillis 超时毫秒数
     * @return 查询是否成功
     */
    boolean CLIENT_QueryDevInfo(
            DahuaJnaStructures.DahuaLong loginId,
            int queryType,
            Pointer input,
            Pointer output,
            Pointer reserved,
            int timeoutMillis);

    /**
     * @param loginId 原生登录句柄
     * @param input 区域输入
     * @param output 区域输出
     * @param timeoutMillis 超时毫秒数
     * @return 查询是否成功
     */
    boolean CLIENT_RadiometryGetRandomRegionTemper(
            DahuaJnaStructures.DahuaLong loginId,
            Pointer input,
            Pointer output,
            int timeoutMillis);

    /**
     * @param loginId 原生登录句柄
     * @param type 查询类型
     * @param input 查询输入
     * @param output 查询输出
     * @param timeoutMillis 超时毫秒数
     * @return 启动是否成功
     */
    boolean CLIENT_StartFind(
            DahuaJnaStructures.DahuaLong loginId,
            int type,
            Pointer input,
            Pointer output,
            int timeoutMillis);

    /**
     * @param loginId 原生登录句柄
     * @param type 查询类型
     * @param input 分页输入
     * @param output 分页输出
     * @param timeoutMillis 超时毫秒数
     * @return 查询是否成功
     */
    boolean CLIENT_DoFind(
            DahuaJnaStructures.DahuaLong loginId,
            int type,
            Pointer input,
            Pointer output,
            int timeoutMillis);

    /**
     * @param loginId 原生登录句柄
     * @param type 查询类型
     * @param input 关闭输入
     * @param output 关闭输出
     * @param timeoutMillis 超时毫秒数
     * @return 关闭是否成功
     */
    boolean CLIENT_StopFind(
            DahuaJnaStructures.DahuaLong loginId,
            int type,
            Pointer input,
            Pointer output,
            int timeoutMillis);

    /**
     * @return 最近一次原生错误码
     */
    int CLIENT_GetLastError();

    /** 清理进程级大华 SDK。 */
    void CLIENT_Cleanup();
}
