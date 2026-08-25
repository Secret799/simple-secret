package com.ss.application.easymedia.sei;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.validation.annotation.Validated;

/** 浏览器使用的 DJI SEI 实时诊断接口。 */
@RestController
@RequestMapping("/easyMedia/api/sei")
@ConditionalOnProperty(prefix = "simple-secret.dji-sei", name = "enabled", havingValue = "true")
@Validated
public class DjiSeiController {

    private final DjiSeiEventHub eventHub;

    public DjiSeiController(DjiSeiEventHub eventHub) {
        this.eventHub = eventHub;
    }

    /** 订阅指定 RTMP 流的统计、SEI 消息和解析问题。 */
    @GetMapping(path = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(
            @RequestParam @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9_.-]+") String app,
            @RequestParam @NotBlank @Size(max = 128) @Pattern(regexp = "[A-Za-z0-9_.-]+") String stream) {
        try {
            return eventHub.subscribe(app, stream);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage(), exception);
        }
    }
}
