package com.fiap.hackathon.upload_service.adapter.controller;

import com.fiap.hackathon.upload_service.infra.audit.AuditEvent;
import com.fiap.hackathon.upload_service.infra.audit.AuditEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditEventControllerTest {

    @Mock
    private AuditEventRepository auditEventRepository;

    @InjectMocks
    private AuditEventController auditEventController;

    @Test
    void getAllEvents_ShouldReturnPageOfAuditEventDTOs() {
        AuditEvent event1 = new AuditEvent("AUTHENTICATION_SUCCESS", "user1", "127.0.0.1", "POST /v1/uploads", "SUCCESS", null);
        AuditEvent event2 = new AuditEvent("FILE_UPLOAD_SUCCESS", "user1", "127.0.0.1", "POST /v1/uploads", "SUCCESS", null);
        List<AuditEvent> events = Arrays.asList(event1, event2);
        Page<AuditEvent> page = new PageImpl<>(events);
        Pageable pageable = PageRequest.of(0, 10);

        when(auditEventRepository.findAll(any(Pageable.class))).thenReturn(page);

        ResponseEntity<Page<com.fiap.hackathon.upload_service.infra.audit.AuditEventDTO>> response = auditEventController.getAllEvents(pageable);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertEquals(2, response.getBody().getTotalElements());
    }

    @Test
    void getEventsByType_ShouldReturnFilteredPage() {
        AuditEvent event = new AuditEvent("AUTHENTICATION_SUCCESS", "user1", "127.0.0.1", "POST /v1/uploads", "SUCCESS", null);
        List<AuditEvent> events = Arrays.asList(event);
        Page<AuditEvent> page = new PageImpl<>(events);
        Pageable pageable = PageRequest.of(0, 10);

        when(auditEventRepository.findByEventTypeAndTimestampBetween(any(), any(), any(), any(Pageable.class))).thenReturn(page);

        ResponseEntity<Page<com.fiap.hackathon.upload_service.infra.audit.AuditEventDTO>> response = auditEventController.getEventsByType("AUTHENTICATION_SUCCESS", pageable);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, response.getBody().getTotalElements());
    }

    @Test
    void getEventsByUserId_ShouldReturnFilteredPage() {
        AuditEvent event = new AuditEvent("AUTHENTICATION_SUCCESS", "user1", "127.0.0.1", "POST /v1/uploads", "SUCCESS", null);
        List<AuditEvent> events = Arrays.asList(event);
        Page<AuditEvent> page = new PageImpl<>(events);
        Pageable pageable = PageRequest.of(0, 10);

        when(auditEventRepository.findByUserIdAndTimestampBetween(any(), any(), any(), any(Pageable.class))).thenReturn(page);

        ResponseEntity<Page<com.fiap.hackathon.upload_service.infra.audit.AuditEventDTO>> response = auditEventController.getEventsByUserId("user1", pageable);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, response.getBody().getTotalElements());
    }

    @Test
    void getEventsByDateRange_ShouldReturnFilteredPage() {
        AuditEvent event = new AuditEvent("AUTHENTICATION_SUCCESS", "user1", "127.0.0.1", "POST /v1/uploads", "SUCCESS", null);
        List<AuditEvent> events = Arrays.asList(event);
        Page<AuditEvent> page = new PageImpl<>(events);
        Pageable pageable = PageRequest.of(0, 10);
        LocalDateTime start = LocalDateTime.now().minusDays(30);
        LocalDateTime end = LocalDateTime.now();

        when(auditEventRepository.findByTimestampBetween(any(), any(), any(Pageable.class))).thenReturn(page);

        ResponseEntity<Page<com.fiap.hackathon.upload_service.infra.audit.AuditEventDTO>> response = auditEventController.getEventsByDateRange(start, end, pageable);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, response.getBody().getTotalElements());
    }
}
