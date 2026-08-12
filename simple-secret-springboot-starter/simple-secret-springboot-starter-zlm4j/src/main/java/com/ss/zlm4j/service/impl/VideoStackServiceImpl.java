package com.ss.zlm4j.service.impl;

import com.aizuda.zlm4j.structure.MK_MEDIA_SOURCE;
import com.ss.zlm4j.constants.ZlmMediaServerConstants;
import com.ss.zlm4j.context.VideoStackContext;
import com.ss.zlm4j.helper.ZlmMediaHelper;
import com.ss.zlm4j.security.MediaResourcePolicy;
import com.ss.zlm4j.security.MediaResourceUsage;
import com.ss.zlm4j.service.IVideoStackService;
import com.ss.zlm4j.service.domain.bo.VideoStackBO;
import com.ss.zlm4j.service.validation.VideoStackValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 视频拼接服务实现
 *
 * @author JunPzx
 * @since 2025/8/20 18:22
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "simple-secret.zlm4j.enabled", havingValue = "true")
public class VideoStackServiceImpl implements IVideoStackService {


    private final ConcurrentMap<String, VideoStackContext> videoStackTasks = new ConcurrentHashMap<>();
    private final MediaResourcePolicy mediaResourcePolicy;
    private final VideoStackValidator validator;
    private final Supplier<com.aizuda.zlm4j.core.ZLMApi> zlmApiSupplier;
    private final Function<VideoStackBO, VideoStackContext> contextFactory;

    /**
     * 创建并初始化实例。
     *
     * @param mediaResourcePolicy 媒体资源访问策略
     * @param validator 视频拼接参数校验器
     */
    @Autowired
    public VideoStackServiceImpl(MediaResourcePolicy mediaResourcePolicy, VideoStackValidator validator) {
        this(mediaResourcePolicy, validator, ZlmMediaHelper::getZlmApi, VideoStackContext::new);
    }

    VideoStackServiceImpl(MediaResourcePolicy mediaResourcePolicy, VideoStackValidator validator,
                          com.aizuda.zlm4j.core.ZLMApi zlmApi,
                          Function<VideoStackBO, VideoStackContext> contextFactory) {
        this(mediaResourcePolicy, validator, () -> zlmApi, contextFactory);
    }

    private VideoStackServiceImpl(MediaResourcePolicy mediaResourcePolicy, VideoStackValidator validator,
                                  Supplier<com.aizuda.zlm4j.core.ZLMApi> zlmApiSupplier,
                                  Function<VideoStackBO, VideoStackContext> contextFactory) {
        this.mediaResourcePolicy = Objects.requireNonNull(mediaResourcePolicy, "mediaResourcePolicy");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.zlmApiSupplier = Objects.requireNonNull(zlmApiSupplier, "zlmApiSupplier");
        this.contextFactory = Objects.requireNonNull(contextFactory, "contextFactory");
    }

    @Override
    public void startStack(VideoStackBO param) {
        validator.validate(param);
        validateExternalResources(param);
        if (param.getPushUrl() == null || param.getPushUrl().isBlank()) {
            MK_MEDIA_SOURCE mkMediaSource = zlmApiSupplier.get()
                    .mk_media_source_find2("rtmp", ZlmMediaServerConstants.DEFAULT_VHOST, param.getApp(), param.getId(), 0);
            if (mkMediaSource != null) {
                throw new IllegalStateException("当前流已在线");
            }
        }
        String id = param.getId();
        VideoStackContext videoStack = contextFactory.apply(param);
        if (videoStackTasks.putIfAbsent(id, videoStack) != null) {
            throw new IllegalStateException("拼接屏任务已存在");
        }
        videoStack.setCompletionCallback(() -> videoStackTasks.remove(id, videoStack));
        try {
            videoStack.init();
        } catch (RuntimeException | Error e) {
            videoStackTasks.remove(id, videoStack);
            videoStack.stop();
            throw e;
        }
    }

    @Override
    public void resetStack(VideoStackBO param) {
        validator.validate(param);
        validateExternalResources(param);
        VideoStackContext videoStack = videoStackTasks.get(param.getId());
        if (videoStack == null) {
            throw new IllegalStateException("拼接屏任务不存在");
        }
        videoStack.reset(param);
    }

    @Override
    public void stopStack(String id) {
        VideoStackContext videoStack = videoStackTasks.remove(id);
        if (videoStack == null) {
            throw new IllegalStateException("拼接屏任务不存在");
        }
        videoStack.stop();
    }

    private void validateExternalResources(VideoStackBO param) {
        if (param.getPushUrl() != null && !param.getPushUrl().isBlank()) {
            param.setPushUrl(mediaResourcePolicy.requireAllowed(param.getPushUrl(), MediaResourceUsage.STACK_OUTPUT)
                    .toASCIIString());
        }
        if (param.getFillImgUrl() != null && !param.getFillImgUrl().isBlank()) {
            param.setFillImgUrl(mediaResourcePolicy.requireAllowed(param.getFillImgUrl(), MediaResourceUsage.STACK_IMAGE)
                    .toASCIIString());
        }
        if (param.getWindowList() == null) {
            return;
        }
        param.getWindowList().forEach(window -> {
            if (window.getVideoUrl() != null && !window.getVideoUrl().isBlank()) {
                window.setVideoUrl(mediaResourcePolicy.requireAllowed(window.getVideoUrl(), MediaResourceUsage.STACK_VIDEO)
                        .toASCIIString());
            }
            if (window.getImgUrl() != null && !window.getImgUrl().isBlank()) {
                window.setImgUrl(mediaResourcePolicy.requireAllowed(window.getImgUrl(), MediaResourceUsage.STACK_IMAGE)
                        .toASCIIString());
            }
        });
    }
}
