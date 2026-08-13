package com.ss.application.pushstream.config;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 推流配置约束测试。
 *
 * @author junpzx
 * @since 2026-08-12
 */
class PublishStreamPropertiesTest {

    @Test
    void shouldReportNullScanDirectoryAsConstraintViolation() {
        PublishStreamProperties properties = new PublishStreamProperties();
        properties.setEnabled(true);
        properties.setScanDirectory(null);

        Set<ConstraintViolation<PublishStreamProperties>> violations = validate(properties);

        assertThat(violations).extracting(violation -> violation.getPropertyPath().toString())
                .contains("scanDirectory");
    }

    @Test
    void shouldRejectNullAllowedSuffixElement() {
        PublishStreamProperties properties = new PublishStreamProperties();
        Set<String> suffixes = new LinkedHashSet<>();
        suffixes.add("mp4");
        suffixes.add(null);
        properties.setAllowedSuffixes(suffixes);

        Set<ConstraintViolation<PublishStreamProperties>> violations = validate(properties);

        assertThat(violations).extracting(violation -> violation.getPropertyPath().toString())
                .contains("allowedSuffixes[].<iterable element>");
    }

    private Set<ConstraintViolation<PublishStreamProperties>> validate(PublishStreamProperties properties) {
        try (ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = validatorFactory.getValidator();
            return validator.validate(properties);
        }
    }
}
