package com.ss.auth.support;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import com.ss.auth.domain.LoginUser;
import com.ss.auth.exception.AuthException;

import java.util.Objects;
import java.util.Optional;

/** 基于 Sa-Token 会话保存和读取登录用户的辅助类。 */
public class LoginHelper {
    private static final String LOGIN_USER_KEY = LoginHelper.class.getName() + ".loginUser";

    private final StpLogic stpLogic;

    /**
     * 创建绑定指定 Sa-Token 逻辑实例的登录辅助类。
     *
     * @param stpLogic Sa-Token 登录逻辑
     */
    public LoginHelper(StpLogic stpLogic) {
        this.stpLogic = Objects.requireNonNull(stpLogic, "stpLogic");
    }

    /**
     * 使用默认登录参数登录并保存登录用户。
     *
     * @param loginUser 登录用户
     */
    public void login(LoginUser loginUser) {
        login(loginUser, null);
    }

    /**
     * 使用调用方提供的登录参数登录并保存登录用户。
     *
     * @param loginUser 登录用户
     * @param parameter Sa-Token 登录参数；为 {@code null} 时使用默认参数
     */
    public void login(LoginUser loginUser, SaLoginParameter parameter) {
        LoginUser required = Objects.requireNonNull(loginUser, "loginUser");
        SaLoginParameter loginParameter = parameter == null ? stpLogic.createSaLoginParameter() : parameter;
        stpLogic.login(required.loginId(), loginParameter);
        stpLogic.getTokenSession().set(LOGIN_USER_KEY, required);
    }

    /** 清除当前登录状态。 */
    public void logout() {
        stpLogic.logout();
    }

    /**
     * 判断当前 token 会话是否保存了登录用户。
     *
     * @return {@code true} 表示当前存在登录用户
     */
    public boolean isLogin() {
        return getLoginUser().isPresent();
    }

    /**
     * 获取当前 token 会话中的登录用户。
     *
     * @return 当前登录用户；不存在或类型不正确时为空
     */
    public Optional<LoginUser> getLoginUser() {
        if (!stpLogic.isLogin()) {
            return Optional.empty();
        }
        return getLoginUser(stpLogic.getTokenSession());
    }

    /**
     * 按 token 获取对应 token 会话中的登录用户。
     *
     * @param token token 值
     * @return 登录用户；token 为空白、会话不存在或类型不正确时为空
     */
    public Optional<LoginUser> getLoginUser(String token) {
        if (token == null || token.isBlank() || stpLogic.getLoginIdByToken(token) == null) {
            return Optional.empty();
        }
        return getLoginUser(stpLogic.getTokenSessionByToken(token, false));
    }

    boolean matches(LoginUser loginUser, Object loginId, String loginType) {
        return Objects.equals(stpLogic.getLoginType(), loginType)
                && Objects.equals(String.valueOf(loginUser.loginId()), String.valueOf(loginId));
    }

    /**
     * 获取当前登录用户，不存在时抛出固定认证失败异常。
     *
     * @return 当前登录用户
     * @throws AuthException 当前没有可用登录用户时抛出
     */
    public LoginUser requireLoginUser() {
        return getLoginUser().orElseThrow(() -> new AuthException(AuthException.Reason.UNAUTHENTICATED));
    }

    private static Optional<LoginUser> getLoginUser(SaSession session) {
        if (session == null) {
            return Optional.empty();
        }
        Object value = session.get(LOGIN_USER_KEY);
        return value instanceof LoginUser loginUser ? Optional.of(loginUser) : Optional.empty();
    }
}
