package com.fiap.hackathon.upload_service.infra.aws;

import com.fiap.hackathon.upload_service.infra.audit.AuditEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.services.sqs.model.SqsException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class SqsEventPublisherTest {

    private SqsClientWrapper sqsClientWrapper;
    private SqsDeadLetterRepository deadLetterRepository;
    private AuditEventPublisher auditEventPublisher;
    private SqsEventPublisher publisher;

    @BeforeEach
    void setUp() {
        sqsClientWrapper = mock(SqsClientWrapper.class);
        deadLetterRepository = mock(SqsDeadLetterRepository.class);
        auditEventPublisher = mock(AuditEventPublisher.class);
        publisher = new SqsEventPublisher(sqsClientWrapper, deadLetterRepository, auditEventPublisher);
        // set private fields via reflection (max-retries=2, backoff=1ms)
        setField(publisher, "maxRetries", 2);
        setField(publisher, "initialBackoffMs", 1L);
    }

    @Test
    void shouldPublishSuccessfullyOnFirstAttempt() {
        doNothing().when(sqsClientWrapper).sendMessage(anyString(), anyString());

        publisher.publishUploadEvent("https://sqs.queue", "{}", "upload-1", "user-1");

        verify(sqsClientWrapper, times(1)).sendMessage(anyString(), anyString());
        verify(deadLetterRepository, never()).save(any());
    }

    @Test
    void shouldRetryTransientFailureAndSucceedOnSecondAttempt() {
        SqsException transientEx = (SqsException) SqsException.builder()
                .statusCode(500)
                .message("Internal server error")
                .build();

        doThrow(transientEx)
                .doNothing()
                .when(sqsClientWrapper).sendMessage(anyString(), anyString());

        publisher.publishUploadEvent("https://sqs.queue", "{}", "upload-2", "user-1");

        verify(sqsClientWrapper, times(2)).sendMessage(anyString(), anyString());
        verify(deadLetterRepository, never()).save(any());
    }

    @Test
    void shouldSendToDlqAfterExhaustingRetries() {
        SqsException transientEx = (SqsException) SqsException.builder()
                .statusCode(503)
                .message("Service Unavailable")
                .build();

        doThrow(transientEx).when(sqsClientWrapper).sendMessage(anyString(), anyString());
        when(deadLetterRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        publisher.publishUploadEvent("https://sqs.queue", "{}", "upload-3", "user-1");

        // initial + 2 retries = 3 total calls
        verify(sqsClientWrapper, times(3)).sendMessage(anyString(), anyString());
        verify(deadLetterRepository, times(1)).save(any(SqsDeadLetterEntry.class));
    }

    @Test
    void shouldSendToDlqImmediatelyOnPermanentFailure() {
        SqsException permanentEx = (SqsException) SqsException.builder()
                .statusCode(400)
                .awsErrorDetails(AwsErrorDetails.builder()
                        .errorCode("AWS.SimpleQueueService.NonExistentQueue")
                        .errorMessage("Queue not found")
                        .build())
                .message("Queue not found")
                .build();

        doThrow(permanentEx).when(sqsClientWrapper).sendMessage(anyString(), anyString());
        when(deadLetterRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        publisher.publishUploadEvent("https://sqs.queue", "{}", "upload-4", "user-1");

        // No retry for permanent failures
        verify(sqsClientWrapper, times(1)).sendMessage(anyString(), anyString());
        verify(deadLetterRepository, times(1)).save(any(SqsDeadLetterEntry.class));
    }

    @Test
    void shouldClassifyThrottlingAsTransient() {
        SqsException throttleEx = (SqsException) SqsException.builder()
                .statusCode(400)
                .awsErrorDetails(AwsErrorDetails.builder()
                        .errorCode("ThrottlingException")
                        .errorMessage("Rate exceeded")
                        .build())
                .message("Rate exceeded")
                .build();

        assertThat(SqsFailureClassifier.classify(throttleEx))
                .isEqualTo(SqsFailureClassifier.FailureType.TRANSIENT);
    }

    @Test
    void shouldClassifyQueueNotFoundAsPermanent() {
        SqsException notFoundEx = (SqsException) SqsException.builder()
                .statusCode(400)
                .awsErrorDetails(AwsErrorDetails.builder()
                        .errorCode("AWS.SimpleQueueService.NonExistentQueue")
                        .errorMessage("Queue not found")
                        .build())
                .message("Queue not found")
                .build();

        assertThat(SqsFailureClassifier.classify(notFoundEx))
                .isEqualTo(SqsFailureClassifier.FailureType.PERMANENT);
    }

    @Test
    void shouldClassifyAccessDeniedAsPermanent() {
        SqsException authEx = (SqsException) SqsException.builder()
                .statusCode(403)
                .awsErrorDetails(AwsErrorDetails.builder()
                        .errorCode("AccessDenied")
                        .errorMessage("Access denied")
                        .build())
                .message("Access denied")
                .build();

        assertThat(SqsFailureClassifier.classify(authEx))
                .isEqualTo(SqsFailureClassifier.FailureType.PERMANENT);
    }

    @Test
    void shouldClassifyIoExceptionAsTransient() {
        assertThat(SqsFailureClassifier.classify(new java.io.IOException("timeout")))
                .isEqualTo(SqsFailureClassifier.FailureType.TRANSIENT);
    }

    @Test
    void shouldClassify5xxAsTransient() {
        SqsException serverEx = (SqsException) SqsException.builder()
                .statusCode(500)
                .message("Internal Server Error")
                .build();

        assertThat(SqsFailureClassifier.classify(serverEx))
                .isEqualTo(SqsFailureClassifier.FailureType.TRANSIENT);
    }

    private static void setField(Object target, String name, Object value) {
        try {
            java.lang.reflect.Field f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
