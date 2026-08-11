package com.ss.sensitive.core;

/** 决定指定字段在当前调用上下文中是否需要脱敏。 */
@FunctionalInterface
public interface SensitiveService {

    /**
     * 判断是否对字段值执行脱敏。
     *
     * @param roleKey 注解声明的角色提示
     * @param perms 注解声明的权限提示
     * @return 需要脱敏时为 {@code true}
     */
    boolean isSensitive(String roleKey, String perms);

    /**
     * 返回默认失败关闭策略。
     *
     * @return 始终要求脱敏的服务
     */
    static SensitiveService alwaysMask() {
        return (roleKey, perms) -> true;
    }
}
