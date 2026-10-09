package com.ss.dji.camera.web;

import com.ss.dji.camera.config.DjiSeiProperties;
import com.ss.dji.camera.diagnostic.DjiSeiEventListener;
import com.ss.dji.camera.diagnostic.PayloadPreview;
import com.ss.dji.camera.event.DjiSeiPacketParsedEvent;
import com.ss.dji.camera.parser.SeiMessage;
import com.ss.dji.camera.parser.SeiParseIssue;
import com.ss.dji.camera.parser.SeiParseResult;
import com.ss.dji.camera.parser.VideoCodec;
import com.ss.easymedia.callback.TrackDelegateCallback.TackDelegateInfo;
import com.ss.zlm4j.domain.MediaSourceDomain;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Bridges synchronous DJI SEI diagnostics to bounded, asynchronous SSE delivery. */
public class DjiSeiSseService implements DjiSeiEventListener {

    private static final int MAX_RECENT_EVENTS = 10;
    private static final int MAX_CLIENTS_PER_STREAM = 16;
    private static final int EVENT_QUEUE_CAPACITY = 1024;
    private static final long STATS_INTERVAL_MILLIS = 1000L;
    private static final long HEARTBEAT_SECONDS = 15L;

    private final DjiSeiProperties properties;
    private final Clock clock;
    private final EmitterFactory emitterFactory;
    private final Map<StreamKey, StreamState> streams = new ConcurrentHashMap<>();
    private final AtomicLong droppedEvents = new AtomicLong();
    private final ThreadPoolExecutor dispatcher;
    private final ScheduledThreadPoolExecutor heartbeatExecutor;


    @Autowired
    public DjiSeiSseService(DjiSeiProperties properties, Clock clock) {
        this(properties, clock, () -> new SseEmitter(0L));
    }

    DjiSeiSseService(DjiSeiProperties properties, Clock clock, EmitterFactory emitterFactory) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.emitterFactory = Objects.requireNonNull(emitterFactory, "emitterFactory");
        this.dispatcher = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(EVENT_QUEUE_CAPACITY), runnable -> daemonThread(runnable,
                "dji-sei-sse-dispatcher"), new ThreadPoolExecutor.AbortPolicy());
        this.heartbeatExecutor = new ScheduledThreadPoolExecutor(1,
                runnable -> daemonThread(runnable, "dji-sei-sse-heartbeat"));
        this.heartbeatExecutor.setRemoveOnCancelPolicy(true);
        this.heartbeatExecutor.scheduleAtFixedRate(this::enqueueHeartbeat,
                HEARTBEAT_SECONDS, HEARTBEAT_SECONDS, TimeUnit.SECONDS);
    }

    /** Create a device subscription where the device ID is the RTMP stream ID. */
    public SseEmitter subscribeDevice(String deviceId) {
        StreamKey key = new StreamKey(properties.getAllowedApp(), deviceId);
        StreamState state = state(key);
        SseEmitter emitter = addClient(key, state, ClientType.DEVICE);
        try {
            emitter.send(SseEmitter.event().name("connected")
                    .data(new ConnectedEvent(deviceId, key.app(), clock.instant()), MediaType.APPLICATION_JSON));
        } catch (IOException | RuntimeException exception) {
            remove(state, emitter, ClientType.DEVICE);
            emitter.completeWithError(exception);
        }
        return emitter;
    }

    /** Create the diagnostics subscription consumed by the existing browser player. */
    public SseEmitter subscribeDiagnostics(String app, String stream) {
        StreamKey key = new StreamKey(app, stream);
        StreamState state = state(key);
        SseEmitter emitter = addClient(key, state, ClientType.DIAGNOSTICS);
        try {
            emitter.send(SseEmitter.event().name("snapshot")
                    .data(new InitialSnapshot(state.stats(key, clock.instant()), state.recentSnapshot()),
                            MediaType.APPLICATION_JSON));
        } catch (IOException | RuntimeException exception) {
            remove(state, emitter, ClientType.DIAGNOSTICS);
            emitter.completeWithError(exception);
        }
        return emitter;
    }

    @Override
    public void onStreamRegistered(MediaSourceDomain source) {
        if (!isAllowed(source)) {
            return;
        }
        StreamKey key = StreamKey.from(source);
        Instant registeredAt = clock.instant();
        enqueue(key, () -> {
            StreamState state = state(key);
            state.reset(registeredAt);
            broadcastDiagnostics(state, "stream", state.nextId(), state.stats(key, registeredAt));
        });
    }

    /** Queue parsed frames without making the native media callback wait on HTTP clients. */
    @Override
    public void onFrame(MediaSourceDomain source, TackDelegateInfo frame,
                        VideoCodec codec, SeiParseResult result) {
        if (!isAllowed(source)) {
            return;
        }
        StreamKey key = StreamKey.from(source);
        FrameUpdate update = FrameUpdate.from(source, frame, codec, result, clock.instant());
        enqueue(key, () -> acceptFrame(key, update));
    }

    @Override
    public void onStreamDeregistered(MediaSourceDomain source) {
        if (!isAllowed(source)) {
            return;
        }
        StreamKey key = StreamKey.from(source);
        Instant deregisteredAt = clock.instant();
        enqueue(key, () -> {
            StreamState state = state(key);
            state.active = false;
            state.updatedAt = deregisteredAt;
            broadcastDiagnostics(state, "stream", state.nextId(), state.stats(key, deregisteredAt));
        });
    }

    private void acceptFrame(StreamKey key, FrameUpdate update) {
        StreamState state = state(key);
        state.active = true;
        state.codec = update.codec();
        state.updatedAt = update.parsedAt();

        int messageLimit = Math.min(update.messages().size(), properties.getMaxMessageLogs());
        for (int index = 0; index < update.messages().size(); index++) {
            SeiMessage message = update.messages().get(index);
            long id = state.nextId();
            broadcastDevice(state, "sei", id, SeiEvent.from(key, update, message));
            if (index < messageLimit) {
                DiagnosticEvent diagnostic = DiagnosticEvent.message(id, update.parsedAt(), key, update,
                        message, properties.getPreviewBytes());
                state.addRecent(diagnostic);
                broadcastDiagnostics(state, "sei", id, diagnostic);
            }
        }
        for (SeiParseIssue issue : update.issues()) {
            long id = state.nextId();
            DiagnosticEvent diagnostic = DiagnosticEvent.issue(id, update.parsedAt(), key, update, issue);
            state.addRecent(diagnostic);
            broadcastDiagnostics(state, "issue", id, diagnostic);
        }

        state.videoFrames = saturatedAdd(state.videoFrames, 1);
        state.seiNalUnits = saturatedAdd(state.seiNalUnits, update.seiNalUnits());
        state.seiMessages = saturatedAdd(state.seiMessages, update.messages().size());
        state.parseIssues = saturatedAdd(state.parseIssues, update.issues().size());
        long now = update.parsedAt().toEpochMilli();
        if (messageLimit > 0 || !update.issues().isEmpty() || now - state.lastStatsAt >= STATS_INTERVAL_MILLIS) {
            state.lastStatsAt = now;
            broadcastDiagnostics(state, "stats", state.nextId(), state.stats(key, update.parsedAt()));
        }
    }

    private SseEmitter addClient(StreamKey key, StreamState state, ClientType type) {
        SseEmitter emitter = emitterFactory.create();
        if (!state.add(emitter, type)) {
            emitter.complete();
            throw new IllegalStateException("SEI subscriber limit reached for stream " + key.stream());
        }
        Runnable remove = () -> remove(state, emitter, type);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(ignored -> remove.run());
        return emitter;
    }

    private void enqueue(StreamKey key, Runnable operation) {
        try {
            dispatcher.execute(operation);
        } catch (RuntimeException exception) {
            droppedEvents.incrementAndGet();
            state(key).droppedEvents.incrementAndGet();
        }
    }

    private void enqueueHeartbeat() {
        try {
            dispatcher.execute(() -> {
                Instant now = clock.instant();
                for (Map.Entry<StreamKey, StreamState> entry : streams.entrySet()) {
                    StreamState state = entry.getValue();
                    broadcastDiagnostics(state, "heartbeat", state.nextId(), Map.of("time", now.toString()));
                    broadcastDevice(state, "heartbeat", state.nextId(),
                            new HeartbeatEvent(entry.getKey().stream(), now, droppedEvents.get()));
                }
            });
        } catch (RuntimeException exception) {
            droppedEvents.incrementAndGet();
        }
    }

    private StreamState state(StreamKey key) {
        return streams.computeIfAbsent(key, ignored -> new StreamState(clock.instant()));
    }

    private void broadcastDiagnostics(StreamState state, String name, long id, Object data) {
        send(state, state.diagnosticClients, ClientType.DIAGNOSTICS, name, id, data);
    }

    private void broadcastDevice(StreamState state, String name, long id, Object data) {
        send(state, state.deviceClients, ClientType.DEVICE, name, id, data);
    }

    private void send(StreamState state, CopyOnWriteArrayList<SseEmitter> clients, ClientType type,
                      String name, long id, Object data) {
        for (SseEmitter emitter : clients) {
            try {
                emitter.send(SseEmitter.event().id(Long.toString(id)).name(name)
                        .data(data, MediaType.APPLICATION_JSON));
            } catch (IOException | RuntimeException exception) {
                remove(state, emitter, type);
                emitter.complete();
            }
        }
    }

    private void remove(StreamState state, SseEmitter emitter, ClientType type) {
        state.clients(type).remove(emitter);
    }

    @PreDestroy
    public void close() {
        heartbeatExecutor.shutdownNow();
        dispatcher.shutdownNow();
        streams.values().forEach(state -> {
            state.deviceClients.forEach(SseEmitter::complete);
            state.diagnosticClients.forEach(SseEmitter::complete);
        });
        streams.clear();
    }

    InitialSnapshot snapshot(String app, String stream) {
        StreamKey key = new StreamKey(app, stream);
        StreamState state = state(key);
        return new InitialSnapshot(state.stats(key, clock.instant()), state.recentSnapshot());
    }

    int subscriberCount(String deviceId) {
        StreamState state = streams.get(new StreamKey(properties.getAllowedApp(), deviceId));
        return state == null ? 0 : state.deviceClients.size();
    }

    long droppedEventCount() {
        return droppedEvents.get();
    }

    private boolean isAllowed(MediaSourceDomain source) {
        return source != null && Objects.equals(properties.getAllowedApp(), source.getApp());
    }

    private static long saturatedAdd(long current, long increment) {
        return increment >= Long.MAX_VALUE - current ? Long.MAX_VALUE : current + increment;
    }

    private static Thread daemonThread(Runnable runnable, String name) {
        Thread thread = new Thread(runnable, name);
        thread.setDaemon(true);
        return thread;
    }

    private static String decodeUtf8(byte[] payload, int offset) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(payload, offset, payload.length - offset))
                    .toString();
        } catch (CharacterCodingException exception) {
            return null;
        }
    }

    interface EmitterFactory {
        SseEmitter create();
    }

    private enum ClientType {
        DEVICE,
        DIAGNOSTICS
    }

    record StreamKey(String app, String stream) {
        static StreamKey from(MediaSourceDomain source) {
            return new StreamKey(source.getApp(), source.getStream());
        }
    }

    public record ConnectedEvent(String deviceId, String app, Instant connectedAt) {
    }

    public record HeartbeatEvent(String deviceId, Instant time, long droppedEvents) {
    }

    public record Stats(String app, String stream, boolean active, String codec,
                        long videoFrames, long seiNalUnits, long seiMessages,
                        long parseIssues, long droppedEvents, Instant startedAt, Instant updatedAt) {
    }

    public record InitialSnapshot(Stats stats, List<DiagnosticEvent> recentEvents) {
    }

    public record DiagnosticEvent(long id, String kind, Instant time, String app, String stream,
                                  String codec, Long pts, Long dts, Integer payloadType,
                                  Integer payloadBytes, UUID uuid, String hex, String text,
                                  String payloadBase64, boolean truncated,
                                  String issueCode, String issueMessage) {

        static DiagnosticEvent message(long id, Instant time, StreamKey key, FrameUpdate frame,
                                       SeiMessage message, int previewBytes) {
            byte[] payload = message.payload();
            PayloadPreview preview = PayloadPreview.from(payload, previewBytes);
            int textOffset = message.payloadType() == 5 && message.uuid().isPresent() ? 16 : 0;
            return new DiagnosticEvent(id, "sei", time, key.app(), key.stream(), frame.codec(),
                    frame.pts(), frame.dts(), message.payloadType(), payload.length,
                    message.uuid().orElse(null), preview.hex(), decodeUtf8(payload, textOffset),
                    Base64.getEncoder().encodeToString(payload), preview.truncated(), null, null);
        }

        static DiagnosticEvent issue(long id, Instant time, StreamKey key, FrameUpdate frame,
                                     SeiParseIssue issue) {
            return new DiagnosticEvent(id, "issue", time, key.app(), key.stream(), frame.codec(),
                    frame.pts(), frame.dts(), null, null, null, null, null, null,
                    false, issue.code(), issue.message());
        }
    }

    public record SeiEvent(String deviceId, String schema, String app, String vhost, String codec,
                           Long pts, Long dts, int payloadType, int payloadBytes, UUID uuid,
                           String data, String payloadBase64, Instant parsedAt) {

        static SeiEvent from(StreamKey key, FrameUpdate frame, SeiMessage message) {
            byte[] payload = message.payload();
            UUID uuid = message.uuid().orElse(null);
            int textOffset = message.payloadType() == 5 && uuid != null ? 16 : 0;
            return new SeiEvent(key.stream(), frame.schema(), key.app(), frame.vhost(), frame.codec(),
                    frame.pts(), frame.dts(), message.payloadType(), payload.length, uuid,
                    decodeUtf8(payload, textOffset), Base64.getEncoder().encodeToString(payload),
                    frame.parsedAt());
        }

        static SeiEvent from(DjiSeiPacketParsedEvent event) {
            byte[] payload = event.getPayload();
            UUID uuid = event.getUuid().orElse(null);
            int textOffset = event.getPayloadType() == 5 && uuid != null ? 16 : 0;
            return new SeiEvent(event.getStream(), event.getSchema(), event.getApp(), event.getVhost(),
                    event.getCodec().name(), event.getPts(), event.getDts(), event.getPayloadType(),
                    event.getPayloadBytes(), uuid, decodeUtf8(payload, textOffset),
                    Base64.getEncoder().encodeToString(payload), event.getParsedAt());
        }
    }

    private record FrameUpdate(String schema, String vhost, String codec, Long pts, Long dts,
                               int seiNalUnits, List<SeiMessage> messages,
                               List<SeiParseIssue> issues, Instant parsedAt) {
        static FrameUpdate from(MediaSourceDomain source, TackDelegateInfo frame, VideoCodec codec,
                                SeiParseResult result, Instant parsedAt) {
            return new FrameUpdate(source.getSchema(), source.getVhost(), codec.name(),
                    frame.getPts(), frame.getDts(), result.seiNalUnitCount(),
                    List.copyOf(result.messages()), List.copyOf(result.issues()), parsedAt);
        }
    }

    private static final class StreamState {
        private final CopyOnWriteArrayList<SseEmitter> deviceClients = new CopyOnWriteArrayList<>();
        private final CopyOnWriteArrayList<SseEmitter> diagnosticClients = new CopyOnWriteArrayList<>();
        private final ArrayDeque<DiagnosticEvent> recentEvents = new ArrayDeque<>();
        private final AtomicLong droppedEvents = new AtomicLong();
        private long sequence;
        private boolean active;
        private String codec;
        private long videoFrames;
        private long seiNalUnits;
        private long seiMessages;
        private long parseIssues;
        private long lastStatsAt;
        private Instant startedAt;
        private Instant updatedAt;

        private StreamState(Instant now) {
            this.startedAt = now;
            this.updatedAt = now;
        }

        private synchronized boolean add(SseEmitter emitter, ClientType type) {
            if (deviceClients.size() + diagnosticClients.size() >= MAX_CLIENTS_PER_STREAM) {
                return false;
            }
            clients(type).add(emitter);
            return true;
        }

        private CopyOnWriteArrayList<SseEmitter> clients(ClientType type) {
            return type == ClientType.DEVICE ? deviceClients : diagnosticClients;
        }

        private synchronized void reset(Instant now) {
            active = true;
            codec = null;
            videoFrames = 0;
            seiNalUnits = 0;
            seiMessages = 0;
            parseIssues = 0;
            lastStatsAt = 0;
            startedAt = now;
            updatedAt = now;
            recentEvents.clear();
        }

        private synchronized long nextId() {
            return ++sequence;
        }

        private synchronized void addRecent(DiagnosticEvent event) {
            while (recentEvents.size() >= MAX_RECENT_EVENTS) {
                recentEvents.removeFirst();
            }
            recentEvents.addLast(event);
        }

        private synchronized List<DiagnosticEvent> recentSnapshot() {
            return List.copyOf(recentEvents);
        }

        private synchronized Stats stats(StreamKey key, Instant now) {
            return new Stats(key.app(), key.stream(), active, codec, videoFrames, seiNalUnits,
                    seiMessages, parseIssues, droppedEvents.get(), startedAt,
                    updatedAt == null ? now : updatedAt);
        }
    }
}
