package com.fiap.hackathon.upload_service.usecase;

import com.fiap.hackathon.upload_service.adapter.controller.UploadValidationException;
import com.fiap.hackathon.upload_service.adapter.dto.UploadRequest;
import com.fiap.hackathon.upload_service.adapter.dto.UploadResponse;
import com.fiap.hackathon.upload_service.adapter.persistence.UploadRepository;
import com.fiap.hackathon.upload_service.config.observability.UploadMetricsService;
import com.fiap.hackathon.upload_service.domain.Upload;
import com.fiap.hackathon.upload_service.domain.UploadStatus;
import com.fiap.hackathon.upload_service.infra.audit.AuditEventPublisher;
import com.fiap.hackathon.upload_service.infra.aws.S3ClientWrapper;
import com.fiap.hackathon.upload_service.infra.aws.SqsEventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SingleUploadUseCaseTest {

    @Mock
    private S3ClientWrapper s3ClientWrapper;

    @Mock
    private UploadRepository uploadRepository;

    @Mock
    private SqsEventPublisher sqsEventPublisher;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private FileSignatureValidator fileSignatureValidator;

    @Mock
    private AuditEventPublisher auditEventPublisher;

    @Mock
    private FilenameValidator filenameValidator;

    @Mock
    private UploadMetricsService uploadMetricsService;

    @InjectMocks
    private SingleUploadUseCase singleUploadUseCase;

    @Test
    void execute_WithNullFile_ShouldThrowException() throws IOException {
        UploadRequest request = new UploadRequest();
        request.setFile(null);
        request.setFilename("test.pdf");
        request.setProjectId("project-123");
        request.setUploaderId("user-456");

        assertThrows(UploadValidationException.class, () -> singleUploadUseCase.execute(request));
        verify(auditEventPublisher, times(1)).publishEvent(
            eq(AuditEventPublisher.EventType.FILE_VALIDATION_FAILURE),
            any(),
            any(),
            eq("POST /v1/uploads"),
            eq(AuditEventPublisher.ActionResult.FAILURE),
            anyString()
        );
    }

    @Test
    void execute_WithEmptyFile_ShouldThrowException() throws IOException {
        MultipartFile file = new MockMultipartFile("file", new byte[0]);
        UploadRequest request = new UploadRequest();
        request.setFile(file);
        request.setFilename("test.pdf");
        request.setProjectId("project-123");
        request.setUploaderId("user-456");

        assertThrows(UploadValidationException.class, () -> singleUploadUseCase.execute(request));
        verify(auditEventPublisher, times(1)).publishEvent(
            eq(AuditEventPublisher.EventType.FILE_VALIDATION_FAILURE),
            any(),
            any(),
            eq("POST /v1/uploads"),
            eq(AuditEventPublisher.ActionResult.FAILURE),
            anyString()
        );
    }

    @Test
    void execute_WithFileSizeExceeded_ShouldThrowException() throws IOException {
        byte[] largeFile = new byte[1024 * 1024 * 1024 + 1]; // 1GB + 1 byte
        MultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", largeFile);
        UploadRequest request = new UploadRequest();
        request.setFile(file);
        request.setFilename("test.pdf");
        request.setProjectId("project-123");
        request.setUploaderId("user-456");

        assertThrows(UploadValidationException.class, () -> singleUploadUseCase.execute(request));
        verify(auditEventPublisher, times(1)).publishEvent(
            eq(AuditEventPublisher.EventType.FILE_VALIDATION_FAILURE),
            any(),
            any(),
            eq("POST /v1/uploads"),
            eq(AuditEventPublisher.ActionResult.FAILURE),
            anyString()
        );
    }

    @Test
    void execute_WithInvalidContentType_ShouldThrowException() throws IOException {
        MultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "content".getBytes());
        UploadRequest request = new UploadRequest();
        request.setFile(file);
        request.setFilename("test.txt");
        request.setProjectId("project-123");
        request.setUploaderId("user-456");

        assertThrows(UploadValidationException.class, () -> singleUploadUseCase.execute(request));
        verify(auditEventPublisher, times(1)).publishEvent(
            eq(AuditEventPublisher.EventType.FILE_VALIDATION_FAILURE),
            any(),
            any(),
            eq("POST /v1/uploads"),
            eq(AuditEventPublisher.ActionResult.FAILURE),
            anyString()
        );
    }

    @Test
    void execute_WithInvalidFilename_ShouldThrowException() throws IOException {
        MultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", "content".getBytes());
        UploadRequest request = new UploadRequest();
        request.setFile(file);
        request.setFilename("../../etc/passwd");
        request.setProjectId("project-123");
        request.setUploaderId("user-456");

        when(fileSignatureValidator.detectContentType(any())).thenReturn("application/pdf");
        when(filenameValidator.isValid(any())).thenReturn(false);
        when(filenameValidator.validateAndGetError(any())).thenReturn("Invalid filename");

        assertThrows(UploadValidationException.class, () -> singleUploadUseCase.execute(request));
        verify(auditEventPublisher, times(1)).publishEvent(
            eq(AuditEventPublisher.EventType.FILE_VALIDATION_FAILURE),
            any(),
            any(),
            eq("POST /v1/uploads"),
            eq(AuditEventPublisher.ActionResult.FAILURE),
            anyString()
        );
    }

    @Test
    void execute_WithValidPdfFile_ShouldSucceed() throws IOException {
        byte[] pdfHeader = new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D, 0x00, 0x00, 0x00};
        MultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", pdfHeader);
        UploadRequest request = new UploadRequest();
        request.setFile(file);
        request.setFilename("test.pdf");
        request.setProjectId("project-123");
        request.setUploaderId("user-456");

        when(fileSignatureValidator.detectContentType(any())).thenReturn("application/pdf");
        when(filenameValidator.isValid(any())).thenReturn(true);
        when(uploadRepository.save(any(Upload.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UploadResponse response = singleUploadUseCase.execute(request);

        assertNotNull(response);
        assertNotNull(response.getUploadId());
        assertNotNull(response.getS3Key());
        verify(s3ClientWrapper, times(1)).uploadFile(anyString(), any(MultipartFile.class));
        verify(uploadRepository, times(1)).save(any(Upload.class));
        verify(uploadMetricsService, times(1)).recordUploadCreated(anyString());
        verify(auditEventPublisher, times(1)).publishEvent(
            eq(AuditEventPublisher.EventType.FILE_UPLOAD_SUCCESS),
            any(),
            any(),
            eq("POST /v1/uploads"),
            eq(AuditEventPublisher.ActionResult.SUCCESS),
            anyString()
        );
    }

    @Test
    void execute_WithS3UploadFailure_ShouldThrowRuntimeException() throws IOException {
        byte[] pdfHeader = new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D, 0x00, 0x00, 0x00};
        MultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", pdfHeader);
        UploadRequest request = new UploadRequest();
        request.setFile(file);
        request.setFilename("test.pdf");
        request.setProjectId("project-123");
        request.setUploaderId("user-456");

        when(fileSignatureValidator.detectContentType(any())).thenReturn("application/pdf");
        when(filenameValidator.isValid(any())).thenReturn(true);
        doThrow(new IOException("S3 upload failed")).when(s3ClientWrapper).uploadFile(anyString(), any(MultipartFile.class));

        assertThrows(RuntimeException.class, () -> singleUploadUseCase.execute(request));
        verify(auditEventPublisher, times(1)).publishEvent(
            eq(AuditEventPublisher.EventType.FILE_UPLOAD_FAILURE),
            any(),
            any(),
            eq("POST /v1/uploads"),
            eq(AuditEventPublisher.ActionResult.FAILURE),
            anyString()
        );
    }

    @Test
    void execute_WithNullProjectId_ShouldUseNoProject() throws IOException {
        byte[] pdfHeader = new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D, 0x00, 0x00, 0x00};
        MultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", pdfHeader);
        UploadRequest request = new UploadRequest();
        request.setFile(file);
        request.setFilename("test.pdf");
        request.setProjectId(null);
        request.setUploaderId("user-456");

        when(fileSignatureValidator.detectContentType(any())).thenReturn("application/pdf");
        when(filenameValidator.isValid(any())).thenReturn(true);
        when(uploadRepository.save(any(Upload.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UploadResponse response = singleUploadUseCase.execute(request);

        assertNotNull(response);
        assertTrue(response.getS3Key().contains("no-project"));
    }

    @Test
    void execute_WithInvalidProjectId_ShouldIgnoreAndSetNull() throws IOException {
        byte[] pdfHeader = new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D, 0x00, 0x00, 0x00};
        MultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", pdfHeader);
        UploadRequest request = new UploadRequest();
        request.setFile(file);
        request.setFilename("test.pdf");
        request.setProjectId("invalid-uuid");
        request.setUploaderId("user-456");

        when(fileSignatureValidator.detectContentType(any())).thenReturn("application/pdf");
        when(filenameValidator.isValid(any())).thenReturn(true);
        when(uploadRepository.save(any(Upload.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UploadResponse response = singleUploadUseCase.execute(request);

        assertNotNull(response);
        verify(uploadRepository, times(1)).save(any(Upload.class));
    }

    @Test
    void execute_WithBlankProjectId_ShouldIgnoreAndSetNull() throws IOException {
        byte[] pdfHeader = new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D, 0x00, 0x00, 0x00};
        MultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", pdfHeader);
        UploadRequest request = new UploadRequest();
        request.setFile(file);
        request.setFilename("test.pdf");
        request.setProjectId("   ");
        request.setUploaderId("user-456");

        when(fileSignatureValidator.detectContentType(any())).thenReturn("application/pdf");
        when(filenameValidator.isValid(any())).thenReturn(true);
        when(uploadRepository.save(any(Upload.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UploadResponse response = singleUploadUseCase.execute(request);

        assertNotNull(response);
        verify(uploadRepository, times(1)).save(any(Upload.class));
    }

    @Test
    void execute_WithNullUploaderId_ShouldUseUnknown() throws IOException {
        byte[] pdfHeader = new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D, 0x00, 0x00, 0x00};
        MultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", pdfHeader);
        UploadRequest request = new UploadRequest();
        request.setFile(file);
        request.setFilename("test.pdf");
        request.setProjectId("project-123");
        request.setUploaderId(null);

        when(fileSignatureValidator.detectContentType(any())).thenReturn("application/pdf");
        when(filenameValidator.isValid(any())).thenReturn(true);
        when(uploadRepository.save(any(Upload.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UploadResponse response = singleUploadUseCase.execute(request);

        assertNotNull(response);
        assertTrue(response.getS3Key().contains("unknown"));
    }

    @Test
    void execute_WithBlankUploaderId_ShouldUseUnknown() throws IOException {
        byte[] pdfHeader = new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D, 0x00, 0x00, 0x00};
        MultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", pdfHeader);
        UploadRequest request = new UploadRequest();
        request.setFile(file);
        request.setFilename("test.pdf");
        request.setProjectId("project-123");
        request.setUploaderId("   ");

        when(fileSignatureValidator.detectContentType(any())).thenReturn("application/pdf");
        when(filenameValidator.isValid(any())).thenReturn(true);
        when(uploadRepository.save(any(Upload.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UploadResponse response = singleUploadUseCase.execute(request);

        assertNotNull(response);
        assertTrue(response.getS3Key().contains("unknown"));
    }

    @Test
    void execute_WithPngFile_ShouldSucceed() throws IOException {
        byte[] pngHeader = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00};
        MultipartFile file = new MockMultipartFile("file", "test.png", "image/png", pngHeader);
        UploadRequest request = new UploadRequest();
        request.setFile(file);
        request.setFilename("test.png");
        request.setProjectId("project-123");
        request.setUploaderId("user-456");

        when(fileSignatureValidator.detectContentType(any())).thenReturn("image/png");
        when(filenameValidator.isValid(any())).thenReturn(true);
        when(uploadRepository.save(any(Upload.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UploadResponse response = singleUploadUseCase.execute(request);

        assertNotNull(response);
        verify(s3ClientWrapper, times(1)).uploadFile(anyString(), any(MultipartFile.class));
    }

    @Test
    void execute_WithJpgFile_ShouldSucceed() throws IOException {
        byte[] jpgHeader = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x00};
        MultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpg", jpgHeader);
        UploadRequest request = new UploadRequest();
        request.setFile(file);
        request.setFilename("test.jpg");
        request.setProjectId("project-123");
        request.setUploaderId("user-456");

        when(fileSignatureValidator.detectContentType(any())).thenReturn("image/jpg");
        when(filenameValidator.isValid(any())).thenReturn(true);
        when(uploadRepository.save(any(Upload.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UploadResponse response = singleUploadUseCase.execute(request);

        assertNotNull(response);
        verify(s3ClientWrapper, times(1)).uploadFile(anyString(), any(MultipartFile.class));
    }

    @Test
    void execute_WithJpegFile_ShouldSucceed() throws IOException {
        byte[] jpgHeader = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x00};
        MultipartFile file = new MockMultipartFile("file", "test.jpeg", "image/jpeg", jpgHeader);
        UploadRequest request = new UploadRequest();
        request.setFile(file);
        request.setFilename("test.jpeg");
        request.setProjectId("project-123");
        request.setUploaderId("user-456");

        when(fileSignatureValidator.detectContentType(any())).thenReturn("image/jpeg");
        when(filenameValidator.isValid(any())).thenReturn(true);
        when(uploadRepository.save(any(Upload.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UploadResponse response = singleUploadUseCase.execute(request);

        assertNotNull(response);
        verify(s3ClientWrapper, times(1)).uploadFile(anyString(), any(MultipartFile.class));
    }

    @Test
    void execute_WithExtensionMismatch_ShouldThrowException() throws IOException {
        byte[] pdfHeader = new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D, 0x00, 0x00, 0x00};
        MultipartFile file = new MockMultipartFile("file", "test.jpg", "application/pdf", pdfHeader);
        UploadRequest request = new UploadRequest();
        request.setFile(file);
        request.setFilename("test.jpg");
        request.setProjectId("project-123");
        request.setUploaderId("user-456");

        when(fileSignatureValidator.detectContentType(any())).thenReturn("application/pdf");
        when(filenameValidator.isValid(any())).thenReturn(true);

        assertThrows(UploadValidationException.class, () -> singleUploadUseCase.execute(request));
        verify(auditEventPublisher, times(1)).publishEvent(
            eq(AuditEventPublisher.EventType.FILE_VALIDATION_FAILURE),
            any(),
            any(),
            eq("POST /v1/uploads"),
            eq(AuditEventPublisher.ActionResult.FAILURE),
            anyString()
        );
    }

    @Test
    void execute_WithNullContentType_ShouldThrowException() throws IOException {
        byte[] pdfHeader = new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D, 0x00, 0x00, 0x00};
        MultipartFile file = new MockMultipartFile("file", "test.pdf", null, pdfHeader);
        UploadRequest request = new UploadRequest();
        request.setFile(file);
        request.setFilename("test.pdf");
        request.setProjectId("project-123");
        request.setUploaderId("user-456");

        assertThrows(UploadValidationException.class, () -> singleUploadUseCase.execute(request));
        verify(auditEventPublisher, times(1)).publishEvent(
            eq(AuditEventPublisher.EventType.FILE_VALIDATION_FAILURE),
            any(),
            any(),
            eq("POST /v1/uploads"),
            eq(AuditEventPublisher.ActionResult.FAILURE),
            anyString()
        );
    }

    @Test
    void execute_WithEmptyContentType_ShouldThrowException() throws IOException {
        byte[] pdfHeader = new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D, 0x00, 0x00, 0x00};
        MultipartFile file = new MockMultipartFile("file", "test.pdf", "", pdfHeader);
        UploadRequest request = new UploadRequest();
        request.setFile(file);
        request.setFilename("test.pdf");
        request.setProjectId("project-123");
        request.setUploaderId("user-456");

        assertThrows(UploadValidationException.class, () -> singleUploadUseCase.execute(request));
        verify(auditEventPublisher, times(1)).publishEvent(
            eq(AuditEventPublisher.EventType.FILE_VALIDATION_FAILURE),
            any(),
            any(),
            eq("POST /v1/uploads"),
            eq(AuditEventPublisher.ActionResult.FAILURE),
            anyString()
        );
    }

    @Test
    void execute_WithNullDetectedContentType_ShouldThrowException() throws IOException {
        byte[] pdfHeader = new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D, 0x00, 0x00, 0x00};
        MultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", pdfHeader);
        UploadRequest request = new UploadRequest();
        request.setFile(file);
        request.setFilename("test.pdf");
        request.setProjectId("project-123");
        request.setUploaderId("user-456");

        when(fileSignatureValidator.detectContentType(any())).thenReturn(null);

        assertThrows(UploadValidationException.class, () -> singleUploadUseCase.execute(request));
        verify(auditEventPublisher, times(1)).publishEvent(
            eq(AuditEventPublisher.EventType.FILE_VALIDATION_FAILURE),
            any(),
            any(),
            eq("POST /v1/uploads"),
            eq(AuditEventPublisher.ActionResult.FAILURE),
            anyString()
        );
    }

    @Test
    void execute_WithEmptyDetectedContentType_ShouldThrowException() throws IOException {
        byte[] pdfHeader = new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D, 0x00, 0x00, 0x00};
        MultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", pdfHeader);
        UploadRequest request = new UploadRequest();
        request.setFile(file);
        request.setFilename("test.pdf");
        request.setProjectId("project-123");
        request.setUploaderId("user-456");

        when(fileSignatureValidator.detectContentType(any())).thenReturn("");

        assertThrows(UploadValidationException.class, () -> singleUploadUseCase.execute(request));
        verify(auditEventPublisher, times(1)).publishEvent(
            eq(AuditEventPublisher.EventType.FILE_VALIDATION_FAILURE),
            any(),
            any(),
            eq("POST /v1/uploads"),
            eq(AuditEventPublisher.ActionResult.FAILURE),
            anyString()
        );
    }

    @Test
    void execute_WithJpgDeclaredAndJpegDetected_ShouldMatch() throws IOException {
        byte[] jpgHeader = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x00};
        MultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpg", jpgHeader);
        UploadRequest request = new UploadRequest();
        request.setFile(file);
        request.setFilename("test.jpg");
        request.setProjectId("project-123");
        request.setUploaderId("user-456");

        when(fileSignatureValidator.detectContentType(any())).thenReturn("image/jpeg");
        when(filenameValidator.isValid(any())).thenReturn(true);
        when(uploadRepository.save(any(Upload.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UploadResponse response = singleUploadUseCase.execute(request);

        assertNotNull(response);
        verify(s3ClientWrapper, times(1)).uploadFile(anyString(), any(MultipartFile.class));
    }

    @Test
    void execute_WithJpegDeclaredAndJpgDetected_ShouldNotMatch() throws IOException {
        byte[] jpgHeader = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x00};
        MultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpeg", jpgHeader);
        UploadRequest request = new UploadRequest();
        request.setFile(file);
        request.setFilename("test.jpg");
        request.setProjectId("project-123");
        request.setUploaderId("user-456");

        when(fileSignatureValidator.detectContentType(any())).thenReturn("image/jpg");

        assertThrows(UploadValidationException.class, () -> singleUploadUseCase.execute(request));
        verify(auditEventPublisher, times(1)).publishEvent(
            eq(AuditEventPublisher.EventType.FILE_VALIDATION_FAILURE),
            any(),
            any(),
            eq("POST /v1/uploads"),
            eq(AuditEventPublisher.ActionResult.FAILURE),
            anyString()
        );
    }

    @Test
    void execute_WithNoExtension_ShouldThrowException() throws IOException {
        byte[] pdfHeader = new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D, 0x00, 0x00, 0x00};
        MultipartFile file = new MockMultipartFile("file", "test", "application/pdf", pdfHeader);
        UploadRequest request = new UploadRequest();
        request.setFile(file);
        request.setFilename("test");
        request.setProjectId("project-123");
        request.setUploaderId("user-456");

        when(fileSignatureValidator.detectContentType(any())).thenReturn("application/pdf");
        when(filenameValidator.isValid(any())).thenReturn(true);

        assertThrows(UploadValidationException.class, () -> singleUploadUseCase.execute(request));
        verify(auditEventPublisher, times(1)).publishEvent(
            eq(AuditEventPublisher.EventType.FILE_VALIDATION_FAILURE),
            any(),
            any(),
            eq("POST /v1/uploads"),
            eq(AuditEventPublisher.ActionResult.FAILURE),
            anyString()
        );
    }

    @Test
    void execute_WithNoQueueUrl_ShouldNotPublishSqs() throws IOException {
        byte[] pdfHeader = new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D, 0x00, 0x00, 0x00};
        MultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", pdfHeader);
        UploadRequest request = new UploadRequest();
        request.setFile(file);
        request.setFilename("test.pdf");
        request.setProjectId("project-123");
        request.setUploaderId("user-456");

        when(fileSignatureValidator.detectContentType(any())).thenReturn("application/pdf");
        when(filenameValidator.isValid(any())).thenReturn(true);
        when(uploadRepository.save(any(Upload.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SingleUploadUseCase useCaseWithNoQueue = new SingleUploadUseCase(
            s3ClientWrapper,
            uploadRepository,
            sqsEventPublisher,
            objectMapper,
            fileSignatureValidator,
            auditEventPublisher,
            filenameValidator,
            uploadMetricsService
        ) {
            {
                try {
                    java.lang.reflect.Field field = SingleUploadUseCase.class.getDeclaredField("queueUrl");
                    field.setAccessible(true);
                    field.set(this, "");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };

        UploadResponse response = useCaseWithNoQueue.execute(request);

        assertNotNull(response);
        verify(sqsEventPublisher, never()).publishUploadEvent(anyString(), anyString(), anyString(), anyString());
    }
}
