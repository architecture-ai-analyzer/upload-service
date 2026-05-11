CREATE TABLE IF NOT EXISTS audit_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    user_id VARCHAR(255),
    client_ip VARCHAR(45),
    action VARCHAR(255) NOT NULL,
    action_result VARCHAR(50),
    details LONGTEXT,
    CONSTRAINT fk_timestamp_index UNIQUE KEY (timestamp)
);

CREATE INDEX idx_event_type ON audit_events (event_type);
CREATE INDEX idx_user_id ON audit_events (user_id);
CREATE INDEX idx_client_ip ON audit_events (client_ip);
CREATE INDEX idx_timestamp ON audit_events (timestamp);
