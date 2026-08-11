package com.ss.ics.service;

import com.ss.ics.domain.LoggedDomain;
import com.ss.ics.domain.LoginDomain;

/** 厂商 SDK 登录和登出能力。 */
public interface DeviceLoginService extends CameraSdkService {

    /**
     * @param login 登录参数
     * @return 登录会话摘要
     */
    LoggedDomain login(LoginDomain login);

    /**
     * @param userId 厂商登录句柄的字符串表示
     */
    void logout(String userId);
}
