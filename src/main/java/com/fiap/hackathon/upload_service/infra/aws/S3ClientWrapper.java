package com.fiap.hackathon.upload_service.infra.aws;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.nio.file.Path;

@Component
public class S3ClientWrapper {

    private final S3Client s3Client;

    @Value("${application.s3.bucket:upload}")
    private String bucket;

    public S3ClientWrapper(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    public void downloadToFile(String key, Path dest) {
        GetObjectRequest getReq = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();
        s3Client.getObject(getReq, ResponseTransformer.toFile(dest));
    }

    public String uploadFile(String key, MultipartFile file) throws IOException {
        RequestBody requestBody = RequestBody.fromInputStream(
                file.getInputStream(),
                file.getSize()
        );
        
        PutObjectRequest putReq = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(file.getContentType())
                .contentLength(file.getSize())
                .build();
        
        var response = s3Client.putObject(putReq, requestBody);
        return response.eTag();
    }
}
