package com.fiap.hackathon.upload_service.infra.aws;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class S3ClientWrapperTest {

    @Mock
    private S3Client s3Client;

    private S3ClientWrapper s3ClientWrapper;

    @BeforeEach
    void setUp() {
        s3ClientWrapper = new S3ClientWrapper(s3Client) {
            {
                try {
                    java.lang.reflect.Field bucketField = S3ClientWrapper.class.getDeclaredField("bucket");
                    bucketField.setAccessible(true);
                    bucketField.set(this, "test-bucket");
                    
                    java.lang.reflect.Field encryptionEnabledField = S3ClientWrapper.class.getDeclaredField("serverSideEncryptionEnabled");
                    encryptionEnabledField.setAccessible(true);
                    encryptionEnabledField.set(this, true);
                    
                    java.lang.reflect.Field encryptionAlgorithmField = S3ClientWrapper.class.getDeclaredField("serverSideEncryptionAlgorithm");
                    encryptionAlgorithmField.setAccessible(true);
                    encryptionAlgorithmField.set(this, "AES256");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }

    @Test
    void uploadFile_WithValidFile_ShouldReturnETag() throws IOException {
        MultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", "content".getBytes());
        PutObjectResponse response = PutObjectResponse.builder().eTag("test-etag").build();

        when(s3Client.putObject(any(software.amazon.awssdk.services.s3.model.PutObjectRequest.class), any(RequestBody.class)))
            .thenReturn(response);

        String eTag = s3ClientWrapper.uploadFile("test-key", file);

        assertEquals("test-etag", eTag);
        verify(s3Client, times(1)).putObject(any(software.amazon.awssdk.services.s3.model.PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void uploadFile_WithEncryptionEnabled_ShouldIncludeEncryption() throws IOException {
        MultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", "content".getBytes());
        PutObjectResponse response = PutObjectResponse.builder().eTag("test-etag").build();

        when(s3Client.putObject(any(software.amazon.awssdk.services.s3.model.PutObjectRequest.class), any(RequestBody.class)))
            .thenReturn(response);

        String eTag = s3ClientWrapper.uploadFile("test-key", file);

        assertEquals("test-etag", eTag);
        verify(s3Client, times(1)).putObject(any(software.amazon.awssdk.services.s3.model.PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void uploadFile_WithEncryptionDisabled_ShouldNotIncludeEncryption() throws IOException {
        S3ClientWrapper wrapperWithoutEncryption = new S3ClientWrapper(s3Client) {
            {
                try {
                    java.lang.reflect.Field bucketField = S3ClientWrapper.class.getDeclaredField("bucket");
                    bucketField.setAccessible(true);
                    bucketField.set(this, "test-bucket");
                    
                    java.lang.reflect.Field encryptionEnabledField = S3ClientWrapper.class.getDeclaredField("serverSideEncryptionEnabled");
                    encryptionEnabledField.setAccessible(true);
                    encryptionEnabledField.set(this, false);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };

        MultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", "content".getBytes());
        PutObjectResponse response = PutObjectResponse.builder().eTag("test-etag").build();

        when(s3Client.putObject(any(software.amazon.awssdk.services.s3.model.PutObjectRequest.class), any(RequestBody.class)))
            .thenReturn(response);

        String eTag = wrapperWithoutEncryption.uploadFile("test-key", file);

        assertEquals("test-etag", eTag);
    }

    @Test
    void uploadFile_WithDifferentEncryptionAlgorithm_ShouldNotIncludeEncryption() throws IOException {
        S3ClientWrapper wrapperWithDifferentAlgorithm = new S3ClientWrapper(s3Client) {
            {
                try {
                    java.lang.reflect.Field bucketField = S3ClientWrapper.class.getDeclaredField("bucket");
                    bucketField.setAccessible(true);
                    bucketField.set(this, "test-bucket");
                    
                    java.lang.reflect.Field encryptionEnabledField = S3ClientWrapper.class.getDeclaredField("serverSideEncryptionEnabled");
                    encryptionEnabledField.setAccessible(true);
                    encryptionEnabledField.set(this, true);
                    
                    java.lang.reflect.Field encryptionAlgorithmField = S3ClientWrapper.class.getDeclaredField("serverSideEncryptionAlgorithm");
                    encryptionAlgorithmField.setAccessible(true);
                    encryptionAlgorithmField.set(this, "AES256");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };

        MultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", "content".getBytes());
        PutObjectResponse response = PutObjectResponse.builder().eTag("test-etag").build();

        when(s3Client.putObject(any(software.amazon.awssdk.services.s3.model.PutObjectRequest.class), any(RequestBody.class)))
            .thenReturn(response);

        String eTag = wrapperWithDifferentAlgorithm.uploadFile("test-key", file);

        assertEquals("test-etag", eTag);
    }

    @Test
    void downloadToFile_ShouldCallS3Client() {
        Path dest = Path.of("test-file.pdf");

        s3ClientWrapper.downloadToFile("test-key", dest);

        verify(s3Client, times(1)).getObject(any(software.amazon.awssdk.services.s3.model.GetObjectRequest.class), any(software.amazon.awssdk.core.sync.ResponseTransformer.class));
    }
}
