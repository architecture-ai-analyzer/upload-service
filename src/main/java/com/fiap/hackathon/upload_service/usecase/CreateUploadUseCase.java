package com.fiap.hackathon.upload_service.usecase;

import com.fiap.hackathon.upload_service.adapter.dto.UploadRequest;
import com.fiap.hackathon.upload_service.adapter.dto.UploadResponse;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.UUID;

@Service
public class CreateUploadUseCase {

    public UploadResponse create(UploadRequest req) {
        String uuid = UUID.randomUUID().toString();
        String ext = "bin";
        if (req.getFilename() != null && req.getFilename().contains(".")) {
            ext = req.getFilename().substring(req.getFilename().lastIndexOf('.') + 1);
        }
        String date = LocalDate.now().toString();
        String proj = req.getProjectId() == null ? "no-project" : req.getProjectId();
        String key = String.format("uploads/%s/%s/%s/%s/%s.%s", "dev", proj, date, req.getUploaderId() == null ? "unknown" : req.getUploaderId(), uuid, ext);
        // presigned URL generation removed; client or another flow should upload to S3. Return uploadId and s3Key
        return new UploadResponse(uuid, null, key, 0);
    }
}
