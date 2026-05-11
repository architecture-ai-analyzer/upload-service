package com.fiap.hackathon.upload_service.infra.aws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiap.hackathon.upload_service.adapter.dto.AnalysisCallbackRequest;
import com.fiap.hackathon.upload_service.adapter.dto.AnalysisResultMessage;
import com.fiap.hackathon.upload_service.config.SqsResultListenerProperties;
import com.fiap.hackathon.upload_service.infra.audit.AuditEventPublisher;
import com.fiap.hackathon.upload_service.service.AnalysisCallbackService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
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
    void shouldProcessMessageWhenAnalysisResultIsOk() throws Exception {
        String uploadId = "00000000-0000-0000-0000-000000000001";
        AnalysisResultMessage resultMessage = new AnalysisResultMessage();
        resultMessage.setUploadId(uploadId);
        resultMessage.setAnalysisResult(AnalysisResultMessage.AnalysisResult.OK);
        resultMessage.setRiskScore(15);
        resultMessage.setFindings(List.of("low_risk"));
        resultMessage.setTimestamp(System.currentTimeMillis());
        resultMessage.setProcessingServiceId("analyzer-service-1");

        String messageBody = objectMapper.writeValueAsString(resultMessage);
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

        verify(analysisCallbackService).processAnalysisResult(
                argThat(req -> 
                    req.getUploadId().equals(uploadId) &&
                    req.getAnalysisResult() == AnalysisCallbackRequest.AnalysisResult.OK &&
                    req.getRiskScore() == 15
                ),
                eq("sqs-listener")
        );

        verify(sqsClient).deleteMessage(argThat((DeleteMessageRequest req) -> 
                req.queueUrl().equals(queueUrl) &&
                req.receiptHandle().equals("receipt-handle-123")
        ));
    }

    @Test
    void shouldProcessMessageWhenAnalysisResultIsQuarantined() throws Exception {
        String uploadId = "00000000-0000-0000-0000-000000000002";
        AnalysisResultMessage resultMessage = new AnalysisResultMessage();
        resultMessage.setUploadId(uploadId);
        resultMessage.setAnalysisResult(AnalysisResultMessage.AnalysisResult.QUARANTINED);
        resultMessage.setRiskScore(90);
        resultMessage.setFindings(List.of("malware_detected", "suspicious_behavior"));
        resultMessage.setTimestamp(System.currentTimeMillis());
        resultMessage.setProcessingServiceId("analyzer-service-1");

        String messageBody = objectMapper.writeValueAsString(resultMessage);
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

        verify(analysisCallbackService).processAnalysisResult(
                argThat(req -> 
                    req.getUploadId().equals(uploadId) &&
                    req.getAnalysisResult() == AnalysisCallbackRequest.AnalysisResult.QUARANTINED &&
                    req.getRiskScore() == 90
                ),
                eq("sqs-listener")
        );

        verify(sqsClient).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void shouldProcessMessageWhenAnalysisResultIsInconclusive() throws Exception {
        String uploadId = "00000000-0000-0000-0000-000000000003";
        AnalysisResultMessage resultMessage = new AnalysisResultMessage();
        resultMessage.setUploadId(uploadId);
        resultMessage.setAnalysisResult(AnalysisResultMessage.AnalysisResult.INCONCLUSIVE);
        resultMessage.setRiskScore(50);
        resultMessage.setFindings(List.of("inconclusive_findings"));
        resultMessage.setTimestamp(System.currentTimeMillis());

        String messageBody = objectMapper.writeValueAsString(resultMessage);
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

        verify(analysisCallbackService).processAnalysisResult(
                argThat(req -> 
                    req.getUploadId().equals(uploadId) &&
                    req.getAnalysisResult() == AnalysisCallbackRequest.AnalysisResult.INCONCLUSIVE
                ),
                eq("sqs-listener")
        );

        verify(sqsClient).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void shouldNotDeleteMessageWhenProcessingFails() throws Exception {
        String uploadId = "00000000-0000-0000-0000-000000000004";
        AnalysisResultMessage resultMessage = new AnalysisResultMessage();
        resultMessage.setUploadId(uploadId);
        resultMessage.setAnalysisResult(AnalysisResultMessage.AnalysisResult.OK);
        resultMessage.setRiskScore(20);
        resultMessage.setTimestamp(System.currentTimeMillis());

        String messageBody = objectMapper.writeValueAsString(resultMessage);
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
                .processAnalysisResult(any(), eq("sqs-listener"));

        listener.pollResultQueue();

        verify(analysisCallbackService).processAnalysisResult(any(), eq("sqs-listener"));
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

        verify(analysisCallbackService, never()).processAnalysisResult(any(), any());
        verify(sqsClient, never()).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void shouldProcessMultipleMessagesInBatch() throws Exception {
        Message msg1 = Message.builder()
                .messageId("msg-1")
                .body(objectMapper.writeValueAsString(createResultMessage(
                        "00000000-0000-0000-0000-000000000011",
                        AnalysisResultMessage.AnalysisResult.OK,
                        20
                )))
                .receiptHandle("receipt-1")
                .build();

        Message msg2 = Message.builder()
                .messageId("msg-2")
                .body(objectMapper.writeValueAsString(createResultMessage(
                        "00000000-0000-0000-0000-000000000012",
                        AnalysisResultMessage.AnalysisResult.QUARANTINED,
                        85
                )))
                .receiptHandle("receipt-2")
                .build();

        ReceiveMessageResponse response = ReceiveMessageResponse.builder()
                .messages(msg1, msg2)
                .build();

        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(response);

        listener.pollResultQueue();

        verify(analysisCallbackService, times(2)).processAnalysisResult(any(), eq("sqs-listener"));
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

        verify(analysisCallbackService, never()).processAnalysisResult(any(), any());
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
    void shouldHandleMissingUploadIdInMessage() throws Exception {
        AnalysisResultMessage resultMessage = new AnalysisResultMessage();
        resultMessage.setUploadId(null); // Invalid
        resultMessage.setAnalysisResult(AnalysisResultMessage.AnalysisResult.OK);
        resultMessage.setRiskScore(20);
        resultMessage.setTimestamp(System.currentTimeMillis());

        String messageBody = objectMapper.writeValueAsString(resultMessage);
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

        verify(analysisCallbackService, never()).processAnalysisResult(any(), any());
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

    private AnalysisResultMessage createResultMessage(String uploadId, AnalysisResultMessage.AnalysisResult result, int riskScore) {
        AnalysisResultMessage msg = new AnalysisResultMessage();
        msg.setUploadId(uploadId);
        msg.setAnalysisResult(result);
        msg.setRiskScore(riskScore);
        msg.setTimestamp(System.currentTimeMillis());
        return msg;
    }
}
