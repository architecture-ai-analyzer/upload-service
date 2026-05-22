package com.fiap.hackathon.upload_service.adapter.controller;

import com.fiap.hackathon.upload_service.adapter.dto.ProjectRequest;
import com.fiap.hackathon.upload_service.adapter.dto.ProjectResponse;
import com.fiap.hackathon.upload_service.adapter.persistence.ProjectRepository;
import com.fiap.hackathon.upload_service.domain.Project;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectControllerTest {

    @Mock
    private ProjectRepository projectRepository;

    @InjectMocks
    private ProjectController projectController;

    @Test
    void list_WithProjects_ShouldReturnList() {
        UUID projectId1 = UUID.randomUUID();
        UUID projectId2 = UUID.randomUUID();
        Project project1 = new Project(projectId1, "Project 1", "Description 1", "owner1", OffsetDateTime.now());
        Project project2 = new Project(projectId2, "Project 2", "Description 2", "owner2", OffsetDateTime.now());

        when(projectRepository.findAll()).thenReturn(List.of(project1, project2));

        ResponseEntity<List<ProjectResponse>> response = projectController.list();

        assertEquals(200, response.getStatusCode().value());
        assertEquals(2, response.getBody().size());
    }

    @Test
    void list_WithNoProjects_ShouldReturnEmptyList() {
        when(projectRepository.findAll()).thenReturn(List.of());

        ResponseEntity<List<ProjectResponse>> response = projectController.list();

        assertEquals(200, response.getStatusCode().value());
        assertEquals(0, response.getBody().size());
    }

    @Test
    void getById_WithValidProjectId_ShouldReturnProject() {
        UUID projectId = UUID.randomUUID();
        Project project = new Project(projectId, "Project 1", "Description 1", "owner1", OffsetDateTime.now());

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        ResponseEntity<ProjectResponse> response = projectController.getById(projectId);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(projectId, response.getBody().getId());
    }

    @Test
    void getById_WithInvalidProjectId_ShouldReturn404() {
        UUID projectId = UUID.randomUUID();

        when(projectRepository.findById(projectId)).thenReturn(Optional.empty());

        ResponseEntity<ProjectResponse> response = projectController.getById(projectId);

        assertEquals(404, response.getStatusCode().value());
    }

    @Test
    void create_WithValidRequest_ShouldReturnCreatedProject() {
        ProjectRequest request = new ProjectRequest();
        request.setName("New Project");
        request.setDescription("Description");
        request.setOwnerId("owner1");

        Project savedProject = new Project(UUID.randomUUID(), "New Project", "Description", "owner1", OffsetDateTime.now());
        when(projectRepository.save(any(Project.class))).thenReturn(savedProject);

        ResponseEntity<ProjectResponse> response = projectController.create(request);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody().getId());
        assertEquals("New Project", response.getBody().getName());
    }

    @Test
    void create_WithNullName_ShouldReturnCreatedProject() {
        ProjectRequest request = new ProjectRequest();
        request.setName(null);
        request.setDescription("Description");
        request.setOwnerId("owner1");

        Project savedProject = new Project(UUID.randomUUID(), null, "Description", "owner1", OffsetDateTime.now());
        when(projectRepository.save(any(Project.class))).thenReturn(savedProject);

        ResponseEntity<ProjectResponse> response = projectController.create(request);

        assertEquals(200, response.getStatusCode().value());
        assertNull(response.getBody().getName());
    }

    @Test
    void create_WithEmptyDescription_ShouldReturnCreatedProject() {
        ProjectRequest request = new ProjectRequest();
        request.setName("New Project");
        request.setDescription("");
        request.setOwnerId("owner1");

        Project savedProject = new Project(UUID.randomUUID(), "New Project", "", "owner1", OffsetDateTime.now());
        when(projectRepository.save(any(Project.class))).thenReturn(savedProject);

        ResponseEntity<ProjectResponse> response = projectController.create(request);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("", response.getBody().getDescription());
    }

    @Test
    void toResponse_ShouldMapAllFields() {
        UUID projectId = UUID.randomUUID();
        OffsetDateTime createdAt = OffsetDateTime.now();
        Project project = new Project(projectId, "Project 1", "Description 1", "owner1", createdAt);

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        ResponseEntity<ProjectResponse> response = projectController.getById(projectId);

        assertEquals(projectId, response.getBody().getId());
        assertEquals("Project 1", response.getBody().getName());
        assertEquals("Description 1", response.getBody().getDescription());
        assertEquals("owner1", response.getBody().getOwnerId());
        assertEquals(createdAt, response.getBody().getCreatedAt());
    }
}
