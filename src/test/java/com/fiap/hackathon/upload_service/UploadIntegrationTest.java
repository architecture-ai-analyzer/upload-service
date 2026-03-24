package com.fiap.hackathon.upload_service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.*;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.junit.jupiter.Container;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.net.URI;
import java.util.Map;
import java.util.UUID;
import java.time.Duration;
import java.util.List;

import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;

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
    private static String dlqUrl;
    private static GenericContainer<?> clamavContainer;

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private com.fiap.hackathon.upload_service.adapter.persistence.UploadRepository uploadRepository;

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
        dlqUrl = sqsClient.createQueue(CreateQueueRequest.builder().queueName("test-dlq").build()).queueUrl();

        // start clamav container
        clamavContainer = new GenericContainer<>(DockerImageName.parse("mkodockx/docker-clamav:alpine"))
            .withExposedPorts(3310)
            .waitingFor(Wait.forListeningPort());
        clamavContainer.start();
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
        registry.add("application.sqs.dlqUrl", () -> dlqUrl);
        registry.add("application.clamd.host", () -> clamavContainer.getHost());
        registry.add("application.clamd.port", () -> clamavContainer.getMappedPort(3310));
    }

    @AfterAll
    public static void tearDown() {
        if (s3client != null) s3client.close();
        if (sqsClient != null) sqsClient.close();
        localstack.stop();
        if (clamavContainer != null) clamavContainer.stop();
    }

    @Test
    public void fullUploadFlow_usingPresigned_thenComplete_publishesSqs() throws Exception {
        // 1) request presigned URL
        Map<String,Object> req = Map.of("filename","diagram.pdf","contentType","application/pdf","sizeBytes",1024);
        ResponseEntity<Map> resp = restTemplate.postForEntity(new URI("http://localhost:"+port+"/v1/uploads"), req, Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        String uploadId = (String) resp.getBody().get("uploadId");
        String s3Key = (String) resp.getBody().get("s3Key");

        // 2) simulate upload by putting object directly to local S3
        s3client.putObject(PutObjectRequest.builder().bucket(bucketName).key(s3Key).contentType("application/pdf").build(), RequestBody.fromString("dummy"));

        // 3) call complete endpoint
        Map<String,Object> completeReq = Map.of("s3Key", s3Key, "filename","diagram.pdf","contentType","application/pdf","sizeBytes",1024);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String,Object>> entity = new HttpEntity<>(completeReq, headers);
        ResponseEntity<String> completeResp = restTemplate.postForEntity(new URI("http://localhost:"+port+"/v1/uploads/"+uploadId+"/complete"), entity, String.class);
        assertThat(completeResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        // 4) verify SQS message was published
        boolean found = false;
        long deadline = System.currentTimeMillis() + Duration.ofSeconds(10).toMillis();
        while (System.currentTimeMillis() < deadline && !found) {
            ReceiveMessageRequest r = ReceiveMessageRequest.builder().queueUrl(queueUrl).maxNumberOfMessages(10).waitTimeSeconds(1).build();
            List<Message> messages = sqsClient.receiveMessage(r).messages();
            for (Message m : messages) {
                if (m.body() != null && m.body().contains(s3Key)) {
                    found = true;
                    break;
                }
            }
            if (!found) Thread.sleep(500);
        }
        assertThat(found).isTrue();

        // 5) create an upload containing the EICAR test string and verify scanner detects it (QUARANTINED)
        Map<String,Object> req2 = Map.of("filename","eicar.txt","contentType","text/plain","sizeBytes",68);
        ResponseEntity<Map> resp2 = restTemplate.postForEntity(new URI("http://localhost:"+port+"/v1/uploads"), req2, Map.class);
        assertThat(resp2.getStatusCode()).isEqualTo(HttpStatus.OK);
        String uploadId2 = (String) resp2.getBody().get("uploadId");
        String s3Key2 = (String) resp2.getBody().get("s3Key");

        // upload EICAR test file
        String eicar = "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*";
        s3client.putObject(PutObjectRequest.builder().bucket(bucketName).key(s3Key2).contentType("text/plain").build(), RequestBody.fromString(eicar));

        // complete upload (publish message)
        Map<String,Object> completeReq2 = Map.of("s3Key", s3Key2, "filename","eicar.txt","contentType","text/plain","sizeBytes",eicar.length());
        HttpEntity<Map<String,Object>> entity2 = new HttpEntity<>(completeReq2, headers);
        ResponseEntity<String> completeResp2 = restTemplate.postForEntity(new URI("http://localhost:"+port+"/v1/uploads/"+uploadId2+"/complete"), entity2, String.class);
        assertThat(completeResp2.getStatusCode()).isEqualTo(HttpStatus.OK);

        // wait for worker to process and mark QUARANTINED
        boolean quarantined = false;
        long waitDeadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < waitDeadline && !quarantined) {
            java.util.Optional<com.fiap.hackathon.upload_service.domain.Upload> ou = uploadRepository.findById(java.util.UUID.fromString(uploadId2));
            if (ou.isPresent() && ou.get().getStatus() != null && ou.get().getStatus().name().equals("QUARANTINED")) {
                quarantined = true;
                break;
            }
            Thread.sleep(1000);
        }
        assertThat(quarantined).isTrue();

        // 6) send a message that will force failure in the worker and verify it ends in the DLQ
        String forcedBody = String.format("{\"eventId\":\"%s\",\"s3Key\":\"%s\", \"forceFail\": true}", uploadId, s3Key);
        sqsClient.createQueue(CreateQueueRequest.builder().queueName("test-queue").build());
        sqsClient.sendMessage(b -> b.queueUrl(queueUrl).messageBody(forcedBody));

        boolean dlqFound = false;
        long dlqDeadline = System.currentTimeMillis() + Duration.ofSeconds(30).toMillis();
        while (System.currentTimeMillis() < dlqDeadline && !dlqFound) {
            ReceiveMessageRequest r = ReceiveMessageRequest.builder().queueUrl(dlqUrl).maxNumberOfMessages(10).waitTimeSeconds(2).build();
            List<Message> messages = sqsClient.receiveMessage(r).messages();
            for (Message m : messages) {
                if (m.body() != null && m.body().contains(s3Key) && m.body().contains("forceFail")) {
                    dlqFound = true;
                    break;
                }
            }
            if (!dlqFound) Thread.sleep(1000);
        }
        assertThat(dlqFound).isTrue();
    }
}
