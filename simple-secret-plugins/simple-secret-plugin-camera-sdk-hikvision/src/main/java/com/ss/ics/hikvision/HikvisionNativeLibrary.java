package com.ss.ics.hikvision;

import com.sun.jna.Library;
import com.sun.jna.Pointer;

/** 本阶段使用的海康网络 SDK 原生函数。 */
interface HikvisionNativeLibrary extends Library {

    boolean NET_DVR_SetSDKInitCfg(int type, Pointer buffer);

    boolean NET_DVR_Init();

    boolean NET_DVR_Cleanup();

    int NET_DVR_GetLastError();

    int NET_DVR_Login_V40(
            HikvisionJnaStructures.UserLoginInfo login,
            HikvisionJnaStructures.DeviceInfoV40 deviceInfo);

    boolean NET_DVR_Logout(int userId);

    boolean NET_DVR_PTZControlWithSpeed_Other(
            int userId, int channel, int command, int stop, int speed);

    int NET_DVR_FindFile_V50(
            int userId, HikvisionJnaStructures.FileSearchCondition condition);

    int NET_DVR_FindNextFile_V50(
            int findHandle, HikvisionJnaStructures.FileSearchData data);

    boolean NET_DVR_FindClose_V30(int findHandle);
}
