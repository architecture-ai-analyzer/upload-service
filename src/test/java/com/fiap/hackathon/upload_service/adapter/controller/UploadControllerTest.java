package com.fiap.hackathon.upload_service.adapter.controller;

import com.fiap.hackathon.upload_service.adapter.dto.UploadRequest;
import com.fiap.hackathon.upload_service.adapter.dto.UploadResponse;
import com.fiap.hackathon.upload_service.domain.Upload;
import com.fiap.hackathon.upload_service.domain.UploadStatus;
import com.fiap.hackathon.upload_service.service.UploadService;
import com.fiap.hackathon.upload_service.usecase.SingleUploadUseCase;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.util.GlobalTracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UploadControllerTest {

    @Mock
    private SingleUploadUseCase singleUploadUseCase;

    @Mock
    private UploadService uploadService;

    @Mock
    private Span span;

    @Mock
    private Tracer tracer;

    private UploadController uploadController;

    @BeforeEach
    void setUp() {
        uploadController = new UploadController(singleUploadUseCase, uploadService);
    }

    @Test
    void upload_WithValidFile_ShouldReturn201() throws IOException {
        byte[] pdfHeader = new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D, 0x00, 0x00, 0x00};
        MultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", pdfHeader);
        UploadRequest metadata = new UploadRequest();
        metadata.setFilename("test.pdf");
        metadata.setProjectId("project-123");
        metadata.setUploaderId("user-456");

        UploadResponse response = new UploadResponse();
        response.setUploadId(UUID.randomUUID().toString());
        response.setS3Key("s3-key");

        when(singleUploadUseCase.execute(any(UploadRequest.class))).thenReturn(response);
        when(tracer.activeSpan()).thenReturn(span);
        when(span.setTag(anyString(), anyString())).thenReturn(span);

        try (MockedStatic<GlobalTracer> mockedTracer = mockStatic(GlobalTracer.class)) {
            mockedTracer.when(GlobalTracer::get).thenReturn(tracer);

            ResponseEntity<UploadResponse> result = uploadController.upload(file, metadata);

            assertEquals(HttpStatus.CREATED, result.getStatusCode());
            assertEquals(response, result.getBody());
        }
    }

    @Test
    void upload_WithNullFile_ShouldThrowException() {
        UploadRequest metadata = new UploadRequest();
        metadata.setFilename("test.pdf");
        metadata.setProjectId("project-123");
        metadata.setUploaderId("user-456");

        when(tracer.activeSpan()).thenReturn(null);
        try (MockedStatic<GlobalTracer> mockedTracer = mockStatic(GlobalTracer.class)) {
            mockedTracer.when(GlobalTracer::get).thenReturn(tracer);
            assertThrows(UploadValidationException.class, () -> uploadController.upload(null, metadata));
        }
    }

    @Test
    void upload_WithEmptyFile_ShouldThrowException() {
        MultipartFile file = new MockMultipartFile("file", new byte[0]);
        UploadRequest metadata = new UploadRequest();
        metadata.setFilename("test.pdf");
        metadata.setProjectId("project-123");
        metadata.setUploaderId("user-456");

        when(tracer.activeSpan()).thenReturn(null);
        try (MockedStatic<GlobalTracer> mockedTracer = mockStatic(GlobalTracer.class)) {
            mockedTracer.when(GlobalTracer::get).thenReturn(tracer);
            assertThrows(UploadValidationException.class, () -> uploadController.upload(file, metadata));
        }
    }

    @Test
    void upload_WithFileSizeExceeded_ShouldThrowException() {
        byte[] largeFile = new byte[8 * 1024 * 1024 + 1];
        MultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", largeFile);
        UploadRequest metadata = new UploadRequest();
        metadata.setFilename("test.pdf");
        metadata.setProjectId("project-123");
        metadata.setUploaderId("user-456");

        when(tracer.activeSpan()).thenReturn(null);
        try (MockedStatic<GlobalTracer> mockedTracer = mockStatic(GlobalTracer.class)) {
            mockedTracer.when(GlobalTracer::get).thenReturn(tracer);
            assertThrows(UploadValidationException.class, () -> uploadController.upload(file, metadata));
        }
    }

    @Test
    void upload_WithInvalidContentType_ShouldThrowException() {
        MultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "content".getBytes());
        UploadRequest metadata = new UploadRequest();
        metadata.setFilename("test.txt");
        metadata.setProjectId("project-123");
        metadata.setUploaderId("user-456");

        when(tracer.activeSpan()).thenReturn(null);
        try (MockedStatic<GlobalTracer> mockedTracer = mockStatic(GlobalTracer.class)) {
            mockedTracer.when(GlobalTracer::get).thenReturn(tracer);
            assertThrows(UploadValidationException.class, () -> uploadController.upload(file, metadata));
        }
    }

    @Test
    void upload_WithNullContentType_ShouldThrowException() {
        MultipartFile file = new MockMultipartFile("file", "test.pdf", null, "content".getBytes());
        UploadRequest metadata = new UploadRequest();
        metadata.setFilename("test.pdf");
        metadata.setProjectId("project-123");
        metadata.setUploaderId("user-456");

        when(tracer.activeSpan()).thenReturn(null);
        try (MockedStatic<GlobalTracer> mockedTracer = mockStatic(GlobalTracer.class)) {
            mockedTracer.when(GlobalTracer::get).thenReturn(tracer);
            assertThrows(UploadValidationException.class, () -> uploadController.upload(file, metadata));
        }
    }

    @Test
    void upload_WithIOException_ShouldThrowException() throws IOException {
        MultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", "content".getBytes());
        UploadRequest metadata = new UploadRequest();
        metadata.setFilename("test.pdf");
        metadata.setProjectId("project-123");
        metadata.setUploaderId("user-456");

        when(singleUploadUseCase.execute(any(UploadRequest.class))).thenThrow(new IOException("Error"));

        when(tracer.activeSpan()).thenReturn(null);
        try (MockedStatic<GlobalTracer> mockedTracer = mockStatic(GlobalTracer.class)) {
            mockedTracer.when(GlobalTracer::get).thenReturn(tracer);
            assertThrows(UploadValidationException.class, () -> uploadController.upload(file, metadata));
        }
    }

    @Test
    void listByProject_WithValidProjectId_ShouldReturnUploads() {
        UUID projectId = UUID.randomUUID();
        Upload upload1 = new Upload();
        upload1.setId(UUID.randomUUID());
        upload1.setProjectId(projectId);
        Upload upload2 = new Upload();
        upload2.setId(UUID.randomUUID());
        upload2.setProjectId(projectId);

        when(uploadService.listUploadsByProject(projectId)).thenReturn(List.of(upload1, upload2));

        ResponseEntity<List<Upload>> result = uploadController.listByProject(projectId);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals(2, result.getBody().size());
    }

    @Test
    void getById_WithValidUploadId_ShouldReturnUpload() {
        UUID uploadId = UUID.randomUUID();
        Upload upload = new Upload();
        upload.setId(uploadId);
        upload.setStatus(UploadStatus.EM_PROCESSAMENTO);

        when(uploadService.getUpload(uploadId)).thenReturn(Optional.of(upload));

        ResponseEntity<Upload> result = uploadController.getById(uploadId.toString());

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals(uploadId, result.getBody().getId());
    }

    @Test
    void getById_WithInvalidUploadId_ShouldThrowException() {
        UUID uploadId = UUID.randomUUID();

        when(uploadService.getUpload(uploadId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> uploadController.getById(uploadId.toString()));
    }

    @Test
    void upload_WithPngFile_ShouldReturn201() throws IOException {
        byte[] pngHeader = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00};
        MultipartFile file = new MockMultipartFile("file", "test.png", "image/png", pngHeader);
        UploadRequest metadata = new UploadRequest();
        metadata.setFilename("test.png");
        metadata.setProjectId("project-123");
        metadata.setUploaderId("user-456");

        UploadResponse response = new UploadResponse();
        response.setUploadId(UUID.randomUUID().toString());
        response.setS3Key("s3-key");

        when(singleUploadUseCase.execute(any(UploadRequest.class))).thenReturn(response);
        when(tracer.activeSpan()).thenReturn(span);
        when(span.setTag(anyString(), anyString())).thenReturn(span);

        try (MockedStatic<GlobalTracer> mockedTracer = mockStatic(GlobalTracer.class)) {
            mockedTracer.when(GlobalTracer::get).thenReturn(tracer);

            ResponseEntity<UploadResponse> result = uploadController.upload(file, metadata);

            assertEquals(HttpStatus.CREATED, result.getStatusCode());
            assertEquals(response, result.getBody());
        }
    }

    @Test
    void upload_WithJpgFile_ShouldReturn201() throws IOException {
        byte[] jpgHeader = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x00};
        MultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpg", jpgHeader);
        UploadRequest metadata = new UploadRequest();
        metadata.setFilename("test.jpg");
        metadata.setProjectId("project-123");
        metadata.setUploaderId("user-456");

        UploadResponse response = new UploadResponse();
        response.setUploadId(UUID.randomUUID().toString());
        response.setS3Key("s3-key");

        when(singleUploadUseCase.execute(any(UploadRequest.class))).thenReturn(response);
        when(tracer.activeSpan()).thenReturn(span);
        when(span.setTag(anyString(), anyString())).thenReturn(span);

        try (MockedStatic<GlobalTracer> mockedTracer = mockStatic(GlobalTracer.class)) {
            mockedTracer.when(GlobalTracer::get).thenReturn(tracer);

            ResponseEntity<UploadResponse> result = uploadController.upload(file, metadata);

            assertEquals(HttpStatus.CREATED, result.getStatusCode());
            assertEquals(response, result.getBody());
        }
    }

    @Test
    void upload_WithJpegFile_ShouldReturn201() throws IOException {
        byte[] jpgHeader = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x00};
        MultipartFile file = new MockMultipartFile("file", "test.jpeg", "image/jpeg", jpgHeader);
        UploadRequest metadata = new UploadRequest();
        metadata.setFilename("test.jpeg");
        metadata.setProjectId("project-123");
        metadata.setUploaderId("user-456");

        UploadResponse response = new UploadResponse();
        response.setUploadId(UUID.randomUUID().toString());
        response.setS3Key("s3-key");

        when(singleUploadUseCase.execute(any(UploadRequest.class))).thenReturn(response);
        when(tracer.activeSpan()).thenReturn(span);
        when(span.setTag(anyString(), anyString())).thenReturn(span);

        try (MockedStatic<GlobalTracer> mockedTracer = mockStatic(GlobalTracer.class)) {
            mockedTracer.when(GlobalTracer::get).thenReturn(tracer);

            ResponseEntity<UploadResponse> result = uploadController.upload(file, metadata);

            assertEquals(HttpStatus.CREATED, result.getStatusCode());
            assertEquals(response, result.getBody());
        }
    }

    @Test
    void upload_WithNullSpan_ShouldStillWork() throws IOException {
        byte[] pdfHeader = new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D, 0x00, 0x00, 0x00};
        MultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", pdfHeader);
        UploadRequest metadata = new UploadRequest();
        metadata.setFilename("test.pdf");
        metadata.setProjectId("project-123");
        metadata.setUploaderId("user-456");

        UploadResponse response = new UploadResponse();
        response.setUploadId(UUID.randomUUID().toString());
        response.setS3Key("s3-key");

        when(singleUploadUseCase.execute(any(UploadRequest.class))).thenReturn(response);
        when(tracer.activeSpan()).thenReturn(null);

        try (MockedStatic<GlobalTracer> mockedTracer = mockStatic(GlobalTracer.class)) {
            mockedTracer.when(GlobalTracer::get).thenReturn(tracer);

            ResponseEntity<UploadResponse> result = uploadController.upload(file, metadata);

            assertEquals(HttpStatus.CREATED, result.getStatusCode());
            assertEquals(response, result.getBody());
        }
    }
}
