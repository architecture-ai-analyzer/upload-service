package com.fiap.hackathon.upload_service.infra.aws;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SqsDeadLetterRepository extends JpaRepository<SqsDeadLetterEntry, Long> {

    Page<SqsDeadLetterEntry> findByStatus(SqsDeadLetterEntry.Status status, Pageable pageable);

    Page<SqsDeadLetterEntry> findByUploadId(String uploadId, Pageable pageable);

    long countByStatus(SqsDeadLetterEntry.Status status);
}
