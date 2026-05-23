package com.fiap.hackathon.upload_service.infra.audit;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_events", indexes = {
    @Index(name = "idx_event_type", columnList = "event_type"),
    @Index(name = "idx_user_id", columnList = "user_id"),
    @Index(name = "idx_client_ip", columnList = "client_ip"),
    @Index(name = "idx_timestamp", columnList = "timestamp")
})
public class AuditEvent {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String eventType;
    
    @Column(nullable = false)
    private LocalDateTime timestamp;
    
    @Column(name = "user_id")
    private String userId;
    
    @Column(name = "client_ip")
    private String clientIp;
    
    @Column(nullable = false)
    private String action;
    
    @Column(name = "action_result")
    private String actionResult;
    
    @Lob
    @Column(columnDefinition = "TEXT")
    private String details;
    
    // Constructors
    public AuditEvent() {
    }
    
    public AuditEvent(String eventType, String userId, String clientIp, String action, String actionResult) {
        this.eventType = eventType;
        this.userId = userId;
        this.clientIp = clientIp;
        this.action = action;
        this.actionResult = actionResult;
        this.timestamp = LocalDateTime.now();
    }
    
    public AuditEvent(String eventType, String userId, String clientIp, String action, String actionResult, String details) {
        this(eventType, userId, clientIp, action, actionResult);
        this.details = details;
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getEventType() {
        return eventType;
    }
    
    public void setEventType(String eventType) {
        this.eventType = eventType;
    }
    
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    public String getClientIp() {
        return clientIp;
    }
    
    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }
    
    public String getAction() {
        return action;
    }
    
    public void setAction(String action) {
        this.action = action;
    }
    
    public String getActionResult() {
        return actionResult;
    }
    
    public void setActionResult(String actionResult) {
        this.actionResult = actionResult;
    }
    
    public String getDetails() {
        return details;
    }
    
    public void setDetails(String details) {
        this.details = details;
    }
}
