package com.fiap.hackathon.upload_service.adapter.controller;

import com.fiap.hackathon.upload_service.adapter.dto.ApiErrorResponse;
import com.fiap.hackathon.upload_service.adapter.dto.UploadRequest;
import com.fiap.hackathon.upload_service.adapter.dto.UploadResponse;
import com.fiap.hackathon.upload_service.domain.Upload;
import com.fiap.hackathon.upload_service.service.UploadService;
import com.fiap.hackathon.upload_service.usecase.SingleUploadUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.util.Set;

@RestController
@RequestMapping("/v1/uploads")
public class UploadController {

    private static final long MAX_FILE_SIZE_BYTES = 1024L * 1024L * 1024L; // 1 GiB
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "image/png",
            "image/jpg",
            "image/jpeg"
    );

    private final SingleUploadUseCase singleUploadUseCase;
    private final UploadService uploadService;

    public UploadController(SingleUploadUseCase singleUploadUseCase, UploadService uploadService) {
        this.singleUploadUseCase = singleUploadUseCase;
        this.uploadService = uploadService;
    }

    @Operation(
            summary = "Upload de arquivo com metadados em endpoint unico",
            description = "Recebe multipart/form-data com duas partes: file (binario) e metadata (JSON).",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                            schema = @Schema(implementation = UploadMultipartRequestDoc.class),
                            examples = @ExampleObject(
                                    name = "Upload multipart",
                                    value = "{\n"
                                            + "  \"file\": \"(binary)\",\n"
                                            + "  \"metadata\": {\n"
                                            + "    \"filename\": \"resultado.pdf\",\n"
                                            + "    \"projectId\": \"11111111-1111-1111-1111-111111111111\",\n"
                                            + "    \"uploaderId\": \"user-123\"\n"
                                            + "  }\n"
                                            + "}"
                            )
                    )
            )
    )
    @ApiResponse(responseCode = "201", description = "Upload criado e arquivo enviado com sucesso")
    @ApiResponse(
            responseCode = "400",
            description = "Payload invalido, contentType nao permitido ou arquivo acima de 1 GiB",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiErrorResponse.class),
                    examples = @ExampleObject(
                            value = "{\"message\":\"Invalid upload request\",\"code\":\"INVALID_CONTENT_TYPE\",\"detail\":\"Only PDF, PNG, JPG or JPEG files are allowed\"}"
                    )
            )
    )
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadResponse> upload(
            @RequestPart("file") MultipartFile file,
            @RequestPart("metadata") @Valid UploadRequest metadata) {
        // Basic multipart validation.
        if (file == null || file.isEmpty()) {
            throw new UploadValidationException("FILE_REQUIRED", "File part is required and cannot be empty");
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new UploadValidationException("FILE_SIZE_EXCEEDED", "File size exceeds maximum allowed size of 1GB");
        }

        String fileContentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
                if (!ALLOWED_CONTENT_TYPES.contains(fileContentType)) {
            throw new UploadValidationException("INVALID_CONTENT_TYPE", "Only PDF, PNG, JPG or JPEG files are allowed");
        }

        // Set the file in the metadata request
        metadata.setFile(file);

                // Execute unified upload
                UploadResponse response;
                try {
                        response = singleUploadUseCase.execute(metadata);
                } catch (IOException e) {
                        throw new UploadValidationException("FILE_UPLOAD_ERROR", "Failed to process uploaded file");
                }

        // Return 201 Created with Location header
        return ResponseEntity
                .created(URI.create("/v1/uploads/" + response.getUploadId()))
                .body(response);
    }

    @GetMapping("/{uploadId}")
    public ResponseEntity<Upload> getById(@PathVariable("uploadId") String uploadId) {
        Upload upload = uploadService.getUpload(java.util.UUID.fromString(uploadId))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "UPLOAD_NOT_FOUND",
                        "Upload not found for id " + uploadId
                ));
        return ResponseEntity.ok(upload);
    }

    @Schema(name = "UploadMultipartRequest", description = "Multipart request: file binario + metadata JSON")
    static class UploadMultipartRequestDoc {
        @Schema(type = "string", format = "binary", description = "Arquivo a ser enviado")
        public String file;

        @Schema(
                description = "JSON com os metadados do upload",
                example = "{\"filename\":\"resultado.pdf\",\"projectId\":\"11111111-1111-1111-1111-111111111111\",\"uploaderId\":\"user-123\"}"
        )
        public String metadata;
    }
}
