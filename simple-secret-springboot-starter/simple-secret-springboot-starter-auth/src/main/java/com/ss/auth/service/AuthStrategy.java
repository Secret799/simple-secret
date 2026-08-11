package com.ss.auth.service;

import com.ss.auth.domain.BaseClientDomain;
import com.ss.auth.domain.BaseLoginBody;
import com.ss.auth.domain.LoginUser;

/**
 * 某一种授权类型的认证策略。
 */
public interface AuthStrategy {

    /**
     * 获取该策略支持的精确授权类型。
     *
     * @return 授权类型
     */
    String grantType();

    /**
     * 对已经完成客户端校验的请求执行认证。
     *
     * @param body 登录请求
     * @param client 客户端配置
     * @return 登录用户
     */
    LoginUser login(BaseLoginBody body, BaseClientDomain client);
}
