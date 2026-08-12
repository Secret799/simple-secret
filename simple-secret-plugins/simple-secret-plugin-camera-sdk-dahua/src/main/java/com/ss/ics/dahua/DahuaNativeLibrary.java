package com.ss.ics.dahua;

import com.sun.jna.Callback;
import com.sun.jna.Pointer;

/**
 * 大华 JNA 结构公开字段使用的回调类型。
 *
 * <p>该类型保留在根包用于兼容已有字段描述符。第三方应用应通过
 * {@link DahuaCameraSdkService} 使用驱动，不应直接加载厂商函数。</p>
 *
 * @author junpzx
 * @since 2026-08-12
 */
public interface DahuaNativeLibrary {

    /** 设备断线回调。 */
    interface DisconnectCallback extends Callback {
        /**
         * @param loginId 原生登录句柄
         * @param ip 设备 IP
         * @param port 设备端口
         * @param user 用户上下文
         */
        void invoke(DahuaJnaStructures.DahuaLong loginId, String ip, int port, Pointer user);
    }

    /** 设备重连回调。 */
    interface ReconnectCallback extends Callback {
        /**
         * @param loginId 原生登录句柄
         * @param ip 设备 IP
         * @param port 设备端口
         * @param user 用户上下文
         */
        void invoke(DahuaJnaStructures.DahuaLong loginId, String ip, int port, Pointer user);
    }

    /** 实时码流回调。 */
    interface RealDataCallback extends Callback {
        /**
         * @param previewHandle 原生预览句柄
         * @param dataType 数据类型
         * @param buffer 原生数据缓冲区
         * @param bufferSize 缓冲区长度
         * @param parameter 厂商参数
         * @param user 用户上下文
         */
        void invoke(DahuaJnaStructures.DahuaLong previewHandle, int dataType,
                    Pointer buffer, int bufferSize, int parameter, Pointer user);
    }

    /** 扩展实时码流回调。 */
    interface ExtendedRealDataCallback extends Callback {
        /**
         * @param previewHandle 原生预览句柄
         * @param dataType 数据类型
         * @param buffer 原生数据缓冲区
         * @param bufferSize 缓冲区长度
         * @param parameter 厂商参数
         * @param user 用户上下文
         */
        void invoke(DahuaJnaStructures.DahuaLong previewHandle, int dataType,
                    Pointer buffer, int bufferSize,
                    DahuaJnaStructures.DahuaLong parameter, Pointer user);
    }

    /** 实时数据结构回调。 */
    interface DataCallback extends Callback {
        /**
         * @param previewHandle 原生预览句柄
         * @param info 回调数据结构
         * @param user 用户上下文
         * @return 厂商回调状态
         */
        int invoke(DahuaJnaStructures.DahuaLong previewHandle,
                   DahuaJnaStructures.DataCallbackInfo info, Pointer user);
    }

    /** 热成像数据回调。 */
    interface RadiometryCallback extends Callback {
        /**
         * @param subscriptionHandle 原生订阅句柄
         * @param data 热成像数据结构
         * @param bufferLength 数据长度
         * @param user 用户上下文
         */
        void invoke(DahuaJnaStructures.DahuaLong subscriptionHandle,
                    DahuaJnaStructures.ThermalData data, int bufferLength, Pointer user);
    }
}
