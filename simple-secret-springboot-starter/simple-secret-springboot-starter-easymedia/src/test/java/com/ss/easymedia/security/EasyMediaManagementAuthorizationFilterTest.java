package com.ss.easymedia.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class EasyMediaManagementAuthorizationFilterTest {

    @Test
    void shouldRejectManagementRequestByDefault() throws Exception {
        EasyMediaManagementAuthorizationFilter filter =
                new EasyMediaManagementAuthorizationFilter(request -> false);
        MockHttpServletRequest request = new MockHttpServletRequest("POST",
                "/easyMedia/api/common/restartServer");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void shouldContinueWhenCustomAuthorizerAllowsRequest() throws Exception {
        EasyMediaManagementAuthorizationFilter filter =
                new EasyMediaManagementAuthorizationFilter(request -> true);
        MockHttpServletRequest request = new MockHttpServletRequest("POST",
                "/easyMedia/api/common/restartServer");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isSameAs(request);
    }
}
