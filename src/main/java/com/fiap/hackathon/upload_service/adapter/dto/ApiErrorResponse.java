package com.fiap.hackathon.upload_service.adapter.dto;

public record ApiErrorResponse(
        String message,
        String code,
        String detail
) {
}
