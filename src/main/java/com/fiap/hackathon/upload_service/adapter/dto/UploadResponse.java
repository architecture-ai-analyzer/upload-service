package com.fiap.hackathon.upload_service.adapter.dto;

public class UploadResponse {

    private String uploadId;
    private String presignedUrl;
    private String s3Key;
    private int expiresInSeconds;

    public UploadResponse() {}

    public UploadResponse(String uploadId, String presignedUrl, String s3Key, int expiresInSeconds) {
        this.uploadId = uploadId;
        this.presignedUrl = presignedUrl;
        this.s3Key = s3Key;
        this.expiresInSeconds = expiresInSeconds;
    }

    public String getUploadId() {
        return uploadId;
    }

    public void setUploadId(String uploadId) {
        this.uploadId = uploadId;
    }

    public String getPresignedUrl() {
        return presignedUrl;
    }

    public void setPresignedUrl(String presignedUrl) {
        this.presignedUrl = presignedUrl;
    }

    public String getS3Key() {
        return s3Key;
    }

    public void setS3Key(String s3Key) {
        this.s3Key = s3Key;
    }

    public int getExpiresInSeconds() {
        return expiresInSeconds;
    }

    public void setExpiresInSeconds(int expiresInSeconds) {
        this.expiresInSeconds = expiresInSeconds;
    }
}
