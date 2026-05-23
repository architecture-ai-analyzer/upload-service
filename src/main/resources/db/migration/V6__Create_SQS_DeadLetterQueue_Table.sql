CREATE TABLE IF NOT EXISTS sqs_dead_letter_queue (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    queue_url       VARCHAR(500) NOT NULL,
    message_body    LONGTEXT NOT NULL,
    failure_reason  VARCHAR(1000) NOT NULL,
    failure_type    VARCHAR(100) NOT NULL,
    retry_attempts  INT NOT NULL DEFAULT 0,
    upload_id       VARCHAR(36),
    uploader_id     VARCHAR(255),
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP
);

CREATE INDEX idx_dlq_status     ON sqs_dead_letter_queue (status);
CREATE INDEX idx_dlq_queue_url  ON sqs_dead_letter_queue (queue_url);
CREATE INDEX idx_dlq_created_at ON sqs_dead_letter_queue (created_at);
CREATE INDEX idx_dlq_upload_id  ON sqs_dead_letter_queue (upload_id);
