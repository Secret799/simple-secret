package com.ss.zlm4j.service.impl;

import com.aizuda.zlm4j.structure.MK_MEDIA_SOURCE;
import com.ss.zlm4j.constants.ZlmMediaServerConstants;
import com.ss.zlm4j.context.TranscodeContext;
import com.ss.zlm4j.helper.ZlmMediaHelper;
import com.ss.zlm4j.security.MediaResourcePolicy;
import com.ss.zlm4j.security.MediaResourceUsage;
import com.ss.zlm4j.service.ITranscodeService;
import com.ss.zlm4j.service.domain.bo.TranscodeBO;
import com.ss.zlm4j.support.SpringUtils;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.helpers.MessageFormatter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * 转码服务实现
 *
 * @author JunPzx
 * @since 2025/8/20 18:02
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "simple-secret.zlm4j.enabled", havingValue = "true")
public class TranscodeServiceImpl implements ITranscodeService {

    private final ConcurrentMap<String, TaskRegistration> transcodeTasks = new ConcurrentHashMap<>();
    private final MediaResourcePolicy mediaResourcePolicy;
    private final Supplier<com.aizuda.zlm4j.core.ZLMApi> zlmApiSupplier;
    private final IntSupplier rtmpPortSupplier;
    private final Supplier<ExecutorService> executorSupplier;
    private final BiFunction<TranscodeBO, String, TranscodeContext> contextFactory;

    /**
     * 创建并初始化实例。
     *
     * @param mediaResourcePolicy 媒体资源访问策略
     */
    @Autowired
    public TranscodeServiceImpl(MediaResourcePolicy mediaResourcePolicy) {
        this(mediaResourcePolicy, ZlmMediaHelper::getZlmApi,
                () -> ZlmMediaHelper.getContext().getDefaultProperties().getRtmpPort(),
                SpringUtils::getSimpleSecretScheduledExecutor, TranscodeContext::new);
    }

    TranscodeServiceImpl(MediaResourcePolicy mediaResourcePolicy, com.aizuda.zlm4j.core.ZLMApi zlmApi,
                         int rtmpPort, ExecutorService executor,
                         BiFunction<TranscodeBO, String, TranscodeContext> contextFactory) {
        this(mediaResourcePolicy, () -> zlmApi, () -> rtmpPort, () -> executor, contextFactory);
    }

    private TranscodeServiceImpl(MediaResourcePolicy mediaResourcePolicy,
                                 Supplier<com.aizuda.zlm4j.core.ZLMApi> zlmApiSupplier,
                                 IntSupplier rtmpPortSupplier,
                                 Supplier<ExecutorService> executorSupplier,
                                 BiFunction<TranscodeBO, String, TranscodeContext> contextFactory) {
        this.mediaResourcePolicy = Objects.requireNonNull(mediaResourcePolicy, "mediaResourcePolicy");
        this.zlmApiSupplier = Objects.requireNonNull(zlmApiSupplier, "zlmApiSupplier");
        this.rtmpPortSupplier = Objects.requireNonNull(rtmpPortSupplier, "rtmpPortSupplier");
        this.executorSupplier = Objects.requireNonNull(executorSupplier, "executorSupplier");
        this.contextFactory = Objects.requireNonNull(contextFactory, "contextFactory");
    }

    @Override
    public void transcode(TranscodeBO param) {
        param.setUrl(mediaResourcePolicy.requireAllowed(param.getUrl(), MediaResourceUsage.TRANSCODE).toASCIIString());
        MK_MEDIA_SOURCE mkMediaSource = zlmApiSupplier.get()
                .mk_media_source_find2("rtmp", ZlmMediaServerConstants.DEFAULT_VHOST, param.getApp(), param.getStream(), 0);
        if (mkMediaSource != null) {
            throw new IllegalStateException("当前流已在线");
        }
        String pushUrl = MessageFormatter.basicArrayFormat(
                "rtmp://127.0.0.1:{}/{}/{}", new Object[]{
                        rtmpPortSupplier.getAsInt(),
                        param.getApp(), param.getStream()});
        TranscodeContext context = contextFactory.apply(param, pushUrl);
        TaskRegistration registration = new TaskRegistration(context);
        String stream = param.getStream();
        if (transcodeTasks.putIfAbsent(stream, registration) != null) {
            throw new IllegalStateException("转码任务已存在");
        }
        try {
            Future<?> future = executorSupplier.get().submit(() -> {
                try {
                    context.start();
                } finally {
                    transcodeTasks.remove(stream, registration);
                }
            });
            registration.attach(future);
        } catch (RuntimeException e) {
            transcodeTasks.remove(stream, registration);
            registration.stop();
            throw e;
        }
    }

    @Override
    public void stopTranscode(String stream) {
        TaskRegistration registration = transcodeTasks.remove(stream);
        if (registration != null) {
            registration.stop();
        }
    }

    /**
     * 停止当前服务实例持有的全部转码任务。
     */
    @PreDestroy
    public void close() {
        transcodeTasks.forEach((stream, registration) -> {
            if (transcodeTasks.remove(stream, registration)) {
                registration.stop();
            }
        });
    }

    private static final class TaskRegistration {

        private final TranscodeContext context;
        private final AtomicReference<Future<?>> future = new AtomicReference<>();
        private final AtomicBoolean stopped = new AtomicBoolean(false);

        private TaskRegistration(TranscodeContext context) {
            this.context = Objects.requireNonNull(context, "context");
        }

        private void attach(Future<?> submittedFuture) {
            future.set(submittedFuture);
            if (stopped.get()) {
                submittedFuture.cancel(true);
            }
        }

        private void stop() {
            if (stopped.compareAndSet(false, true)) {
                context.stop();
                Future<?> submittedFuture = future.get();
                if (submittedFuture != null) {
                    submittedFuture.cancel(true);
                }
            }
        }
    }
}
