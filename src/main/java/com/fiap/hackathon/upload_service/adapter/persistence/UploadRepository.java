package com.fiap.hackathon.upload_service.adapter.persistence;

import com.fiap.hackathon.upload_service.domain.Upload;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UploadRepository extends JpaRepository<Upload, UUID> {
}
