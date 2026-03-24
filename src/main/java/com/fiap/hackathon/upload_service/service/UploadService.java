package com.fiap.hackathon.upload_service.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiap.hackathon.upload_service.adapter.persistence.UploadRepository;
import com.fiap.hackathon.upload_service.domain.Upload;
import com.fiap.hackathon.upload_service.domain.UploadStatus;
import com.fiap.hackathon.upload_service.infra.aws.SqsClientWrapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class UploadService {

    private final UploadRepository uploadRepository;
    private final SqsClientWrapper sqsClientWrapper;
    private final ObjectMapper objectMapper;

    @Value("${application.sqs.queueUrl:}")
    private String queueUrl;

    public UploadService(UploadRepository uploadRepository, SqsClientWrapper sqsClientWrapper, ObjectMapper objectMapper) {
        this.uploadRepository = uploadRepository;
        this.sqsClientWrapper = sqsClientWrapper;
        this.objectMapper = objectMapper;
    }

    public Upload completeUpload(UUID uploadId, String s3Key, String filename, String contentType, Long sizeBytes, String uploaderId, String projectId) {
        Optional<Upload> existing = uploadRepository.findById(uploadId);
        Upload up = existing.orElseGet(() -> new Upload());
        up.setId(uploadId);
        up.setS3Key(s3Key);
        up.setFilename(filename);
        up.setContentType(contentType);
        up.setSizeBytes(sizeBytes);
        up.setUploaderId(uploaderId);
        if (projectId != null && !projectId.isBlank()) {
            try {
                up.setProjectId(UUID.fromString(projectId));
            } catch (Exception e) {
                // ignore invalid uuid; leave null
            }
        }
        up.setStatus(UploadStatus.COMPLETED);
        up.setCompletedAt(OffsetDateTime.now());
        if (up.getCreatedAt() == null) up.setCreatedAt(OffsetDateTime.now());
        uploadRepository.save(up);

        // publish event
        if (queueUrl != null && !queueUrl.isBlank()) {
            var payload = new UploadEvent(uploadId.toString(), s3Key, contentType, sizeBytes, uploaderId, projectId);
            try {
                String body = objectMapper.writeValueAsString(payload);
                sqsClientWrapper.sendMessage(queueUrl, body);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
        return up;
    }

    static class UploadEvent {
        public String eventId;
        public String s3Key;
        public String contentType;
        public Long sizeBytes;
        public String uploaderId;
        public String projectId;

        public UploadEvent(String eventId, String s3Key, String contentType, Long sizeBytes, String uploaderId, String projectId) {
            this.eventId = eventId;
            this.s3Key = s3Key;
            this.contentType = contentType;
            this.sizeBytes = sizeBytes;
            this.uploaderId = uploaderId;
            this.projectId = projectId;
        }
    }
}
