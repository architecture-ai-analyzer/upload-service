package com.fiap.hackathon.upload_service.service;

import com.fiap.hackathon.upload_service.adapter.dto.AnalysisCallbackRequest;
import com.fiap.hackathon.upload_service.adapter.persistence.UploadRepository;
import com.fiap.hackathon.upload_service.domain.Upload;
import com.fiap.hackathon.upload_service.domain.UploadStatus;
import com.fiap.hackathon.upload_service.infra.audit.AuditEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class AnalysisCallbackService {

    private static final String ANALYSIS_RESULT_ACTION = "SQS analysis result message";

    private final UploadRepository uploadRepository;
    private final AuditEventPublisher auditEventPublisher;

    public AnalysisCallbackService(UploadRepository uploadRepository, AuditEventPublisher auditEventPublisher) {
        this.uploadRepository = uploadRepository;
        this.auditEventPublisher = auditEventPublisher;
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

    private UploadStatus mapAnalysisResultToStatus(AnalysisCallbackRequest.AnalysisResult result) {
        return switch (result) {
            case OK -> UploadStatus.SCANNED_OK;
            case QUARANTINED -> UploadStatus.QUARANTINED;
            case INCONCLUSIVE -> UploadStatus.ANALYSIS_REVIEW_REQUIRED;
        };
    }

    private boolean isFinalStatus(UploadStatus status) {
        return status == UploadStatus.SCANNED_OK ||
               status == UploadStatus.QUARANTINED ||
               status == UploadStatus.ANALYSIS_INVALID ||
               status == UploadStatus.ANALYSIS_REVIEW_REQUIRED;
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
