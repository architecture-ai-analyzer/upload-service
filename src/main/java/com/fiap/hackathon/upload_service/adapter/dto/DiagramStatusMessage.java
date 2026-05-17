package com.fiap.hackathon.upload_service.adapter.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * Minimal SQS payload from the processing service: diagram (upload) id, status, and optional timestamp.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DiagramStatusMessage {

    @JsonProperty("diagram_id")
    @JsonAlias("diagramId")
    private UUID diagramId;

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
