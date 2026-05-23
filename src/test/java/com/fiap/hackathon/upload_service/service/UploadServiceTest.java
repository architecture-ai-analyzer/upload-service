package com.fiap.hackathon.upload_service.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiap.hackathon.upload_service.adapter.persistence.UploadRepository;
import com.fiap.hackathon.upload_service.domain.Upload;
import com.fiap.hackathon.upload_service.domain.UploadStatus;
import com.fiap.hackathon.upload_service.infra.aws.SqsClientWrapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UploadServiceTest {

    @Mock
    private UploadRepository uploadRepository;

    @Mock
    private SqsClientWrapper sqsClientWrapper;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private UploadService uploadService;

    @Test
    void completeUpload_WithExistingUpload_ShouldUpdateAndReturn() {
        UUID uploadId = UUID.randomUUID();
        Upload existingUpload = new Upload();
        existingUpload.setId(uploadId);

        when(uploadRepository.findById(uploadId)).thenReturn(Optional.of(existingUpload));
        when(uploadRepository.save(any(Upload.class))).thenReturn(existingUpload);

        Upload result = uploadService.completeUpload(uploadId, "s3-key", "filename.pdf", "application/pdf", 1024L, "user1", "project-123");

        assertNotNull(result);
        assertEquals(uploadId, result.getId());
        assertEquals("s3-key", result.getS3Key());
        assertEquals("filename.pdf", result.getFilename());
        assertEquals("application/pdf", result.getContentType());
        assertEquals(1024L, result.getSizeBytes());
        assertEquals("user1", result.getUploaderId());
        assertEquals(UploadStatus.EM_PROCESSAMENTO, result.getStatus());
        assertNotNull(result.getCompletedAt());
    }

    @Test
    void completeUpload_WithNewUpload_ShouldCreateAndReturn() {
        UUID uploadId = UUID.randomUUID();

        when(uploadRepository.findById(uploadId)).thenReturn(Optional.empty());
        when(uploadRepository.save(any(Upload.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Upload result = uploadService.completeUpload(uploadId, "s3-key", "filename.pdf", "application/pdf", 1024L, "user1", "project-123");

        assertNotNull(result);
        assertEquals(uploadId, result.getId());
        assertEquals("s3-key", result.getS3Key());
        assertEquals("filename.pdf", result.getFilename());
        assertEquals("application/pdf", result.getContentType());
        assertEquals(1024L, result.getSizeBytes());
        assertEquals("user1", result.getUploaderId());
        assertEquals(UploadStatus.EM_PROCESSAMENTO, result.getStatus());
        assertNotNull(result.getCompletedAt());
        assertNotNull(result.getCreatedAt());
    }

    @Test
    void completeUpload_WithInvalidProjectId_ShouldIgnoreAndSetNull() {
        UUID uploadId = UUID.randomUUID();
        Upload existingUpload = new Upload();
        existingUpload.setId(uploadId);

        when(uploadRepository.findById(uploadId)).thenReturn(Optional.of(existingUpload));
        when(uploadRepository.save(any(Upload.class))).thenReturn(existingUpload);

        Upload result = uploadService.completeUpload(uploadId, "s3-key", "filename.pdf", "application/pdf", 1024L, "user1", "invalid-uuid");

        assertNotNull(result);
        assertNull(result.getProjectId());
    }

    @Test
    void completeUpload_WithNullProjectId_ShouldSetNull() {
        UUID uploadId = UUID.randomUUID();
        Upload existingUpload = new Upload();
        existingUpload.setId(uploadId);

        when(uploadRepository.findById(uploadId)).thenReturn(Optional.of(existingUpload));
        when(uploadRepository.save(any(Upload.class))).thenReturn(existingUpload);

        Upload result = uploadService.completeUpload(uploadId, "s3-key", "filename.pdf", "application/pdf", 1024L, "user1", null);

        assertNotNull(result);
        assertNull(result.getProjectId());
    }

    @Test
    void completeUpload_WithValidProjectId_ShouldSetProjectId() {
        UUID uploadId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Upload existingUpload = new Upload();
        existingUpload.setId(uploadId);

        when(uploadRepository.findById(uploadId)).thenReturn(Optional.of(existingUpload));
        when(uploadRepository.save(any(Upload.class))).thenReturn(existingUpload);

        Upload result = uploadService.completeUpload(uploadId, "s3-key", "filename.pdf", "application/pdf", 1024L, "user1", projectId.toString());

        assertNotNull(result);
        assertEquals(projectId, result.getProjectId());
    }

    @Test
    void completeUpload_WithBlankProjectId_ShouldSetNull() {
        UUID uploadId = UUID.randomUUID();
        Upload existingUpload = new Upload();
        existingUpload.setId(uploadId);

        when(uploadRepository.findById(uploadId)).thenReturn(Optional.of(existingUpload));
        when(uploadRepository.save(any(Upload.class))).thenReturn(existingUpload);

        Upload result = uploadService.completeUpload(uploadId, "s3-key", "filename.pdf", "application/pdf", 1024L, "user1", "   ");

        assertNotNull(result);
        assertNull(result.getProjectId());
    }

    @Test
    void completeUpload_WithQueueUrl_ShouldPublishSqsMessage() throws JsonProcessingException {
        UUID uploadId = UUID.randomUUID();
        Upload existingUpload = new Upload();
        existingUpload.setId(uploadId);

        when(uploadRepository.findById(uploadId)).thenReturn(Optional.of(existingUpload));
        when(uploadRepository.save(any(Upload.class))).thenReturn(existingUpload);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        UploadService serviceWithQueue = new UploadService(uploadRepository, sqsClientWrapper, objectMapper) {
            {
                try {
                    java.lang.reflect.Field field = UploadService.class.getDeclaredField("queueUrl");
                    field.setAccessible(true);
                    field.set(this, "test-queue-url");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
        serviceWithQueue.completeUpload(uploadId, "s3-key", "filename.pdf", "application/pdf", 1024L, "user1", "project-123");

        verify(sqsClientWrapper, times(1)).sendMessage(anyString(), anyString());
    }

    @Test
    void completeUpload_WithJsonProcessingException_ShouldThrowRuntimeException() throws JsonProcessingException {
        UUID uploadId = UUID.randomUUID();
        Upload existingUpload = new Upload();
        existingUpload.setId(uploadId);

        when(uploadRepository.findById(uploadId)).thenReturn(Optional.of(existingUpload));
        when(uploadRepository.save(any(Upload.class))).thenReturn(existingUpload);
        when(objectMapper.writeValueAsString(any())).thenThrow(new RuntimeException("Error"));

        UploadService serviceWithQueue = new UploadService(uploadRepository, sqsClientWrapper, objectMapper) {
            {
                try {
                    java.lang.reflect.Field field = UploadService.class.getDeclaredField("queueUrl");
                    field.setAccessible(true);
                    field.set(this, "test-queue-url");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };

        assertThrows(RuntimeException.class, () -> serviceWithQueue.completeUpload(uploadId, "s3-key", "filename.pdf", "application/pdf", 1024L, "user1", "project-123"));
    }

    @Test
    void getUpload_WithExistingId_ShouldReturnUpload() {
        UUID uploadId = UUID.randomUUID();
        Upload upload = new Upload();
        upload.setId(uploadId);

        when(uploadRepository.findById(uploadId)).thenReturn(Optional.of(upload));

        Optional<Upload> result = uploadService.getUpload(uploadId);

        assertTrue(result.isPresent());
        assertEquals(uploadId, result.get().getId());
    }

    @Test
    void getUpload_WithNonExistingId_ShouldReturnEmpty() {
        UUID uploadId = UUID.randomUUID();

        when(uploadRepository.findById(uploadId)).thenReturn(Optional.empty());

        Optional<Upload> result = uploadService.getUpload(uploadId);

        assertFalse(result.isPresent());
    }

    @Test
    void listUploadsByProject_ShouldReturnUploads() {
        UUID projectId = UUID.randomUUID();
        Upload upload1 = new Upload();
        upload1.setId(UUID.randomUUID());
        upload1.setProjectId(projectId);
        Upload upload2 = new Upload();
        upload2.setId(UUID.randomUUID());
        upload2.setProjectId(projectId);

        when(uploadRepository.findByProjectIdOrderByCreatedAtDesc(projectId)).thenReturn(Arrays.asList(upload1, upload2));

        var result = uploadService.listUploadsByProject(projectId);

        assertNotNull(result);
        assertEquals(2, result.size());
    }
}
