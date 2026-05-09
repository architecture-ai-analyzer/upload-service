package com.fiap.hackathon.upload_service.infra.aws;

import software.amazon.awssdk.services.sqs.model.SqsException;

/**
 * Classifies SQS exceptions as transient (retry) or permanent (send to DLQ).
 *
 * <p>Transient failures are caused by temporary infrastructure issues and are
 * safe to retry. Permanent failures indicate a configuration or payload problem
 * that will not be resolved by retrying.</p>
 */
public final class SqsFailureClassifier {

    private SqsFailureClassifier() {
        // static utility
    }

    public enum FailureType {
        /** Temporary; should be retried with back-off. */
        TRANSIENT,
        /** Non-recoverable; should go to DLQ without retry. */
        PERMANENT
    }

    /**
     * Classifies the given {@link Exception} into a {@link FailureType}.
     *
     * <p>Rules:</p>
     * <ul>
     *   <li>Network/IO errors → TRANSIENT</li>
     *   <li>SQS throttling ({@code ThrottlingException}, {@code RequestThrottled}) → TRANSIENT</li>
     *   <li>SQS service errors 5xx → TRANSIENT</li>
     *   <li>SQS auth / queue-not-found / invalid-attribute / payload-too-large → PERMANENT</li>
     *   <li>All other → PERMANENT (conservative: unknown errors go to DLQ for inspection)</li>
     * </ul>
     */
    public static FailureType classify(Exception ex) {
        // Network and IO failures are always transient
        if (ex instanceof java.io.IOException
                || ex instanceof java.net.SocketException
                || ex instanceof java.net.SocketTimeoutException) {
            return FailureType.TRANSIENT;
        }

        if (ex instanceof SqsException sqsEx) {
            return classifySqsException(sqsEx);
        }

        // SDK wrapper: unwrap cause and try again
        if (ex.getCause() instanceof SqsException sqsEx) {
            return classifySqsException(sqsEx);
        }

        // Unknown errors go to DLQ for manual inspection
        return FailureType.PERMANENT;
    }

    private static FailureType classifySqsException(SqsException ex) {
        int statusCode = ex.statusCode();
        String errorCode = ex.awsErrorDetails() != null ? ex.awsErrorDetails().errorCode() : "";

        // Server-side errors are transient
        if (statusCode >= 500 && statusCode < 600) {
            return FailureType.TRANSIENT;
        }

        // Known transient error codes
        if ("ThrottlingException".equalsIgnoreCase(errorCode)
                || "RequestThrottled".equalsIgnoreCase(errorCode)
                || "ServiceUnavailable".equalsIgnoreCase(errorCode)
                || "RequestTimeout".equalsIgnoreCase(errorCode)) {
            return FailureType.TRANSIENT;
        }

        // Permanent: auth, wrong queue, oversized message, invalid attributes
        if ("AccessDenied".equalsIgnoreCase(errorCode)
                || "InvalidClientTokenId".equalsIgnoreCase(errorCode)
                || "AWS.SimpleQueueService.NonExistentQueue".equalsIgnoreCase(errorCode)
                || "InvalidMessageContents".equalsIgnoreCase(errorCode)
                || "MessageTooLong".equalsIgnoreCase(errorCode)
                || "InvalidAttributeName".equalsIgnoreCase(errorCode)
                || "InvalidAttributeValue".equalsIgnoreCase(errorCode)) {
            return FailureType.PERMANENT;
        }

        // 4xx client errors not listed above → PERMANENT
        if (statusCode >= 400 && statusCode < 500) {
            return FailureType.PERMANENT;
        }

        return FailureType.PERMANENT;
    }
}
