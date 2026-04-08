package com.fiap.hackathon.upload_service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.junit.jupiter.Container;

import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
public class UploadIntegrationTest {

    @Container
    public static LocalStackContainer localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:1.4.0"))
            .withServices(LocalStackContainer.Service.S3, LocalStackContainer.Service.SQS);

    private static S3Client s3client;
    private static SqsClient sqsClient;
    private static String bucketName;
    private static String queueUrl;

    @LocalServerPort
    private int port;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @BeforeAll
    public static void setup() {
        localstack.start();
        AwsBasicCredentials creds = AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey());
        s3client = S3Client.builder()
                .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.S3))
                .region(Region.of(localstack.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(creds))
                .build();

        sqsClient = SqsClient.builder()
                .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.SQS))
                .region(Region.of(localstack.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(creds))
                .build();

        bucketName = "test-bucket-" + UUID.randomUUID();
        s3client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());

        queueUrl = sqsClient.createQueue(CreateQueueRequest.builder().queueName("test-queue").build()).queueUrl();
        sqsClient.createQueue(CreateQueueRequest.builder().queueName("test-dlq").build());
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("cloud.aws.endpoint.s3", () -> localstack.getEndpointOverride(LocalStackContainer.Service.S3).toString());
        registry.add("cloud.aws.endpoint.sqs", () -> localstack.getEndpointOverride(LocalStackContainer.Service.SQS).toString());
        registry.add("cloud.aws.accessKey", () -> localstack.getAccessKey());
        registry.add("cloud.aws.secretKey", () -> localstack.getSecretKey());
        registry.add("cloud.aws.region", () -> localstack.getRegion());
        registry.add("application.s3.bucket", () -> bucketName);
        registry.add("application.sqs.queueUrl", () -> queueUrl);
        registry.add("application.sqs.dlqUrl", () -> "");
        registry.add("application.sqs.maxRetries", () -> "1");
    }

    @AfterAll
    public static void tearDown() {
        if (s3client != null) s3client.close();
        if (sqsClient != null) sqsClient.close();
        localstack.stop();
    }

    @Test
    public void unifiedUpload_withMultipart_uploads_toS3_andReturns201() throws Exception {
        // 1) Create project
        HttpResponse<String> projectResp = postJson("/v1/projects", Map.of(
            "name", "Projeto Teste",
            "description", "Projeto para fluxo de upload",
            "ownerId", "owner-1"
        ));
        assertThat(projectResp.statusCode()).isEqualTo(HttpStatus.OK.value());
        Map<String, Object> projectBody = objectMapper.readValue(projectResp.body(), Map.class);
        String projectId = String.valueOf(projectBody.get("id"));

        // 2) Upload file with metadata in single multipart request
        String fileContent = "This is a test PDF file content";
        byte[] fileBytes = fileContent.getBytes();
        
        HttpResponse<String> uploadResp = postMultipart("/v1/uploads", 
            "diagram.pdf", 
            "application/pdf", 
            fileBytes, 
            projectId, 
            "user-123"
        );
        assertThat(uploadResp.statusCode()).isEqualTo(HttpStatus.CREATED.value());
        
        Map<String, Object> uploadBody = objectMapper.readValue(uploadResp.body(), Map.class);
        String uploadId = (String) uploadBody.get("uploadId");
        String s3Key = (String) uploadBody.get("s3Key");
        
        assertThat(uploadId).isNotNull();
        assertThat(s3Key).isNotNull();
        assertThat(s3Key).endsWith(".pdf");

        // 3) Verify Location header is present
        String location = uploadResp.headers().firstValue("Location").orElse(null);
        assertThat(location).isNotNull().contains("/v1/uploads/" + uploadId);

        // 4) Verify file exists in S3
        boolean fileExists = s3client.headObject(HeadObjectRequest.builder().bucket(bucketName).key(s3Key).build()) != null;
        assertThat(fileExists).isTrue();

        // 5) Verify upload status is COMPLETED via GET endpoint
        HttpResponse<String> statusResp = getJson("/v1/uploads/" + uploadId);
        assertThat(statusResp.statusCode()).isEqualTo(HttpStatus.OK.value());
        Map<String, Object> statusBody = objectMapper.readValue(statusResp.body(), Map.class);
        assertThat(statusBody.get("status")).isEqualTo("COMPLETED");
    }

    @Test
    public void unifiedUpload_withPngMultipart_uploads_toS3_andReturns201() throws Exception {
        HttpResponse<String> projectResp = postJson("/v1/projects", Map.of(
            "name", "Projeto PNG",
            "description", "Projeto para upload PNG",
            "ownerId", "owner-1"
        ));
        assertThat(projectResp.statusCode()).isEqualTo(HttpStatus.OK.value());
        Map<String, Object> projectBody = objectMapper.readValue(projectResp.body(), Map.class);
        String projectId = String.valueOf(projectBody.get("id"));

        byte[] fileBytes = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

        HttpResponse<String> uploadResp = postMultipart(
            "/v1/uploads",
            "imagem.png",
            "image/png",
            fileBytes,
            projectId,
            "user-123"
        );

        assertThat(uploadResp.statusCode()).isEqualTo(HttpStatus.CREATED.value());
        Map<String, Object> uploadBody = objectMapper.readValue(uploadResp.body(), Map.class);
        String uploadId = (String) uploadBody.get("uploadId");
        String s3Key = (String) uploadBody.get("s3Key");

        assertThat(uploadId).isNotNull();
        assertThat(s3Key).isNotNull();
        assertThat(s3Key).endsWith(".png");
    }

    @Test
    public void unifiedUpload_withMetadataAsTextPlain_returns400() throws Exception {
        HttpResponse<String> projectResp = postJson("/v1/projects", Map.of(
            "name", "Projeto Metadata",
            "description", "Projeto para metadata invalida",
            "ownerId", "owner-1"
        ));
        assertThat(projectResp.statusCode()).isEqualTo(HttpStatus.OK.value());
        Map<String, Object> projectBody = objectMapper.readValue(projectResp.body(), Map.class);
        String projectId = String.valueOf(projectBody.get("id"));

        byte[] fileBytes = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

        HttpResponse<String> uploadResp = postMultipart(
            "/v1/uploads",
            "imagem.png",
            "image/png",
            fileBytes,
            projectId,
            "user-123",
            "text/plain"
        );

        assertThat(uploadResp.statusCode()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    }

        @Test
        public void unifiedUpload_withInvalidContentType_returns400() throws Exception {
        HttpResponse<String> projectResp = postJson("/v1/projects", Map.of(
            "name", "Projeto Teste",
            "description", "Projeto para validacao",
            "ownerId", "owner-1"
        ));
        assertThat(projectResp.statusCode()).isEqualTo(HttpStatus.OK.value());
        Map<String, Object> projectBody = objectMapper.readValue(projectResp.body(), Map.class);
        String projectId = String.valueOf(projectBody.get("id"));

        byte[] fileBytes = "content".getBytes();
        HttpResponse<String> uploadResp = postMultipart(
            "/v1/uploads",
            "arquivo.txt",
            "text/plain",
            fileBytes,
            projectId,
            "user-123"
        );

        assertThat(uploadResp.statusCode()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        Map<String, Object> errorBody = objectMapper.readValue(uploadResp.body(), Map.class);
        assertThat(errorBody.get("message")).isEqualTo("Invalid upload request");
        assertThat(errorBody.get("code")).isEqualTo("INVALID_CONTENT_TYPE");
        assertThat(String.valueOf(errorBody.get("detail"))).contains("PDF");
        }

    @Test
    public void unifiedUpload_withMismatchedFilenameExtension_returns400() throws Exception {
        HttpResponse<String> projectResp = postJson("/v1/projects", Map.of(
            "name", "Projeto Teste",
            "description", "Projeto para validacao de extensao",
            "ownerId", "owner-1"
        ));
        assertThat(projectResp.statusCode()).isEqualTo(HttpStatus.OK.value());
        Map<String, Object> projectBody = objectMapper.readValue(projectResp.body(), Map.class);
        String projectId = String.valueOf(projectBody.get("id"));

        byte[] fileBytes = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47};
        HttpResponse<String> uploadResp = postMultipart(
            "/v1/uploads",
            "imagem.pdf",
            "image/png",
            fileBytes,
            projectId,
            "user-123"
        );

        assertThat(uploadResp.statusCode()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        Map<String, Object> errorBody = objectMapper.readValue(uploadResp.body(), Map.class);
        assertThat(errorBody.get("message")).isEqualTo("Invalid request");
        assertThat(errorBody.get("code")).isEqualTo("BAD_REQUEST");
        assertThat(String.valueOf(errorBody.get("detail"))).contains("extension");
    }

    @Test
    public void getUploadStatus_withUnknownId_returns404WithStandardError() throws Exception {
        String unknownUploadId = UUID.randomUUID().toString();

        HttpResponse<String> statusResp = getJson("/v1/uploads/" + unknownUploadId);

        assertThat(statusResp.statusCode()).isEqualTo(HttpStatus.NOT_FOUND.value());
        Map<String, Object> errorBody = objectMapper.readValue(statusResp.body(), Map.class);
        assertThat(errorBody.get("message")).isEqualTo("Resource not found");
        assertThat(errorBody.get("code")).isEqualTo("UPLOAD_NOT_FOUND");
        assertThat(String.valueOf(errorBody.get("detail"))).contains(unknownUploadId);
    }

    private HttpResponse<String> postJson(String path, Map<String, Object> body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI("http://localhost:" + port + path))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postMultipart(String path, String filename, String contentType, byte[] fileBytes, String projectId, String uploaderId) throws Exception {
        return postMultipart(path, filename, contentType, fileBytes, projectId, uploaderId, "application/json");
    }

    private HttpResponse<String> postMultipart(String path, String filename, String contentType, byte[] fileBytes, String projectId, String uploaderId, String metadataPartContentType) throws Exception {
        String boundary = "----WebKitFormBoundary7MA4YWxkTrZu0gW";
        byte[] body = buildMultipartBody(boundary, filename, contentType, fileBytes, projectId, uploaderId, metadataPartContentType);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI("http://localhost:" + port + path))
                .header(HttpHeaders.CONTENT_TYPE, "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private byte[] buildMultipartBody(String boundary, String filename, String contentType, byte[] fileBytes, String projectId, String uploaderId, String metadataPartContentType) throws Exception {
        String CRLF = "\r\n";
        StringBuilder sb = new StringBuilder();

        // File part
        sb.append("--").append(boundary).append(CRLF);
        sb.append("Content-Disposition: form-data; name=\"file\"; filename=\"").append(filename).append("\"").append(CRLF);
        sb.append("Content-Type: ").append(contentType).append(CRLF);
        sb.append(CRLF);

        byte[] filePart = sb.toString().getBytes();
        
        // Metadata JSON part
        sb = new StringBuilder();
        sb.append(CRLF).append("--").append(boundary).append(CRLF);
        sb.append("Content-Disposition: form-data; name=\"metadata\"").append(CRLF);
        sb.append("Content-Type: ").append(metadataPartContentType).append(CRLF);
        sb.append(CRLF);
        
        Map<String, String> metadata = Map.of(
            "filename", filename,
            "projectId", projectId,
            "uploaderId", uploaderId
        );
        String metadataJson = objectMapper.writeValueAsString(metadata);
        byte[] metadataPart = (sb.toString() + metadataJson).getBytes();

        // Closing boundary
        String closing = CRLF + "--" + boundary + "--" + CRLF;
        byte[] closingPart = closing.getBytes();

        // Combine all parts
        byte[] result = new byte[filePart.length + fileBytes.length + metadataPart.length + closingPart.length];
        int offset = 0;
        System.arraycopy(filePart, 0, result, offset, filePart.length);
        offset += filePart.length;
        System.arraycopy(fileBytes, 0, result, offset, fileBytes.length);
        offset += fileBytes.length;
        System.arraycopy(metadataPart, 0, result, offset, metadataPart.length);
        offset += metadataPart.length;
        System.arraycopy(closingPart, 0, result, offset, closingPart.length);

        return result;
    }

    private HttpResponse<String> getJson(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI("http://localhost:" + port + path))
                .GET()
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
