package com.fiap.hackathon.upload_service.adapter.controller;

import com.fiap.hackathon.upload_service.adapter.dto.UploadRequest;
import com.fiap.hackathon.upload_service.adapter.dto.UploadResponse;
import com.fiap.hackathon.upload_service.usecase.CreateUploadUseCase;
import com.fiap.hackathon.upload_service.adapter.dto.CompleteUploadRequest;
import com.fiap.hackathon.upload_service.domain.Upload;
import com.fiap.hackathon.upload_service.service.UploadService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/uploads")
public class UploadController {

    private final CreateUploadUseCase createUploadUseCase;
    private final UploadService uploadService;

    public UploadController(CreateUploadUseCase createUploadUseCase, UploadService uploadService) {
        this.createUploadUseCase = createUploadUseCase;
        this.uploadService = uploadService;
    }

    @PostMapping
    public ResponseEntity<UploadResponse> create(@Valid @RequestBody UploadRequest request) {
        UploadResponse resp = createUploadUseCase.create(request);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/{uploadId}/complete")
    public ResponseEntity<Upload> complete(@PathVariable("uploadId") String uploadId, @Valid @RequestBody CompleteUploadRequest req) {
        Upload up = uploadService.completeUpload(java.util.UUID.fromString(uploadId), req.getS3Key(), req.getFilename(), req.getContentType(), req.getSizeBytes(), req.getUploaderId(), req.getProjectId());
        return ResponseEntity.ok(up);
    }
}
