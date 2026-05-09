package com.fiap.hackathon.upload_service.adapter.controller;

import com.fiap.hackathon.upload_service.infra.audit.AuditEvent;
import com.fiap.hackathon.upload_service.infra.audit.AuditEventDTO;
import com.fiap.hackathon.upload_service.infra.audit.AuditEventRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/v1/audit")
public class AuditEventController {
    
    private final AuditEventRepository auditEventRepository;
    
    public AuditEventController(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }
    
    /**
     * Get all audit events with pagination.
     * Requires audit:read scope
     */
    @GetMapping("/events")
    @PreAuthorize("hasAnyAuthority('SCOPE_audit:read', 'SCOPE_admin')")
    public ResponseEntity<Page<AuditEventDTO>> getAllEvents(Pageable pageable) {
        Page<AuditEvent> events = auditEventRepository.findAll(pageable);
        List<AuditEventDTO> dtos = events.getContent().stream()
                .map(AuditEventDTO::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(new PageImpl<>(dtos, pageable, events.getTotalElements()));
    }
    
    /**
     * Get audit events by type.
     * Requires audit:read scope
     */
    @GetMapping("/events/by-type")
    @PreAuthorize("hasAnyAuthority('SCOPE_audit:read', 'SCOPE_admin')")
    public ResponseEntity<Page<AuditEventDTO>> getEventsByType(
            @RequestParam String eventType,
            Pageable pageable) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = now.minusDays(30); // Last 30 days
        Page<AuditEvent> events = auditEventRepository.findByEventTypeAndTimestampBetween(eventType, start, now, pageable);
        List<AuditEventDTO> dtos = events.getContent().stream()
                .map(AuditEventDTO::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(new PageImpl<>(dtos, pageable, events.getTotalElements()));
    }
    
    /**
     * Get audit events by user ID.
     * Requires audit:read scope
     */
    @GetMapping("/events/by-user")
    @PreAuthorize("hasAnyAuthority('SCOPE_audit:read', 'SCOPE_admin')")
    public ResponseEntity<Page<AuditEventDTO>> getEventsByUserId(
            @RequestParam String userId,
            Pageable pageable) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = now.minusDays(30); // Last 30 days
        Page<AuditEvent> events = auditEventRepository.findByUserIdAndTimestampBetween(userId, start, now, pageable);
        List<AuditEventDTO> dtos = events.getContent().stream()
                .map(AuditEventDTO::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(new PageImpl<>(dtos, pageable, events.getTotalElements()));
    }
    
    /**
     * Get audit events by date range.
     * Requires audit:read scope
     */
    @GetMapping("/events/by-date-range")
    @PreAuthorize("hasAnyAuthority('SCOPE_audit:read', 'SCOPE_admin')")
    public ResponseEntity<Page<AuditEventDTO>> getEventsByDateRange(
            @RequestParam LocalDateTime start,
            @RequestParam LocalDateTime end,
            Pageable pageable) {
        Page<AuditEvent> events = auditEventRepository.findByTimestampBetween(start, end, pageable);
        List<AuditEventDTO> dtos = events.getContent().stream()
                .map(AuditEventDTO::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(new PageImpl<>(dtos, pageable, events.getTotalElements()));
    }
}
