package com.fiap.hackathon.upload_service.usecase;

import com.fiap.hackathon.upload_service.adapter.dto.UploadRequest;
import com.fiap.hackathon.upload_service.adapter.dto.UploadResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CreateUploadUseCaseTest {

    @Test
    void create_WithFilenameWithExtension_ShouldReturnCorrectKey() {
        CreateUploadUseCase useCase = new CreateUploadUseCase();
        UploadRequest request = new UploadRequest();
        request.setFilename("document.pdf");
        request.setProjectId("project-123");
        request.setUploaderId("user-456");

        UploadResponse response = useCase.create(request);

        assertNotNull(response);
        assertNotNull(response.getUploadId());
        assertTrue(response.getS3Key().endsWith(".pdf"));
        assertTrue(response.getS3Key().contains("project-123"));
        assertTrue(response.getS3Key().contains("user-456"));
    }

    @Test
    void create_WithFilenameWithoutExtension_ShouldReturnBinExtension() {
        CreateUploadUseCase useCase = new CreateUploadUseCase();
        UploadRequest request = new UploadRequest();
        request.setFilename("document");
        request.setProjectId("project-123");
        request.setUploaderId("user-456");

        UploadResponse response = useCase.create(request);

        assertNotNull(response);
        assertTrue(response.getS3Key().endsWith(".bin"));
    }

    @Test
    void create_WithNullFilename_ShouldReturnBinExtension() {
        CreateUploadUseCase useCase = new CreateUploadUseCase();
        UploadRequest request = new UploadRequest();
        request.setFilename(null);
        request.setProjectId("project-123");
        request.setUploaderId("user-456");

        UploadResponse response = useCase.create(request);

        assertNotNull(response);
        assertTrue(response.getS3Key().endsWith(".bin"));
    }

    @Test
    void create_WithNullProjectId_ShouldUseNoProject() {
        CreateUploadUseCase useCase = new CreateUploadUseCase();
        UploadRequest request = new UploadRequest();
        request.setFilename("document.pdf");
        request.setProjectId(null);
        request.setUploaderId("user-456");

        UploadResponse response = useCase.create(request);

        assertNotNull(response);
        assertTrue(response.getS3Key().contains("no-project"));
    }

    @Test
    void create_WithNullUploaderId_ShouldUseUnknown() {
        CreateUploadUseCase useCase = new CreateUploadUseCase();
        UploadRequest request = new UploadRequest();
        request.setFilename("document.pdf");
        request.setProjectId("project-123");
        request.setUploaderId(null);

        UploadResponse response = useCase.create(request);

        assertNotNull(response);
        assertTrue(response.getS3Key().contains("unknown"));
    }

    @Test
    void create_ShouldReturnNonNullUploadId() {
        CreateUploadUseCase useCase = new CreateUploadUseCase();
        UploadRequest request = new UploadRequest();
        request.setFilename("document.pdf");
        request.setProjectId("project-123");
        request.setUploaderId("user-456");

        UploadResponse response = useCase.create(request);

        assertNotNull(response.getUploadId());
        assertFalse(response.getUploadId().isEmpty());
    }

    @Test
    void create_WithMultipleExtensions_ShouldUseLastExtension() {
        CreateUploadUseCase useCase = new CreateUploadUseCase();
        UploadRequest request = new UploadRequest();
        request.setFilename("document.tar.gz");
        request.setProjectId("project-123");
        request.setUploaderId("user-456");

        UploadResponse response = useCase.create(request);

        assertNotNull(response);
        assertTrue(response.getS3Key().endsWith(".gz"));
    }
}
