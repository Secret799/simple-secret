package com.ss.ics.hikvision;

import com.ss.ics.domain.LoginDomain;

/** 驱动内部使用的最小原生 SDK 适配接口。 */
interface HikvisionNativeApi {

    boolean initialize();

    boolean cleanup();

    int lastError();

    default HikvisionNativeLoginResult login(LoginDomain login) {
        throw new UnsupportedOperationException("login is not implemented");
    }

    default boolean logout(int userId) {
        throw new UnsupportedOperationException("logout is not implemented");
    }

    default boolean ptzControl(int userId, int channel, int command, int stop, int speed) {
        throw new UnsupportedOperationException("PTZ control is not implemented");
    }

    default long findFiles(int userId, HikvisionFileSearchCondition condition) {
        throw new UnsupportedOperationException("file search is not implemented");
    }

    default HikvisionFileSearchResult findNextFile(long findHandle) {
        throw new UnsupportedOperationException("file search is not implemented");
    }

    default boolean closeFind(long findHandle) {
        throw new UnsupportedOperationException("file search is not implemented");
    }
}
