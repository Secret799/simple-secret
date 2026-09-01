package com.ss.dji.camera.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Exposes device events and the diagnostics contract used by the browser player. */
@RestController
@RequestMapping("/easyMedia/api/sei")
@Validated
public class DjiSeiSseController {

    private final DjiSeiSseService sseService;

    public DjiSeiSseController(DjiSeiSseService sseService) {
        this.sseService = sseService;
    }

    /** Subscribe to parsed SEI packets for the RTMP stream identified by {@code deviceId}. */
    @GetMapping(path = "/devices/{deviceId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> deviceEvents(
            @PathVariable @NotBlank @Size(max = 128)
            @Pattern(regexp = "[A-Za-z0-9_.-]+") String deviceId) {
        return response(() -> sseService.subscribeDevice(deviceId));
    }

    /** Subscribe to the stream diagnostics expected by the existing player page. */
    @GetMapping(path = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> diagnosticEvents(
            @RequestParam @NotBlank @Size(max = 64)
            @Pattern(regexp = "[A-Za-z0-9_.-]+") String app,
            @RequestParam @NotBlank @Size(max = 128)
            @Pattern(regexp = "[A-Za-z0-9_.-]+") String stream) {
        return response(() -> sseService.subscribeDiagnostics(app, stream));
    }

    private ResponseEntity<SseEmitter> response(Subscription subscription) {
        try {
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_EVENT_STREAM)
                    .cacheControl(CacheControl.noStore())
                    .header("X-Accel-Buffering", "no")
                    .body(subscription.subscribe());
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    exception.getMessage(), exception);
        }
    }

    private interface Subscription {
        SseEmitter subscribe();
    }
}
