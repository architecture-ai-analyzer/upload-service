package com.fiap.hackathon.upload_service.domain;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ProjectTest {

    @Test
    void defaultConstructor_ShouldCreateEmptyProject() {
        Project project = new Project();

        assertNotNull(project);
        assertNull(project.getId());
        assertNull(project.getName());
        assertNull(project.getDescription());
        assertNull(project.getOwnerId());
        assertNull(project.getCreatedAt());
    }

    @Test
    void parameterizedConstructor_ShouldCreateProjectWithValues() {
        UUID id = UUID.randomUUID();
        String name = "Test Project";
        String description = "Test Description";
        String ownerId = "user-123";
        OffsetDateTime createdAt = OffsetDateTime.now();

        Project project = new Project(id, name, description, ownerId, createdAt);

        assertEquals(id, project.getId());
        assertEquals(name, project.getName());
        assertEquals(description, project.getDescription());
        assertEquals(ownerId, project.getOwnerId());
        assertEquals(createdAt, project.getCreatedAt());
    }

    @Test
    void settersAndGetters_ShouldWorkCorrectly() {
        Project project = new Project();
        UUID id = UUID.randomUUID();
        String name = "Test Project";
        String description = "Test Description";
        String ownerId = "user-123";
        OffsetDateTime createdAt = OffsetDateTime.now();

        project.setId(id);
        project.setName(name);
        project.setDescription(description);
        project.setOwnerId(ownerId);
        project.setCreatedAt(createdAt);

        assertEquals(id, project.getId());
        assertEquals(name, project.getName());
        assertEquals(description, project.getDescription());
        assertEquals(ownerId, project.getOwnerId());
        assertEquals(createdAt, project.getCreatedAt());
    }
}
