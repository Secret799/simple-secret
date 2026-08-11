package com.ss.auth.support;

import cn.dev33.satoken.stp.StpInterface;
import com.ss.auth.domain.LoginUser;

import java.util.ArrayList;
import java.util.List;

/** 将当前登录用户的权限和角色映射给 Sa-Token 的权限接口。 */
public class LoginUserStpInterface implements StpInterface {
    private final LoginHelper loginHelper;

    /**
     * 创建基于指定登录辅助类的权限桥接器。
     *
     * @param loginHelper 登录辅助类
     */
    public LoginUserStpInterface(LoginHelper loginHelper) {
        this.loginHelper = loginHelper;
    }

    /**
     * 获取当前登录用户权限的独立列表。
     *
     * @param loginId Sa-Token 传入的登录标识
     * @param loginType Sa-Token 传入的登录类型
     * @return 当前登录用户的权限列表；未登录时为空列表
     */
    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return loginHelper.getLoginUser()
                .filter(loginUser -> loginHelper.matches(loginUser, loginId, loginType))
                .map(LoginUser::permissions)
                .<List<String>>map(ArrayList::new)
                .orElseGet(List::of);
    }

    /**
     * 获取当前登录用户角色的独立列表。
     *
     * @param loginId Sa-Token 传入的登录标识
     * @param loginType Sa-Token 传入的登录类型
     * @return 当前登录用户的角色列表；未登录时为空列表
     */
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        return loginHelper.getLoginUser()
                .filter(loginUser -> loginHelper.matches(loginUser, loginId, loginType))
                .map(LoginUser::roles)
                .<List<String>>map(ArrayList::new)
                .orElseGet(List::of);
    }
}
