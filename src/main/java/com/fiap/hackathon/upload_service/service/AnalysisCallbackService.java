package com.fiap.hackathon.upload_service.service;

import com.fiap.hackathon.upload_service.adapter.dto.AnalysisCallbackRequest;
import com.fiap.hackathon.upload_service.adapter.persistence.UploadRepository;
import com.fiap.hackathon.upload_service.domain.Upload;
import com.fiap.hackathon.upload_service.domain.UploadStatus;
import com.fiap.hackathon.upload_service.config.observability.UploadMetricsService;
import com.fiap.hackathon.upload_service.infra.audit.AuditEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class AnalysisCallbackService {

    private static final String ANALYSIS_RESULT_ACTION = "SQS analysis result message";

    private final UploadRepository uploadRepository;
    private final AuditEventPublisher auditEventPublisher;
    private final UploadMetricsService uploadMetricsService;

    public AnalysisCallbackService(
            UploadRepository uploadRepository,
            AuditEventPublisher auditEventPublisher,
            UploadMetricsService uploadMetricsService) {
        this.uploadRepository = uploadRepository;
        this.auditEventPublisher = auditEventPublisher;
        this.uploadMetricsService = uploadMetricsService;
    }

    @Transactional
    public Upload processAnalysisResult(AnalysisCallbackRequest request, String source) {
        UUID uploadId = UUID.fromString(request.getUploadId());
        
        // Find the upload
        Optional<Upload> uploadOpt = uploadRepository.findById(uploadId);
        if (uploadOpt.isEmpty()) {
            auditEventPublisher.publishEvent(
                AuditEventPublisher.EventType.ANALYSIS_CALLBACK_RECEIVED,
                null,
                source,
                ANALYSIS_RESULT_ACTION,
                AuditEventPublisher.ActionResult.DENIED,
                "Upload not found: " + uploadId
            );
            throw new UploadNotFoundException("UPLOAD_NOT_FOUND", "Upload not found for id " + uploadId);
        }

        Upload upload = uploadOpt.get();

        // Check if already in a final state (idempotency protection)
        if (isFinalStatus(upload.getStatus())) {
            auditEventPublisher.publishEvent(
                AuditEventPublisher.EventType.ANALYSIS_CALLBACK_RECEIVED,
                upload.getUploaderId(),
                source,
                ANALYSIS_RESULT_ACTION,
                AuditEventPublisher.ActionResult.DENIED,
                "Upload already in final state: " + upload.getStatus() + ". Message ignored to prevent replay processing."
            );
            throw new UploadConflictException(
                "UPLOAD_ALREADY_ANALYZED",
                "Upload is already in final state: " + upload.getStatus()
            );
        }

        // Map analysis result to upload status
        UploadStatus newStatus = mapAnalysisResultToStatus(request.getAnalysisResult());
        upload.setStatus(newStatus);
        upload.setCompletedAt(OffsetDateTime.now());

        // Persist changes
        Upload updated = uploadRepository.save(upload);
        recordStatusMetrics(upload, newStatus);

        // Publish audit event
        auditEventPublisher.publishEvent(
            AuditEventPublisher.EventType.ANALYSIS_CALLBACK_RECEIVED,
            upload.getUploaderId(),
            source,
            ANALYSIS_RESULT_ACTION,
            AuditEventPublisher.ActionResult.SUCCESS,
            String.format(
                "Analysis result consumed from queue. New status: %s. Risk score: %d. Findings: %s. Processing service: %s",
                newStatus,
                request.getRiskScore(),
                request.getFindings() != null ? String.join(", ", request.getFindings()) : "none",
                request.getAnalysisServiceId() != null ? request.getAnalysisServiceId() : "unknown"
            )
        );

        return updated;
    }

    private UploadStatus parseUploadStatus(String status) {
        try {
            return UploadStatus.fromString(status);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid upload status: " + status, ex);
        }
    }

    /**
     * Applies an explicit {@link UploadStatus} from the SQS diagram-status message (diagram id = upload id).
     * {@code completedAt} is set only when the new status is a terminal analysis state.
     */
    @Transactional
    public Upload applyDiagramStatusFromQueue(UUID diagramId, String status, String source) {
        UploadStatus newStatus = parseUploadStatus(status);

        Optional<Upload> uploadOpt = uploadRepository.findById(diagramId);
        if (uploadOpt.isEmpty()) {
            auditEventPublisher.publishEvent(
                    AuditEventPublisher.EventType.ANALYSIS_CALLBACK_RECEIVED,
                    null,
                    source,
                    ANALYSIS_RESULT_ACTION,
                    AuditEventPublisher.ActionResult.DENIED,
                    "Upload not found: " + diagramId
            );
            throw new UploadNotFoundException(
                    "UPLOAD_NOT_FOUND",
                    "Upload not found for id "
                            + diagramId
                            + ". Status messages must use the same UUID as job_id (upload id) from the analysis input payload.");
        }

        Upload upload = uploadOpt.get();

        if (isFinalStatus(upload.getStatus())) {
            auditEventPublisher.publishEvent(
                    AuditEventPublisher.EventType.ANALYSIS_CALLBACK_RECEIVED,
                    upload.getUploaderId(),
                    source,
                    ANALYSIS_RESULT_ACTION,
                    AuditEventPublisher.ActionResult.DENIED,
                    "Upload already in final state: " + upload.getStatus() + ". Message ignored to prevent replay processing."
            );
            throw new UploadConflictException(
                    "UPLOAD_ALREADY_ANALYZED",
                    "Upload is already in final state: " + upload.getStatus()
            );
        }

        upload.setStatus(newStatus);
        if (isFinalStatus(newStatus)) {
            upload.setCompletedAt(OffsetDateTime.now());
        }

        Upload updated = uploadRepository.save(upload);
        recordStatusMetrics(upload, newStatus);

        auditEventPublisher.publishEvent(
                AuditEventPublisher.EventType.ANALYSIS_CALLBACK_RECEIVED,
                upload.getUploaderId(),
                source,
                ANALYSIS_RESULT_ACTION,
                AuditEventPublisher.ActionResult.SUCCESS,
                String.format("Diagram status update from queue. New status: %s", newStatus)
        );

        return updated;
    }

    private void recordStatusMetrics(Upload upload, UploadStatus newStatus) {
        if (upload.getCreatedAt() == null) {
            return;
        }
        long transitionSeconds = Duration.between(upload.getCreatedAt(), OffsetDateTime.now()).getSeconds();
        uploadMetricsService.recordStatusTransitionDuration(transitionSeconds, "status:" + newStatus.name());

        if (isFinalStatus(newStatus) && upload.getCompletedAt() != null) {
            long pipelineSeconds = Duration.between(upload.getCreatedAt(), upload.getCompletedAt()).getSeconds();
            uploadMetricsService.recordPipelineDuration(pipelineSeconds, "status:" + newStatus.name());
        }
    }

    private UploadStatus mapAnalysisResultToStatus(AnalysisCallbackRequest.AnalysisResult result) {
        return switch (result) {
            case OK -> UploadStatus.ANALISADO;
            case QUARANTINED -> UploadStatus.ANALISADO;
            case INCONCLUSIVE -> UploadStatus.ERRO;
        };
    }

    private boolean isFinalStatus(UploadStatus status) {
        return status == UploadStatus.ANALISADO || status == UploadStatus.ERRO;
    }

    public static class UploadNotFoundException extends RuntimeException {
        private final String code;

        public UploadNotFoundException(String code, String message) {
            super(message);
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }

    public static class UploadConflictException extends RuntimeException {
        private final String code;

        public UploadConflictException(String code, String message) {
            super(message);
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }
}
