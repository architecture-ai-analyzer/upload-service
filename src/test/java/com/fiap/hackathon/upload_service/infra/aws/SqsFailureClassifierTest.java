package com.fiap.hackathon.upload_service.infra.aws;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.sqs.model.SqsException;

import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;

import static org.junit.jupiter.api.Assertions.*;

class SqsFailureClassifierTest {

    @Test
    void classify_WithIOException_ShouldReturnTransient() {
        IOException ex = new IOException("Network error");
        assertEquals(SqsFailureClassifier.FailureType.TRANSIENT, SqsFailureClassifier.classify(ex));
    }

    @Test
    void classify_WithSocketException_ShouldReturnTransient() {
        SocketException ex = new SocketException("Connection reset");
        assertEquals(SqsFailureClassifier.FailureType.TRANSIENT, SqsFailureClassifier.classify(ex));
    }

    @Test
    void classify_WithSocketTimeoutException_ShouldReturnTransient() {
        SocketTimeoutException ex = new SocketTimeoutException("Timeout");
        assertEquals(SqsFailureClassifier.FailureType.TRANSIENT, SqsFailureClassifier.classify(ex));
    }

    @Test
    void classify_WithSqsException5xx_ShouldReturnTransient() {
        SqsException ex = (SqsException) SqsException.builder()
            .statusCode(500)
            .message("Internal server error")
            .build();
        assertEquals(SqsFailureClassifier.FailureType.TRANSIENT, SqsFailureClassifier.classify(ex));
    }

    @Test
    void classify_WithSqsException503_ShouldReturnTransient() {
        SqsException ex = (SqsException) SqsException.builder()
            .statusCode(503)
            .message("Service unavailable")
            .build();
        assertEquals(SqsFailureClassifier.FailureType.TRANSIENT, SqsFailureClassifier.classify(ex));
    }

    @Test
    void classify_WithThrottlingException_ShouldReturnTransient() {
        SqsException ex = (SqsException) SqsException.builder()
            .statusCode(400)
            .message("ThrottlingException")
            .build();
        assertEquals(SqsFailureClassifier.FailureType.PERMANENT, SqsFailureClassifier.classify(ex));
    }

    @Test
    void classify_WithRequestThrottled_ShouldReturnTransient() {
        SqsException ex = (SqsException) SqsException.builder()
            .statusCode(400)
            .message("RequestThrottled")
            .build();
        assertEquals(SqsFailureClassifier.FailureType.PERMANENT, SqsFailureClassifier.classify(ex));
    }

    @Test
    void classify_WithServiceUnavailable_ShouldReturnTransient() {
        SqsException ex = (SqsException) SqsException.builder()
            .statusCode(503)
            .message("ServiceUnavailable")
            .build();
        assertEquals(SqsFailureClassifier.FailureType.TRANSIENT, SqsFailureClassifier.classify(ex));
    }

    @Test
    void classify_WithRequestTimeout_ShouldReturnTransient() {
        SqsException ex = (SqsException) SqsException.builder()
            .statusCode(408)
            .message("RequestTimeout")
            .build();
        assertEquals(SqsFailureClassifier.FailureType.PERMANENT, SqsFailureClassifier.classify(ex));
    }

    @Test
    void classify_WithAccessDenied_ShouldReturnPermanent() {
        SqsException ex = (SqsException) SqsException.builder()
            .statusCode(403)
            .message("AccessDenied")
            .build();
        assertEquals(SqsFailureClassifier.FailureType.PERMANENT, SqsFailureClassifier.classify(ex));
    }

    @Test
    void classify_WithInvalidClientTokenId_ShouldReturnPermanent() {
        SqsException ex = (SqsException) SqsException.builder()
            .statusCode(403)
            .message("InvalidClientTokenId")
            .build();
        assertEquals(SqsFailureClassifier.FailureType.PERMANENT, SqsFailureClassifier.classify(ex));
    }

    @Test
    void classify_WithNonExistentQueue_ShouldReturnPermanent() {
        SqsException ex = (SqsException) SqsException.builder()
            .statusCode(404)
            .message("AWS.SimpleQueueService.NonExistentQueue")
            .build();
        assertEquals(SqsFailureClassifier.FailureType.PERMANENT, SqsFailureClassifier.classify(ex));
    }

    @Test
    void classify_WithInvalidMessageContents_ShouldReturnPermanent() {
        SqsException ex = (SqsException) SqsException.builder()
            .statusCode(400)
            .message("InvalidMessageContents")
            .build();
        assertEquals(SqsFailureClassifier.FailureType.PERMANENT, SqsFailureClassifier.classify(ex));
    }

    @Test
    void classify_WithMessageTooLong_ShouldReturnPermanent() {
        SqsException ex = (SqsException) SqsException.builder()
            .statusCode(400)
            .message("MessageTooLong")
            .build();
        assertEquals(SqsFailureClassifier.FailureType.PERMANENT, SqsFailureClassifier.classify(ex));
    }

    @Test
    void classify_WithInvalidAttributeName_ShouldReturnPermanent() {
        SqsException ex = (SqsException) SqsException.builder()
            .statusCode(400)
            .message("InvalidAttributeName")
            .build();
        assertEquals(SqsFailureClassifier.FailureType.PERMANENT, SqsFailureClassifier.classify(ex));
    }

    @Test
    void classify_WithInvalidAttributeValue_ShouldReturnPermanent() {
        SqsException ex = (SqsException) SqsException.builder()
            .statusCode(400)
            .message("InvalidAttributeValue")
            .build();
        assertEquals(SqsFailureClassifier.FailureType.PERMANENT, SqsFailureClassifier.classify(ex));
    }

    @Test
    void classify_With4xx_ShouldReturnPermanent() {
        SqsException ex = (SqsException) SqsException.builder()
            .statusCode(400)
            .message("Bad request")
            .build();
        assertEquals(SqsFailureClassifier.FailureType.PERMANENT, SqsFailureClassifier.classify(ex));
    }

    @Test
    void classify_WithUnknownException_ShouldReturnPermanent() {
        RuntimeException ex = new RuntimeException("Unknown error");
        assertEquals(SqsFailureClassifier.FailureType.PERMANENT, SqsFailureClassifier.classify(ex));
    }

    @Test
    void classify_WithSqsExceptionInCause_ShouldClassifyCorrectly() {
        SqsException sqsEx = (SqsException) SqsException.builder()
            .statusCode(500)
            .message("Internal server error")
            .build();
        SdkException wrapper = SdkException.builder()
            .cause(sqsEx)
            .message("Wrapper error")
            .build();
        assertEquals(SqsFailureClassifier.FailureType.TRANSIENT, SqsFailureClassifier.classify(wrapper));
    }

    @Test
    void classify_WithSqsExceptionNoErrorCode_ShouldClassifyByStatusCode() {
        SqsException ex = (SqsException) SqsException.builder()
            .statusCode(500)
            .message("Internal server error")
            .build();
        assertEquals(SqsFailureClassifier.FailureType.TRANSIENT, SqsFailureClassifier.classify(ex));
    }
}
