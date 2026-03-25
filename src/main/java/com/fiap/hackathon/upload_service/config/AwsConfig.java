package com.fiap.hackathon.upload_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.net.URI;

@Configuration
public class AwsConfig {

    @Value("${cloud.aws.region:us-east-1}")
    private String region;

    @Value("${cloud.aws.endpoint.s3:}")
    private String s3Endpoint;

    @Value("${cloud.aws.endpoint.sqs:}")
    private String sqsEndpoint;

    @Value("${cloud.aws.accessKey:}")
    private String accessKey;

    @Value("${cloud.aws.secretKey:}")
    private String secretKey;

    @Bean
    public S3Client s3Client() {
        var b = S3Client.builder().region(Region.of(region));
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
        var b = SqsClient.builder().region(Region.of(region));
        if (sqsEndpoint != null && !sqsEndpoint.isBlank()) {
            b.endpointOverride(URI.create(sqsEndpoint));
        }
        if (accessKey != null && !accessKey.isBlank()) {
            b.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)));
        }
        return b.build();
    }
}
