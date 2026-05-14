package com.fiap.hackathon.upload_service.usecase;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiap.hackathon.upload_service.adapter.controller.UploadValidationException;
import com.fiap.hackathon.upload_service.adapter.dto.UploadRequest;
import com.fiap.hackathon.upload_service.adapter.dto.UploadResponse;
import com.fiap.hackathon.upload_service.adapter.persistence.UploadRepository;
import com.fiap.hackathon.upload_service.domain.Upload;
import com.fiap.hackathon.upload_service.domain.UploadStatus;
import com.fiap.hackathon.upload_service.infra.aws.S3ClientWrapper;
import com.fiap.hackathon.upload_service.infra.aws.SqsEventPublisher;
import com.fiap.hackathon.upload_service.infra.audit.AuditEventPublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class SingleUploadUseCase {

    private static final long MAX_FILE_SIZE_BYTES = 1024L * 1024L * 1024L; // 1 GiB
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "image/png",
            "image/jpg",
            "image/jpeg"
    );

    private static final Map<String, Set<String>> CONTENT_TYPE_TO_EXTENSIONS = Map.of(
            "application/pdf", Set.of("pdf"),
            "image/png", Set.of("png"),
            "image/jpg", Set.of("jpg"),
            "image/jpeg", Set.of("jpg", "jpeg")
    );

    private final S3ClientWrapper s3ClientWrapper;
    private final UploadRepository uploadRepository;
    private final SqsEventPublisher sqsEventPublisher;
    private final ObjectMapper objectMapper;
    private final FileSignatureValidator fileSignatureValidator;
    private final AuditEventPublisher auditEventPublisher;
    private final FilenameValidator filenameValidator;

    @Value("${application.sqs.queueUrl:}")
    private String queueUrl;

    @Value("${application.s3.bucket:upload}")
    private String bucketName;

    public SingleUploadUseCase(
            S3ClientWrapper s3ClientWrapper,
            UploadRepository uploadRepository,
            SqsEventPublisher sqsEventPublisher,
            ObjectMapper objectMapper,
            FileSignatureValidator fileSignatureValidator,
            AuditEventPublisher auditEventPublisher,
            FilenameValidator filenameValidator) {
        this.s3ClientWrapper = s3ClientWrapper;
        this.uploadRepository = uploadRepository;
        this.sqsEventPublisher = sqsEventPublisher;
        this.objectMapper = objectMapper;
        this.fileSignatureValidator = fileSignatureValidator;
        this.auditEventPublisher = auditEventPublisher;
        this.filenameValidator = filenameValidator;
    }

    public UploadResponse execute(UploadRequest request) throws IOException {
        String userId = getCurrentUserId();
        String clientIp = getClientIp();
        
        MultipartFile file = request.getFile();
        if (file == null || file.isEmpty()) {
            auditEventPublisher.publishEvent(
                AuditEventPublisher.EventType.FILE_VALIDATION_FAILURE,
                userId,
                clientIp,
                "POST /v1/uploads",
                AuditEventPublisher.ActionResult.FAILURE,
                "File part is required and cannot be empty"
            );
            throw new UploadValidationException("FILE_REQUIRED", "File part is required and cannot be empty");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            auditEventPublisher.publishEvent(
                AuditEventPublisher.EventType.FILE_VALIDATION_FAILURE,
                userId,
                clientIp,
                "POST /v1/uploads",
                AuditEventPublisher.ActionResult.FAILURE,
                "File size " + file.getSize() + " bytes exceeds maximum allowed size of 1GB"
            );
            throw new UploadValidationException("FILE_SIZE_EXCEEDED", "File size exceeds maximum allowed size of 1GB");
        }
        String fileContentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        if (!ALLOWED_CONTENT_TYPES.contains(fileContentType)) {
            auditEventPublisher.publishEvent(
                AuditEventPublisher.EventType.FILE_VALIDATION_FAILURE,
                userId,
                clientIp,
                "POST /v1/uploads",
                AuditEventPublisher.ActionResult.FAILURE,
                "Invalid content type: " + fileContentType
            );
            throw new UploadValidationException("INVALID_CONTENT_TYPE", "Only PDF, PNG, JPG or JPEG files are allowed");
        }

        String detectedContentType = fileSignatureValidator.detectContentType(file);
        if (!matchesDeclaredContentType(fileContentType, detectedContentType)) {
            auditEventPublisher.publishEvent(
                AuditEventPublisher.EventType.FILE_VALIDATION_FAILURE,
                userId,
                clientIp,
                "POST /v1/uploads",
                AuditEventPublisher.ActionResult.FAILURE,
                "MIME signature mismatch: declared=" + fileContentType + ", detected=" + detectedContentType
            );
            throw new UploadValidationException(
                    "MIME_SIGNATURE_MISMATCH",
                    "Detected file signature does not match declared content type"
            );
        }

        String filename = request.getFilename();
        
        // Validate filename to prevent path traversal and injection attacks
        if (!filenameValidator.isValid(filename)) {
            String errorDetails = filenameValidator.validateAndGetError(filename);
            auditEventPublisher.publishEvent(
                AuditEventPublisher.EventType.FILE_VALIDATION_FAILURE,
                userId,
                clientIp,
                "POST /v1/uploads",
                AuditEventPublisher.ActionResult.FAILURE,
                "Invalid filename: " + errorDetails
            );
            throw new UploadValidationException(
                    "INVALID_FILENAME",
                    errorDetails != null ? errorDetails : "Filename contains invalid characters"
            );
        }
        
        String ext = extractFileExtension(filename);
        if (!isExtensionAllowedForContentType(ext, fileContentType)) {
            auditEventPublisher.publishEvent(
                AuditEventPublisher.EventType.FILE_VALIDATION_FAILURE,
                userId,
                clientIp,
                "POST /v1/uploads",
                AuditEventPublisher.ActionResult.FAILURE,
                "Extension " + ext + " does not match content type " + fileContentType
            );
            throw new UploadValidationException(
                    "EXTENSION_CONTENT_TYPE_MISMATCH",
                    "File extension does not match content type"
            );
        }

        // Generate upload metadata
        UUID uploadId = UUID.randomUUID();
        String s3Key = generateS3Key(request.getProjectId(), request.getUploaderId(), uploadId, ext);
        
        // Upload file to S3 synchronously
        long fileSize = file.getSize();
        
        try {
            s3ClientWrapper.uploadFile(s3Key, file);
        } catch (IOException e) {
            auditEventPublisher.publishEvent(
                AuditEventPublisher.EventType.FILE_UPLOAD_FAILURE,
                userId,
                clientIp,
                "POST /v1/uploads",
                AuditEventPublisher.ActionResult.FAILURE,
                "Failed to upload file to S3: " + e.getMessage()
            );
            throw new RuntimeException("Failed to upload file to S3: " + e.getMessage(), e);
        }

        // Save upload metadata to database with PENDING status (awaiting AI analysis)
        Upload upload = new Upload();
        upload.setId(uploadId);
        upload.setS3Key(s3Key);
        upload.setFilename(request.getFilename());
        upload.setContentType(fileContentType);
        upload.setSizeBytes(fileSize);
        upload.setUploaderId(request.getUploaderId());
        upload.setTemplateId(request.getTemplateId());
        
        if (request.getProjectId() != null && !request.getProjectId().isBlank()) {
            try {
                upload.setProjectId(UUID.fromString(request.getProjectId()));
            } catch (Exception e) {
                // ignore invalid uuid; leave null
            }
        }
        
        upload.setStatus(UploadStatus.PENDING);
        upload.setCreatedAt(OffsetDateTime.now());
        upload.setCompletedAt(OffsetDateTime.now());
        
        uploadRepository.save(upload);

        // Publish SQS event with retry + DLQ fallback
        if (queueUrl != null && !queueUrl.isBlank()) {
            var payload = new UploadEvent(
                    uploadId.toString(),
                    s3Key,
                    fileContentType,
                    fileSize,
                    request.getUploaderId(),
                    request.getProjectId(),
                    request.getTemplateId()
            );
            try {
                String body = objectMapper.writeValueAsString(payload);
                sqsEventPublisher.publishUploadEvent(queueUrl, body, uploadId.toString(), request.getUploaderId());
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to serialize upload event: " + e.getMessage(), e);
            }
        }

        // Log successful upload
        auditEventPublisher.publishEvent(
            AuditEventPublisher.EventType.FILE_UPLOAD_SUCCESS,
            userId,
            clientIp,
            "POST /v1/uploads",
            AuditEventPublisher.ActionResult.SUCCESS,
            "File uploaded successfully - uploadId=" + uploadId + ", size=" + fileSize + " bytes, type=" + fileContentType
        );

        // Return response with upload confirmation
        return new UploadResponse(uploadId.toString(), null, s3Key, 0);
    }

    private String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            return authentication.getName();
        }
        return null;
    }

    private String getClientIp() {
        // This would be filled by filter if request available
        // For now, return null (filter handles it)
        return null;
    }

    private String extractFileExtension(String filename) {
        if (filename != null && filename.contains(".")) {
            return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
        }
        return "";
    }

    private boolean isExtensionAllowedForContentType(String extension, String contentType) {
        if (extension == null || extension.isBlank() || contentType == null || contentType.isBlank()) {
            return false;
        }
        Set<String> allowedExtensions = CONTENT_TYPE_TO_EXTENSIONS.get(contentType.toLowerCase());
        return allowedExtensions != null && allowedExtensions.contains(extension.toLowerCase());
    }

    private boolean matchesDeclaredContentType(String declaredContentType, String detectedContentType) {
        if (declaredContentType == null || declaredContentType.isBlank()) {
            return false;
        }
        if (detectedContentType == null || detectedContentType.isBlank()) {
            return false;
        }

        if (declaredContentType.equals(detectedContentType)) {
            return true;
        }

        return "image/jpg".equals(declaredContentType) && "image/jpeg".equals(detectedContentType);
    }

    private String generateS3Key(String projectId, String uploaderId, UUID uploadId, String ext) {
        String proj = projectId == null || projectId.isBlank() ? "no-project" : projectId;
        String user = uploaderId == null || uploaderId.isBlank() ? "unknown" : uploaderId;
        String bucketPrefix = (bucketName == null || bucketName.isBlank()) ? "upload" : bucketName;
        return String.format("%s/projects/%s/%s/%s.%s", bucketPrefix, proj, user, uploadId, ext);
    }

    static class UploadEvent {
        public String eventId;
        public String s3Key;
        public String contentType;
        public Long sizeBytes;
        public String uploaderId;
        public String projectId;
        public String templateId;

        public UploadEvent(String eventId, String s3Key, String contentType, Long sizeBytes, String uploaderId, String projectId, String templateId) {
            this.eventId = eventId;
            this.s3Key = s3Key;
            this.contentType = contentType;
            this.sizeBytes = sizeBytes;
            this.uploaderId = uploaderId;
            this.projectId = projectId;
            this.templateId = templateId;
        }
    }
}
