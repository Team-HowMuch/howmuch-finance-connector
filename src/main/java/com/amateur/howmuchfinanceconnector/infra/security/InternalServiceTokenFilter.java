package com.amateur.howmuchfinanceconnector.infra.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Set;

public final class InternalServiceTokenFilter extends OncePerRequestFilter {

    private static final Set<String> LOOPBACK_ADDRESSES = Set.of("127.0.0.1", "::1", "0:0:0:0:0:0:0:1");
    private final byte[] expectedToken;

    public InternalServiceTokenFilter(String expectedToken) {
        if (expectedToken == null || expectedToken.isBlank()) {
            throw new IllegalArgumentException("CONNECTOR_INTERNAL_SERVICE_TOKEN must not be blank");
        }
        this.expectedToken = expectedToken.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (!LOOPBACK_ADDRESSES.contains(request.getRemoteAddr())) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        String authorization = request.getHeader("Authorization");
        byte[] actual = authorization != null && authorization.startsWith("Bearer ")
                ? authorization.substring(7).getBytes(StandardCharsets.UTF_8)
                : new byte[0];
        if (!MessageDigest.isEqual(expectedToken, actual)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("backend-v2", "", List.of()));
        filterChain.doFilter(request, response);
    }
}
