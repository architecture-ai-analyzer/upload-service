package com.fiap.hackathon.upload_service.infra.aws;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AiAnalysisPayload(
        @JsonProperty("schema_version") int schemaVersion,
        Source source,
        @JsonProperty("job_id") String jobId,
        @JsonProperty("correlation_id") String correlationId
) {
    public record Source(
            String type,
            String bucket,
            String key
    ) {
    }
}
