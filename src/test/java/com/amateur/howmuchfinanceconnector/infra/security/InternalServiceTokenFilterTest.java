package com.amateur.howmuchfinanceconnector.infra.security;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class InternalServiceTokenFilterTest {

    private final InternalServiceTokenFilter filter = new InternalServiceTokenFilter("expected-token");

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void requiresLoopbackAndMatchingBearerToken() throws ServletException, IOException {
        assertThat(invoke("10.0.0.5", "Bearer expected-token").getStatus()).isEqualTo(403);
        assertThat(invoke("127.0.0.1", "Bearer wrong-token").getStatus()).isEqualTo(401);

        MockHttpServletResponse accepted = invoke("127.0.0.1", "Bearer expected-token");

        assertThat(accepted.getStatus()).isEqualTo(200);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    private MockHttpServletResponse invoke(String remoteAddress, String authorization)
            throws ServletException, IOException {
        var request = new MockHttpServletRequest("PUT", "/internal/v1/connections/test");
        request.setRemoteAddr(remoteAddress);
        request.addHeader("Authorization", authorization);
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
