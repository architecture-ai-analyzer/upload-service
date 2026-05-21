package com.fiap.hackathon.upload_service.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Persiste {@link UploadStatus} pelo {@link Enum#name()} ({@code RECEBIDO}, …),
 * alinhado ao {@link UploadStatus#toJson()} e ao {@link UploadStatus#fromString(String)}.
 */
@Converter(autoApply = false)
public class UploadStatusJpaConverter implements AttributeConverter<UploadStatus, String> {

    @Override
    public String convertToDatabaseColumn(UploadStatus attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public UploadStatus convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        return UploadStatus.fromString(dbData.trim());
    }
}
