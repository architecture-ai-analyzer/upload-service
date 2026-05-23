package com.fiap.hackathon.upload_service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiap.hackathon.upload_service.infra.audit.AuditEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class GatewayTrustAuthenticationFilterTest {

    private GatewayTrustAuthenticationFilter filter;
    private GatewayTrustProperties properties;
    private AuditEventPublisher auditEventPublisher;
    private ObjectMapper objectMapper;
    private final String sharedSecret = "super-secret-key-for-hmac";
    private final String userId = "test-user-123";

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        properties = new GatewayTrustProperties();
        properties.setEnabled(true);
        properties.setSharedSecret(sharedSecret);
        properties.setUserHeader("X-Authenticated-User");
        properties.setScopesHeader("X-Authenticated-Scopes");
        properties.setSignatureHeader("X-Gateway-Signature");
        properties.setTimestampHeader("X-Gateway-Timestamp");
        properties.setMaxSkewSeconds(300);

        auditEventPublisher = mock(AuditEventPublisher.class);
        objectMapper = new ObjectMapper();
        filter = new GatewayTrustAuthenticationFilter(properties, objectMapper, auditEventPublisher);
    }

    @Test
    void shouldReturn401_whenUserHeaderMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/uploads");
        request.setServletPath("/v1/uploads");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("MISSING_IDENTITY_CONTEXT");
        verify(auditEventPublisher).publishEvent(
            eq(AuditEventPublisher.EventType.AUTHENTICATION_FAILURE),
            eq(null),
            any(),
            any(),
            eq(AuditEventPublisher.ActionResult.DENIED),
            any()
        );
    }

    @Test
    void shouldReturn401_whenSignatureHeaderMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/uploads");
        request.setServletPath("/v1/uploads");
        request.addHeader("X-Authenticated-User", userId);
        request.addHeader("X-Authenticated-Scopes", "upload:write");
        request.addHeader("X-Gateway-Timestamp", String.valueOf(Instant.now().getEpochSecond()));
        // Missing X-Gateway-Signature header

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("MISSING_GATEWAY_SIGNATURE");
        verify(auditEventPublisher).publishEvent(
            eq(AuditEventPublisher.EventType.INVALID_SIGNATURE),
            eq(userId),
            any(),
            any(),
            eq(AuditEventPublisher.ActionResult.DENIED),
            any()
        );
    }

    @Test
    void shouldReturn401_whenTimestampStale() throws Exception {
        long staleTimestamp = Instant.now().getEpochSecond() - 400; // 400 seconds old (max is 300)
        String signature = generateSignature("POST", "/v1/uploads", staleTimestamp, userId, "upload:write");

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/uploads");
        request.setServletPath("/v1/uploads");
        request.addHeader("X-Authenticated-User", userId);
        request.addHeader("X-Authenticated-Scopes", "upload:write");
        request.addHeader("X-Gateway-Timestamp", String.valueOf(staleTimestamp));
        request.addHeader("X-Gateway-Signature", signature);

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("STALE_GATEWAY_SIGNATURE");
        verify(auditEventPublisher).publishEvent(
            eq(AuditEventPublisher.EventType.INVALID_TIMESTAMP),
            eq(userId),
            any(),
            any(),
            eq(AuditEventPublisher.ActionResult.DENIED),
            any()
        );
    }

    @Test
    void shouldReturn401_whenSignatureInvalid() throws Exception {
        long timestamp = Instant.now().getEpochSecond();
        String invalidSignature = "invalid-signature-data";

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/uploads");
        request.setServletPath("/v1/uploads");
        request.addHeader("X-Authenticated-User", userId);
        request.addHeader("X-Authenticated-Scopes", "upload:write");
        request.addHeader("X-Gateway-Timestamp", String.valueOf(timestamp));
        request.addHeader("X-Gateway-Signature", invalidSignature);

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("INVALID_GATEWAY_SIGNATURE");
        verify(auditEventPublisher).publishEvent(
            eq(AuditEventPublisher.EventType.INVALID_SIGNATURE),
            eq(userId),
            any(),
            any(),
            eq(AuditEventPublisher.ActionResult.DENIED),
            any()
        );
    }

    @Test
    void shouldReturnAuthenticated_whenValidSignatureAndScope() throws Exception {
        long timestamp = Instant.now().getEpochSecond();
        String signature = generateSignature("POST", "/v1/uploads", timestamp, userId, "upload:write");

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/uploads");
        request.setServletPath("/v1/uploads");
        request.addHeader("X-Authenticated-User", userId);
        request.addHeader("X-Authenticated-Scopes", "upload:write");
        request.addHeader("X-Gateway-Timestamp", String.valueOf(timestamp));
        request.addHeader("X-Gateway-Signature", signature);

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();
        filter.doFilter(request, response, filterChain);

        // Should not send error response and should continue filter chain
        assertThat(response.getStatus()).isNotEqualTo(401);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo(userId);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
            .anyMatch(auth -> auth.getAuthority().equals("SCOPE_upload:write"));

        verify(auditEventPublisher).publishEvent(
            eq(AuditEventPublisher.EventType.AUTHENTICATION_SUCCESS),
            eq(userId),
            any(),
            any(),
            eq(AuditEventPublisher.ActionResult.SUCCESS),
            any()
        );
    }

    @Test
    void shouldReturnAuthenticated_withMultipleScopes() throws Exception {
        long timestamp = Instant.now().getEpochSecond();
        String multipleScopes = "upload:read,upload:write,audit:read";
        String signature = generateSignature("POST", "/v1/uploads", timestamp, userId, multipleScopes);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/uploads");
        request.setServletPath("/v1/uploads");
        request.addHeader("X-Authenticated-User", userId);
        request.addHeader("X-Authenticated-Scopes", multipleScopes);
        request.addHeader("X-Gateway-Timestamp", String.valueOf(timestamp));
        request.addHeader("X-Gateway-Signature", signature);

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
            .hasSize(3)
            .anyMatch(auth -> auth.getAuthority().equals("SCOPE_upload:read"))
            .anyMatch(auth -> auth.getAuthority().equals("SCOPE_upload:write"))
            .anyMatch(auth -> auth.getAuthority().equals("SCOPE_audit:read"));
    }

    @Test
    void shouldReturnAuthenticated_withXForwardedForHeader() throws Exception {
        long timestamp = Instant.now().getEpochSecond();
        String signature = generateSignature("GET", "/v1/uploads/123", timestamp, userId, "upload:read");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/uploads/123");
        request.setServletPath("/v1/uploads/123");
        request.addHeader("X-Authenticated-User", userId);
        request.addHeader("X-Authenticated-Scopes", "upload:read");
        request.addHeader("X-Forwarded-For", "203.0.113.5, 198.51.100.2");
        request.addHeader("X-Gateway-Timestamp", String.valueOf(timestamp));
        request.addHeader("X-Gateway-Signature", signature);

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        verify(auditEventPublisher).publishEvent(
            eq(AuditEventPublisher.EventType.AUTHENTICATION_SUCCESS),
            eq(userId),
            eq("203.0.113.5"),
            any(),
            eq(AuditEventPublisher.ActionResult.SUCCESS),
            any()
        );
    }

    /**
     * Generates a valid HMAC-SHA256 signature matching the filter's algorithm
     */
    private String generateSignature(String method, String path, long timestamp, String user, String scopes) {
        try {
            String canonical = method + "\n"
                    + path + "\n"
                    + timestamp + "\n"
                    + user + "\n"
                    + scopes;

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(sharedSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate signature", e);
        }
    }
}
