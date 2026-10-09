package com.ss.application.dahuatest.web;

import com.sun.jna.Callback;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 大华 NetSDK 经典预览路径裸探针：CLIENT_RealPlayEx + CLIENT_SetRealDataCallBackEx。
 *
 * <p>用于诊断 {@code CLIENT_RealPlayByDataType(dataType=4)} 结构化回调拿不到帧的场景：
 * 直接统计原始回调的 dwDataType 直方图、缓冲大小和首字节，确认设备实际下发的码流形态。
 * 复用插件已完成初始化的 SDK 进程状态，只追加预览，不调用 CLIENT_Cleanup。</p>
 *
 * @author junpzx
 * @since 2026-09-16
 */
final class RawDhProbe {

    private RawDhProbe() {
    }

    /** 仅绑定诊断所需的原生函数，避免完整 SDK 接口。 */
    interface RawDhSdk extends Library {

        /**
         * 经典实时预览。
         *
         * @param loginId 登录句柄
         * @param channel 通道号
         * @param window 播放窗口，无窗口传 null
         * @param realPlayType 预览类型，0 主码流
         * @return 预览句柄，0 表示失败
         */
        long CLIENT_RealPlayEx(long loginId, int channel, Pointer window, int realPlayType);

        /**
         * 注册原始码流回调。
         *
         * @param realHandle 预览句柄
         * @param callback 回调
         * @param user 用户数据
         * @param flag 回调数据类型掩码，31 表示全部
         * @return 是否成功
         */
        boolean CLIENT_SetRealDataCallBackEx(long realHandle, Callback callback, Pointer user, int flag);

        /**
         * 停止预览。
         *
         * @param realHandle 预览句柄
         * @return 是否成功
         */
        boolean CLIENT_StopRealPlayEx(long realHandle);

        /**
         * 读取最近一次 SDK 错误码。
         *
         * @return 错误码
         */
        int CLIENT_GetLastError();
    }

    /** 原始码流回调：按类型统计并记录首字节样本。 */
    interface RawDataCallback extends Callback {

        /**
         * SDK 原始数据回调入口。
         *
         * @param realHandle 预览句柄
         * @param dataType 数据类型
         * @param buffer 数据缓冲
         * @param bufferSize 数据长度
         * @param param 参数
         * @param user 用户数据
         */
        void invoke(long realHandle, int dataType, Pointer buffer, int bufferSize, int param, Pointer user);
    }

    /**
     * 采样指定登录句柄的原始码流。
     *
     * @param libraryDirectory NetSDK 目录
     * @param loginId 登录句柄
     * @param channel 通道号
     * @param durationMillis 采样时长
     * @return 统计结果
     * @throws InterruptedException 等待被中断
     */
    static Map<String, Object> probe(Path libraryDirectory, long loginId, int channel, long durationMillis)
            throws InterruptedException {
        RawDhSdk sdk = Native.load(
                libraryDirectory.resolve(
                        System.getProperty("os.name").toLowerCase().contains("win")
                                ? "dhnetsdk.dll" : "libdhnetsdk.so").toString(),
                RawDhSdk.class);
        Map<Integer, AtomicLong> typeCounts = new ConcurrentHashMap<>();
        Map<Integer, AtomicLong> typeBytes = new ConcurrentHashMap<>();
        Map<Integer, List<String>> typeSamples = new ConcurrentHashMap<>();
        AtomicLong invocations = new AtomicLong();
        RawDataCallback callback = (realHandle, dataType, buffer, bufferSize, param, user) -> {
            try {
                invocations.incrementAndGet();
                typeCounts.computeIfAbsent(dataType, k -> new AtomicLong()).incrementAndGet();
                typeBytes.computeIfAbsent(dataType, k -> new AtomicLong()).addAndGet(bufferSize);
                typeSamples.computeIfAbsent(dataType, k -> new java.util.concurrent.CopyOnWriteArrayList<>());
                List<String> samples = typeSamples.get(dataType);
                if (samples.size() < 5) {
                    byte[] head = buffer.getByteArray(0, Math.min(bufferSize, 160));
                    int startCode = -1;
                    for (int i = 0; i + 3 < head.length; i++) {
                        if (head[i] == 0 && head[i + 1] == 0 && head[i + 2] == 0 && head[i + 3] == 1) {
                            startCode = i;
                            break;
                        }
                    }
                    StringBuilder payloadHex = new StringBuilder();
                    int from = Math.max(startCode, 0);
                    for (int i = from; i < Math.min(head.length, from + 24); i++) {
                        payloadHex.append(String.format("%02x ", head[i]));
                    }
                    samples.add("len=" + bufferSize + " annexB@=" + startCode + " bytes=[" + payloadHex + "]");
                }
            } catch (Throwable ignored) {
                // 诊断回调同样不允许异常穿透 native 边界
            }
        };
        long handle = sdk.CLIENT_RealPlayEx(loginId, channel, null, 0);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("realPlayHandle", handle);
        if (handle == 0) {
            result.put("lastError", String.format("0x%08x", sdk.CLIENT_GetLastError()));
            result.put("message", "CLIENT_RealPlayEx 返回 0，预览建立失败");
            return result;
        }
        try {
            boolean registered = sdk.CLIENT_SetRealDataCallBackEx(handle, callback, null, 31);
            result.put("callbackRegistered", registered);
            Thread.sleep(durationMillis);
        } finally {
            sdk.CLIENT_StopRealPlayEx(handle);
        }
        Map<String, Object> types = new LinkedHashMap<>();
        typeCounts.keySet().stream().sorted().forEach(type -> {
            Map<String, Object> stat = new LinkedHashMap<>();
            stat.put("count", typeCounts.get(type).get());
            stat.put("bytes", typeBytes.get(type).get());
            stat.put("samples", typeSamples.get(type));
            types.put(String.valueOf(type), stat);
        });
        result.put("invocations", invocations.get());
        result.put("types", types);
        return result;
    }

}
