package com.fiap.hackathon.upload_service.adapter.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;

/**
 * Message format received from the analysis/processing service via SQS.
 * This message is published when the processing service completes analysis of an upload.
 */
public class AnalysisResultMessage {

    @NotBlank(message = "uploadId is required")
    @JsonProperty("upload_id")
    private String uploadId;

    @NotNull(message = "analysisResult is required")
    @JsonProperty("analysis_result")
    private AnalysisResult analysisResult;

    @NotNull(message = "riskScore is required")
    @Min(value = 0, message = "riskScore must be at least 0")
    @Max(value = 100, message = "riskScore must be at most 100")
    @JsonProperty("risk_score")
    private Integer riskScore;

    @JsonProperty("findings")
    private List<String> findings;

    @NotNull(message = "timestamp is required")
    @JsonProperty("timestamp")
    private Long timestamp;

    @JsonProperty("processing_service_id")
    private String processingServiceId;

    @JsonProperty("request_id")
    private String requestId;

    public enum AnalysisResult {
        OK,
        QUARANTINED,
        INCONCLUSIVE
    }

    public AnalysisResultMessage() {}

    public AnalysisResultMessage(String uploadId, AnalysisResult analysisResult, Integer riskScore, 
                                 List<String> findings, Long timestamp, String processingServiceId) {
        this.uploadId = uploadId;
        this.analysisResult = analysisResult;
        this.riskScore = riskScore;
        this.findings = findings;
        this.timestamp = timestamp;
        this.processingServiceId = processingServiceId;
    }

    public String getUploadId() {
        return uploadId;
    }

    public void setUploadId(String uploadId) {
        this.uploadId = uploadId;
    }

    public AnalysisResult getAnalysisResult() {
        return analysisResult;
    }

    public void setAnalysisResult(AnalysisResult analysisResult) {
        this.analysisResult = analysisResult;
    }

    public Integer getRiskScore() {
        return riskScore;
    }

    public void setRiskScore(Integer riskScore) {
        this.riskScore = riskScore;
    }

    public List<String> getFindings() {
        return findings;
    }

    public void setFindings(List<String> findings) {
        this.findings = findings;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    public String getProcessingServiceId() {
        return processingServiceId;
    }

    public void setProcessingServiceId(String processingServiceId) {
        this.processingServiceId = processingServiceId;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
}
