package com.ss.auth.strategy;

import com.ss.auth.domain.BaseClientDomain;
import com.ss.auth.domain.BaseLoginBody;
import com.ss.auth.domain.ClientStatus;
import com.ss.auth.domain.LoginUser;
import com.ss.auth.exception.AuthException;
import com.ss.auth.service.AuthStrategy;
import com.ss.auth.service.ClientService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthStrategyRegistryTest {

    @Test
    void shouldDispatchPasswordGrantToMatchingStrategy() {
        LoginUser expected = loginUser();
        AuthStrategyRegistry registry = registry(client("client-a", ClientStatus.NORMAL, List.of("password")),
                new PasswordStrategy(expected));

        assertThat(registry.login(request("password", "client-a"))).isSameAs(expected);
    }

    @Test
    void shouldNotMatchGrantTypeWithDifferentCase() {
        AuthStrategyRegistry registry = registry(client("client-a", ClientStatus.NORMAL, List.of("password")),
                new PasswordStrategy(loginUser()));

        assertFailure(registry, request("PASSWORD", "client-a"), AuthException.Reason.UNSUPPORTED_GRANT);
    }

    @Test
    void shouldRejectGrantTypeWithLeadingWhitespaceInsteadOfNormalizingIt() {
        AuthStrategyRegistry registry = registry(client("client-a", ClientStatus.NORMAL, List.of("password")),
                new PasswordStrategy(loginUser()));

        assertFailure(registry, request(" password", "client-a"), AuthException.Reason.INVALID_REQUEST);
    }

    @Test
    void shouldRejectDuplicateStrategyGrantTypesAtConstruction() {
        assertThatThrownBy(() -> registry(client("client-a", ClientStatus.NORMAL, List.of("password")),
                new PasswordStrategy(loginUser()), new PasswordStrategy(loginUser())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("存在重复的授权策略");
    }

    @Test
    void shouldRejectBlankStrategyGrantTypeAtConstruction() {
        assertThatThrownBy(() -> registry(client("client-a", ClientStatus.NORMAL, List.of("password")),
                new BlankGrantStrategy()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectNullRequest() {
        AuthStrategyRegistry registry = registry(client("client-a", ClientStatus.NORMAL, List.of("password")),
                new PasswordStrategy(loginUser()));

        assertThatThrownBy(() -> registry.login(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("loginBody");
    }

    @Test
    void shouldRejectBlankGrantType() {
        AuthStrategyRegistry registry = registry(client("client-a", ClientStatus.NORMAL, List.of("password")),
                new PasswordStrategy(loginUser()));

        assertFailure(registry, request("", "client-a"), AuthException.Reason.INVALID_REQUEST);
    }

    @Test
    void shouldRejectBlankClientId() {
        AuthStrategyRegistry registry = registry(client("client-a", ClientStatus.NORMAL, List.of("password")),
                new PasswordStrategy(loginUser()));

        assertFailure(registry, request("password", ""), AuthException.Reason.INVALID_REQUEST);
    }

    @Test
    void shouldRejectUnknownClient() {
        AuthStrategyRegistry registry = registry(null, new PasswordStrategy(loginUser()));

        assertFailure(registry, request("password", "missing"), AuthException.Reason.CLIENT_UNAVAILABLE);
    }

    @Test
    void shouldRejectDisabledClient() {
        AuthStrategyRegistry registry = registry(client("client-a", ClientStatus.DISABLED, List.of("password")),
                new PasswordStrategy(loginUser()));

        assertFailure(registry, request("password", "client-a"), AuthException.Reason.CLIENT_UNAVAILABLE);
    }

    @Test
    void shouldRejectGrantNotAllowedForClient() {
        AuthStrategyRegistry registry = registry(client("client-a", ClientStatus.NORMAL, List.of("sms")),
                new PasswordStrategy(loginUser()));

        assertFailure(registry, request("password", "client-a"), AuthException.Reason.UNSUPPORTED_GRANT);
    }

    private static AuthStrategyRegistry registry(BaseClientDomain client, AuthStrategy... strategies) {
        ClientService clientService = clientId -> client;
        return new AuthStrategyRegistry(clientService, List.of(strategies));
    }

    private static BaseLoginBody request(String grantType, String clientId) {
        BaseLoginBody body = new BaseLoginBody();
        body.setGrantType(grantType);
        body.setClientId(clientId);
        return body;
    }

    private static BaseClientDomain client(String clientId, ClientStatus status, List<String> grantTypes) {
        BaseClientDomain client = new BaseClientDomain();
        client.setClientId(clientId);
        client.setStatus(status);
        client.setGrantTypeList(grantTypes);
        return client;
    }

    private static LoginUser loginUser() {
        return new LoginUser(1L, "alice", Set.of("orders:read"), Set.of("operator"), Map.of());
    }

    private static void assertFailure(AuthStrategyRegistry registry, BaseLoginBody request,
                                      AuthException.Reason reason) {
        assertThatThrownBy(() -> registry.login(request))
                .isInstanceOf(AuthException.class)
                .extracting(throwable -> ((AuthException) throwable).getReason())
                .isEqualTo(reason);
    }

    private static final class PasswordStrategy implements AuthStrategy {
        private final LoginUser result;

        private PasswordStrategy(LoginUser result) {
            this.result = result;
        }

        @Override
        public String grantType() {
            return "password";
        }

        @Override
        public LoginUser login(BaseLoginBody body, BaseClientDomain client) {
            return result;
        }
    }

    private static final class BlankGrantStrategy implements AuthStrategy {
        @Override
        public String grantType() {
            return " ";
        }

        @Override
        public LoginUser login(BaseLoginBody body, BaseClientDomain client) {
            return loginUser();
        }
    }
}
