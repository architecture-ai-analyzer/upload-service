package com.fiap.hackathon.upload_service.adapter.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * Minimal SQS payload from the processing service: diagram (upload) id, status, and optional timestamp.
 * <p>
 * The id fields must equal {@code job_id} from {@link com.fiap.hackathon.upload_service.infra.aws.AiAnalysisPayload}
 * (the upload-service primary key), not an internal diagram id from the worker unless it is the same UUID.
 * <p>
 * <strong>Formato de {@code status}:</strong> valores canônicos em português, iguais ao JSON de
 * {@link com.fiap.hackathon.upload_service.domain.UploadStatus}: {@code RECEBIDO},
 * {@code EM_PROCESSAMENTO}, {@code ANALISADO}, {@code ERRO}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DiagramStatusMessage {

    @JsonProperty("diagram_id")
    @JsonAlias({"diagramId", "job_id", "upload_id"})
    private UUID diagramId;

    /**
     * Estado do ciclo de vida: RECEBIDO, EM_PROCESSAMENTO, ANALISADO ou ERRO.
     */
    @JsonProperty("status")
    private String status;

    @JsonProperty("timestamp")
    private String timestamp;

    public DiagramStatusMessage() {
    }

    public UUID getDiagramId() {
        return diagramId;
    }

    public void setDiagramId(UUID diagramId) {
        this.diagramId = diagramId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
}
