package com.fiap.hackathon.upload_service.infra.aws;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;

class SqsDeadLetterEntryTest {

    @Test
    void defaultConstructor_ShouldCreateInstance() {
        SqsDeadLetterEntry entry = new SqsDeadLetterEntry();

        assertNotNull(entry);
        assertNull(entry.getId());
        assertNull(entry.getQueueUrl());
        assertNull(entry.getMessageBody());
        assertNull(entry.getFailureReason());
        assertNull(entry.getFailureType());
        assertEquals(0, entry.getRetryAttempts());
        assertNull(entry.getUploadId());
        assertNull(entry.getUploaderId());
        assertNull(entry.getStatus());
        assertNull(entry.getCreatedAt());
        assertNull(entry.getUpdatedAt());
    }

    @Test
    void parameterizedConstructor_ShouldSetFields() {
        SqsDeadLetterEntry entry = new SqsDeadLetterEntry(
            "https://sqs.us-east-1.amazonaws.com/123456789012/test-queue",
            "test message body",
            "Connection timeout",
            "NETWORK_ERROR",
            3
        );

        assertEquals("https://sqs.us-east-1.amazonaws.com/123456789012/test-queue", entry.getQueueUrl());
        assertEquals("test message body", entry.getMessageBody());
        assertEquals("Connection timeout", entry.getFailureReason());
        assertEquals("NETWORK_ERROR", entry.getFailureType());
        assertEquals(3, entry.getRetryAttempts());
        assertEquals(SqsDeadLetterEntry.Status.PENDING, entry.getStatus());
        assertNotNull(entry.getCreatedAt());
    }

    @Test
    void settersAndGetters_ShouldWorkCorrectly() {
        SqsDeadLetterEntry entry = new SqsDeadLetterEntry();

        entry.setId(1L);
        entry.setQueueUrl("https://sqs.us-east-1.amazonaws.com/123456789012/test-queue");
        entry.setMessageBody("test message body");
        entry.setFailureReason("Connection timeout");
        entry.setFailureType("NETWORK_ERROR");
        entry.setRetryAttempts(3);
        entry.setUploadId("upload-123");
        entry.setUploaderId("user-456");
        entry.setStatus(SqsDeadLetterEntry.Status.REPROCESSED);
        OffsetDateTime now = OffsetDateTime.now();
        entry.setCreatedAt(now);
        entry.setUpdatedAt(now);

        assertEquals(1L, entry.getId());
        assertEquals("https://sqs.us-east-1.amazonaws.com/123456789012/test-queue", entry.getQueueUrl());
        assertEquals("test message body", entry.getMessageBody());
        assertEquals("Connection timeout", entry.getFailureReason());
        assertEquals("NETWORK_ERROR", entry.getFailureType());
        assertEquals(3, entry.getRetryAttempts());
        assertEquals("upload-123", entry.getUploadId());
        assertEquals("user-456", entry.getUploaderId());
        assertEquals(SqsDeadLetterEntry.Status.REPROCESSED, entry.getStatus());
        assertEquals(now, entry.getCreatedAt());
        assertEquals(now, entry.getUpdatedAt());
    }

    @Test
    void statusEnum_ShouldHaveCorrectValues() {
        assertEquals("PENDING", SqsDeadLetterEntry.Status.PENDING.name());
        assertEquals("REPROCESSED", SqsDeadLetterEntry.Status.REPROCESSED.name());
    }
}
