package com.ss.application.dahuatest.web;

import com.ss.camerazlm.DahuaZlmStreamService;
import com.ss.camerazlm.DahuaZlmStreamSession;
import com.ss.ics.constants.enums.PtzControlCommandEnums;
import com.ss.ics.dahua.DahuaCameraSdkService;
import com.ss.ics.domain.DeviceDomain;
import com.ss.ics.domain.PlayDomain;
import com.ss.ics.domain.PTZControlDomain;
import com.ss.zlm4j.config.properties.ZlmMediaProperties;
import com.ss.zlm4j.domain.MediaSourceDomain;
import com.ss.zlm4j.service.IZlmMediaService;
import com.ss.zlm4j.service.domain.bo.GetMediaListBO;
import jakarta.validation.Valid;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 大华 SDK 播放与云台控制测试接口。
 *
 * <p>测试专用：单活动推流会话，云台每次调用独立登录注销，无服务端凭据存储。
 * 本类及其异常处理不得输出请求体或设备域对象，避免账号密码进入日志。</p>
 *
 * @author junpzx
 * @since 2026-09-16
 */
@RestController
@RequestMapping("/api")
public class DahuaTestController {

    private final ObjectProvider<DahuaCameraSdkService> cameraSdk;
    private final ObjectProvider<DahuaZlmStreamService> streamService;
    private final ObjectProvider<IZlmMediaService> mediaService;
    private final ObjectProvider<ZlmMediaProperties> zlmProperties;

    private final Object streamLock = new Object();
    private DahuaZlmStreamSession session;
    private String sessionApp;
    private String sessionStream;

    /**
     * 注入可选依赖，SDK 或 ZLM 未就绪时仍可提供 /api/config 自检。
     *
     * @param cameraSdk 大华 SDK 服务
     * @param streamService 大华转推 ZLM 服务
     * @param mediaService ZLM 媒体服务
     * @param zlmProperties ZLM 配置
     */
    public DahuaTestController(ObjectProvider<DahuaCameraSdkService> cameraSdk,
                               ObjectProvider<DahuaZlmStreamService> streamService,
                               ObjectProvider<IZlmMediaService> mediaService,
                               ObjectProvider<ZlmMediaProperties> zlmProperties) {
        this.cameraSdk = cameraSdk;
        this.streamService = streamService;
        this.mediaService = mediaService;
        this.zlmProperties = zlmProperties;
    }

    /**
     * 页面自检信息。
     *
     * @return SDK 就绪状态与 ZLM 播放端口
     */
    @GetMapping("/config")
    public Map<String, Object> config() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sdkReady", cameraSdk.getIfAvailable() != null);
        result.put("streamServiceReady", streamService.getIfAvailable() != null);
        ZlmMediaProperties properties = zlmProperties.getIfAvailable();
        result.put("zlmHttpPort", properties == null ? null : properties.getHttpPort());
        return result;
    }

    /**
     * 开始 SDK 拉流并推送到 ZLM。
     *
     * @param request 推流请求
     * @return 播放信息
     */
    @PostMapping("/stream/start")
    public Map<String, Object> startStream(@Valid @RequestBody StreamStartRequest request) {
        DahuaZlmStreamService service = requireStreamService();
        synchronized (streamLock) {
            if (session != null && !session.isClosed()) {
                throw new IllegalStateException("已有活动推流会话，请先停止");
            }
            DahuaZlmStreamSession created = service.start(
                    toDevice(request), toPlay(request), request.app(), request.stream());
            this.session = created;
            this.sessionApp = request.app();
            this.sessionStream = request.stream();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("app", request.app());
        result.put("stream", request.stream());
        return result;
    }

    /**
     * 停止当前推流会话。
     *
     * @return 停止结果
     */
    @PostMapping("/stream/stop")
    public Map<String, Object> stopStream() {
        synchronized (streamLock) {
            if (session == null || session.isClosed()) {
                this.session = null;
                this.sessionApp = null;
                this.sessionStream = null;
                return Map.of("stopped", false, "message", "当前没有活动会话");
            }
            session.close();
            this.session = null;
            this.sessionApp = null;
            this.sessionStream = null;
        }
        return Map.of("stopped", true);
    }

    /**
     * 推流会话与 ZLM 在线状态。
     *
     * @return 状态快照
     */
    @GetMapping("/stream/status")
    public Map<String, Object> streamStatus() {
        Map<String, Object> result = new LinkedHashMap<>();
        DahuaZlmStreamSession current;
        synchronized (streamLock) {
            current = this.session;
        }
        result.put("sessionActive", current != null && !current.isClosed());
        result.put("failure", current == null || current.failure().isEmpty()
                ? null : String.valueOf(current.failure().get().getMessage()));
        DahuaZlmStreamService service = streamService.getIfAvailable();
        result.put("activeSessionCount", service == null ? 0 : service.activeSessionCount());
        result.put("zlmSchemas", zlmSchemas());
        return result;
    }

    /**
     * 云台同步控制。
     *
     * @param request 云台请求
     * @return 执行结果
     */
    @PostMapping("/ptz")
    public Map<String, Object> ptz(@Valid @RequestBody PtzRequest request) {
        DahuaCameraSdkService sdk = cameraSdk.getIfAvailable();
        if (sdk == null) {
            throw new IllegalStateException("大华 SDK 未初始化，请设置 DAHUA_NETSDK_DIRECTORY");
        }
        PTZControlDomain control = new PTZControlDomain().setCommand(request.command());
        if (request.duration() != null && !request.duration().isBlank()) {
            try {
                control.setDuration(java.time.Duration.parse(request.duration()));
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException("duration 必须是 ISO-8601 时长，例如 PT0.8S");
            }
        } else {
            control.setIsBegin(request.begin());
        }
        if (request.speedLevel() != null) {
            control.setSpeedLevel(request.speedLevel());
        }
        boolean accepted = sdk.syncControl(toDevice(request), control);
        return Map.of("command", request.command().name(),
                "mode", control.getDuration() != null ? "pulse" : "hold",
                "accepted", accepted);
    }

    /**
     * SDK 预览回调诊断：直接订阅 realPlay 回调并统计帧数，用于区分“回调未触发”和“帧被过滤”。
     *
     * @param request 诊断请求
     * @return 采样期内的帧计数与字节统计
     * @throws InterruptedException 等待被中断
     */
    @PostMapping("/debug/frames")
    public Map<String, Object> debugFrames(@Valid @RequestBody PtzRequest request) throws InterruptedException {
        DahuaCameraSdkService sdk = cameraSdk.getIfAvailable();
        if (sdk == null) {
            throw new IllegalStateException("大华 SDK 未初始化，请设置 DAHUA_NETSDK_DIRECTORY");
        }
        java.util.concurrent.atomic.AtomicLong frames = new java.util.concurrent.atomic.AtomicLong();
        java.util.concurrent.atomic.AtomicLong bytes = new java.util.concurrent.atomic.AtomicLong();
        java.util.Map<Integer, java.util.concurrent.atomic.AtomicLong> frameTypes = new java.util.concurrent.ConcurrentHashMap<>();
        com.ss.ics.dahua.DahuaRealPlaySession debug = sdk.realPlay(
                toDevice(request),
                new PlayDomain().setTakeStreamParam(new PlayDomain.TakeStreamParam().setStreamType(0)),
                frame -> {
                    frames.incrementAndGet();
                    bytes.addAndGet(frame.data().length);
                    frameTypes.computeIfAbsent(frame.frameType(), k -> new java.util.concurrent.atomic.AtomicLong())
                            .incrementAndGet();
                });
        try {
            Thread.sleep(8000);
        } finally {
            debug.close();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("frames", frames.get());
        result.put("bytes", bytes.get());
        result.put("frameTypes", frameTypes);
        result.put("note", "frames=0 表示 SDK 回调未触发或全部被 Annex-B 过滤（frameType 直方图为空）");
        return result;
    }

    /**
     * 经典预览路径裸探针：绕过插件的结构化回调，直接统计原始码流回调数据形态。
     *
     * @param request 诊断请求（复用设备字段）
     * @return 原始回调统计
     * @throws Exception 探针执行失败
     */
    @PostMapping("/debug/raw")
    public Map<String, Object> debugRaw(@Valid @RequestBody PtzRequest request) throws Exception {
        DahuaCameraSdkService sdk = cameraSdk.getIfAvailable();
        if (sdk == null) {
            throw new IllegalStateException("大华 SDK 未初始化，请设置 DAHUA_NETSDK_DIRECTORY");
        }
        com.ss.ics.domain.LoggedDomain logged = sdk.login(
                new com.ss.ics.domain.LoginDomain()
                        .setIp(request.ip()).setPort(request.port())
                        .setUsername(request.username()).setPassword(request.password()));
        try {
            // 经典 CLIENT_RealPlayEx 通道号从 0 起，插件的逻辑通道从 1 起，这里换算
            return RawDhProbe.probe(Path.of(System.getenv("DAHUA_NETSDK_DIRECTORY")),
                    Long.parseLong(logged.getUserId()), Integer.parseInt(request.channel()) - 1, 8000);
        } finally {
            sdk.logout(logged.getUserId());
        }
    }

    private List<String> zlmSchemas() {
        IZlmMediaService media = mediaService.getIfAvailable();
        String app;
        String stream;
        synchronized (streamLock) {
            app = this.sessionApp;
            stream = this.sessionStream;
        }
        if (media == null || app == null || stream == null) {
            return List.of();
        }
        return media.getMediaList(new GetMediaListBO().setApp(app).setStream(stream)).stream()
                .map(MediaSourceDomain::getSchema)
                .distinct()
                .sorted()
                .toList();
    }

    private DahuaZlmStreamService requireStreamService() {
        DahuaZlmStreamService service = streamService.getIfAvailable();
        if (service == null) {
            throw new IllegalStateException("推流服务未就绪：需要 DAHUA_NETSDK_DIRECTORY 与 simple-secret.camera-zlm.enabled=true");
        }
        return service;
    }

    private static DeviceDomain toDevice(StreamStartRequest request) {
        return new DeviceDomain()
                .setIp(request.ip())
                .setPort(request.port())
                .setUsername(request.username())
                .setPassword(request.password())
                .setChannel(request.channel());
    }

    private static DeviceDomain toDevice(PtzRequest request) {
        return new DeviceDomain()
                .setIp(request.ip())
                .setPort(request.port())
                .setUsername(request.username())
                .setPassword(request.password())
                .setChannel(request.channel());
    }

    private static PlayDomain toPlay(StreamStartRequest request) {
        return new PlayDomain().setTakeStreamParam(
                new PlayDomain.TakeStreamParam().setStreamType(request.streamType()));
    }

    /**
     * 测试接口统一异常输出，仅返回消息文本。
     */
    @RestControllerAdvice(assignableTypes = DahuaTestController.class)
    static class ApiErrorHandler {

        /**
         * 参数校验失败。
         *
         * @param exception 校验异常
         * @return 400 与首条校验消息
         */
        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<Map<String, Object>> invalid(MethodArgumentNotValidException exception) {
            String message = exception.getBindingResult().getFieldErrors().isEmpty()
                    ? "请求参数不合法"
                    : exception.getBindingResult().getFieldErrors().get(0).getDefaultMessage();
            return ResponseEntity.badRequest().body(Map.of("message", message));
        }

        /**
         * 参数与状态错误。
         *
         * @param exception 业务异常
         * @return 400/409 与消息
         */
        @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
        public ResponseEntity<Map<String, Object>> conflict(RuntimeException exception) {
            return ResponseEntity.status(409).body(Map.of("message", String.valueOf(exception.getMessage())));
        }

        /**
         * 请求体不可解析（含未知枚举值）。
         *
         * @param exception 消息转换异常
         * @return 400 与消息
         */
        @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
        public ResponseEntity<Map<String, Object>> unreadable(Exception exception) {
            return ResponseEntity.badRequest().body(Map.of("message", "请求体格式不合法"));
        }

        /**
         * 其他失败。
         *
         * @param exception 未分类异常
         * @return 500 与消息
         */
        @ExceptionHandler(Exception.class)
        public ResponseEntity<Map<String, Object>> failure(Exception exception) {
            return ResponseEntity.status(500).body(Map.of("message", String.valueOf(exception.getMessage())));
        }
    }

}
