package com.fiap.hackathon.upload_service.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import org.slf4j.LoggerFactory;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Estados do ciclo de vida do upload. Os identificadores do enum coincidem com o formato canônico
 * (REST JSON, coluna no banco, campo {@code status} em mensagens SQS):
 * {@code RECEBIDO}, {@code EM_PROCESSAMENTO}, {@code ANALISADO}, {@code ERRO}.
 * <p>
 * A desserialização aceita variações com/sem acentos e com espaços no lugar de underscore
 * (ex.: {@code Em processamento} → {@code EM_PROCESSAMENTO}).
 */
public enum UploadStatus {
    /** Recebido pelo serviço. */
    RECEBIDO,
    /** Em processamento pelo serviço de análise. */
    EM_PROCESSAMENTO,
    /** Análise concluída (detalhes de quarentena etc. ficam em outros campos). */
    ANALISADO,
    /** Erro no processamento ou na análise. */
    ERRO;

    @Override
    public String toString() {
        return name();
    }

    @JsonValue
    public String toJson() {
        return name();
    }

    @JsonCreator
    public static UploadStatus fromString(String value) {
        if (value == null) return null;
        String v = value.trim();
        if (v.isEmpty()) return null;

        String upper = v.toUpperCase(Locale.ROOT);

        String normalized = normalize(upper)
                .replaceAll("\\s+", "_")
                .replaceAll("[^A-Z0-9_]", "");

        for (UploadStatus s : values()) {
            if (s.name().equals(normalized)) {
                return s;
            }
        }

        String compact = normalized.replaceAll("_+", "_");
        for (UploadStatus s : values()) {
            if (s.name().equals(compact) || s.name().replace("_", "").equals(compact.replace("_", ""))) {
                return s;
            }
        }

        LoggerFactory.getLogger(UploadStatus.class).debug("Unknown UploadStatus: {}", value);
        throw new IllegalArgumentException("Unknown UploadStatus: " + value);
    }

    private static String normalize(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD);
        return n.replaceAll("\\p{M}", "");
    }
}
