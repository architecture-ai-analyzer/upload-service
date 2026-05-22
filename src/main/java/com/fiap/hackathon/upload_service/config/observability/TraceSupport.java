package com.fiap.hackathon.upload_service.config.observability;

import io.opentracing.Span;
import io.opentracing.util.GlobalTracer;
import org.slf4j.MDC;

public final class TraceSupport {

    private TraceSupport() {
    }

    public static void tagActiveSpan(String key, String value) {
        if (value == null) {
            return;
        }
        Span span = GlobalTracer.get().activeSpan();
        if (span != null) {
            span.setTag(key, value);
        }
    }

    public static void addErrorToSpan(Exception ex, String errorCode) {
        Span span = GlobalTracer.get().activeSpan();
        if (span != null) {
            span.setTag("error", true);
            span.setTag("error.type", ex.getClass().getName());
            span.setTag("error.message", ex.getMessage() != null ? ex.getMessage() : "Unknown error");
            if (errorCode != null) {
                span.setTag("error.code", errorCode);
            }
            span.setTag("error.stack", getStackTrace(ex));
        }
    }

    public static void putErrorMdc(Exception ex, String errorCode, int httpStatus) {
        MDC.put("error", "true");
        MDC.put("error.type", ex.getClass().getSimpleName());
        if (errorCode != null) {
            MDC.put("error.code", errorCode);
        }
        MDC.put("error.message", ex.getMessage() != null ? ex.getMessage() : "Unknown error");
        MDC.put("http.status_code", String.valueOf(httpStatus));
    }

    public static void clearErrorMdc() {
        MDC.remove("error");
        MDC.remove("error.type");
        MDC.remove("error.code");
        MDC.remove("error.message");
        MDC.remove("http.status_code");
    }

    private static String getStackTrace(Exception ex) {
        var sw = new java.io.StringWriter();
        var pw = new java.io.PrintWriter(sw);
        ex.printStackTrace(pw);
        return sw.toString();
    }
}
