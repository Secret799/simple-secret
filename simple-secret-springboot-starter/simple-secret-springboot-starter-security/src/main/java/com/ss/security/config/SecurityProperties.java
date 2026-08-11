package com.ss.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Simple Secret Servlet 路由登录保护配置。 */
@ConfigurationProperties("simple-secret.security")
public class SecurityProperties {
    private boolean enabled;
    private List<String> pathPatterns = List.of("/**");
    private List<String> excludePathPatterns = List.of();
    private int order;

    /**
     * 判断是否启用路由登录保护。
     *
     * @return {@code true} 表示启用
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置是否启用路由登录保护。
     *
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 获取需要登录保护的路径模式。
     *
     * @return 不可变路径模式列表
     */
    public List<String> getPathPatterns() {
        return pathPatterns;
    }

    /**
     * 设置需要登录保护的路径模式。
     *
     * @param pathPatterns 路径模式列表
     */
    public void setPathPatterns(List<String> pathPatterns) {
        this.pathPatterns = validatedPatterns(pathPatterns);
    }

    /**
     * 获取无需登录保护的排除路径模式。
     *
     * @return 不可变排除路径模式列表
     */
    public List<String> getExcludePathPatterns() {
        return excludePathPatterns;
    }

    /**
     * 设置无需登录保护的排除路径模式。
     *
     * @param excludePathPatterns 排除路径模式列表
     */
    public void setExcludePathPatterns(List<String> excludePathPatterns) {
        this.excludePathPatterns = validatedPatterns(excludePathPatterns);
    }

    /**
     * 获取登录拦截器顺序。
     *
     * @return 拦截器顺序
     */
    public int getOrder() {
        return order;
    }

    /**
     * 设置登录拦截器顺序。
     *
     * @param order 拦截器顺序
     */
    public void setOrder(int order) {
        this.order = order;
    }

    private static List<String> validatedPatterns(List<String> patterns) {
        Objects.requireNonNull(patterns, "patterns");
        List<String> copy = new ArrayList<>(patterns.size());
        for (String pattern : patterns) {
            if (pattern == null || pattern.isBlank() || !pattern.equals(pattern.trim())) {
                throw new IllegalArgumentException("Invalid security path pattern.");
            }
            copy.add(pattern);
        }
        return List.copyOf(copy);
    }
}
