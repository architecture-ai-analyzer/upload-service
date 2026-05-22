package com.fiap.hackathon.upload_service.config.observability;

import com.timgroup.statsd.StatsDClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class UploadMetricsServiceImpl implements UploadMetricsService {

    private static final Logger logger = LoggerFactory.getLogger(UploadMetricsServiceImpl.class);
    private final StatsDClient statsDClient;

    public UploadMetricsServiceImpl(StatsDClient statsDClient) {
        this.statsDClient = statsDClient;
    }

    @Override
    public void recordUploadCreated(String contentTypeTag) {
        try {
            statsDClient.incrementCounter("upload.created", contentTypeTag);
        } catch (Exception e) {
            logger.warn("Failed to send Datadog metric upload.created: {}", e.getMessage());
        }
    }

    @Override
    public void recordStatusTransitionDuration(long durationInSeconds, String statusTag) {
        try {
            statsDClient.gauge("upload.status.transition.duration", durationInSeconds, statusTag);
        } catch (Exception e) {
            logger.warn("Failed to send Datadog metric upload.status.transition.duration: {}", e.getMessage());
        }
    }

    @Override
    public void recordPipelineDuration(long durationInSeconds, String statusTag) {
        try {
            statsDClient.gauge("upload.pipeline.duration", durationInSeconds, statusTag);
        } catch (Exception e) {
            logger.warn("Failed to send Datadog metric upload.pipeline.duration: {}", e.getMessage());
        }
    }
}
