package com.fiap.hackathon.upload_service.infra.aws;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

/**
 * Stores SQS messages that failed to be published after all retry attempts.
 * Used as an application-level Dead Letter Queue for permanent SQS failures.
 */
@Entity
@Table(name = "sqs_dead_letter_queue", indexes = {
    @Index(name = "idx_dlq_status", columnList = "status"),
    @Index(name = "idx_dlq_queue_url", columnList = "queue_url"),
    @Index(name = "idx_dlq_created_at", columnList = "created_at")
})
public class SqsDeadLetterEntry {

    public enum Status {
        PENDING,    // Awaiting manual reprocessing
        REPROCESSED // Successfully republished
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "queue_url", nullable = false, length = 500)
    private String queueUrl;

    @Lob
    @Column(name = "message_body", nullable = false, columnDefinition = "TEXT")
    private String messageBody;

    @Column(name = "failure_reason", nullable = false, length = 1000)
    private String failureReason;

    @Column(name = "failure_type", nullable = false, length = 100)
    private String failureType;

    @Column(name = "retry_attempts", nullable = false)
    private int retryAttempts;

    @Column(name = "upload_id", length = 36)
    private String uploadId;

    @Column(name = "uploader_id", length = 255)
    private String uploaderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    public SqsDeadLetterEntry() {
    }

    public SqsDeadLetterEntry(String queueUrl, String messageBody, String failureReason, String failureType, int retryAttempts) {
        this.queueUrl = queueUrl;
        this.messageBody = messageBody;
        this.failureReason = failureReason;
        this.failureType = failureType;
        this.retryAttempts = retryAttempts;
        this.status = Status.PENDING;
        this.createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getQueueUrl() { return queueUrl; }
    public void setQueueUrl(String queueUrl) { this.queueUrl = queueUrl; }
    public String getMessageBody() { return messageBody; }
    public void setMessageBody(String messageBody) { this.messageBody = messageBody; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public String getFailureType() { return failureType; }
    public void setFailureType(String failureType) { this.failureType = failureType; }
    public int getRetryAttempts() { return retryAttempts; }
    public void setRetryAttempts(int retryAttempts) { this.retryAttempts = retryAttempts; }
    public String getUploadId() { return uploadId; }
    public void setUploadId(String uploadId) { this.uploadId = uploadId; }
    public String getUploaderId() { return uploaderId; }
    public void setUploaderId(String uploaderId) { this.uploaderId = uploaderId; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
