package com.fiap.hackathon.upload_service.infra.aws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiap.hackathon.upload_service.adapter.dto.DiagramStatusMessage;
import com.fiap.hackathon.upload_service.config.SqsResultListenerProperties;
import com.fiap.hackathon.upload_service.infra.audit.AuditEventPublisher;
import com.fiap.hackathon.upload_service.service.AnalysisCallbackService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AnalysisResultSqsListenerTest {

    @Mock
    private SqsClient sqsClient;

    @Mock
    private AnalysisCallbackService analysisCallbackService;

    @Mock
    private AuditEventPublisher auditEventPublisher;

    private ObjectMapper objectMapper;
    private SqsResultListenerProperties properties;
    private AnalysisResultSqsListener listener;
    private final String queueUrl = "http://localhost:4566/000000000000/analysis-result-queue";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        objectMapper = new ObjectMapper();

        properties = new SqsResultListenerProperties();
        properties.setEnabled(true);
        properties.setQueueUrl(queueUrl);
        properties.setMaxMessages(10);
        properties.setWaitTimeSeconds(20);
        properties.setPollIntervalSeconds(10);

        listener = new AnalysisResultSqsListener(
                properties,
                sqsClient,
                analysisCallbackService,
                auditEventPublisher,
                objectMapper
        );
    }

    @Test
    void shouldProcessMessageWhenStatusIsScannedOk() throws Exception {
        UUID diagramId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        DiagramStatusMessage payload = new DiagramStatusMessage();
        payload.setDiagramId(diagramId);
        payload.setStatus("SCANNED_OK");

        String messageBody = objectMapper.writeValueAsString(payload);
        Message message = Message.builder()
                .messageId("msg-123")
                .body(messageBody)
                .receiptHandle("receipt-handle-123")
                .build();

        ReceiveMessageResponse response = ReceiveMessageResponse.builder()
                .messages(message)
                .build();

        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(response);

        listener.pollResultQueue();

        verify(analysisCallbackService).applyDiagramStatusFromQueue(
                eq(diagramId),
                eq("SCANNED_OK"),
                eq("sqs-listener")
        );

        verify(sqsClient).deleteMessage(argThat((DeleteMessageRequest req) ->
                req.queueUrl().equals(queueUrl) &&
                        req.receiptHandle().equals("receipt-handle-123")
        ));
    }

    @Test
    void shouldProcessMessageWhenStatusIsQuarantined() throws Exception {
        UUID diagramId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        DiagramStatusMessage payload = new DiagramStatusMessage();
        payload.setDiagramId(diagramId);
        payload.setStatus("QUARANTINED");

        String messageBody = objectMapper.writeValueAsString(payload);
        Message message = Message.builder()
                .messageId("msg-456")
                .body(messageBody)
                .receiptHandle("receipt-handle-456")
                .build();

        ReceiveMessageResponse response = ReceiveMessageResponse.builder()
                .messages(message)
                .build();

        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(response);

        listener.pollResultQueue();

        verify(analysisCallbackService).applyDiagramStatusFromQueue(
                eq(diagramId),
                eq("QUARANTINED"),
                eq("sqs-listener")
        );

        verify(sqsClient).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void shouldProcessMessageWhenStatusIsAnalysisReviewRequired() throws Exception {
        UUID diagramId = UUID.fromString("00000000-0000-0000-0000-000000000003");
        DiagramStatusMessage payload = new DiagramStatusMessage();
        payload.setDiagramId(diagramId);
        payload.setStatus("ANALYSIS_REVIEW_REQUIRED");

        String messageBody = objectMapper.writeValueAsString(payload);
        Message message = Message.builder()
                .messageId("msg-789")
                .body(messageBody)
                .receiptHandle("receipt-handle-789")
                .build();

        ReceiveMessageResponse response = ReceiveMessageResponse.builder()
                .messages(message)
                .build();

        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(response);

        listener.pollResultQueue();

        verify(analysisCallbackService).applyDiagramStatusFromQueue(
                eq(diagramId),
                eq("ANALYSIS_REVIEW_REQUIRED"),
                eq("sqs-listener")
        );

        verify(sqsClient).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void shouldAcceptCamelCaseDiagramId() throws Exception {
        UUID diagramId = UUID.fromString("00000000-0000-0000-0000-000000000099");
        String messageBody = "{\"diagramId\":\"" + diagramId + "\",\"status\":\"SCANNED_OK\"}";
        Message message = Message.builder()
                .messageId("msg-camel")
                .body(messageBody)
                .receiptHandle("receipt-camel")
                .build();

        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(
                ReceiveMessageResponse.builder().messages(message).build());

        listener.pollResultQueue();

        verify(analysisCallbackService).applyDiagramStatusFromQueue(
                eq(diagramId),
                eq("SCANNED_OK"),
                eq("sqs-listener")
        );
        verify(sqsClient).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void shouldNotDeleteMessageWhenProcessingFails() throws Exception {
        UUID diagramId = UUID.fromString("00000000-0000-0000-0000-000000000004");
        DiagramStatusMessage payload = new DiagramStatusMessage();
        payload.setDiagramId(diagramId);
        payload.setStatus("SCANNED_OK");

        String messageBody = objectMapper.writeValueAsString(payload);
        Message message = Message.builder()
                .messageId("msg-error")
                .body(messageBody)
                .receiptHandle("receipt-handle-error")
                .build();

        ReceiveMessageResponse response = ReceiveMessageResponse.builder()
                .messages(message)
                .build();

        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(response);
        doThrow(new AnalysisCallbackService.UploadNotFoundException("UPLOAD_NOT_FOUND", "Upload not found"))
                .when(analysisCallbackService)
                .applyDiagramStatusFromQueue(any(), any(), eq("sqs-listener"));

        listener.pollResultQueue();

        verify(analysisCallbackService).applyDiagramStatusFromQueue(any(), any(), eq("sqs-listener"));
        verify(sqsClient, never()).deleteMessage(any(DeleteMessageRequest.class));
        verify(auditEventPublisher).publishEvent(
                eq(AuditEventPublisher.EventType.SQS_PUBLISH_FAILURE),
                any(),
                any(),
                any(),
                eq(AuditEventPublisher.ActionResult.FAILURE),
                any()
        );
    }

    @Test
    void shouldSkipPollWhenListenerDisabled() {
        properties.setEnabled(false);

        listener.pollResultQueue();

        verify(sqsClient, never()).receiveMessage(any(ReceiveMessageRequest.class));
    }

    @Test
    void shouldSkipPollWhenQueueUrlNotConfigured() {
        properties.setQueueUrl("");

        listener.pollResultQueue();

        verify(sqsClient, never()).receiveMessage(any(ReceiveMessageRequest.class));
    }

    @Test
    void shouldHandleEmptyMessageList() {
        ReceiveMessageResponse response = ReceiveMessageResponse.builder()
                .messages(List.of())
                .build();

        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(response);

        listener.pollResultQueue();

        verify(analysisCallbackService, never()).applyDiagramStatusFromQueue(any(), any(), any());
        verify(sqsClient, never()).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void shouldProcessMultipleMessagesInBatch() throws Exception {
        UUID id1 = UUID.fromString("00000000-0000-0000-0000-000000000011");
        UUID id2 = UUID.fromString("00000000-0000-0000-0000-000000000012");

        Message msg1 = Message.builder()
                .messageId("msg-1")
                .body(objectMapper.writeValueAsString(diagramPayload(id1, "SCANNED_OK")))
                .receiptHandle("receipt-1")
                .build();

        Message msg2 = Message.builder()
                .messageId("msg-2")
                .body(objectMapper.writeValueAsString(diagramPayload(id2, "QUARANTINED")))
                .receiptHandle("receipt-2")
                .build();

        ReceiveMessageResponse response = ReceiveMessageResponse.builder()
                .messages(msg1, msg2)
                .build();

        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(response);

        listener.pollResultQueue();

        verify(analysisCallbackService, times(2)).applyDiagramStatusFromQueue(any(), any(), eq("sqs-listener"));
        verify(sqsClient, times(2)).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void shouldHandleInvalidJsonMessage() {
        Message message = Message.builder()
                .messageId("msg-invalid")
                .body("{invalid json")
                .receiptHandle("receipt-invalid")
                .build();

        ReceiveMessageResponse response = ReceiveMessageResponse.builder()
                .messages(message)
                .build();

        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(response);

        listener.pollResultQueue();

        verify(analysisCallbackService, never()).applyDiagramStatusFromQueue(any(), any(), any());
        verify(sqsClient, never()).deleteMessage(any(DeleteMessageRequest.class));
        verify(auditEventPublisher).publishEvent(
                eq(AuditEventPublisher.EventType.SQS_PUBLISH_FAILURE),
                any(),
                any(),
                any(),
                eq(AuditEventPublisher.ActionResult.FAILURE),
                any()
        );
    }

    @Test
    void shouldHandleMissingDiagramIdInMessage() throws Exception {
        String messageBody = "{\"status\":\"SCANNED_OK\"}";
        Message message = Message.builder()
                .messageId("msg-no-id")
                .body(messageBody)
                .receiptHandle("receipt-no-id")
                .build();

        ReceiveMessageResponse response = ReceiveMessageResponse.builder()
                .messages(message)
                .build();

        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(response);

        listener.pollResultQueue();

        verify(analysisCallbackService, never()).applyDiagramStatusFromQueue(any(), any(), any());
        verify(sqsClient, never()).deleteMessage(any(DeleteMessageRequest.class));
        verify(auditEventPublisher).publishEvent(
                eq(AuditEventPublisher.EventType.SQS_PUBLISH_FAILURE),
                any(),
                any(),
                any(),
                eq(AuditEventPublisher.ActionResult.FAILURE),
                any()
        );
    }

    @Test
    void shouldHandleInvalidStatusString() throws Exception {
        UUID diagramId = UUID.fromString("00000000-0000-0000-0000-000000000020");
        String messageBody = objectMapper.writeValueAsString(diagramPayload(diagramId, "NOT_A_REAL_STATUS"));
        Message message = Message.builder()
                .messageId("msg-bad-status")
                .body(messageBody)
                .receiptHandle("receipt-bad")
                .build();

        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(
                ReceiveMessageResponse.builder().messages(message).build());

        listener.pollResultQueue();

        verify(analysisCallbackService).applyDiagramStatusFromQueue(eq(diagramId), eq("NOT_A_REAL_STATUS"), eq("sqs-listener"));
        verify(sqsClient, never()).deleteMessage(any(DeleteMessageRequest.class));
        verify(auditEventPublisher).publishEvent(
                eq(AuditEventPublisher.EventType.SQS_PUBLISH_FAILURE),
                any(),
                any(),
                any(),
                eq(AuditEventPublisher.ActionResult.FAILURE),
                any()
        );
    }

    private DiagramStatusMessage diagramPayload(UUID diagramId, String status) {
        DiagramStatusMessage m = new DiagramStatusMessage();
        m.setDiagramId(diagramId);
        m.setStatus(status);
        return m;
    }
}
