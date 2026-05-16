package com.fiap.hackathon.upload_service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration test covering the full analysis flow:
 *
 * 1. Create project
 * 2. Upload file → stored in S3, status = PENDING, event published to upload-queue
 * 3. Publish diagram status to analysis-result-queue (simulating external analyzer)
 * 4. Listener polls queue and transitions upload status to SCANNED_OK / QUARANTINED / ANALYSIS_REVIEW_REQUIRED
 * 5. GET /v1/uploads/{id} confirms the final status
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
public class UploadToAnalysisE2ETest {

    @Container
    public static LocalStackContainer localstack = new LocalStackContainer(
            DockerImageName.parse("localstack/localstack:1.4.0"))
            .withServices(LocalStackContainer.Service.S3, LocalStackContainer.Service.SQS);

    private static S3Client s3client;
    private static SqsClient sqsClient;
    private static String bucketName;
    private static String uploadQueueUrl;
    private static String analysisResultQueueUrl;

    @LocalServerPort
    private int port;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @BeforeAll
    static void setup() {
        localstack.start();

        AwsBasicCredentials creds = AwsBasicCredentials.create(
                localstack.getAccessKey(), localstack.getSecretKey());

        s3client = S3Client.builder()
                .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.S3))
                .region(Region.of(localstack.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(creds))
                .forcePathStyle(true)
                .build();

        sqsClient = SqsClient.builder()
                .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.SQS))
                .region(Region.of(localstack.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(creds))
                .build();

        bucketName = "e2e-bucket-" + UUID.randomUUID();
        s3client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());

        uploadQueueUrl = sqsClient.createQueue(
                CreateQueueRequest.builder().queueName("e2e-upload-queue").build()).queueUrl();

        analysisResultQueueUrl = sqsClient.createQueue(
                CreateQueueRequest.builder().queueName("e2e-analysis-result-queue").build()).queueUrl();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("cloud.aws.endpoint.s3",
                () -> localstack.getEndpointOverride(LocalStackContainer.Service.S3).toString());
        registry.add("cloud.aws.endpoint.sqs",
                () -> localstack.getEndpointOverride(LocalStackContainer.Service.SQS).toString());
        registry.add("cloud.aws.accessKey", localstack::getAccessKey);
        registry.add("cloud.aws.secretKey", localstack::getSecretKey);
        registry.add("cloud.aws.region", localstack::getRegion);
        registry.add("application.s3.bucket", () -> bucketName);
        registry.add("application.sqs.queueUrl", () -> uploadQueueUrl);
        // Enable and configure the SQS result listener
        registry.add("app.sqs.result-listener.enabled", () -> "true");
        registry.add("app.sqs.result-listener.queue-url", () -> analysisResultQueueUrl);
        registry.add("app.sqs.result-listener.poll-interval-seconds", () -> "1");
        registry.add("app.sqs.result-listener.max-messages", () -> "10");
        registry.add("app.sqs.result-listener.wait-time-seconds", () -> "0");
        registry.add("app.security.gateway-trust.enabled", () -> "false");
        registry.add("app.security.rate-limit.upload.enabled", () -> "false");
    }

    @AfterAll
    static void tearDown() {
        if (s3client != null) s3client.close();
        if (sqsClient != null) sqsClient.close();
        localstack.stop();
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    void fullFlow_upload_thenAnalysisOk_statusBecomesScannedOk() throws Exception {
        String uploadId = uploadPdfAndGetId();

        sendAnalysisResult(uploadId, "OK", 5, List.of("no_threats_detected"));

        String finalStatus = pollUntilStatusChanges(uploadId, "PENDING", 10);
        assertThat(finalStatus).isEqualTo("SCANNED_OK");
    }

    @Test
    void fullFlow_upload_thenAnalysisQuarantined_statusBecomesQuarantined() throws Exception {
        String uploadId = uploadPdfAndGetId();

        sendAnalysisResult(uploadId, "QUARANTINED", 95, List.of("malware_detected", "suspicious_macro"));

        String finalStatus = pollUntilStatusChanges(uploadId, "PENDING", 10);
        assertThat(finalStatus).isEqualTo("QUARANTINED");
    }

    @Test
    void fullFlow_upload_thenAnalysisInconclusive_statusBecomesReviewRequired() throws Exception {
        String uploadId = uploadPdfAndGetId();

        sendAnalysisResult(uploadId, "INCONCLUSIVE", 50, List.of("ambiguous_content"));

        String finalStatus = pollUntilStatusChanges(uploadId, "PENDING", 10);
        assertThat(finalStatus).isEqualTo("ANALYSIS_REVIEW_REQUIRED");
    }

    @Test
    void fullFlow_uploadPng_thenAnalysisOk_statusBecomesScannedOk() throws Exception {
        String uploadId = uploadPngAndGetId();

        sendAnalysisResult(uploadId, "OK", 0, List.of("clean"));

        String finalStatus = pollUntilStatusChanges(uploadId, "PENDING", 10);
        assertThat(finalStatus).isEqualTo("SCANNED_OK");
    }

    @Test
    void fullFlow_analysisResultForUnknownUpload_doesNotThrow() throws Exception {
        // Verifies that the listener handles messages for unknown uploadIds gracefully
        // (no unhandled exception that could poison the queue consumer)
        String nonExistentUploadId = UUID.randomUUID().toString();

        sendAnalysisResult(nonExistentUploadId, "OK", 0, List.of("clean"));

        // Wait for the listener to process — the message should be consumed without crashing the app
        Thread.sleep(3000);

        // The service must still be healthy
        HttpResponse<String> health = getJson("/actuator/health");
        assertThat(health.statusCode()).isEqualTo(HttpStatus.OK.value());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String uploadPdfAndGetId() throws Exception {
        String projectId = createProject("E2E Project PDF");
        byte[] pdfBytes = "%PDF-1.7\n%E2E test content".getBytes();
        return doUpload("e2e-test.pdf", "application/pdf", pdfBytes, projectId);
    }

    private String uploadPngAndGetId() throws Exception {
        String projectId = createProject("E2E Project PNG");
        byte[] pngBytes = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        return doUpload("e2e-test.png", "image/png", pngBytes, projectId);
    }

    private String createProject(String name) throws Exception {
        HttpResponse<String> resp = postJson("/v1/projects", Map.of(
                "name", name,
                "description", "E2E test project",
                "ownerId", "e2e-tester"
        ));
        assertThat(resp.statusCode()).isEqualTo(HttpStatus.OK.value());
        Map<?, ?> body = objectMapper.readValue(resp.body(), Map.class);
        return String.valueOf(body.get("id"));
    }

    private String doUpload(String filename, String contentType, byte[] fileBytes, String projectId) throws Exception {
        HttpResponse<String> resp = postMultipart("/v1/uploads", filename, contentType, fileBytes, projectId, "e2e-tester");
        assertThat(resp.statusCode()).isEqualTo(HttpStatus.CREATED.value());
        Map<?, ?> body = objectMapper.readValue(resp.body(), Map.class);
        String uploadId = (String) body.get("uploadId");
        assertThat(uploadId).isNotNull();
        return uploadId;
    }

    /**
     * Publishes a diagram status message to the analysis-result-queue
     * ({@code diagram_id} = upload id, {@code status} = {@link com.fiap.hackathon.upload_service.domain.UploadStatus} name).
     */
    private void sendAnalysisResult(String uploadId, String result, int riskScore, List<String> findings) throws Exception {
        String status = switch (result) {
            case "OK" -> "SCANNED_OK";
            case "QUARANTINED" -> "QUARANTINED";
            case "INCONCLUSIVE" -> "ANALYSIS_REVIEW_REQUIRED";
            default -> throw new IllegalArgumentException("Unknown analysis result shorthand: " + result);
        };
        Map<String, Object> msg = Map.of(
                "diagram_id", uploadId,
                "status", status
        );
        sqsClient.sendMessage(SendMessageRequest.builder()
                .queueUrl(analysisResultQueueUrl)
                .messageBody(objectMapper.writeValueAsString(msg))
                .build());
    }

    /**
     * Polls GET /v1/uploads/{uploadId} every second until the status changes away from
     * {@code waitWhileStatus}, or until {@code maxWaitSeconds} elapses.
     *
     * @return the final observed status
     */
    private String pollUntilStatusChanges(String uploadId, String waitWhileStatus, int maxWaitSeconds) throws Exception {
        String status = waitWhileStatus;
        for (int i = 0; i < maxWaitSeconds; i++) {
            Thread.sleep(1000);
            HttpResponse<String> resp = getJson("/v1/uploads/" + uploadId);
            assertThat(resp.statusCode()).isEqualTo(HttpStatus.OK.value());
            Map<?, ?> body = objectMapper.readValue(resp.body(), Map.class);
            status = String.valueOf(body.get("status"));
            if (!waitWhileStatus.equals(status)) {
                return status;
            }
        }
        return status;
    }

    // -------------------------------------------------------------------------
    // HTTP helpers (same pattern as UploadIntegrationTest)
    // -------------------------------------------------------------------------

    private HttpResponse<String> postJson(String path, Map<String, Object> body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI("http://localhost:" + port + path))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postMultipart(String path, String filename, String contentType,
                                               byte[] fileBytes, String projectId, String uploaderId) throws Exception {
        String boundary = "----E2EBoundary" + UUID.randomUUID().toString().replace("-", "");
        byte[] body = buildMultipartBody(boundary, filename, contentType, fileBytes, projectId, uploaderId);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI("http://localhost:" + port + path))
                .header(HttpHeaders.CONTENT_TYPE, "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private byte[] buildMultipartBody(String boundary, String filename, String contentType,
                                      byte[] fileBytes, String projectId, String uploaderId) throws Exception {
        String CRLF = "\r\n";

        StringBuilder filePart = new StringBuilder();
        filePart.append("--").append(boundary).append(CRLF);
        filePart.append("Content-Disposition: form-data; name=\"file\"; filename=\"").append(filename).append("\"").append(CRLF);
        filePart.append("Content-Type: ").append(contentType).append(CRLF);
        filePart.append(CRLF);
        byte[] filePartBytes = filePart.toString().getBytes();

        Map<String, String> metadata = Map.of(
                "filename", filename,
                "projectId", projectId,
                "uploaderId", uploaderId
        );
        String metadataJson = objectMapper.writeValueAsString(metadata);

        StringBuilder metaPart = new StringBuilder();
        metaPart.append(CRLF).append("--").append(boundary).append(CRLF);
        metaPart.append("Content-Disposition: form-data; name=\"metadata\"").append(CRLF);
        metaPart.append("Content-Type: application/json").append(CRLF);
        metaPart.append(CRLF);
        byte[] metaPartBytes = (metaPart.toString() + metadataJson).getBytes();

        byte[] closingBytes = (CRLF + "--" + boundary + "--" + CRLF).getBytes();

        byte[] result = new byte[filePartBytes.length + fileBytes.length + metaPartBytes.length + closingBytes.length];
        int offset = 0;
        System.arraycopy(filePartBytes, 0, result, offset, filePartBytes.length);
        offset += filePartBytes.length;
        System.arraycopy(fileBytes, 0, result, offset, fileBytes.length);
        offset += fileBytes.length;
        System.arraycopy(metaPartBytes, 0, result, offset, metaPartBytes.length);
        offset += metaPartBytes.length;
        System.arraycopy(closingBytes, 0, result, offset, closingBytes.length);
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
