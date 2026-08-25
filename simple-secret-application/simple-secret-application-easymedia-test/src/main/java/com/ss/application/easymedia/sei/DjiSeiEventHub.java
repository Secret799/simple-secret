package com.ss.application.easymedia.sei;

import com.ss.application.djisei.config.DjiSeiProperties;
import com.ss.application.djisei.diagnostic.DjiSeiEventListener;
import com.ss.application.djisei.diagnostic.PayloadPreview;
import com.ss.application.djisei.parser.SeiMessage;
import com.ss.application.djisei.parser.SeiParseIssue;
import com.ss.application.djisei.parser.SeiParseResult;
import com.ss.application.djisei.parser.VideoCodec;
import com.ss.easymedia.callback.TrackDelegateCallback.TackDelegateInfo;
import com.ss.zlm4j.domain.MediaSourceDomain;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 将原生帧线程产生的 SEI 解析结果异步广播给浏览器诊断页面。
 */
@Component
@ConditionalOnProperty(prefix = "simple-secret.dji-sei", name = "enabled", havingValue = "true")
public class DjiSeiEventHub implements DjiSeiEventListener {

    private static final int MAX_RECENT_EVENTS = 10;
    private static final int MAX_CLIENTS_PER_STREAM = 16;
    private static final int EVENT_QUEUE_CAPACITY = 1024;
    private static final long STATS_INTERVAL_MILLIS = 1000L;
    private static final long HEARTBEAT_SECONDS = 15L;

    private final Clock clock;
    private final DjiSeiProperties properties;
    private final Map<StreamKey, StreamState> streams = new ConcurrentHashMap<>();
    private final ThreadPoolExecutor dispatcher;
    private final ScheduledExecutorService heartbeatExecutor;

    public DjiSeiEventHub(Clock clock, DjiSeiProperties properties) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.dispatcher = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(EVENT_QUEUE_CAPACITY), runnable -> {
                    Thread thread = new Thread(runnable, "dji-sei-sse-dispatcher");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
        ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(1, runnable -> {
            Thread thread = new Thread(runnable, "dji-sei-sse-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.setRemoveOnCancelPolicy(true);
        this.heartbeatExecutor = scheduler;
        heartbeatExecutor.scheduleAtFixedRate(this::enqueueHeartbeat,
                HEARTBEAT_SECONDS, HEARTBEAT_SECONDS, TimeUnit.SECONDS);
    }

    @Override
    public void onStreamRegistered(MediaSourceDomain source) {
        StreamKey key = StreamKey.from(source);
        enqueue(key, () -> {
            StreamState state = state(key);
            state.reset(clock.instant());
            broadcast(state, "stream", state.nextId(), state.stats(key, clock.instant()));
        });
    }

    @Override
    public void onFrame(MediaSourceDomain source, TackDelegateInfo frame,
                        VideoCodec codec, SeiParseResult result) {
        StreamKey key = StreamKey.from(source);
        FrameUpdate update = FrameUpdate.from(frame, codec, result, properties.getMaxMessageLogs());
        enqueue(key, () -> acceptFrame(key, update));
    }

    @Override
    public void onStreamDeregistered(MediaSourceDomain source) {
        StreamKey key = StreamKey.from(source);
        enqueue(key, () -> {
            StreamState state = state(key);
            state.active = false;
            state.updatedAt = clock.instant();
            broadcast(state, "stream", state.nextId(), state.stats(key, state.updatedAt));
        });
    }

    /** 创建指定流的 SSE 订阅，并立即返回当前统计和最近事件。 */
    public SseEmitter subscribe(String app, String stream) {
        StreamKey key = new StreamKey(app, stream);
        StreamState state = state(key);
        if (state.clients.size() >= MAX_CLIENTS_PER_STREAM) {
            throw new IllegalStateException("SEI diagnostic client limit reached");
        }
        SseEmitter emitter = new SseEmitter(0L);
        state.clients.add(emitter);
        Runnable remove = () -> state.clients.remove(emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(ignored -> remove.run());
        try {
            emitter.send(SseEmitter.event().name("snapshot")
                    .data(new InitialSnapshot(state.stats(key, clock.instant()), state.recentSnapshot()),
                            MediaType.APPLICATION_JSON));
        } catch (IOException | RuntimeException exception) {
            remove.run();
            emitter.completeWithError(exception);
        }
        return emitter;
    }

    /** 返回测试和接口初始化共用的不可变快照。 */
    InitialSnapshot snapshot(String app, String stream) {
        StreamKey key = new StreamKey(app, stream);
        StreamState state = state(key);
        return new InitialSnapshot(state.stats(key, clock.instant()), state.recentSnapshot());
    }

    private void acceptFrame(StreamKey key, FrameUpdate update) {
        StreamState state = state(key);
        state.active = true;
        state.codec = update.codec;
        state.updatedAt = clock.instant();

        for (DiagnosticEvent event : update.events) {
            DiagnosticEvent identified = event.withIdentity(state.nextId(), state.updatedAt, key, update);
            state.addRecent(identified);
            broadcast(state, identified.kind, identified.id, identified);
        }
        state.videoFrames = saturatedAdd(state.videoFrames, 1);
        state.seiNalUnits = saturatedAdd(state.seiNalUnits, update.seiNalUnits);
        state.seiMessages = saturatedAdd(state.seiMessages, update.messageCount);
        state.parseIssues = saturatedAdd(state.parseIssues, update.issueCount);
        long now = state.updatedAt.toEpochMilli();
        if (!update.events.isEmpty() || now - state.lastStatsAt >= STATS_INTERVAL_MILLIS) {
            state.lastStatsAt = now;
            broadcast(state, "stats", state.nextId(), state.stats(key, state.updatedAt));
        }
    }

    private void enqueue(StreamKey key, Runnable operation) {
        try {
            dispatcher.execute(operation);
        } catch (RuntimeException exception) {
            state(key).droppedEvents.incrementAndGet();
        }
    }

    private void enqueueHeartbeat() {
        try {
            dispatcher.execute(() -> {
                Instant now = clock.instant();
                for (StreamState state : streams.values()) {
                    broadcast(state, "heartbeat", state.nextId(), Map.of("time", now.toString()));
                }
            });
        } catch (RuntimeException ignored) {
            // A full diagnostics queue must never affect media processing.
        }
    }

    private StreamState state(StreamKey key) {
        return streams.computeIfAbsent(key, ignored -> new StreamState(clock.instant()));
    }

    private void broadcast(StreamState state, String eventName, long id, Object data) {
        for (SseEmitter emitter : state.clients) {
            try {
                emitter.send(SseEmitter.event().id(Long.toString(id)).name(eventName)
                        .data(data, MediaType.APPLICATION_JSON));
            } catch (IOException | RuntimeException exception) {
                state.clients.remove(emitter);
                emitter.complete();
            }
        }
    }

    @PreDestroy
    public void close() {
        heartbeatExecutor.shutdownNow();
        dispatcher.shutdownNow();
        streams.values().forEach(state -> state.clients.forEach(SseEmitter::complete));
    }

    private static long saturatedAdd(long current, long increment) {
        return increment >= Long.MAX_VALUE - current ? Long.MAX_VALUE : current + increment;
    }

    record StreamKey(String app, String stream) {
        static StreamKey from(MediaSourceDomain source) {
            return new StreamKey(source.getApp(), source.getStream());
        }
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
                                  boolean truncated, String issueCode, String issueMessage) {

        static DiagnosticEvent message(SeiMessage message) {
            byte[] payload = message.payload();
            PayloadPreview hexPreview = PayloadPreview.from(payload, Math.max(1, payload.length));
            int textOffset = message.payloadType() == 5 && payload.length >= 16 ? 16 : 0;
            byte[] textPayload = Arrays.copyOfRange(payload, textOffset, payload.length);
            String text = new String(textPayload, StandardCharsets.UTF_8);
            return new DiagnosticEvent(0, "sei", null, null, null, null, null, null,
                    message.payloadType(), payload.length, message.uuid().orElse(null),
                    hexPreview.hex(), text, false, null, null);
        }

        static DiagnosticEvent issue(SeiParseIssue issue) {
            return new DiagnosticEvent(0, "issue", null, null, null, null, null, null,
                    null, null, null, null, null, false, issue.code(), issue.message());
        }

        DiagnosticEvent withIdentity(long eventId, Instant eventTime, StreamKey key, FrameUpdate frame) {
            return new DiagnosticEvent(eventId, kind, eventTime, key.app, key.stream, frame.codec,
                    frame.pts, frame.dts, payloadType, payloadBytes, uuid, hex, text,
                    truncated, issueCode, issueMessage);
        }
    }

    private record FrameUpdate(String codec, Long pts, Long dts, int seiNalUnits,
                               int messageCount, int issueCount, List<DiagnosticEvent> events) {
        static FrameUpdate from(TackDelegateInfo frame, VideoCodec codec, SeiParseResult result,
                                int maxMessageEvents) {
            List<DiagnosticEvent> events = new ArrayList<>();
            int messageLimit = Math.min(result.messages().size(), maxMessageEvents);
            for (int index = 0; index < messageLimit; index++) {
                events.add(DiagnosticEvent.message(result.messages().get(index)));
            }
            result.issues().forEach(issue -> events.add(DiagnosticEvent.issue(issue)));
            return new FrameUpdate(codec.name(), frame.getPts(), frame.getDts(),
                    result.seiNalUnitCount(), result.messages().size(), result.issues().size(), List.copyOf(events));
        }
    }

    private static final class StreamState {
        private final CopyOnWriteArrayList<SseEmitter> clients = new CopyOnWriteArrayList<>();
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
            return new Stats(key.app, key.stream, active, codec, videoFrames, seiNalUnits,
                    seiMessages, parseIssues, droppedEvents.get(), startedAt,
                    updatedAt == null ? now : updatedAt);
        }
    }
}
