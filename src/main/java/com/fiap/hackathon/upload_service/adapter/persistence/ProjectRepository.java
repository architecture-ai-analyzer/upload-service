package com.fiap.hackathon.upload_service.adapter.persistence;

import com.fiap.hackathon.upload_service.domain.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID> {
}
