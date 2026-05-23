package com.fiap.hackathon.upload_service.config.observability;

import com.timgroup.statsd.StatsDClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UploadMetricsServiceImplTest {

    @Mock
    private StatsDClient statsDClient;

    private UploadMetricsServiceImpl metricsService;

    @BeforeEach
    void setUp() {
        metricsService = new UploadMetricsServiceImpl(statsDClient);
    }

    @Test
    void recordUploadCreated_shouldIncrementCounter() {
        metricsService.recordUploadCreated("content_type:application/pdf");

        verify(statsDClient).incrementCounter("upload.created", "content_type:application/pdf");
    }

    @Test
    void recordStatusTransitionDuration_shouldSendGauge() {
        metricsService.recordStatusTransitionDuration(120L, "status:ANALISADO");

        verify(statsDClient).gauge("upload.status.transition.duration", 120L, "status:ANALISADO");
    }

    @Test
    void recordPipelineDuration_shouldSendGauge() {
        metricsService.recordPipelineDuration(300L, "status:ERRO");

        verify(statsDClient).gauge("upload.pipeline.duration", 300L, "status:ERRO");
    }

    @Test
    void recordUploadCreated_shouldNotThrowWhenStatsDFails() {
        doThrow(new RuntimeException("statsd unavailable"))
                .when(statsDClient)
                .incrementCounter("upload.created", "content_type:image/png");

        metricsService.recordUploadCreated("content_type:image/png");
    }
}
