package com.fiap.hackathon.upload_service.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "uploads")
public class Upload {

    @Id
    private UUID id;

    private String s3Key;

    private String filename;

    private String contentType;

    private Long sizeBytes;

    private String uploaderId;

    private java.util.UUID projectId;

    @Convert(converter = UploadStatusJpaConverter.class)
    private UploadStatus status;

    private OffsetDateTime createdAt;

    private OffsetDateTime completedAt;

    private String templateId;

    public Upload() {}

    public Upload(UUID id, String s3Key, String filename, String contentType, Long sizeBytes, String uploaderId, UploadStatus status, OffsetDateTime createdAt) {
        this.id = id;
        this.s3Key = s3Key;
        this.filename = filename;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.uploaderId = uploaderId;
        this.status = status;
        this.createdAt = createdAt;
    }

    public java.util.UUID getProjectId() {
        return projectId;
    }

    public void setProjectId(java.util.UUID projectId) {
        this.projectId = projectId;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getS3Key() {
        return s3Key;
    }

    public void setS3Key(String s3Key) {
        this.s3Key = s3Key;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(Long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public String getUploaderId() {
        return uploaderId;
    }

    public void setUploaderId(String uploaderId) {
        this.uploaderId = uploaderId;
    }

    public UploadStatus getStatus() {
        return status;
    }

    public void setStatus(UploadStatus status) {
        this.status = status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(OffsetDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public String getTemplateId() {
        return templateId;
    }

    public void setTemplateId(String templateId) {
        this.templateId = templateId;
    }
}
