package com.ss.auth.domain;

import java.io.Serial;
import java.io.Serializable;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 已完成认证的登录用户信息。
 *
 * @param loginId 登录主体标识
 * @param username 用户名
 * @param permissions 权限集合
 * @param roles 角色集合
 * @param attributes 附加属性
 */
public record LoginUser(Serializable loginId, String username, Set<String> permissions, Set<String> roles,
                        Map<String, Serializable> attributes) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 创建不可变的登录用户信息。
     *
     * @param loginId 登录主体标识
     * @param username 用户名
     * @param permissions 权限集合
     * @param roles 角色集合
     * @param attributes 附加属性
     */
    public LoginUser {
        Objects.requireNonNull(loginId, "loginId");
        permissions = immutableTextSet(permissions, "permissions");
        roles = immutableTextSet(roles, "roles");
        attributes = immutableAttributes(attributes);
    }

    /**
     * 获取登录主体标识。
     *
     * @return 登录主体标识
     */
    @Override
    public Serializable loginId() {
        return loginId;
    }

    /**
     * 获取用户名。
     *
     * @return 用户名
     */
    @Override
    public String username() {
        return username;
    }

    /**
     * 获取不可变权限集合。
     *
     * @return 权限集合
     */
    @Override
    public Set<String> permissions() {
        return permissions;
    }

    /**
     * 获取不可变角色集合。
     *
     * @return 角色集合
     */
    @Override
    public Set<String> roles() {
        return roles;
    }

    /**
     * 获取不可变附加属性。
     *
     * @return 附加属性
     */
    @Override
    public Map<String, Serializable> attributes() {
        return attributes;
    }

    private static Set<String> immutableTextSet(Set<String> values, String name) {
        return Set.copyOf(new LinkedHashSet<>(Objects.requireNonNull(values, name)));
    }

    private static Map<String, Serializable> immutableAttributes(Map<String, Serializable> values) {
        return Map.copyOf(Objects.requireNonNull(values, "attributes"));
    }
}
