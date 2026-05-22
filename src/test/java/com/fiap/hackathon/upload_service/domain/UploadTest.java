package com.fiap.hackathon.upload_service.domain;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UploadTest {

    @Test
    void defaultConstructor_ShouldCreateEmptyUpload() {
        Upload upload = new Upload();

        assertNotNull(upload);
        assertNull(upload.getId());
        assertNull(upload.getS3Key());
        assertNull(upload.getFilename());
        assertNull(upload.getContentType());
        assertNull(upload.getSizeBytes());
        assertNull(upload.getUploaderId());
        assertNull(upload.getProjectId());
        assertNull(upload.getStatus());
        assertNull(upload.getCreatedAt());
        assertNull(upload.getCompletedAt());
    }

    @Test
    void parameterizedConstructor_ShouldCreateUploadWithValues() {
        UUID id = UUID.randomUUID();
        String s3Key = "uploads/test/file.pdf";
        String filename = "file.pdf";
        String contentType = "application/pdf";
        Long sizeBytes = 1024L;
        String uploaderId = "user-123";
        UploadStatus status = UploadStatus.RECEBIDO;
        OffsetDateTime createdAt = OffsetDateTime.now();

        Upload upload = new Upload(id, s3Key, filename, contentType, sizeBytes, uploaderId, status, createdAt);

        assertEquals(id, upload.getId());
        assertEquals(s3Key, upload.getS3Key());
        assertEquals(filename, upload.getFilename());
        assertEquals(contentType, upload.getContentType());
        assertEquals(sizeBytes, upload.getSizeBytes());
        assertEquals(uploaderId, upload.getUploaderId());
        assertEquals(status, upload.getStatus());
        assertEquals(createdAt, upload.getCreatedAt());
    }

    @Test
    void settersAndGetters_ShouldWorkCorrectly() {
        Upload upload = new Upload();
        UUID id = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        String s3Key = "uploads/test/file.pdf";
        String filename = "file.pdf";
        String contentType = "application/pdf";
        Long sizeBytes = 1024L;
        String uploaderId = "user-123";
        UploadStatus status = UploadStatus.RECEBIDO;
        OffsetDateTime createdAt = OffsetDateTime.now();
        OffsetDateTime completedAt = OffsetDateTime.now();

        upload.setId(id);
        upload.setProjectId(projectId);
        upload.setS3Key(s3Key);
        upload.setFilename(filename);
        upload.setContentType(contentType);
        upload.setSizeBytes(sizeBytes);
        upload.setUploaderId(uploaderId);
        upload.setStatus(status);
        upload.setCreatedAt(createdAt);
        upload.setCompletedAt(completedAt);

        assertEquals(id, upload.getId());
        assertEquals(projectId, upload.getProjectId());
        assertEquals(s3Key, upload.getS3Key());
        assertEquals(filename, upload.getFilename());
        assertEquals(contentType, upload.getContentType());
        assertEquals(sizeBytes, upload.getSizeBytes());
        assertEquals(uploaderId, upload.getUploaderId());
        assertEquals(status, upload.getStatus());
        assertEquals(createdAt, upload.getCreatedAt());
        assertEquals(completedAt, upload.getCompletedAt());
    }
}
