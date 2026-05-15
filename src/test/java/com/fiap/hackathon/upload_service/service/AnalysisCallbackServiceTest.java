package com.fiap.hackathon.upload_service.service;

import com.fiap.hackathon.upload_service.adapter.dto.AnalysisCallbackRequest;
import com.fiap.hackathon.upload_service.adapter.persistence.UploadRepository;
import com.fiap.hackathon.upload_service.domain.Upload;
import com.fiap.hackathon.upload_service.domain.UploadStatus;
import com.fiap.hackathon.upload_service.infra.audit.AuditEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AnalysisCallbackServiceTest {

    private AnalysisCallbackService service;
    private UploadRepository uploadRepository;
    private AuditEventPublisher auditEventPublisher;
    private final String clientIp = "192.168.1.1";

    @BeforeEach
    void setUp() {
        uploadRepository = mock(UploadRepository.class);
        auditEventPublisher = mock(AuditEventPublisher.class);
        service = new AnalysisCallbackService(uploadRepository, auditEventPublisher);
    }

    @Test
    void shouldUpdateStatusToScannedOk_whenAnalysisResultIsOk() {
        UUID uploadId = UUID.randomUUID();
        Upload upload = createUploadWithStatus(uploadId, UploadStatus.COMPLETED);
        
        AnalysisCallbackRequest request = new AnalysisCallbackRequest();
        request.setUploadId(uploadId.toString());
        request.setAnalysisResult(AnalysisCallbackRequest.AnalysisResult.OK);
        request.setRiskScore(15);
        request.setFindings(List.of("low_risk"));
        request.setTimestamp(System.currentTimeMillis());
        request.setAnalysisServiceId("analyzer-v1");

        when(uploadRepository.findById(uploadId)).thenReturn(Optional.of(upload));
        when(uploadRepository.save(any(Upload.class))).thenReturn(upload);

        Upload result = service.processAnalysisResult(request, clientIp);

        assertThat(result.getStatus()).isEqualTo(UploadStatus.SCANNED_OK);
        assertThat(result.getCompletedAt()).isNotNull();
        
        verify(uploadRepository).save(argThat(u -> u.getStatus() == UploadStatus.SCANNED_OK));
        verify(auditEventPublisher).publishEvent(
            eq(AuditEventPublisher.EventType.ANALYSIS_CALLBACK_RECEIVED),
            eq(upload.getUploaderId()),
            eq(clientIp),
            any(),
            eq(AuditEventPublisher.ActionResult.SUCCESS),
            any()
        );
    }

    @Test
    void shouldUpdateStatusToQuarantined_whenAnalysisResultIsQuarantined() {
        UUID uploadId = UUID.randomUUID();
        Upload upload = createUploadWithStatus(uploadId, UploadStatus.COMPLETED);
        
        AnalysisCallbackRequest request = new AnalysisCallbackRequest();
        request.setUploadId(uploadId.toString());
        request.setAnalysisResult(AnalysisCallbackRequest.AnalysisResult.QUARANTINED);
        request.setRiskScore(85);
        request.setFindings(List.of("malware_detected", "suspicious_patterns"));
        request.setTimestamp(System.currentTimeMillis());

        when(uploadRepository.findById(uploadId)).thenReturn(Optional.of(upload));
        when(uploadRepository.save(any(Upload.class))).thenReturn(upload);

        Upload result = service.processAnalysisResult(request, clientIp);

        assertThat(result.getStatus()).isEqualTo(UploadStatus.QUARANTINED);
        verify(uploadRepository).save(argThat(u -> u.getStatus() == UploadStatus.QUARANTINED));
    }

    @Test
    void shouldUpdateStatusToAnalysisReviewRequired_whenAnalysisResultIsInconclusive() {
        UUID uploadId = UUID.randomUUID();
        Upload upload = createUploadWithStatus(uploadId, UploadStatus.COMPLETED);
        
        AnalysisCallbackRequest request = new AnalysisCallbackRequest();
        request.setUploadId(uploadId.toString());
        request.setAnalysisResult(AnalysisCallbackRequest.AnalysisResult.INCONCLUSIVE);
        request.setRiskScore(50);
        request.setFindings(List.of("inconclusive_findings"));
        request.setTimestamp(System.currentTimeMillis());

        when(uploadRepository.findById(uploadId)).thenReturn(Optional.of(upload));
        when(uploadRepository.save(any(Upload.class))).thenReturn(upload);

        Upload result = service.processAnalysisResult(request, clientIp);

        assertThat(result.getStatus()).isEqualTo(UploadStatus.ANALYSIS_REVIEW_REQUIRED);
        verify(uploadRepository).save(argThat(u -> u.getStatus() == UploadStatus.ANALYSIS_REVIEW_REQUIRED));
    }

    @Test
    void shouldThrowNotFoundException_whenUploadNotFound() {
        UUID uploadId = UUID.randomUUID();
        AnalysisCallbackRequest request = new AnalysisCallbackRequest();
        request.setUploadId(uploadId.toString());
        request.setAnalysisResult(AnalysisCallbackRequest.AnalysisResult.OK);
        request.setRiskScore(10);
        request.setTimestamp(System.currentTimeMillis());

        when(uploadRepository.findById(uploadId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.processAnalysisResult(request, clientIp))
            .isInstanceOf(AnalysisCallbackService.UploadNotFoundException.class)
            .hasMessageContaining("Upload not found");

        verify(auditEventPublisher).publishEvent(
            eq(AuditEventPublisher.EventType.ANALYSIS_CALLBACK_RECEIVED),
            eq(null),
            eq(clientIp),
            any(),
            eq(AuditEventPublisher.ActionResult.DENIED),
            any()
        );
    }

    @Test
    void shouldThrowConflictException_whenUploadAlreadyScannedOk() {
        UUID uploadId = UUID.randomUUID();
        Upload upload = createUploadWithStatus(uploadId, UploadStatus.SCANNED_OK);
        
        AnalysisCallbackRequest request = new AnalysisCallbackRequest();
        request.setUploadId(uploadId.toString());
        request.setAnalysisResult(AnalysisCallbackRequest.AnalysisResult.QUARANTINED);
        request.setRiskScore(80);
        request.setTimestamp(System.currentTimeMillis());

        when(uploadRepository.findById(uploadId)).thenReturn(Optional.of(upload));

        assertThatThrownBy(() -> service.processAnalysisResult(request, clientIp))
            .isInstanceOf(AnalysisCallbackService.UploadConflictException.class)
            .hasMessageContaining("already in final state");

        verify(uploadRepository, never()).save(any());
    }

    @Test
    void shouldThrowConflictException_whenUploadAlreadyQuarantined() {
        UUID uploadId = UUID.randomUUID();
        Upload upload = createUploadWithStatus(uploadId, UploadStatus.QUARANTINED);
        
        AnalysisCallbackRequest request = new AnalysisCallbackRequest();
        request.setUploadId(uploadId.toString());
        request.setAnalysisResult(AnalysisCallbackRequest.AnalysisResult.OK);
        request.setRiskScore(10);
        request.setTimestamp(System.currentTimeMillis());

        when(uploadRepository.findById(uploadId)).thenReturn(Optional.of(upload));

        assertThatThrownBy(() -> service.processAnalysisResult(request, clientIp))
            .isInstanceOf(AnalysisCallbackService.UploadConflictException.class);

        verify(uploadRepository, never()).save(any());
    }

    @Test
    void shouldThrowConflictException_whenUploadAlreadyAnalysisInvalid() {
        UUID uploadId = UUID.randomUUID();
        Upload upload = createUploadWithStatus(uploadId, UploadStatus.ANALYSIS_INVALID);
        
        AnalysisCallbackRequest request = new AnalysisCallbackRequest();
        request.setUploadId(uploadId.toString());
        request.setAnalysisResult(AnalysisCallbackRequest.AnalysisResult.OK);
        request.setRiskScore(10);
        request.setTimestamp(System.currentTimeMillis());

        when(uploadRepository.findById(uploadId)).thenReturn(Optional.of(upload));

        assertThatThrownBy(() -> service.processAnalysisResult(request, clientIp))
            .isInstanceOf(AnalysisCallbackService.UploadConflictException.class);

        verify(uploadRepository, never()).save(any());
    }

    @Test
    void shouldThrowConflictException_whenUploadAlreadyAnalysisReviewRequired() {
        UUID uploadId = UUID.randomUUID();
        Upload upload = createUploadWithStatus(uploadId, UploadStatus.ANALYSIS_REVIEW_REQUIRED);
        
        AnalysisCallbackRequest request = new AnalysisCallbackRequest();
        request.setUploadId(uploadId.toString());
        request.setAnalysisResult(AnalysisCallbackRequest.AnalysisResult.OK);
        request.setRiskScore(10);
        request.setTimestamp(System.currentTimeMillis());

        when(uploadRepository.findById(uploadId)).thenReturn(Optional.of(upload));

        assertThatThrownBy(() -> service.processAnalysisResult(request, clientIp))
            .isInstanceOf(AnalysisCallbackService.UploadConflictException.class);

        verify(uploadRepository, never()).save(any());
    }

    @Test
    void shouldAllowReprocessing_whenUploadIsCompleted() {
        UUID uploadId = UUID.randomUUID();
        Upload upload = createUploadWithStatus(uploadId, UploadStatus.COMPLETED);
        
        AnalysisCallbackRequest request = new AnalysisCallbackRequest();
        request.setUploadId(uploadId.toString());
        request.setAnalysisResult(AnalysisCallbackRequest.AnalysisResult.OK);
        request.setRiskScore(20);
        request.setTimestamp(System.currentTimeMillis());

        when(uploadRepository.findById(uploadId)).thenReturn(Optional.of(upload));
        when(uploadRepository.save(any(Upload.class))).thenReturn(upload);

        Upload result = service.processAnalysisResult(request, clientIp);

        assertThat(result.getStatus()).isEqualTo(UploadStatus.SCANNED_OK);
        verify(uploadRepository).save(any());
    }

    @Test
    void shouldIncludeDetailsInAuditEvent_withFindings() {
        UUID uploadId = UUID.randomUUID();
        Upload upload = createUploadWithStatus(uploadId, UploadStatus.COMPLETED);
        
        AnalysisCallbackRequest request = new AnalysisCallbackRequest();
        request.setUploadId(uploadId.toString());
        request.setAnalysisResult(AnalysisCallbackRequest.AnalysisResult.QUARANTINED);
        request.setRiskScore(90);
        request.setFindings(List.of("malware_detected", "suspicious_behavior"));
        request.setTimestamp(System.currentTimeMillis());
        request.setAnalysisServiceId("analyzer-v2.1");

        when(uploadRepository.findById(uploadId)).thenReturn(Optional.of(upload));
        when(uploadRepository.save(any(Upload.class))).thenReturn(upload);

        service.processAnalysisResult(request, clientIp);

        ArgumentCaptor<String> detailsCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditEventPublisher).publishEvent(
            eq(AuditEventPublisher.EventType.ANALYSIS_CALLBACK_RECEIVED),
            any(),
            any(),
            any(),
            any(),
            detailsCaptor.capture()
        );

        String details = detailsCaptor.getValue();
        assertThat(details)
            .contains("QUARANTINED")
            .contains("90")
            .contains("malware_detected")
            .contains("analyzer-v2.1");
    }

    @Test
    void shouldApplyDiagramStatusFromQueue_whenValidStatus() {
        UUID diagramId = UUID.randomUUID();
        Upload upload = createUploadWithStatus(diagramId, UploadStatus.PENDING);

        when(uploadRepository.findById(diagramId)).thenReturn(Optional.of(upload));
        when(uploadRepository.save(any(Upload.class))).thenAnswer(inv -> inv.getArgument(0));

        Upload result = service.applyDiagramStatusFromQueue(diagramId, "scanned_ok", "sqs-listener");

        assertThat(result.getStatus()).isEqualTo(UploadStatus.SCANNED_OK);
        assertThat(result.getCompletedAt()).isNotNull();
        verify(uploadRepository).save(argThat(u -> u.getStatus() == UploadStatus.SCANNED_OK));
        verify(auditEventPublisher).publishEvent(
                eq(AuditEventPublisher.EventType.ANALYSIS_CALLBACK_RECEIVED),
                eq(upload.getUploaderId()),
                eq("sqs-listener"),
                any(),
                eq(AuditEventPublisher.ActionResult.SUCCESS),
                any()
        );
    }

    @Test
    void shouldApplyDiagramStatusFromQueue_withoutCompletedAt_whenNonFinalStatus() {
        UUID diagramId = UUID.randomUUID();
        Upload upload = createUploadWithStatus(diagramId, UploadStatus.PENDING);
        upload.setCompletedAt(null);

        when(uploadRepository.findById(diagramId)).thenReturn(Optional.of(upload));
        when(uploadRepository.save(any(Upload.class))).thenAnswer(inv -> inv.getArgument(0));

        Upload result = service.applyDiagramStatusFromQueue(diagramId, "COMPLETED", "sqs-listener");

        assertThat(result.getStatus()).isEqualTo(UploadStatus.COMPLETED);
        assertThat(result.getCompletedAt()).isNull();
    }

    @Test
    void shouldThrowWhenDiagramStatusInvalid() {
        UUID diagramId = UUID.randomUUID();
        Upload upload = createUploadWithStatus(diagramId, UploadStatus.PENDING);
        when(uploadRepository.findById(diagramId)).thenReturn(Optional.of(upload));

        assertThatThrownBy(() -> service.applyDiagramStatusFromQueue(diagramId, "NOT_A_STATUS", "sqs-listener"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid upload status");
        verify(uploadRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenDiagramUploadNotFound() {
        UUID diagramId = UUID.randomUUID();
        when(uploadRepository.findById(diagramId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.applyDiagramStatusFromQueue(diagramId, "SCANNED_OK", "sqs-listener"))
                .isInstanceOf(AnalysisCallbackService.UploadNotFoundException.class);
    }

    @Test
    void shouldThrowWhenDiagramUploadAlreadyFinal() {
        UUID diagramId = UUID.randomUUID();
        Upload upload = createUploadWithStatus(diagramId, UploadStatus.SCANNED_OK);
        when(uploadRepository.findById(diagramId)).thenReturn(Optional.of(upload));

        assertThatThrownBy(() -> service.applyDiagramStatusFromQueue(diagramId, "QUARANTINED", "sqs-listener"))
                .isInstanceOf(AnalysisCallbackService.UploadConflictException.class);
        verify(uploadRepository, never()).save(any());
    }

    private Upload createUploadWithStatus(UUID uploadId, UploadStatus status) {
        Upload upload = new Upload();
        upload.setId(uploadId);
        upload.setS3Key("projects/proj-123/user-456/upload-" + uploadId + ".pdf");
        upload.setFilename("test-file.pdf");
        upload.setContentType("application/pdf");
        upload.setSizeBytes(102400L);
        upload.setUploaderId("user-456");
        upload.setProjectId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        upload.setStatus(status);
        upload.setCreatedAt(OffsetDateTime.now().minusHours(1));
        return upload;
    }
}
