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
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class UploadRateLimitFilter extends OncePerRequestFilter {

    private static final String RATE_LIMIT_PATH = "/v1/uploads";

    private final UploadRateLimitProperties properties;
    private final ObjectMapper objectMapper;
    private final AuditEventPublisher auditEventPublisher;
    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();

    public UploadRateLimitFilter(UploadRateLimitProperties properties, ObjectMapper objectMapper, AuditEventPublisher auditEventPublisher) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.auditEventPublisher = auditEventPublisher;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.isEnabled()
                || !HttpMethod.POST.matches(request.getMethod())
                || !RATE_LIMIT_PATH.equals(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        long now = Instant.now().getEpochSecond();
        long window = Math.max(1, properties.getWindowSeconds());
        int maxRequests = Math.max(1, properties.getMaxRequests());

        String key = resolveClientKey(request);
        String clientIp = getClientIp(request);
        String user = request.getHeader("X-Authenticated-User");

        WindowCounter counter = counters.computeIfAbsent(key, k -> new WindowCounter(now));

        synchronized (counter) {
            long elapsed = now - counter.windowStart.get();
            if (elapsed >= window) {
                counter.windowStart.set(now);
                counter.count.set(0);
            }

            int current = counter.count.incrementAndGet();
            if (current > maxRequests) {
                auditEventPublisher.publishEvent(
                    AuditEventPublisher.EventType.RATE_LIMIT_EXCEEDED,
                    user,
                    clientIp,
                    "POST " + request.getRequestURI(),
                    AuditEventPublisher.ActionResult.DENIED,
                    "Rate limit exceeded: " + maxRequests + " requests per " + window + " seconds"
                );
                writeTooManyRequests(response, maxRequests, window);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private static final class WindowCounter {
        private final AtomicLong windowStart;
        private final AtomicInteger count;

        private WindowCounter(long now) {
            this.windowStart = new AtomicLong(now);
            this.count = new AtomicInteger(0);
        }
    }

    private String resolveClientKey(HttpServletRequest request) {
        String user = request.getHeader("X-Authenticated-User");
        if (user != null && !user.isBlank()) {
            return "user:" + user;
        }

        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            int idx = forwardedFor.indexOf(',');
            String ip = idx >= 0 ? forwardedFor.substring(0, idx) : forwardedFor;
            return "ip:" + ip.trim();
        }

        return "ip:" + request.getRemoteAddr();
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void writeTooManyRequests(HttpServletResponse response, int limit, long windowSeconds) throws IOException {
        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", String.valueOf(windowSeconds));
        response.getWriter().write(objectMapper.writeValueAsString(
                new ApiErrorResponse(
                        "Too many requests",
                        "RATE_LIMIT_EXCEEDED",
                        "Upload rate limit exceeded: " + limit + " requests per " + windowSeconds + " seconds"
                )
        ));
    }
}
