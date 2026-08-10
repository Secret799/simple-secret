package com.ss.application.easymedia.config;

import com.ss.easymedia.security.EasyMediaManagementAuthorizer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class EasyMediaTestSecurityConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(EasyMediaTestSecurityConfiguration.class);

    @Test
    void doesNotCreateAuthorizerWhenManagementApiIsDisabled() {
        contextRunner.run(context ->
                assertThat(context).doesNotHaveBean(EasyMediaManagementAuthorizer.class));
    }

    @Test
    void createsTokenAuthorizerWhenManagementApiIsEnabled() {
        contextRunner
                .withPropertyValues(
                        "simple-secret.easymedia.management-api-enabled=true",
                        "simple-secret.test.easymedia.management-token=local-test-token")
                .run(context -> assertThat(context)
                        .hasSingleBean(EasyMediaManagementAuthorizer.class)
                        .getBean(EasyMediaManagementAuthorizer.class)
                        .isInstanceOf(TestTokenManagementAuthorizer.class));
    }
}
