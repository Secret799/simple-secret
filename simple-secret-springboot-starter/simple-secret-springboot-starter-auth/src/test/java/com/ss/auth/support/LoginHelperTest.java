package com.ss.auth.support;

import cn.dev33.satoken.exception.SaTokenException;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import com.ss.auth.domain.LoginUser;
import com.ss.auth.exception.AuthException;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 验证登录助手保存、读取和清除当前登录用户的行为。 */
class LoginHelperTest {

    @Test
    void shouldReturnEmptyWhenCurrentRequestHasNoToken() {
        LoginHelper helper = new LoginHelper(new InMemoryStpLogic());

        assertThat(helper.getLoginUser()).isEmpty();
        assertThat(helper.isLogin()).isFalse();
        assertThatThrownBy(helper::requireLoginUser)
                .isInstanceOf(AuthException.class)
                .extracting("reason")
                .isEqualTo(AuthException.Reason.UNAUTHENTICATED);
    }

    @Test
    void shouldStoreCurrentLoginUserAndClearItOnLogout() {
        InMemoryStpLogic stpLogic = new InMemoryStpLogic();
        LoginHelper helper = new LoginHelper(stpLogic);
        LoginUser user = loginUser();

        helper.login(user);

        assertThat(helper.isLogin()).isTrue();
        assertThat(helper.getLoginUser()).contains(user);
        assertThat(helper.requireLoginUser()).isSameAs(user);

        helper.logout();

        assertThat(helper.isLogin()).isFalse();
        assertThat(helper.getLoginUser()).isEmpty();
        assertThatThrownBy(helper::requireLoginUser)
                .isInstanceOf(AuthException.class)
                .extracting("reason")
                .isEqualTo(AuthException.Reason.UNAUTHENTICATED);
    }

    @Test
    void shouldUseDefaultLoginParameterCreatedByBoundStpLogic() {
        InMemoryStpLogic stpLogic = new InMemoryStpLogic();
        LoginHelper helper = new LoginHelper(stpLogic);

        helper.login(loginUser());

        assertThat(stpLogic.lastLoginParameter()).isSameAs(stpLogic.defaultLoginParameter());
    }

    @Test
    void shouldReturnEmptyForBlankTokenAndUnexpectedTokenSessionValue() {
        InMemoryStpLogic stpLogic = new InMemoryStpLogic();
        LoginHelper helper = new LoginHelper(stpLogic);

        stpLogic.putTokenLoginId("token-a", "7");
        stpLogic.putTokenSession("token-a", new SaSession("token-a")
                .set(LoginHelper.class.getName() + ".loginUser", "unexpected"));

        assertThat(helper.getLoginUser(" ")).isEmpty();
        assertThat(helper.getLoginUser("token-a")).isEmpty();
        assertThat(helper.getLoginUser("unknown-token")).isEmpty();
        assertThat(stpLogic.lastTokenSessionCreate()).isFalse();
    }

    @Test
    void shouldRejectInvalidTokenWithoutReadingRetainedTokenSession() {
        InMemoryStpLogic stpLogic = new InMemoryStpLogic();
        LoginHelper helper = new LoginHelper(stpLogic);
        stpLogic.putTokenSession("revoked-token", new SaSession("revoked-token")
                .set(LoginHelper.class.getName() + ".loginUser", loginUser()));

        assertThat(helper.getLoginUser("revoked-token")).isEmpty();
        assertThat(stpLogic.tokenSessionLookupCount()).isZero();
    }

    @Test
    void shouldKeepConsumerLoginModelAndExposeAuthorizationAsIndependentLists() {
        InMemoryStpLogic stpLogic = new InMemoryStpLogic();
        LoginHelper helper = new LoginHelper(stpLogic);
        LoginUser user = loginUser();
        SaLoginParameter model = new SaLoginParameter();
        LoginUserStpInterface permissionBridge = new LoginUserStpInterface(helper);

        assertThat(permissionBridge.getPermissionList(user.loginId(), "test")).isEmpty();
        assertThat(permissionBridge.getRoleList(user.loginId(), "test")).isEmpty();

        helper.login(user, model);
        var permissions = permissionBridge.getPermissionList(user.loginId(), "test");
        var roles = permissionBridge.getRoleList(user.loginId(), "test");

        assertThat(stpLogic.lastLoginParameter()).isSameAs(model);
        assertThat(permissions).containsExactlyInAnyOrder("orders:read");
        assertThat(roles).containsExactlyInAnyOrder("operator");
        permissions.add("orders:write");
        roles.add("auditor");
        assertThat(user.permissions()).containsExactlyInAnyOrder("orders:read");
        assertThat(user.roles()).containsExactlyInAnyOrder("operator");
    }

    @Test
    void shouldExposeAuthorizationOnlyForMatchingLoginIdAndLoginType() {
        InMemoryStpLogic stpLogic = new InMemoryStpLogic();
        LoginHelper helper = new LoginHelper(stpLogic);
        LoginUser user = loginUser();
        LoginUserStpInterface permissionBridge = new LoginUserStpInterface(helper);
        helper.login(user);

        assertThat(permissionBridge.getPermissionList("7", "test"))
                .containsExactly("orders:read");
        assertThat(permissionBridge.getRoleList("7", "test"))
                .containsExactly("operator");
        assertThat(permissionBridge.getPermissionList("8", "test")).isEmpty();
        assertThat(permissionBridge.getRoleList("8", "test")).isEmpty();
        assertThat(permissionBridge.getPermissionList("7", "other")).isEmpty();
        assertThat(permissionBridge.getRoleList("7", "other")).isEmpty();
    }

    private static LoginUser loginUser() {
        return new LoginUser(7L, "alice", Set.of("orders:read"), Set.of("operator"), Map.of());
    }

    /** 使用真实会话对象模拟当前 token 会话状态的 StpLogic。 */
    private static final class InMemoryStpLogic extends StpLogic {
        private final Map<String, Object> tokenLoginIds = new HashMap<>();
        private final Map<String, SaSession> tokenSessions = new HashMap<>();
        private final SaLoginParameter defaultLoginParameter = new SaLoginParameter();
        private SaSession currentTokenSession;
        private SaLoginParameter lastLoginParameter;
        private Boolean lastTokenSessionCreate;
        private int tokenSessionLookupCount;
        private boolean loggedIn;

        private InMemoryStpLogic() {
            super("test");
        }

        @Override
        public void login(Object loginId, SaLoginParameter parameter) {
            currentTokenSession = new SaSession("current-token");
            lastLoginParameter = parameter;
            loggedIn = true;
        }

        @Override
        public SaLoginParameter createSaLoginParameter() {
            return defaultLoginParameter;
        }

        @Override
        public void logout() {
            currentTokenSession = null;
            loggedIn = false;
        }

        @Override
        public boolean isLogin() {
            return loggedIn;
        }

        @Override
        public SaSession getTokenSession() {
            if (currentTokenSession == null) {
                throw new SaTokenException("Token-Session 获取失败：token 为空");
            }
            return currentTokenSession;
        }

        @Override
        public SaSession getTokenSessionByToken(String token) {
            throw new AssertionError("不应创建或校验未知 token 的会话");
        }

        @Override
        public SaSession getTokenSessionByToken(String token, boolean isCreate) {
            lastTokenSessionCreate = isCreate;
            tokenSessionLookupCount++;
            return tokenSessions.get(token);
        }

        @Override
        public Object getLoginIdByToken(String token) {
            return tokenLoginIds.get(token);
        }

        private void putTokenLoginId(String token, Object loginId) {
            tokenLoginIds.put(token, loginId);
        }

        private void putTokenSession(String token, SaSession session) {
            tokenSessions.put(token, session);
        }

        private SaLoginParameter lastLoginParameter() {
            return lastLoginParameter;
        }

        private SaLoginParameter defaultLoginParameter() {
            return defaultLoginParameter;
        }

        private Boolean lastTokenSessionCreate() {
            return lastTokenSessionCreate;
        }

        private int tokenSessionLookupCount() {
            return tokenSessionLookupCount;
        }
    }
}
