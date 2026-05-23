package com.fiap.hackathon.upload_service.infra.aws;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AiAnalysisPayloadTest {

    @Test
    void shouldDeserializeFromJson() throws Exception {
        String json = """
                {
                  "schema_version": 1,
                  "source": {
                    "type": "s3",
                    "bucket": "upload-service-bucket-ai-analyzer",
                    "key": "upload/projects/9ee8e905-fcbe-4854-bbdc-b57c2604c04a/frontend-dev/8a9b2550-6572-4d6c-8c4a-63a379d868e3.png"
                  },
                  "job_id": "job-123",
                  "correlation_id": "correlation-456"
                }
                """;

        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        AiAnalysisPayload payload = objectMapper.readValue(json, AiAnalysisPayload.class);

        assertThat(payload.schemaVersion()).isEqualTo(1);
        assertThat(payload.source().type()).isEqualTo("s3");
        assertThat(payload.source().bucket()).isEqualTo("upload-service-bucket-ai-analyzer");
        assertThat(payload.source().key()).endsWith("frontend-dev/8a9b2550-6572-4d6c-8c4a-63a379d868e3.png");
        assertThat(payload.jobId()).isEqualTo("job-123");
        assertThat(payload.correlationId()).isEqualTo("correlation-456");
    }
}
