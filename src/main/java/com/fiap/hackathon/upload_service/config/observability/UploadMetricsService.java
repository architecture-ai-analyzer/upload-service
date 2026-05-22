package com.fiap.hackathon.upload_service.config.observability;

/**
 * Sends custom business metrics to Datadog via DogStatsD.
 */
public interface UploadMetricsService {

    void recordUploadCreated(String contentTypeTag);

    void recordStatusTransitionDuration(long durationInSeconds, String statusTag);

    void recordPipelineDuration(long durationInSeconds, String statusTag);
}
