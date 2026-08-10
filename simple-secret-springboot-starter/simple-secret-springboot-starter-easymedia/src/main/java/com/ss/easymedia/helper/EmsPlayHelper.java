package com.ss.easymedia.helper;

import com.ss.easymedia.config.properties.EmsProperties;
import com.ss.zlm4j.support.SpringUtils;

/**
 * ems 播放帮助类
 *
 * @author JunPzx
 * @since 2025/9/17 17:48
 */
public class EmsPlayHelper {

    /**
     * 获取默认播放地址
     *
     * @param args 参数
     * @return 播放地址
     */
    public static String getDefaultPlayUrl(Object... args) {
        return format(SpringUtils.getBean(EmsProperties.class).getDefaultPlayUrlTemplate(), args);
    }

    /**
     * 获取默认发布地址
     *
     * @param args 参数
     * @return 发布地址
     */
    public static String getDefaultPublishUrl(Object... args) {
        return format(SpringUtils.getBean(EmsProperties.class).getDefaultPublishUrlTemplate(), args);
    }

    /**
     * 以顺序占位符 {@code {}} 格式化模板。
     *
     * @param template 模板
     * @param args     占位参数
     * @return 格式化结果
     */
    private static String format(String template, Object... args) {
        if (template == null || args == null || args.length == 0) {
            return template;
        }
        StringBuilder result = new StringBuilder(template.length() + 32);
        int argIndex = 0;
        int start = 0;
        int placeholder;
        while ((placeholder = template.indexOf("{}", start)) != -1) {
            result.append(template, start, placeholder);
            Object arg = argIndex < args.length ? args[argIndex] : null;
            result.append(arg == null ? "null" : arg);
            argIndex++;
            start = placeholder + 2;
        }
        result.append(template, start, template.length());
        return result.toString();
    }
}
