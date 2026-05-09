package com.fiap.hackathon.upload_service.infra.audit;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class AuditEventPublisher {
    
    private static final Logger logger = LoggerFactory.getLogger(AuditEventPublisher.class);
    
    private final AuditEventRepository auditEventRepository;
    
    public AuditEventPublisher(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }
    
    /**
     * Publishes an audit event asynchronously.
     * 
     * @param eventType Type of event (e.g., AUTHENTICATION_FAILURE, RATE_LIMIT_EXCEEDED)
     * @param userId User ID or null if unauthenticated
     * @param clientIp Client IP address
     * @param action Action performed (e.g., POST /v1/uploads)
     * @param actionResult Result of the action (SUCCESS, FAILURE, DENIED)
     */
    @Async
    public void publishEvent(String eventType, String userId, String clientIp, String action, String actionResult) {
        publishEvent(eventType, userId, clientIp, action, actionResult, null);
    }
    
    /**
     * Publishes an audit event with details asynchronously.
     */
    @Async
    public void publishEvent(String eventType, String userId, String clientIp, String action, String actionResult, String details) {
        try {
            AuditEvent event = new AuditEvent(eventType, userId, clientIp, action, actionResult, details);
            auditEventRepository.save(event);
            logger.debug("Audit event published: {} - {} - {}", eventType, action, actionResult);
        } catch (Exception e) {
            logger.error("Failed to publish audit event: {}", eventType, e);
            // Don't propagate - audit logging should not fail the main business operation
        }
    }
    
    /**
     * Event type constants
     */
    public static final class EventType {
        public static final String AUTHENTICATION_FAILURE = "AUTHENTICATION_FAILURE";
        public static final String AUTHENTICATION_SUCCESS = "AUTHENTICATION_SUCCESS";
        public static final String INVALID_SIGNATURE = "INVALID_SIGNATURE";
        public static final String INVALID_TIMESTAMP = "INVALID_TIMESTAMP";
        public static final String AUTHORIZATION_DENIED = "AUTHORIZATION_DENIED";
        public static final String RATE_LIMIT_EXCEEDED = "RATE_LIMIT_EXCEEDED";
        public static final String FILE_VALIDATION_FAILURE = "FILE_VALIDATION_FAILURE";
        public static final String FILE_UPLOAD_SUCCESS = "FILE_UPLOAD_SUCCESS";
        public static final String FILE_UPLOAD_FAILURE = "FILE_UPLOAD_FAILURE";
        public static final String SQS_PUBLISH_FAILURE = "SQS_PUBLISH_FAILURE";
        public static final String ANALYSIS_CALLBACK_RECEIVED = "ANALYSIS_CALLBACK_RECEIVED";
        
        private EventType() {
            // Constants only
        }
    }
    
    /**
     * Action result constants
     */
    public static final class ActionResult {
        public static final String SUCCESS = "SUCCESS";
        public static final String FAILURE = "FAILURE";
        public static final String DENIED = "DENIED";
        
        private ActionResult() {
            // Constants only
        }
    }
}
