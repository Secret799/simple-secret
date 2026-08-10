package com.ss.nats.subject;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NatsSubjectsTest {

    @Test
    void shouldAcceptPublishAndSubscriptionSubjects() {
        assertThatCode(() -> NatsSubjects.validatePublishSubject("devices.edge.state"))
                .doesNotThrowAnyException();
        assertThatCode(() -> NatsSubjects.validateSubscriptionSubject("devices.*.state"))
                .doesNotThrowAnyException();
        assertThatCode(() -> NatsSubjects.validateSubscriptionSubject("devices.>"))
                .doesNotThrowAnyException();
        assertThatCode(() -> NatsSubjects.validateQueue("telemetry-workers"))
                .doesNotThrowAnyException();
        assertThatCode(() -> NatsSubjects.validateQueue(""))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectMalformedSubjectsAndQueues() {
        assertThatThrownBy(() -> NatsSubjects.validatePublishSubject("devices.*.state"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NatsSubjects.validateSubscriptionSubject("devices.>.state"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NatsSubjects.validateSubscriptionSubject("devices..state"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NatsSubjects.validateSubscriptionSubject("devices state"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NatsSubjects.validateQueue("workers.*"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
