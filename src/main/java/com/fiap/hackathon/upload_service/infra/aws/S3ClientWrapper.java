package com.fiap.hackathon.upload_service.infra.aws;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import java.nio.file.Path;

@Component
public class S3ClientWrapper {

    private final S3Client s3Client;

    @Value("${application.s3.bucket:uploads-bucket}")
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
}
