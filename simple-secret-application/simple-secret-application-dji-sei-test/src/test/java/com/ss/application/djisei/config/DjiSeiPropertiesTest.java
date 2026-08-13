package com.ss.application.djisei.config;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DJI SEI 诊断配置校验测试。
 *
 * @author junpzx
 * @since 2026-08-13
 */
class DjiSeiPropertiesTest {

    /** Jakarta Validation 工厂。 */
    private static final ValidatorFactory VALIDATOR_FACTORY = Validation.buildDefaultValidatorFactory();

    /** 配置校验器。 */
    private static final Validator VALIDATOR = VALIDATOR_FACTORY.getValidator();

    @AfterAll
    static void closeValidatorFactory() {
        VALIDATOR_FACTORY.close();
    }

    @Test
    void shouldProvideBoundedDiagnosticDefaults() {
        DjiSeiProperties properties = new DjiSeiProperties();

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getAllowedApp()).isEqualTo("live");
        assertThat(properties.getMaxFrameBytes()).isEqualTo(8388608);
        assertThat(properties.getMaxPayloadBytes()).isEqualTo(1048576);
        assertThat(properties.getPreviewBytes()).isEqualTo(64);
        assertThat(properties.getSummaryInterval()).isEqualTo(Duration.ofSeconds(30));
        assertThat(VALIDATOR.validate(properties)).isEmpty();
    }

    @Test
    void shouldRejectNonPositiveDirectLimitsAndBlankApp() {
        DjiSeiProperties properties = new DjiSeiProperties();
        properties.setAllowedApp(" ");
        properties.setMaxFrameBytes(0);
        properties.setMaxPayloadBytes(0);
        properties.setPreviewBytes(0);

        assertThat(VALIDATOR.validate(properties))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("allowedApp", "maxFrameBytes", "maxPayloadBytes", "previewBytes");
    }

    @Test
    void shouldRejectPayloadLimitGreaterThanFrameLimit() {
        DjiSeiProperties properties = new DjiSeiProperties();
        properties.setMaxFrameBytes(100);
        properties.setMaxPayloadBytes(101);

        assertThat(VALIDATOR.validate(properties))
                .extracting(violation -> violation.getMessage())
                .contains("max-payload-bytes must not exceed max-frame-bytes");
    }

    @Test
    void shouldAcceptInclusiveSummaryIntervalLimits() {
        DjiSeiProperties properties = new DjiSeiProperties();
        properties.setSummaryInterval(Duration.ofSeconds(1));

        assertThat(VALIDATOR.validate(properties)).isEmpty();

        properties.setSummaryInterval(Duration.ofHours(1));
        assertThat(VALIDATOR.validate(properties)).isEmpty();
    }

    @Test
    void shouldRejectSummaryIntervalOutsideAllowedRange() {
        DjiSeiProperties properties = new DjiSeiProperties();
        properties.setSummaryInterval(Duration.ofSeconds(1).minusNanos(1));

        assertThat(VALIDATOR.validate(properties))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("summaryIntervalWithinRange");

        properties.setSummaryInterval(Duration.ofHours(1).plusNanos(1));
        assertThat(VALIDATOR.validate(properties))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("summaryIntervalWithinRange");
    }
}
