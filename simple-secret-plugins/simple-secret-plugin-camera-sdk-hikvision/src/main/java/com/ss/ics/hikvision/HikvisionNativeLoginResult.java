package com.ss.ics.hikvision;

/** 原生登录结果的驱动内部快照。 */
record HikvisionNativeLoginResult(
        int userId,
        int startChannel,
        int deviceType,
        int deviceCategory,
        String serialNumber) {
}
