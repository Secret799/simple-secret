package com.ss.application.easymedia.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class TestTokenManagementAuthorizerTest {

    private final TestTokenManagementAuthorizer authorizer =
            new TestTokenManagementAuthorizer("local-test-token");

    @Test
    void rejectsBlankConfiguredToken() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TestTokenManagementAuthorizer(" "));
    }

    @Test
    void rejectsRequestWithoutToken() {
        assertThat(authorizer.isAuthorized(new MockHttpServletRequest())).isFalse();
    }

    @Test
    void rejectsRequestWithWrongToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TestTokenManagementAuthorizer.TOKEN_HEADER, "wrong-token");

        assertThat(authorizer.isAuthorized(request)).isFalse();
    }

    @Test
    void acceptsRequestWithMatchingToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TestTokenManagementAuthorizer.TOKEN_HEADER, "local-test-token");

        assertThat(authorizer.isAuthorized(request)).isTrue();
    }
}
