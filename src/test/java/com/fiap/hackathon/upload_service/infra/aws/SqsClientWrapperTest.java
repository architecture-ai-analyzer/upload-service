package com.fiap.hackathon.upload_service.infra.aws;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SqsClientWrapperTest {

    @Mock
    private SqsClient sqsClient;

    @Test
    void sendMessage_WithValidParameters_ShouldCallSqsClient() {
        SqsClientWrapper sqsClientWrapper = new SqsClientWrapper(sqsClient);

        sqsClientWrapper.sendMessage("https://sqs.us-east-1.amazonaws.com/123456789012/test-queue", "test message");

        verify(sqsClient, times(1)).sendMessage(any(SendMessageRequest.class));
    }

    @Test
    void sendMessage_WithEmptyMessage_ShouldCallSqsClient() {
        SqsClientWrapper sqsClientWrapper = new SqsClientWrapper(sqsClient);

        sqsClientWrapper.sendMessage("https://sqs.us-east-1.amazonaws.com/123456789012/test-queue", "");

        verify(sqsClient, times(1)).sendMessage(any(SendMessageRequest.class));
    }

    @Test
    void sendMessage_WithNullQueueUrl_ShouldCallSqsClient() {
        SqsClientWrapper sqsClientWrapper = new SqsClientWrapper(sqsClient);

        sqsClientWrapper.sendMessage(null, "test message");

        verify(sqsClient, times(1)).sendMessage(any(SendMessageRequest.class));
    }

    @Test
    void sendMessage_WithNullMessage_ShouldCallSqsClient() {
        SqsClientWrapper sqsClientWrapper = new SqsClientWrapper(sqsClient);

        sqsClientWrapper.sendMessage("https://sqs.us-east-1.amazonaws.com/123456789012/test-queue", null);

        verify(sqsClient, times(1)).sendMessage(any(SendMessageRequest.class));
    }
}
