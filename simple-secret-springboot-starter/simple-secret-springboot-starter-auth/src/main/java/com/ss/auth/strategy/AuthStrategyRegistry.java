package com.ss.auth.strategy;

import com.ss.auth.domain.BaseClientDomain;
import com.ss.auth.domain.BaseLoginBody;
import com.ss.auth.domain.ClientStatus;
import com.ss.auth.domain.LoginUser;
import com.ss.auth.exception.AuthException;
import com.ss.auth.service.AuthStrategy;
import com.ss.auth.service.ClientService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 按精确授权类型分派登录请求的策略注册表。
 */
public class AuthStrategyRegistry {
    private final ClientService clientService;
    private final Map<String, AuthStrategy> strategies;

    /**
     * 创建策略注册表。
     *
     * @param clientService 客户端查询服务
     * @param strategies 已注册的认证策略
     */
    public AuthStrategyRegistry(ClientService clientService, List<? extends AuthStrategy> strategies) {
        this.clientService = Objects.requireNonNull(clientService, "clientService");
        this.strategies = immutableStrategies(strategies);
    }

    /**
     * 对登录请求执行客户端校验并分派至精确匹配的认证策略。
     *
     * @param loginBody 登录请求
     * @return 已认证的登录用户
     */
    public LoginUser login(BaseLoginBody loginBody) {
        BaseLoginBody request = Objects.requireNonNull(loginBody, "loginBody");
        String grantType = exactText(request.getGrantType(), AuthException.Reason.INVALID_REQUEST);
        String clientId = exactText(request.getClientId(), AuthException.Reason.INVALID_REQUEST);
        AuthStrategy strategy = strategies.get(grantType);
        if (strategy == null) {
            throw new AuthException(AuthException.Reason.UNSUPPORTED_GRANT);
        }
        BaseClientDomain client = clientService.queryByClientId(clientId);
        if (client == null || client.getStatus() != ClientStatus.NORMAL) {
            throw new AuthException(AuthException.Reason.CLIENT_UNAVAILABLE);
        }
        List<String> allowed = Objects.requireNonNullElse(client.getGrantTypeList(), List.of());
        if (!allowed.contains(grantType)) {
            throw new AuthException(AuthException.Reason.UNSUPPORTED_GRANT);
        }
        return Objects.requireNonNull(strategy.login(request, client), "strategy login result");
    }

    private static Map<String, AuthStrategy> immutableStrategies(List<? extends AuthStrategy> strategyList) {
        Objects.requireNonNull(strategyList, "strategies");
        Map<String, AuthStrategy> registered = new LinkedHashMap<>();
        for (AuthStrategy strategy : strategyList) {
            AuthStrategy candidate = Objects.requireNonNull(strategy, "strategy");
            String grantType = candidate.grantType();
            if (!isExactNonBlankText(grantType)) {
                throw new IllegalArgumentException("授权策略类型无效");
            }
            if (registered.putIfAbsent(grantType, candidate) != null) {
                throw new IllegalStateException("存在重复的授权策略");
            }
        }
        return Map.copyOf(registered);
    }

    private static String exactText(String value, AuthException.Reason reason) {
        if (!isExactNonBlankText(value)) {
            throw new AuthException(reason);
        }
        return value;
    }

    private static boolean isExactNonBlankText(String value) {
        return value != null && !value.isBlank() && value.equals(value.trim());
    }
}
