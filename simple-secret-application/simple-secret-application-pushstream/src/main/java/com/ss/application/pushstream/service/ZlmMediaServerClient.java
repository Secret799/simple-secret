package com.ss.application.pushstream.service;

import com.ss.zlm4j.domain.MediaSourceDomain;
import com.ss.zlm4j.service.IZlmMediaService;
import com.ss.zlm4j.service.domain.bo.GetMediaListBO;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 使用 zlm4j 服务查询 ZLMediaKit 在线流。
 *
 * @author junpzx
 * @since 2026-08-12
 */
public class ZlmMediaServerClient implements MediaServerClient {

    /** zlm4j 媒体服务。 */
    private final IZlmMediaService mediaService;

    /**
     * 创建 ZLMediaKit 查询客户端。
     *
     * @param mediaService zlm4j 媒体服务
     */
    public ZlmMediaServerClient(IZlmMediaService mediaService) {
        this.mediaService = Objects.requireNonNull(mediaService, "mediaService");
    }

    /**
     * 查询指定应用下的在线流。
     *
     * @param app 媒体应用名
     * @return 在线流标识集合
     */
    @Override
    public Set<String> onlineStreamIds(String app) {
        List<MediaSourceDomain> mediaSources = mediaService.getMediaList(new GetMediaListBO().setApp(app));
        if (mediaSources == null || mediaSources.isEmpty()) {
            return Set.of();
        }
        return mediaSources.stream().map(MediaSourceDomain::getStream)
                .filter(Objects::nonNull).collect(Collectors.toUnmodifiableSet());
    }
}
