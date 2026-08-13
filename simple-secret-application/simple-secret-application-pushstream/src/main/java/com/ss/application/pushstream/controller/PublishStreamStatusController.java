package com.ss.application.pushstream.controller;

import com.ss.application.pushstream.service.PublishStreamService;
import com.ss.application.pushstream.status.PublishStreamStatusView;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/**
 * 提供不包含本地绝对路径的只读推流状态接口。
 *
 * @author junpzx
 * @since 2026-08-12
 */
@RestController
@RequestMapping("/api/publish-stream")
@ConditionalOnProperty(prefix = "simple-secret.publish-stream", name = {"enabled", "status-api-enabled"},
        havingValue = "true")
public class PublishStreamStatusController {

    /** 推流应用服务。 */
    private final PublishStreamService publishStreamService;

    /**
     * 创建只读状态控制器。
     *
     * @param publishStreamService 推流应用服务
     */
    public PublishStreamStatusController(PublishStreamService publishStreamService) {
        this.publishStreamService = Objects.requireNonNull(publishStreamService, "publishStreamService");
    }

    /**
     * 获取当前媒体文件和推流状态。
     *
     * @return 不包含本地绝对路径的状态视图
     */
    @GetMapping("/status")
    public PublishStreamStatusView status() {
        return publishStreamService.status();
    }
}
