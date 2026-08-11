package com.ss.security.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 验证 Security 配置默认值、不可变快照和路径校验。 */
class SecurityPropertiesTest {

    @Test
    void shouldExposeSecureDefaults() {
        SecurityProperties properties = new SecurityProperties();

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getPathPatterns()).containsExactly("/**");
        assertThat(properties.getExcludePathPatterns()).isEmpty();
        assertThat(properties.getOrder()).isZero();
    }

    @Test
    void shouldDefensivelyCopyConfiguredPatterns() {
        SecurityProperties properties = new SecurityProperties();
        List<String> includes = new ArrayList<>(List.of("/api/**"));
        List<String> excludes = new ArrayList<>(List.of("/api/public"));

        properties.setPathPatterns(includes);
        properties.setExcludePathPatterns(excludes);
        includes.add("/changed/**");
        excludes.clear();

        assertThat(properties.getPathPatterns()).containsExactly("/api/**");
        assertThat(properties.getExcludePathPatterns()).containsExactly("/api/public");
        assertThatThrownBy(() -> properties.getPathPatterns().add("/mutated/**"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldAllowExplicitEmptyPatternLists() {
        SecurityProperties properties = new SecurityProperties();

        properties.setPathPatterns(List.of());
        properties.setExcludePathPatterns(List.of());

        assertThat(properties.getPathPatterns()).isEmpty();
        assertThat(properties.getExcludePathPatterns()).isEmpty();
    }

    @Test
    void shouldRejectNullPatternLists() {
        SecurityProperties properties = new SecurityProperties();

        assertThatThrownBy(() -> properties.setPathPatterns(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("patterns");
        assertThatThrownBy(() -> properties.setExcludePathPatterns(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("patterns");
    }

    @ParameterizedTest
    @MethodSource("invalidPatterns")
    void shouldRejectInvalidPatternsWithoutEchoingTheirValue(List<String> patterns) {
        SecurityProperties properties = new SecurityProperties();

        assertThatThrownBy(() -> properties.setPathPatterns(patterns))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid security path pattern.");
        assertThatThrownBy(() -> properties.setExcludePathPatterns(patterns))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid security path pattern.");
    }

    private static Stream<List<String>> invalidPatterns() {
        return Stream.of(
                Collections.singletonList(null),
                List.of(""),
                List.of(" "),
                List.of(" /api/**"),
                List.of("/api/** "));
    }
}
