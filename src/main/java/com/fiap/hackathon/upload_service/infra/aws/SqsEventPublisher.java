package com.fiap.hackathon.upload_service.infra.aws;

import com.fiap.hackathon.upload_service.infra.audit.AuditEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

/**
 * Publishes upload events to SQS with retry + DLQ fallback.
 *
 * <p>Retry policy (configurable via properties):</p>
 * <ul>
 *   <li>Transient failures → retry up to {@code sqs.publisher.max-retries} times
 *       with exponential back-off starting at {@code sqs.publisher.initial-backoff-ms} ms.</li>
 *   <li>Permanent failures → stored in the application DLQ table immediately,
 *       no retry attempted.</li>
 * </ul>
 */
@Service
public class SqsEventPublisher {

    private static final Logger logger = LoggerFactory.getLogger(SqsEventPublisher.class);

    private final SqsClientWrapper sqsClientWrapper;
    private final SqsDeadLetterRepository deadLetterRepository;
    private final AuditEventPublisher auditEventPublisher;

    @Value("${app.sqs.publisher.max-retries:3}")
    private int maxRetries;

    @Value("${app.sqs.publisher.initial-backoff-ms:200}")
    private long initialBackoffMs;

    public SqsEventPublisher(
            SqsClientWrapper sqsClientWrapper,
            SqsDeadLetterRepository deadLetterRepository,
            AuditEventPublisher auditEventPublisher) {
        this.sqsClientWrapper = sqsClientWrapper;
        this.deadLetterRepository = deadLetterRepository;
        this.auditEventPublisher = auditEventPublisher;
    }

    /**
     * Sends a message to the given SQS queue, with retry on transient failures
     * and DLQ persistence on permanent failures.
     *
     * @param queueUrl    target SQS queue URL
     * @param messageBody serialized message payload
     * @param uploadId    upload ID (for DLQ traceability; may be null)
     * @param uploaderId  uploader ID (for DLQ traceability; may be null)
     */
    public void publishUploadEvent(String queueUrl, String messageBody, String uploadId, String uploaderId) {
        int attempt = 0;
        Exception lastException = null;

        while (attempt <= maxRetries) {
            try {
                sqsClientWrapper.sendMessage(queueUrl, messageBody);
                if (attempt > 0) {
                    logger.info("SQS message published successfully on attempt {} for uploadId={}", attempt + 1, uploadId);
                }
                return; // success
            } catch (Exception ex) {
                lastException = ex;
                SqsFailureClassifier.FailureType failureType = SqsFailureClassifier.classify(ex);

                if (failureType == SqsFailureClassifier.FailureType.PERMANENT) {
                    logger.error("Permanent SQS failure for uploadId={}: {}. Routing to DLQ.", uploadId, ex.getMessage());
                    routeToDlq(queueUrl, messageBody, ex, failureType.name(), attempt, uploadId, uploaderId);
                    return; // do not retry permanent failures
                }

                // TRANSIENT
                attempt++;
                if (attempt > maxRetries) {
                    logger.error("SQS transient failure exceeded max retries ({}) for uploadId={}. Routing to DLQ.", maxRetries, uploadId);
                    break;
                }

                long backoffMs = initialBackoffMs * (1L << (attempt - 1)); // exponential: 200, 400, 800...
                logger.warn("Transient SQS failure (attempt {}/{}), retrying in {} ms. Cause: {}", attempt, maxRetries, backoffMs, ex.getMessage());
                sleepQuietly(backoffMs);
            }
        }

        // All retries exhausted → DLQ
        routeToDlq(queueUrl, messageBody, lastException, SqsFailureClassifier.FailureType.TRANSIENT.name(), attempt, uploadId, uploaderId);
    }

    private void routeToDlq(String queueUrl, String messageBody, Exception cause,
                             String failureType, int attempts, String uploadId, String uploaderId) {
        try {
            String failureReason = cause != null ? cause.getClass().getSimpleName() + ": " + cause.getMessage() : "Unknown";
            SqsDeadLetterEntry entry = new SqsDeadLetterEntry(queueUrl, messageBody, failureReason, failureType, attempts);
            entry.setUploadId(uploadId);
            entry.setUploaderId(uploaderId);
            deadLetterRepository.save(entry);

            auditEventPublisher.publishEvent(
                AuditEventPublisher.EventType.SQS_PUBLISH_FAILURE,
                uploaderId,
                null,
                "SQS publish -> " + queueUrl,
                AuditEventPublisher.ActionResult.FAILURE,
                "Routed to DLQ after " + attempts + " attempt(s). Type=" + failureType + ". Reason=" + failureReason
            );

            logger.error("SQS message for uploadId={} stored in DLQ (attempts={}, type={}, reason={})",
                    uploadId, attempts, failureType, failureReason);
        } catch (Exception dlqEx) {
            // DLQ persistence failed — log and do not propagate, main flow must not be blocked
            logger.error("CRITICAL: Failed to persist message to DLQ for uploadId={}. Message body: {}. DLQ error: {}",
                    uploadId, messageBody, dlqEx.getMessage());
        }
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
