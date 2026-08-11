package com.ss.doc.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证 doc starter 对第三方消费者发布的最小依赖边界。 */
class DocPublishedDependencyPolicyTest {

    @Test
    void shouldPublishOnlyRequiredDocFrameworkDependencies() throws Exception {
        String pom = Files.readString(Path.of("pom.xml"));

        assertThat(pom)
                .contains("springdoc-openapi-starter-webmvc-api")
                .doesNotContain("knife4j")
                .doesNotContain("therapi-runtime-javadoc")
                .doesNotContain("jackson-module-kotlin")
                .doesNotContain("simple-secret-common-toolbox")
                .doesNotContain("simple-secret-springboot-starter-json")
                .doesNotContain("simple-secret-springboot-starter-core")
                .doesNotContain("lombok")
                .doesNotContain("com.secret");
    }
}
