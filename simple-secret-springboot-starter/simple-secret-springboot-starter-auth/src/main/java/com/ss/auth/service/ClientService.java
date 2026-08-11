package com.ss.auth.service;

import com.ss.auth.domain.BaseClientDomain;

/**
 * 查询认证客户端配置的服务。
 */
@FunctionalInterface
public interface ClientService {

    /**
     * 按客户端标识查询客户端配置。
     *
     * @param clientId 客户端标识
     * @return 客户端配置；不存在时返回 {@code null}
     */
    BaseClientDomain queryByClientId(String clientId);
}
