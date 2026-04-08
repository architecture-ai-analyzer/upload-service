package com.fiap.hackathon.upload_service.usecase;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiap.hackathon.upload_service.adapter.dto.UploadRequest;
import com.fiap.hackathon.upload_service.adapter.dto.UploadResponse;
import com.fiap.hackathon.upload_service.adapter.persistence.UploadRepository;
import com.fiap.hackathon.upload_service.domain.Upload;
import com.fiap.hackathon.upload_service.domain.UploadStatus;
import com.fiap.hackathon.upload_service.infra.aws.S3ClientWrapper;
import com.fiap.hackathon.upload_service.infra.aws.SqsClientWrapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
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

    private final S3ClientWrapper s3ClientWrapper;
    private final UploadRepository uploadRepository;
    private final SqsClientWrapper sqsClientWrapper;
    private final ObjectMapper objectMapper;

    @Value("${application.sqs.queueUrl:}")
    private String queueUrl;

    @Value("${application.s3.bucket:upload}")
    private String bucketName;

    public SingleUploadUseCase(
            S3ClientWrapper s3ClientWrapper,
            UploadRepository uploadRepository,
            SqsClientWrapper sqsClientWrapper,
            ObjectMapper objectMapper) {
        this.s3ClientWrapper = s3ClientWrapper;
        this.uploadRepository = uploadRepository;
        this.sqsClientWrapper = sqsClientWrapper;
        this.objectMapper = objectMapper;
    }

    public UploadResponse execute(UploadRequest request) throws IOException {
        MultipartFile file = request.getFile();
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is required");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("File size exceeds 1 GiB");
        }
        String fileContentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        if (!ALLOWED_CONTENT_TYPES.contains(fileContentType)) {
            throw new IllegalArgumentException("Unsupported content type");
        }

        // Generate upload metadata
        UUID uploadId = UUID.randomUUID();
        String ext = extractFileExtension(request.getFilename());
        String date = LocalDate.now().toString();
        String s3Key = generateS3Key(request.getProjectId(), date, request.getUploaderId(), uploadId, ext);
        
        // Upload file to S3 synchronously
        long fileSize = file.getSize();
        
        try {
            s3ClientWrapper.uploadFile(s3Key, file);
        } catch (IOException e) {
            throw new RuntimeException("Failed to upload file to S3: " + e.getMessage(), e);
        }

        // Save upload metadata to database with COMPLETED status
        Upload upload = new Upload();
        upload.setId(uploadId);
        upload.setS3Key(s3Key);
        upload.setFilename(request.getFilename());
        upload.setContentType(fileContentType);
        upload.setSizeBytes(fileSize);
        upload.setUploaderId(request.getUploaderId());
        
        if (request.getProjectId() != null && !request.getProjectId().isBlank()) {
            try {
                upload.setProjectId(UUID.fromString(request.getProjectId()));
            } catch (Exception e) {
                // ignore invalid uuid; leave null
            }
        }
        
        upload.setStatus(UploadStatus.COMPLETED);
        upload.setCreatedAt(OffsetDateTime.now());
        upload.setCompletedAt(OffsetDateTime.now());
        
        uploadRepository.save(upload);

        // Publish SQS event
        if (queueUrl != null && !queueUrl.isBlank()) {
            var payload = new UploadEvent(
                    uploadId.toString(),
                    s3Key,
                    fileContentType,
                    fileSize,
                    request.getUploaderId(),
                    request.getProjectId()
            );
            try {
                String body = objectMapper.writeValueAsString(payload);
                sqsClientWrapper.sendMessage(queueUrl, body);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to publish upload event: " + e.getMessage(), e);
            }
        }

        // Return response with upload confirmation
        return new UploadResponse(uploadId.toString(), null, s3Key, 0);
    }

    private String extractFileExtension(String filename) {
        if (filename != null && filename.contains(".")) {
            return filename.substring(filename.lastIndexOf('.') + 1);
        }
        return "bin";
    }

    private String generateS3Key(String projectId, String date, String uploaderId, UUID uploadId, String ext) {
        String proj = projectId == null || projectId.isBlank() ? "no-project" : projectId;
        String user = uploaderId == null || uploaderId.isBlank() ? "unknown" : uploaderId;
        String bucketPrefix = (bucketName == null || bucketName.isBlank()) ? "upload" : bucketName;
        return String.format("upload/%s/%s/%s/%s/%s.%s", bucketPrefix, proj, date, user, uploadId, ext);
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
