package com.fiap.hackathon.upload_service.infra.aws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiap.hackathon.upload_service.adapter.dto.AnalysisCallbackRequest;
import com.fiap.hackathon.upload_service.adapter.dto.AnalysisResultMessage;
import com.fiap.hackathon.upload_service.config.SqsResultListenerProperties;
import com.fiap.hackathon.upload_service.infra.audit.AuditEventPublisher;
import com.fiap.hackathon.upload_service.service.AnalysisCallbackService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import java.util.List;

/**
 * Listens to SQS queue for analysis result messages from the processing service.
 * Polls the queue at regular intervals and processes results by updating upload status.
 */
@Service
@ConditionalOnProperty(name = "app.sqs.result-listener.enabled", havingValue = "true")
public class AnalysisResultSqsListener {

    private static final Logger logger = LoggerFactory.getLogger(AnalysisResultSqsListener.class);

    private final SqsResultListenerProperties properties;
    private final SqsClient sqsClient;
    private final AnalysisCallbackService analysisCallbackService;
    private final AuditEventPublisher auditEventPublisher;
    private final ObjectMapper objectMapper;

    public AnalysisResultSqsListener(
            SqsResultListenerProperties properties,
            SqsClient sqsClient,
            AnalysisCallbackService analysisCallbackService,
            AuditEventPublisher auditEventPublisher,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.sqsClient = sqsClient;
        this.analysisCallbackService = analysisCallbackService;
        this.auditEventPublisher = auditEventPublisher;
        this.objectMapper = objectMapper;
    }

    /**
     * Scheduled task to poll the result queue at regular intervals.
     * Runs every X seconds as configured in properties.
     */
    @Scheduled(
            fixedDelayString = "${app.sqs.result-listener.poll-interval-seconds:10}",
            timeUnit = java.util.concurrent.TimeUnit.SECONDS,
            initialDelayString = "${app.sqs.result-listener.poll-interval-seconds:10}"
    )
    public void pollResultQueue() {
        if (!properties.isEnabled() || properties.getQueueUrl().isBlank()) {
            logger.debug("Analysis result listener disabled or queue URL not configured");
            return;
        }

        try {
            ReceiveMessageRequest request = ReceiveMessageRequest.builder()
                    .queueUrl(properties.getQueueUrl())
                    .maxNumberOfMessages(properties.getMaxMessages())
                    .waitTimeSeconds(properties.getWaitTimeSeconds())
                    .build();

            ReceiveMessageResponse response = sqsClient.receiveMessage(request);
            List<Message> messages = response.messages();

            if (messages == null || messages.isEmpty()) {
                logger.debug("No messages received from analysis result queue");
                return;
            }

            logger.debug("Received {} messages from analysis result queue", messages.size());

            for (Message message : messages) {
                try {
                    processMessage(message);
                    deleteMessage(message);
                } catch (Exception e) {
                    logger.error("Error processing analysis result message: {}", message.messageId(), e);
                    // Don't delete on error - message will be retried after visibility timeout
                    auditEventPublisher.publishEvent(
                            AuditEventPublisher.EventType.SQS_PUBLISH_FAILURE,
                            null,
                            "sqs-listener",
                            "Analysis result message processing",
                            AuditEventPublisher.ActionResult.FAILURE,
                            String.format("Failed to process message %s: %s", message.messageId(), e.getMessage())
                    );
                }
            }
        } catch (Exception e) {
            logger.error("Error polling analysis result queue: {}", properties.getQueueUrl(), e);
            auditEventPublisher.publishEvent(
                    AuditEventPublisher.EventType.SQS_PUBLISH_FAILURE,
                    null,
                    "sqs-listener",
                    "Analysis result queue polling",
                    AuditEventPublisher.ActionResult.FAILURE,
                    String.format("Queue polling error: %s", e.getMessage())
            );
        }
    }

    /**
     * Process a single message from the queue.
     * Deserializes the message, converts to AnalysisCallbackRequest, and processes via AnalysisCallbackService.
     */
    private void processMessage(Message message) throws Exception {
        String body = message.body();
        logger.debug("Processing analysis result message: {}", message.messageId());
        logger.debug("Message body: {}", body);

        // Deserialize the message
        try {
            AnalysisResultMessage resultMessage = objectMapper.readValue(body, AnalysisResultMessage.class);

            // Validate the message
            if (resultMessage.getUploadId() == null || resultMessage.getUploadId().isBlank()) {
                throw new IllegalArgumentException("Message missing uploadId");
            }

            // Convert to AnalysisCallbackRequest format (internal format)
            AnalysisCallbackRequest callbackRequest = convertToCallbackRequest(resultMessage);

            // Process the result message (updates upload status, emits audit events)
            analysisCallbackService.processAnalysisResult(callbackRequest, "sqs-listener");

            logger.debug("Successfully processed analysis result for upload: {}", resultMessage.getUploadId());
        } catch (com.fasterxml.jackson.databind.JsonMappingException | com.fasterxml.jackson.core.JsonParseException e) {
            logger.error("JSON parsing error in message body: {}. Error: {}", body, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Delete a message from the queue after successful processing.
     */
    private void deleteMessage(Message message) {
        try {
            DeleteMessageRequest request = DeleteMessageRequest.builder()
                    .queueUrl(properties.getQueueUrl())
                    .receiptHandle(message.receiptHandle())
                    .build();

            sqsClient.deleteMessage(request);
            logger.debug("Deleted processed message: {}", message.messageId());
        } catch (Exception e) {
            logger.error("Error deleting message from queue: {}", message.messageId(), e);
        }
    }

    /**
     * Convert AnalysisResultMessage (from processing service) to AnalysisCallbackRequest (internal format).
     */
    private AnalysisCallbackRequest convertToCallbackRequest(AnalysisResultMessage resultMessage) {
        AnalysisCallbackRequest request = new AnalysisCallbackRequest();
        request.setUploadId(resultMessage.getUploadId());
        request.setAnalysisResult(mapAnalysisResult(resultMessage.getAnalysisResult()));
        request.setRiskScore(resultMessage.getRiskScore());
        request.setFindings(resultMessage.getFindings());
        request.setTimestamp(resultMessage.getTimestamp());
        request.setAnalysisServiceId(resultMessage.getProcessingServiceId());
        return request;
    }

    /**
     * Map AnalysisResultMessage.AnalysisResult to AnalysisCallbackRequest.AnalysisResult.
     */
    private AnalysisCallbackRequest.AnalysisResult mapAnalysisResult(AnalysisResultMessage.AnalysisResult result) {
        return switch (result) {
            case OK -> AnalysisCallbackRequest.AnalysisResult.OK;
            case QUARANTINED -> AnalysisCallbackRequest.AnalysisResult.QUARANTINED;
            case INCONCLUSIVE -> AnalysisCallbackRequest.AnalysisResult.INCONCLUSIVE;
        };
    }
}
