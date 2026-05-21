package com.fiap.hackathon.upload_service.infra.aws;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Message published to the AI / analysis input queue after a successful upload.
 * <p>
 * {@code job_id} is the upload row primary key in upload-service ({@code uploads.id}).
 * Status callbacks on the <strong>result</strong> queue must set {@code diagram_id} / {@code diagramId}
 * to this same UUID string so {@link com.fiap.hackathon.upload_service.service.AnalysisCallbackService#applyDiagramStatusFromQueue}
 * can resolve the upload.
 * <p>
 * {@code correlation_id} is set equal to {@code job_id} so consumers that mistakenly treat
 * {@code correlation_id} as the diagram/upload key still echo the correct id in result messages.
 */
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
