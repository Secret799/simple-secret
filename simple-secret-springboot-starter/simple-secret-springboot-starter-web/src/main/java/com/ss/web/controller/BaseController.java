package com.ss.web.controller;

import com.ss.core.domain.Result;

/**
 * WebMVC 控制器公共辅助方法。
 */
public class BaseController {

    /**
     * 将受影响行数转换为响应结果。
     *
     * @param rows 受影响行数
     * @return 成功或失败响应结果
     */
    protected Result<Void> toResult(int rows) {
        return rows > 0 ? Result.ok() : Result.fail();
    }

    /**
     * 将操作结果转换为响应结果。
     *
     * @param success 操作是否成功
     * @return 成功或失败响应结果
     */
    protected Result<Void> toResult(boolean success) {
        return success ? Result.ok() : Result.fail();
    }

    /**
     * 构造 Spring MVC 重定向视图名称。
     *
     * @param url 重定向地址
     * @return 带有 {@code redirect:} 前缀的视图名称
     * @throws IllegalArgumentException 当地址为空白或包含 CR/LF 时抛出
     */
    public String redirect(String url) {
        if (url == null || url.isBlank() || url.indexOf('\r') >= 0 || url.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("redirect URL must be non-blank and contain no CR/LF");
        }
        return "redirect:" + url;
    }
}
