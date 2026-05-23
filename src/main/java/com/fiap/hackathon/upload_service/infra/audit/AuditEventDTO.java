package com.fiap.hackathon.upload_service.infra.audit;

import java.time.LocalDateTime;

public class AuditEventDTO {
    
    private Long id;
    private String eventType;
    private LocalDateTime timestamp;
    private String userId;
    private String clientIp;
    private String action;
    private String actionResult;
    private String details;
    
    public AuditEventDTO() {
    }
    
    public AuditEventDTO(AuditEvent event) {
        this.id = event.getId();
        this.eventType = event.getEventType();
        this.timestamp = event.getTimestamp();
        this.userId = event.getUserId();
        this.clientIp = event.getClientIp();
        this.action = event.getAction();
        this.actionResult = event.getActionResult();
        this.details = event.getDetails();
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
