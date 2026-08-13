package com.ss.application.pushstream.service;

import java.util.Set;

/**
 * 查询媒体服务器在线流的最小边界。
 *
 * @author junpzx
 * @since 2026-08-12
 */
@FunctionalInterface
public interface MediaServerClient {

    /**
     * 获取指定应用下的在线流标识。
     *
     * @param app 媒体应用名
     * @return 在线流标识集合
     */
    Set<String> onlineStreamIds(String app);
}
