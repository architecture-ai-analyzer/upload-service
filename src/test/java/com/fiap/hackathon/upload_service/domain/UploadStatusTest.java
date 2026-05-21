package com.fiap.hackathon.upload_service.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UploadStatusTest {

    @ParameterizedTest
    @CsvSource({
            "ERRO, ERRO",
            "erro, ERRO",
            "ANALISADO, ANALISADO",
            "RECEBIDO, RECEBIDO",
            "EM_PROCESSAMENTO, EM_PROCESSAMENTO",
            "'Em processamento', EM_PROCESSAMENTO"
    })
    void fromString_acceptsPortugueseCanonicalAndFriendlyForms(String input, String expectedName) {
        assertThat(UploadStatus.fromString(input)).isEqualTo(UploadStatus.valueOf(expectedName));
    }

    @Test
    void fromString_rejectsEnglishWireValues() {
        assertThatThrownBy(() -> UploadStatus.fromString("RECEIVED"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown UploadStatus");
    }

    @Test
    void fromString_rejectsUnknown() {
        assertThatThrownBy(() -> UploadStatus.fromString("NOT_A_STATUS"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown UploadStatus");
    }
}
