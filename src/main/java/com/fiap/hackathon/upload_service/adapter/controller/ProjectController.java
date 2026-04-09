package com.fiap.hackathon.upload_service.adapter.controller;

import com.fiap.hackathon.upload_service.adapter.dto.ProjectRequest;
import com.fiap.hackathon.upload_service.adapter.dto.ProjectResponse;
import com.fiap.hackathon.upload_service.domain.Project;
import com.fiap.hackathon.upload_service.adapter.persistence.ProjectRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/v1/projects")
public class ProjectController {

    private final ProjectRepository projectRepository;

    public ProjectController(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @PostMapping
    public ResponseEntity<ProjectResponse> create(@Valid @RequestBody ProjectRequest req) {
        UUID id = UUID.randomUUID();
        Project p = new Project(id, req.getName(), req.getDescription(), req.getOwnerId(), OffsetDateTime.now());
        projectRepository.save(p);
        return ResponseEntity.ok(new ProjectResponse(p.getId(), p.getName(), p.getDescription()));
    }
}
