package com.fiap.hackathon.upload_service.config.observability;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

class TraceSupportTest {

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void putErrorMdc_shouldPopulateErrorFields() {
        Exception ex = new IllegalArgumentException("bad request");

        TraceSupport.putErrorMdc(ex, "BAD_REQUEST", 400);

        assertThat(MDC.get("error")).isEqualTo("true");
        assertThat(MDC.get("error.code")).isEqualTo("BAD_REQUEST");
        assertThat(MDC.get("error.message")).isEqualTo("bad request");
        assertThat(MDC.get("http.status_code")).isEqualTo("400");
    }

    @Test
    void clearErrorMdc_shouldRemoveErrorFields() {
        TraceSupport.putErrorMdc(new RuntimeException("x"), "ERR", 500);
        TraceSupport.clearErrorMdc();

        assertThat(MDC.get("error")).isNull();
        assertThat(MDC.get("error.code")).isNull();
        assertThat(MDC.get("http.status_code")).isNull();
    }

    @Test
    void tagActiveSpan_shouldNotThrowWithoutActiveSpan() {
        TraceSupport.tagActiveSpan("operation.type", "upload");
    }
}
