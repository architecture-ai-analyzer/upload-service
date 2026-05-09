package com.fiap.hackathon.upload_service.domain;

public enum UploadStatus {
    /** Upload received and persisted; waiting to be processed. */
    PENDING,
    /** File stored in S3 and SQS event published; awaiting AI analysis. */
    COMPLETED,
    /** AI analysis concluded successfully; file cleared. */
    SCANNED_OK,
    /** AI flagged file as dangerous; access blocked. */
    QUARANTINED,
    /** AI returned a result that failed schema validation or business rules. */
    ANALYSIS_INVALID,
    /** AI result was inconclusive; requires human review before promotion. */
    ANALYSIS_REVIEW_REQUIRED
}
