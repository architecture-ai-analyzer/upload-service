package com.fiap.hackathon.upload_service.infra.aws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiap.hackathon.upload_service.adapter.dto.DiagramStatusMessage;
import com.fiap.hackathon.upload_service.config.SqsResultListenerProperties;
import com.fiap.hackathon.upload_service.config.observability.TraceSupport;
import com.fiap.hackathon.upload_service.infra.audit.AuditEventPublisher;
import io.opentracing.Span;
import io.opentracing.util.GlobalTracer;
import com.fiap.hackathon.upload_service.service.AnalysisCallbackService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import java.util.List;

/**
 * Listens to SQS queue for diagram status messages from the processing service.
 * Polls the queue at regular intervals and updates {@link com.fiap.hackathon.upload_service.domain.Upload} status.
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

        Span span = GlobalTracer.get().activeSpan();
        if (span != null) {
            span.setTag("operation.type", "sqsPoll");
            span.setTag("messaging.system", "sqs");
            span.setTag("messaging.destination", properties.getQueueUrl());
        }

        try {
                String resolvedQueueUrl = resolveQueueUrl(properties.getQueueUrl());

                ReceiveMessageRequest request = ReceiveMessageRequest.builder()
                    .queueUrl(resolvedQueueUrl)
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
                    deleteMessage(message, resolvedQueueUrl);
                } catch (Exception e) {
                    TraceSupport.addErrorToSpan(e, "SQS_MESSAGE_PROCESSING_ERROR");
                    logger.error("Error processing analysis result message: {}", message.messageId(), e);
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
            TraceSupport.addErrorToSpan(e, "SQS_POLL_ERROR");
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

    private void processMessage(Message message) throws Exception {
        String body = message.body();
        logger.debug("Processing analysis result message: {}", message.messageId());
        logger.debug("Message body: {}", body);

        Span span = GlobalTracer.get().activeSpan();
        if (span != null) {
            span.setTag("operation.type", "sqsProcessMessage");
            span.setTag("messaging.system", "sqs");
            span.setTag("messaging.message_id", message.messageId());
        }

        try {
            DiagramStatusMessage payload = objectMapper.readValue(body, DiagramStatusMessage.class);

            if (payload.getDiagramId() == null) {
                throw new IllegalArgumentException("Message missing diagramId");
            }
            if (payload.getStatus() == null || payload.getStatus().isBlank()) {
                throw new IllegalArgumentException("Message missing status");
            }

            TraceSupport.tagActiveSpan("upload.id", payload.getDiagramId().toString());
            TraceSupport.tagActiveSpan("analysis.result", payload.getStatus());

            analysisCallbackService.applyDiagramStatusFromQueue(
                    payload.getDiagramId(),
                    payload.getStatus(),
                    "sqs-listener"
            );

            logger.debug("Successfully processed diagram status for upload: {}", payload.getDiagramId());
        } catch (com.fasterxml.jackson.databind.JsonMappingException | com.fasterxml.jackson.core.JsonParseException e) {
            logger.error("JSON parsing error in message body: {}. Error: {}", body, e.getMessage(), e);
            throw e;
        }
    }

    private void deleteMessage(Message message, String queueUrl) {
        try {
            DeleteMessageRequest request = DeleteMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .receiptHandle(message.receiptHandle())
                    .build();

            sqsClient.deleteMessage(request);
            logger.debug("Deleted processed message: {}", message.messageId());
        } catch (Exception e) {
            logger.error("Error deleting message from queue: {}", message.messageId(), e);
        }
    }

    private String resolveQueueUrl(String provided) {
        if (provided == null) return provided;
        String p = provided.trim();
        if (p.isBlank()) return p;
        // if it's already a URL, return as-is
        if (p.contains(":")) return p;

        try {
            GetQueueUrlRequest req = GetQueueUrlRequest.builder().queueName(p).build();
            return sqsClient.getQueueUrl(req).queueUrl();
        } catch (Exception e) {
            logger.warn("Failed to resolve queue name '{}' to URL, leaving as-is: {}", p, e.getMessage());
            return p;
        }
    }
}
