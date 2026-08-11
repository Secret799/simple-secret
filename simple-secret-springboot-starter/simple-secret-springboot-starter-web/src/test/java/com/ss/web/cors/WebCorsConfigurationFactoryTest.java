package com.ss.web.cors;

import com.ss.web.config.WebProperties;
import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/** 验证 CORS 配置的归一化与安全约束。 */
class WebCorsConfigurationFactoryTest {

    @Test
    void shouldCreateNormalizedConfigurationWithDefaults() {
        WebProperties.Cors properties = corsWithOrigin(" https://app.example.com ");

        CorsConfiguration configuration = WebCorsConfigurationFactory.create(properties);

        assertThat(configuration.getAllowedOrigins()).containsExactly("https://app.example.com");
        assertThat(configuration.getAllowedOriginPatterns()).isEmpty();
        assertThat(configuration.getAllowedMethods()).containsExactly("GET", "HEAD", "POST");
        assertThat(configuration.getAllowedHeaders()).isEmpty();
        assertThat(configuration.getExposedHeaders()).isEmpty();
        assertThat(configuration.getAllowCredentials()).isFalse();
        assertThat(configuration.getMaxAge()).isEqualTo(Duration.ofMinutes(30).toSeconds());
    }

    @Test
    void shouldTrimAllConfiguredLists() {
        WebProperties.Cors properties = corsWithOrigin(" https://app.example.com ");
        properties.setAllowedOriginPatterns(List.of(" https://*.example.com "));
        properties.setAllowedMethods(List.of(" GET ", " PATCH "));
        properties.setAllowedHeaders(List.of(" Authorization "));
        properties.setExposedHeaders(List.of(" X-Request-Id "));

        CorsConfiguration configuration = WebCorsConfigurationFactory.create(properties);

        assertThat(configuration.getAllowedOrigins()).containsExactly("https://app.example.com");
        assertThat(configuration.getAllowedOriginPatterns()).containsExactly("https://*.example.com");
        assertThat(configuration.getAllowedMethods()).containsExactly("GET", "PATCH");
        assertThat(configuration.getAllowedHeaders()).containsExactly("Authorization");
        assertThat(configuration.getExposedHeaders()).containsExactly("X-Request-Id");
    }

    @Test
    void shouldRejectMissingAllowedOrigins() {
        WebProperties.Cors properties = new WebProperties.Cors();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> WebCorsConfigurationFactory.create(properties))
                .withMessageContaining("allowed origins");
    }

    @Test
    void shouldRejectBlankConfiguredEntriesWithoutEchoingValue() {
        WebProperties.Cors properties = corsWithOrigin("https://app.example.com");
        properties.setAllowedHeaders(List.of("   "));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> WebCorsConfigurationFactory.create(properties))
                .withMessageContaining("allowed headers")
                .withMessageNotContaining("   ");
    }

    @Test
    void shouldRejectWildcardAllowedOriginWhenCredentialsAreEnabledWithoutEchoingValue() {
        WebProperties.Cors properties = corsWithOrigin("*");
        properties.setAllowCredentials(true);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> WebCorsConfigurationFactory.create(properties))
                .withMessageContaining("allowed origins")
                .withMessageNotContaining("*");
    }

    @Test
    void shouldRejectWildcardAllowedOriginPatternWhenCredentialsAreEnabledWithoutEchoingValue() {
        WebProperties.Cors properties = new WebProperties.Cors();
        properties.setAllowedOriginPatterns(List.of("https://*.example.com"));
        properties.setAllowCredentials(true);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> WebCorsConfigurationFactory.create(properties))
                .withMessageContaining("allowed origin patterns")
                .withMessageNotContaining("*");
    }

    @Test
    void shouldRejectBlankPathAndNegativeMaxAge() {
        WebProperties.Cors blankPath = corsWithOrigin("https://app.example.com");
        blankPath.setPath(" ");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> WebCorsConfigurationFactory.create(blankPath))
                .withMessageContaining("path");

        WebProperties.Cors negativeMaxAge = corsWithOrigin("https://app.example.com");
        negativeMaxAge.setMaxAge(Duration.ofSeconds(-1));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> WebCorsConfigurationFactory.create(negativeMaxAge))
                .withMessageContaining("max age");
    }

    @Test
    void shouldRejectNullMaxAgeWithoutEchoingConfigurationValue() {
        WebProperties.Cors properties = corsWithOrigin("https://app.example.com");
        properties.setMaxAge(null);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> WebCorsConfigurationFactory.create(properties))
                .withMessageContaining("max age")
                .withMessageNotContaining("null");
    }

    private WebProperties.Cors corsWithOrigin(String origin) {
        WebProperties.Cors properties = new WebProperties.Cors();
        properties.setAllowedOrigins(List.of(origin));
        return properties;
    }
}
