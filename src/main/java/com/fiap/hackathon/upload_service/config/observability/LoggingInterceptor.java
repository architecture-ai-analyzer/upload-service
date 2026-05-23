package com.fiap.hackathon.upload_service.config.observability;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

@Component
public class LoggingInterceptor implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(LoggingInterceptor.class);
    private static final String START_TIME_ATTRIBUTE = "startTime";
    private static final String REQUEST_ID_ATTRIBUTE = "requestId";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        long startTime = System.currentTimeMillis();
        String requestId = UUID.randomUUID().toString();

        request.setAttribute(START_TIME_ATTRIBUTE, startTime);
        request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);

        MDC.put("request.id", requestId);
        MDC.put("http.method", request.getMethod());
        MDC.put("http.path", request.getRequestURI());
        MDC.put("http.query_string", request.getQueryString() != null ? request.getQueryString() : "");
        MDC.put("http.remote_addr", request.getRemoteAddr());

        logger.info("HTTP request received: {} {}", request.getMethod(), request.getRequestURI());

        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception ex) {
        Long startTime = (Long) request.getAttribute(START_TIME_ATTRIBUTE);

        if (startTime != null) {
            long duration = System.currentTimeMillis() - startTime;

            MDC.put("http.status_code", String.valueOf(response.getStatus()));
            MDC.put("http.duration_ms", String.valueOf(duration));

            if (ex != null) {
                MDC.put("error", "true");
                MDC.put("error.type", ex.getClass().getName());
                MDC.put("error.message", ex.getMessage() != null ? ex.getMessage() : "Unknown error");
                logger.error(
                        "HTTP request completed with error: {} {} - Status: {} - Duration: {}ms",
                        request.getMethod(),
                        request.getRequestURI(),
                        response.getStatus(),
                        duration,
                        ex);
            } else {
                logger.info(
                        "HTTP request completed: {} {} - Status: {} - Duration: {}ms",
                        request.getMethod(),
                        request.getRequestURI(),
                        response.getStatus(),
                        duration);
            }

            MDC.remove("request.id");
            MDC.remove("http.method");
            MDC.remove("http.path");
            MDC.remove("http.query_string");
            MDC.remove("http.remote_addr");
            MDC.remove("http.status_code");
            MDC.remove("http.duration_ms");
            MDC.remove("error");
            MDC.remove("error.type");
            MDC.remove("error.message");
        }
    }
}
