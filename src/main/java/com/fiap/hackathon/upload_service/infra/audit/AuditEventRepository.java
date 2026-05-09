package com.fiap.hackathon.upload_service.infra.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {
    
    List<AuditEvent> findByEventType(String eventType);
    
    List<AuditEvent> findByUserId(String userId);
    
    List<AuditEvent> findByClientIp(String clientIp);
    
    Page<AuditEvent> findByTimestampBetween(LocalDateTime start, LocalDateTime end, Pageable pageable);
    
    Page<AuditEvent> findByEventTypeAndTimestampBetween(String eventType, LocalDateTime start, LocalDateTime end, Pageable pageable);
    
    Page<AuditEvent> findByUserIdAndTimestampBetween(String userId, LocalDateTime start, LocalDateTime end, Pageable pageable);
    
    Page<AuditEvent> findAll(Pageable pageable);
}
