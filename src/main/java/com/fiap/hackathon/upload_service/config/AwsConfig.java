package com.fiap.hackathon.upload_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.net.URI;
import java.time.Duration;

@Configuration
public class AwsConfig {

    @Value("${cloud.aws.region:us-east-2}")
    private String region;

    @Value("${cloud.aws.endpoint.s3:}")
    private String s3Endpoint;

    @Value("${cloud.aws.endpoint.sqs:}")
    private String sqsEndpoint;

    @Value("${cloud.aws.accessKey:}")
    private String accessKey;

    @Value("${cloud.aws.secretKey:}")
    private String secretKey;

    @Value("${cloud.aws.client.api-call-timeout-seconds:30}")
    private int apiCallTimeoutSeconds;

    @Value("${cloud.aws.client.api-call-attempt-timeout-seconds:10}")
    private int apiCallAttemptTimeoutSeconds;

    @Value("${cloud.aws.client.max-retries:3}")
    private int maxRetries;

    private ClientOverrideConfiguration clientOverrideConfiguration() {
        return ClientOverrideConfiguration.builder()
                .apiCallTimeout(Duration.ofSeconds(Math.max(1, apiCallTimeoutSeconds)))
                .apiCallAttemptTimeout(Duration.ofSeconds(Math.max(1, apiCallAttemptTimeoutSeconds)))
                .retryPolicy(RetryPolicy.builder().numRetries(Math.max(0, maxRetries)).build())
                .build();
    }

    @Bean
    public S3Client s3Client() {
        var b = S3Client.builder()
                .region(Region.of(region))
                .overrideConfiguration(clientOverrideConfiguration())
                .forcePathStyle(true); // Required for LocalStack and other S3-compatible services
        if (s3Endpoint != null && !s3Endpoint.isBlank()) {
            b.endpointOverride(URI.create(s3Endpoint));
        }
        if (accessKey != null && !accessKey.isBlank()) {
            b.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)));
        }
        return b.build();
    }

    // S3Presigner intentionally not exposed in this build; presigned URLs are generated externally or not used.

    @Bean
    public SqsClient sqsClient() {
        var b = SqsClient.builder()
                .region(Region.of(region))
                .overrideConfiguration(clientOverrideConfiguration());
        if (sqsEndpoint != null && !sqsEndpoint.isBlank()) {
            b.endpointOverride(URI.create(sqsEndpoint));
        }
        if (accessKey != null && !accessKey.isBlank()) {
            b.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)));
        }
        return b.build();
    }
}
