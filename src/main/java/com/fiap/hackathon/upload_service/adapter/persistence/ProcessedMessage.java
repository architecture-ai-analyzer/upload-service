package com.fiap.hackathon.upload_service.adapter.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "processed_messages")
public class ProcessedMessage {

    @Id
    private String id;

    private OffsetDateTime processedAt;

    public ProcessedMessage() {}

    public ProcessedMessage(String id, OffsetDateTime processedAt) {
        this.id = id;
        this.processedAt = processedAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public OffsetDateTime getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(OffsetDateTime processedAt) {
        this.processedAt = processedAt;
    }
}
