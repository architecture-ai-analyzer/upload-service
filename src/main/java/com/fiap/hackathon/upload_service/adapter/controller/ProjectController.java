package com.fiap.hackathon.upload_service.adapter.controller;

import com.fiap.hackathon.upload_service.adapter.dto.ProjectRequest;
import com.fiap.hackathon.upload_service.adapter.dto.ProjectResponse;
import com.fiap.hackathon.upload_service.domain.Project;
import com.fiap.hackathon.upload_service.adapter.persistence.ProjectRepository;
import com.fiap.hackathon.upload_service.config.observability.TraceSupport;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/projects")
public class ProjectController {

    private final ProjectRepository projectRepository;

    public ProjectController(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @GetMapping
    public ResponseEntity<List<ProjectResponse>> list() {
        TraceSupport.tagActiveSpan("operation.type", "listProjects");
        List<ProjectResponse> projects = projectRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(projects);
    }

    @GetMapping("/{projectId}")
    public ResponseEntity<ProjectResponse> getById(@PathVariable UUID projectId) {
        TraceSupport.tagActiveSpan("operation.type", "findProjectById");
        TraceSupport.tagActiveSpan("project.id", projectId.toString());
        return projectRepository.findById(projectId)
                .map(project -> ResponseEntity.ok(toResponse(project)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<ProjectResponse> create(@Valid @RequestBody ProjectRequest req) {
        TraceSupport.tagActiveSpan("operation.type", "createProject");
        UUID id = UUID.randomUUID();
        TraceSupport.tagActiveSpan("project.id", id.toString());
        Project p = new Project(id, req.getName(), req.getDescription(), req.getOwnerId(), OffsetDateTime.now());
        projectRepository.save(p);
        return ResponseEntity.ok(toResponse(p));
    }

    private ProjectResponse toResponse(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getOwnerId(),
                project.getCreatedAt()
        );
    }
}
