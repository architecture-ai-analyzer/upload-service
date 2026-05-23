package com.fiap.hackathon.upload_service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiap.hackathon.upload_service.infra.audit.AuditEventPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class UploadRateLimitFilterTest {

    @Test
    void shouldReturn429WhenRateLimitExceeded() throws Exception {
        UploadRateLimitProperties properties = new UploadRateLimitProperties();
        properties.setEnabled(true);
        properties.setMaxRequests(2);
        properties.setWindowSeconds(60);

        AuditEventPublisher auditEventPublisher = mock(AuditEventPublisher.class);
        UploadRateLimitFilter filter = new UploadRateLimitFilter(properties, new ObjectMapper(), auditEventPublisher);

        MockHttpServletRequest first = buildUploadRequest("10.0.0.1");
        MockHttpServletResponse firstResp = new MockHttpServletResponse();
        filter.doFilter(first, firstResp, new MockFilterChain());
        assertThat(firstResp.getStatus()).isIn(0, 200);

        MockHttpServletRequest second = buildUploadRequest("10.0.0.1");
        MockHttpServletResponse secondResp = new MockHttpServletResponse();
        filter.doFilter(second, secondResp, new MockFilterChain());
        assertThat(secondResp.getStatus()).isIn(0, 200);

        MockHttpServletRequest third = buildUploadRequest("10.0.0.1");
        MockHttpServletResponse thirdResp = new MockHttpServletResponse();
        filter.doFilter(third, thirdResp, new MockFilterChain());

        assertThat(thirdResp.getStatus()).isEqualTo(429);
        assertThat(thirdResp.getContentAsString()).contains("RATE_LIMIT_EXCEEDED");
    }

    private MockHttpServletRequest buildUploadRequest(String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/uploads");
        request.setServletPath("/v1/uploads");
        request.addHeader("X-Forwarded-For", forwardedFor);
        return request;
    }
}
