package com.fiap.hackathon.upload_service.config.observability;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoggingInterceptorTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    private LoggingInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new LoggingInterceptor();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void preHandle_shouldPopulateMdcAndReturnTrue() {
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/v1/uploads");
        when(request.getQueryString()).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isTrue();
        assertThat(MDC.get("http.method")).isEqualTo("GET");
        assertThat(MDC.get("http.path")).isEqualTo("/v1/uploads");
        assertThat(MDC.get("request.id")).isNotBlank();
        verify(request).setAttribute(org.mockito.ArgumentMatchers.eq("startTime"), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void afterCompletion_shouldClearMdcOnSuccess() {
        when(request.getAttribute("startTime")).thenReturn(System.currentTimeMillis() - 50L);
        when(response.getStatus()).thenReturn(200);

        interceptor.afterCompletion(request, response, new Object(), null);

        assertThat(MDC.get("http.method")).isNull();
        assertThat(MDC.get("http.status_code")).isNull();
    }
}
