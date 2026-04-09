package com.fiap.hackathon.upload_service.adapter.dto;

import jakarta.validation.constraints.NotBlank;

public class ProjectRequest {

    @NotBlank
    private String name;

    private String description;

    private String ownerId;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }
}
