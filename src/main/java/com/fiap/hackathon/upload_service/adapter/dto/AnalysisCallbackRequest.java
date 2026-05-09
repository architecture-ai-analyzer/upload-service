package com.fiap.hackathon.upload_service.adapter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;

public class AnalysisCallbackRequest {

    @NotBlank(message = "uploadId is required")
    private String uploadId;

    @NotNull(message = "analysisResult is required")
    private AnalysisResult analysisResult;

    @NotNull(message = "riskScore is required")
    @Min(value = 0, message = "riskScore must be at least 0")
    @Max(value = 100, message = "riskScore must be at most 100")
    private Integer riskScore;

    private List<String> findings;

    @NotNull(message = "timestamp is required")
    private Long timestamp;

    private String analysisServiceId;

    public enum AnalysisResult {
        OK,
        QUARANTINED,
        INCONCLUSIVE
    }

    public AnalysisCallbackRequest() {}

    public AnalysisCallbackRequest(String uploadId, AnalysisResult analysisResult, Integer riskScore, List<String> findings, Long timestamp) {
        this.uploadId = uploadId;
        this.analysisResult = analysisResult;
        this.riskScore = riskScore;
        this.findings = findings;
        this.timestamp = timestamp;
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

    public String getAnalysisServiceId() {
        return analysisServiceId;
    }

    public void setAnalysisServiceId(String analysisServiceId) {
        this.analysisServiceId = analysisServiceId;
    }
}
