package com.fiap.hackathon.upload_service;

import com.fiap.hackathon.upload_service.infra.audit.AuditEvent;
import com.fiap.hackathon.upload_service.infra.audit.AuditEventPublisher;
import com.fiap.hackathon.upload_service.infra.audit.AuditEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@TestPropertySource(properties = {
    "app.security.gateway-trust.enabled=false",
    "app.security.rate-limit.upload.enabled=false"
})
public class AuditEventTest {
    
    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private AuditEventPublisher auditEventPublisher;
    
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

    @Test
    public void testAuditEventPublisherPublishEvent() {
        // Given: Clear existing events
        auditEventRepository.deleteAll();

        // When: Publish an event via publisher
        auditEventPublisher.publishEvent(
            AuditEventPublisher.EventType.FILE_UPLOAD_SUCCESS,
            "test-user",
            "192.168.1.1",
            "POST /v1/uploads",
            AuditEventPublisher.ActionResult.SUCCESS
        );

        // Then: Wait for async operation and verify event was saved
        await().atMost(5, TimeUnit.SECONDS).until(() -> {
            List<AuditEvent> events = auditEventRepository.findByEventType(
                AuditEventPublisher.EventType.FILE_UPLOAD_SUCCESS
            );
            return !events.isEmpty();
        });

        List<AuditEvent> events = auditEventRepository.findByEventType(
            AuditEventPublisher.EventType.FILE_UPLOAD_SUCCESS
        );
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getUserId()).isEqualTo("test-user");
        assertThat(events.get(0).getAction()).isEqualTo("POST /v1/uploads");
    }

    @Test
    public void testAuditEventPublisherPublishEventWithDetails() {
        // Given: Clear existing events
        auditEventRepository.deleteAll();

        // When: Publish an event with details
        auditEventPublisher.publishEvent(
            AuditEventPublisher.EventType.AUTHENTICATION_FAILURE,
            "test-user",
            "192.168.1.1",
            "POST /v1/uploads",
            AuditEventPublisher.ActionResult.FAILURE,
            "Invalid signature"
        );

        // Then: Wait for async operation and verify event was saved
        await().atMost(5, TimeUnit.SECONDS).until(() -> {
            List<AuditEvent> events = auditEventRepository.findByEventType(
                AuditEventPublisher.EventType.AUTHENTICATION_FAILURE
            );
            return !events.isEmpty();
        });

        List<AuditEvent> events = auditEventRepository.findByEventType(
            AuditEventPublisher.EventType.AUTHENTICATION_FAILURE
        );
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getDetails()).isEqualTo("Invalid signature");
    }
}
