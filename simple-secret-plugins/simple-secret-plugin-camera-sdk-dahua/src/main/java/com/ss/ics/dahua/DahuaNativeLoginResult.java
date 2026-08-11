package com.ss.ics.dahua;

/** 原生登录结果的驱动内部快照。 */
record DahuaNativeLoginResult(
        long userId,
        int startChannel,
        int deviceType,
        int deviceCategory,
        String serialNumber) {
}
