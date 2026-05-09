package com.fiap.hackathon.upload_service;

import com.fiap.hackathon.upload_service.infra.audit.AuditEvent;
import com.fiap.hackathon.upload_service.infra.audit.AuditEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
    "app.security.gateway-trust.enabled=false",
    "app.security.rate-limit.upload.enabled=false"
})
public class AuditEventTest {
    
    @Autowired
    private AuditEventRepository auditEventRepository;
    
    @Test
    public void testAuditEventPersistence() {
        // Given: Create a test audit event
        AuditEvent event = new AuditEvent(
            "TEST_EVENT",
            "test-user",
            "192.168.1.1",
            "GET /test",
            "SUCCESS",
            "Test event details"
        );
        
        // When: Save the event
        AuditEvent savedEvent = auditEventRepository.save(event);
        
        // Then: Verify it was saved
        assertThat(savedEvent.getId()).isNotNull();
        assertThat(savedEvent.getEventType()).isEqualTo("TEST_EVENT");
        assertThat(savedEvent.getUserId()).isEqualTo("test-user");
        assertThat(savedEvent.getClientIp()).isEqualTo("192.168.1.1");
    }
    
    @Test
    public void testAuditEventQuery() {
        // Given: Create multiple test events
        AuditEvent event1 = new AuditEvent(
            "AUTHENTICATION_SUCCESS",
            "user1",
            "192.168.1.1",
            "POST /v1/uploads",
            "SUCCESS"
        );
        AuditEvent event2 = new AuditEvent(
            "FILE_UPLOAD_SUCCESS",
            "user1",
            "192.168.1.1",
            "POST /v1/uploads",
            "SUCCESS"
        );
        
        auditEventRepository.save(event1);
        auditEventRepository.save(event2);
        
        // When: Query events by type
        List<AuditEvent> events = auditEventRepository.findByEventType("AUTHENTICATION_SUCCESS");
        
        // Then: Verify query results
        assertThat(events).isNotEmpty();
        assertThat(events.get(0).getEventType()).isEqualTo("AUTHENTICATION_SUCCESS");
    }
    
    @Test
    public void testAuditEventQueryByUserId() {
        // Given: Create events for different users
        AuditEvent event1 = new AuditEvent(
            "AUTHENTICATION_SUCCESS",
            "test-user-1",
            "192.168.1.1",
            "POST /v1/uploads",
            "SUCCESS"
        );
        AuditEvent event2 = new AuditEvent(
            "AUTHENTICATION_SUCCESS",
            "test-user-2",
            "192.168.1.2",
            "POST /v1/uploads",
            "SUCCESS"
        );
        
        auditEventRepository.save(event1);
        auditEventRepository.save(event2);
        
        // When: Query events by user
        List<AuditEvent> events = auditEventRepository.findByUserId("test-user-1");
        
        // Then: Verify query results
        assertThat(events).isNotEmpty();
        assertThat(events).allMatch(e -> e.getUserId().equals("test-user-1"));
    }
}
