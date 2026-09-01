package com.ss.dji.camera.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DjiSeiSseControllerTest {

    @Test
    void shouldReturnUnbufferedDeviceSubscription() {
        DjiSeiSseService service = mock(DjiSeiSseService.class);
        SseEmitter emitter = mock(SseEmitter.class);
        when(service.subscribeDevice("dock-01")).thenReturn(emitter);

        ResponseEntity<SseEmitter> response = new DjiSeiSseController(service).deviceEvents("dock-01");

        assertThat(response.getBody()).isSameAs(emitter);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
        assertThat(response.getHeaders().getFirst("X-Accel-Buffering")).isEqualTo("no");
        verify(service).subscribeDevice("dock-01");
    }

    @Test
    void shouldReturnServiceUnavailableAtSubscriberLimit() {
        DjiSeiSseService service = mock(DjiSeiSseService.class);
        doThrow(new IllegalStateException("subscriber limit"))
                .when(service).subscribeDevice("dock-01");

        assertThatThrownBy(() -> new DjiSeiSseController(service).deviceEvents("dock-01"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void shouldExposeLegacyDiagnosticsSubscription() {
        DjiSeiSseService service = mock(DjiSeiSseService.class);
        SseEmitter emitter = mock(SseEmitter.class);
        when(service.subscribeDiagnostics("live", "dock-01")).thenReturn(emitter);

        ResponseEntity<SseEmitter> response =
                new DjiSeiSseController(service).diagnosticEvents("live", "dock-01");

        assertThat(response.getBody()).isSameAs(emitter);
        assertThat(response.getHeaders().getFirst("X-Accel-Buffering")).isEqualTo("no");
        verify(service).subscribeDiagnostics("live", "dock-01");
    }

    @Test
    void shouldMapEasyMediaDiagnosticsRoute() throws Exception {
        DjiSeiSseService service = mock(DjiSeiSseService.class);
        SseEmitter emitter = new SseEmitter();
        emitter.send(SseEmitter.event().name("snapshot").data("{}"));
        when(service.subscribeDiagnostics("live", "dock-01")).thenReturn(emitter);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new DjiSeiSseController(service)).build();

        mockMvc.perform(get("/easyMedia/api/sei/events")
                        .param("app", "live")
                        .param("stream", "dock-01"))
                .andExpect(request().asyncStarted())
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/event-stream"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string("X-Accel-Buffering", "no"));

        emitter.complete();
        verify(service).subscribeDiagnostics("live", "dock-01");
    }
}
