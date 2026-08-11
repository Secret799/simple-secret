package com.ss.ics.dahua;

import com.sun.jna.Callback;
import com.sun.jna.Library;
import com.sun.jna.Pointer;

/** 本阶段使用的大华网络 SDK 原生函数。 */
interface DahuaNativeLibrary extends Library {

    interface DisconnectCallback extends Callback {
        void invoke(DahuaJnaStructures.DahuaLong loginId, String ip, int port, Pointer user);
    }

    interface ReconnectCallback extends Callback {
        void invoke(DahuaJnaStructures.DahuaLong loginId, String ip, int port, Pointer user);
    }

    interface RealDataCallback extends Callback {
        void invoke(DahuaJnaStructures.DahuaLong previewHandle, int dataType,
                    Pointer buffer, int bufferSize, int parameter, Pointer user);
    }

    interface ExtendedRealDataCallback extends Callback {
        void invoke(DahuaJnaStructures.DahuaLong previewHandle, int dataType,
                    Pointer buffer, int bufferSize,
                    DahuaJnaStructures.DahuaLong parameter, Pointer user);
    }

    interface DataCallback extends Callback {
        int invoke(DahuaJnaStructures.DahuaLong previewHandle,
                   DahuaJnaStructures.DataCallbackInfo info, Pointer user);
    }

    interface RadiometryCallback extends Callback {
        void invoke(DahuaJnaStructures.DahuaLong subscriptionHandle,
                    DahuaJnaStructures.ThermalData data, int bufferLength, Pointer user);
    }

    boolean CLIENT_Init(DisconnectCallback callback, Pointer user);

    void CLIENT_SetAutoReconnect(ReconnectCallback callback, Pointer user);

    void CLIENT_SetConnectTime(int waitTimeMillis, int tryTimes);

    void CLIENT_SetNetworkParam(DahuaJnaStructures.NetworkParam networkParam);

    DahuaJnaStructures.DahuaLong CLIENT_LoginWithHighLevelSecurity(
            DahuaJnaStructures.HighSecurityLoginInput input,
            DahuaJnaStructures.HighSecurityLoginOutput output);

    boolean CLIENT_Logout(DahuaJnaStructures.DahuaLong loginId);

    boolean CLIENT_DHPTZControlEx(
            DahuaJnaStructures.DahuaLong loginId, int channel, int command,
            int param1, int param2, int param3, int stop);

    DahuaJnaStructures.DahuaLong CLIENT_RealPlayByDataType(
            DahuaJnaStructures.DahuaLong loginId,
            DahuaJnaStructures.RealPlayInput input,
            DahuaJnaStructures.RealPlayOutput output,
            int timeoutMillis);

    boolean CLIENT_StopRealPlayEx(DahuaJnaStructures.DahuaLong previewHandle);

    DahuaJnaStructures.DahuaLong CLIENT_RadiometryAttach(
            DahuaJnaStructures.DahuaLong loginId,
            DahuaJnaStructures.RadiometryAttachInput input,
            DahuaJnaStructures.RadiometryAttachOutput output,
            int timeoutMillis);

    boolean CLIENT_RadiometryDetach(DahuaJnaStructures.DahuaLong subscriptionHandle);

    boolean CLIENT_RadiometryFetch(
            DahuaJnaStructures.DahuaLong loginId,
            DahuaJnaStructures.RadiometryFetchInput input,
            DahuaJnaStructures.RadiometryFetchOutput output,
            int timeoutMillis);

    boolean CLIENT_RadiometryDataParse(
            DahuaJnaStructures.ThermalData data,
            short[] grayscale,
            float[] temperatures);

    boolean CLIENT_QueryDevInfo(
            DahuaJnaStructures.DahuaLong loginId,
            int queryType,
            Pointer input,
            Pointer output,
            Pointer reserved,
            int timeoutMillis);

    boolean CLIENT_RadiometryGetRandomRegionTemper(
            DahuaJnaStructures.DahuaLong loginId,
            Pointer input,
            Pointer output,
            int timeoutMillis);

    boolean CLIENT_StartFind(
            DahuaJnaStructures.DahuaLong loginId,
            int type,
            Pointer input,
            Pointer output,
            int timeoutMillis);

    boolean CLIENT_DoFind(
            DahuaJnaStructures.DahuaLong loginId,
            int type,
            Pointer input,
            Pointer output,
            int timeoutMillis);

    boolean CLIENT_StopFind(
            DahuaJnaStructures.DahuaLong loginId,
            int type,
            Pointer input,
            Pointer output,
            int timeoutMillis);

    int CLIENT_GetLastError();

    void CLIENT_Cleanup();
}
