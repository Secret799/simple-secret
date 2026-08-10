package com.ss.easymedia.webrtc.service;

import com.ss.easymedia.config.properties.WebRtcProperties;
import com.ss.easymedia.webrtc.repository.WebRtcSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebRtcSessionCleanupJobTest {

    @Mock
    private WebRtcSessionRepository repository;
    @Mock
    private WebRtcSessionService service;

    @Test
    void shouldProcessDueSessionsInConfiguredBatchOrder() {
        WebRtcProperties properties = new WebRtcProperties();
        properties.setCleanupBatchSize(2);
        Clock clock = Clock.fixed(Instant.ofEpochMilli(1000), ZoneOffset.UTC);
        when(repository.pollClosingRetries(clock.instant(), 2))
                .thenReturn(List.of("sid-1", "sid-2"));
        WebRtcSessionCleanupJob job = new WebRtcSessionCleanupJob(
                repository, service, properties, clock);

        job.retryClosingSessions();

        InOrder order = inOrder(service);
        order.verify(service).retryDelete("sid-1");
        order.verify(service).retryDelete("sid-2");
    }

    @Test
    void shouldContinueAfterOneRetryFails() {
        WebRtcProperties properties = new WebRtcProperties();
        Clock clock = Clock.fixed(Instant.ofEpochMilli(1000), ZoneOffset.UTC);
        when(repository.pollClosingRetries(clock.instant(), properties.getCleanupBatchSize()))
                .thenReturn(List.of("sid-1", "sid-2"));
        doThrow(new IllegalStateException("expected")).when(service).retryDelete("sid-1");
        WebRtcSessionCleanupJob job = new WebRtcSessionCleanupJob(
                repository, service, properties, clock);

        job.retryClosingSessions();

        verify(service).retryDelete("sid-2");
    }
}
