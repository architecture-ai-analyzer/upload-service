package com.fiap.hackathon.upload_service.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import org.slf4j.LoggerFactory;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Simplified upload lifecycle statuses. Values are stored in English.
 * This enum accepts Portuguese string representations and maps them to the
 * corresponding English values when deserialized.
 */
public enum UploadStatus {
    /** Received by the service. */
    RECEIVED("RECEBIDO"),
    /** Currently being processed by analysis service. */
    PROCESSING("EM_PROCESSAMENTO"),
    /** Analysis finished (success or quarantine decisions encoded elsewhere). */
    ANALYZED("ANALISADO"),
    /** There was an error processing the upload or analysis. */
    ERROR("ERRO");

    private final String portugueseKey;

    UploadStatus(String portugueseKey) {
        this.portugueseKey = portugueseKey;
    }

    public String portuguese() {
        return portugueseKey;
    }

    @JsonValue
    public String toJson() {
        // Serialize as English enum name
        return name();
    }

    @JsonCreator
    public static UploadStatus fromString(String value) {
        if (value == null) return null;
        String v = value.trim();
        if (v.isEmpty()) return null;
        // Try direct match by enum name (case-insensitive)
        try {
            return UploadStatus.valueOf(v.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            LoggerFactory.getLogger(UploadStatus.class).debug("Value '{}' did not match enum name, trying Portuguese mapping", value);
        }

        // Normalize input: remove accents, convert to upper case and replace spaces with underscore
        String normalized = normalize(v)
                .toUpperCase(Locale.ROOT)
                .replaceAll("\\s+", "_")
                .replaceAll("[^A-Z0-9_]", "");

        for (UploadStatus s : values()) {
            if (s.portugueseKey.equals(normalized)) {
                return s;
            }
        }

        // Accept also inputs that match portugueseKey without underscores (e.g. EM PROCESSAMENTO)
        String compact = normalized.replaceAll("_+", "_");
        for (UploadStatus s : values()) {
            if (s.portugueseKey.equals(compact) || s.portugueseKey.replace("_", "").equals(compact.replace("_", ""))) {
                return s;
            }
        }

        throw new IllegalArgumentException("Unknown UploadStatus: " + value);
    }

    private static String normalize(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD);
        return n.replaceAll("\\p{M}", "");
    }
}
