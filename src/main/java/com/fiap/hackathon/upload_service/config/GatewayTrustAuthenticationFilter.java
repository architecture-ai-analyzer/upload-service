package com.fiap.hackathon.upload_service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiap.hackathon.upload_service.adapter.dto.ApiErrorResponse;
import com.fiap.hackathon.upload_service.infra.audit.AuditEventPublisher;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Component
public class GatewayTrustAuthenticationFilter extends OncePerRequestFilter {

    private final GatewayTrustProperties properties;
    private final ObjectMapper objectMapper;
    private final AuditEventPublisher auditEventPublisher;

    public GatewayTrustAuthenticationFilter(GatewayTrustProperties properties, ObjectMapper objectMapper, AuditEventPublisher auditEventPublisher) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.auditEventPublisher = auditEventPublisher;
    }

    @PostConstruct
    public void validateConfiguration() {
        if (properties.isEnabled() && (properties.getSharedSecret() == null || properties.getSharedSecret().isBlank())) {
            throw new IllegalStateException(
                "Gateway trust authentication is enabled but shared-secret is not configured. " +
                "Set 'app.security.gateway-trust.shared-secret' environment variable or property."
            );
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return HttpMethod.OPTIONS.matches(request.getMethod())
                || path.startsWith("/actuator/health")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String user = headerValue(request, properties.getUserHeader());
        String scopes = headerValue(request, properties.getScopesHeader());
        String clientIp = getClientIp(request);

        if (user == null || user.isBlank()) {
            auditEventPublisher.publishEvent(
                AuditEventPublisher.EventType.AUTHENTICATION_FAILURE,
                null,
                clientIp,
                request.getMethod() + " " + request.getRequestURI(),
                AuditEventPublisher.ActionResult.DENIED,
                "Missing authenticated user context from API Gateway authorizer"
            );
            writeUnauthorized(response, "MISSING_IDENTITY_CONTEXT", "Missing authenticated user context from API Gateway authorizer");
            return;
        }

        // Shared-secret is always present when gateway-trust is enabled (validated in @PostConstruct)
        String timestamp = headerValue(request, properties.getTimestampHeader());
        String signature = headerValue(request, properties.getSignatureHeader());

        if (timestamp == null || signature == null) {
            auditEventPublisher.publishEvent(
                AuditEventPublisher.EventType.INVALID_SIGNATURE,
                user,
                clientIp,
                request.getMethod() + " " + request.getRequestURI(),
                AuditEventPublisher.ActionResult.DENIED,
                "Missing gateway signature headers"
            );
            writeUnauthorized(response, "MISSING_GATEWAY_SIGNATURE", "Missing gateway signature headers");
            return;
        }

        if (!isFreshTimestamp(timestamp)) {
            auditEventPublisher.publishEvent(
                AuditEventPublisher.EventType.INVALID_TIMESTAMP,
                user,
                clientIp,
                request.getMethod() + " " + request.getRequestURI(),
                AuditEventPublisher.ActionResult.DENIED,
                "Gateway signature timestamp is outside the accepted time window"
            );
            writeUnauthorized(response, "STALE_GATEWAY_SIGNATURE", "Gateway signature timestamp is outside the accepted time window");
            return;
        }

        String expected = signCanonicalRequest(request, user, scopes, timestamp, properties.getSharedSecret());
        if (!Objects.equals(expected, signature)) {
            auditEventPublisher.publishEvent(
                AuditEventPublisher.EventType.INVALID_SIGNATURE,
                user,
                clientIp,
                request.getMethod() + " " + request.getRequestURI(),
                AuditEventPublisher.ActionResult.DENIED,
                "Gateway signature validation failed"
            );
            writeUnauthorized(response, "INVALID_GATEWAY_SIGNATURE", "Gateway signature validation failed");
            return;
        }

        String rawScopes = scopes == null ? "" : scopes;
        List<SimpleGrantedAuthority> authorities = Arrays.stream(rawScopes.split("[,\\s]+"))
                .filter(s -> !s.isBlank())
                .map(s -> new SimpleGrantedAuthority("SCOPE_" + s.toLowerCase(Locale.ROOT)))
                .toList();

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(user, "N/A", authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        auditEventPublisher.publishEvent(
            AuditEventPublisher.EventType.AUTHENTICATION_SUCCESS,
            user,
            clientIp,
            request.getMethod() + " " + request.getRequestURI(),
            AuditEventPublisher.ActionResult.SUCCESS,
            "Scopes: " + rawScopes
        );

        filterChain.doFilter(request, response);
    }

    private String headerValue(HttpServletRequest request, String name) {
        return request.getHeader(name);
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private boolean isFreshTimestamp(String timestampHeader) {
        try {
            long timestamp = Long.parseLong(timestampHeader);
            long now = Instant.now().getEpochSecond();
            long delta = Math.abs(now - timestamp);
            return delta <= properties.getMaxSkewSeconds();
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private String signCanonicalRequest(
            HttpServletRequest request,
            String user,
            String scopes,
            String timestamp,
            String secret
    ) {
        try {
            String canonical = request.getMethod() + "\n"
                    + request.getRequestURI() + "\n"
                    + timestamp + "\n"
                    + user + "\n"
                    + (scopes == null ? "" : scopes);

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (Exception ex) {
            return "";
        }
    }

    private void writeUnauthorized(HttpServletResponse response, String code, String detail) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(
                new ApiErrorResponse("Unauthorized request", code, detail)
        ));
    }
}
