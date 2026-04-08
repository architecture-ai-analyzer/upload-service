package com.fiap.hackathon.upload_service.adapter.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import com.fiap.hackathon.upload_service.adapter.persistence.ProcessedMessageRepository;
import com.fiap.hackathon.upload_service.domain.Upload;
import com.fiap.hackathon.upload_service.adapter.persistence.UploadRepository;
import com.fiap.hackathon.upload_service.infra.aws.S3ClientWrapper;
import com.fiap.hackathon.upload_service.infra.aws.SqsClientWrapper;

import java.time.OffsetDateTime;
import java.util.List;

@Component
public class ScanWorker {

    private static final Logger log = LoggerFactory.getLogger(ScanWorker.class);

    private final SqsClient sqsClient;
    private final ProcessedMessageRepository processedMessageRepository;
    private final S3ClientWrapper s3ClientWrapper;
    private final SqsClientWrapper sqsClientWrapper;
    private final UploadRepository uploadRepository;

    @Value("${application.sqs.queueUrl:}")
    private String queueUrl;

    @Value("${application.sqs.dlqUrl:}")
    private String dlqUrl;

    @Value("${application.sqs.maxRetries:5}")
    private int maxRetries;

    public ScanWorker(SqsClient sqsClient, ProcessedMessageRepository processedMessageRepository, S3ClientWrapper s3ClientWrapper, SqsClientWrapper sqsClientWrapper, UploadRepository uploadRepository) {
        this.sqsClient = sqsClient;
        this.processedMessageRepository = processedMessageRepository;
        this.s3ClientWrapper = s3ClientWrapper;
        this.sqsClientWrapper = sqsClientWrapper;
        this.uploadRepository = uploadRepository;
    }

    @Scheduled(fixedDelayString = "${worker.poll.ms:5000}")
    public void poll() {
        if (queueUrl == null || queueUrl.isBlank()) {
            return;
        }
        ReceiveMessageRequest req = ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .maxNumberOfMessages(5)
                .waitTimeSeconds(10)
            .attributeNamesWithStrings("ApproximateReceiveCount")
                .build();
        List<Message> messages = sqsClient.receiveMessage(req).messages();
        for (Message m : messages) {
            String messageId = m.messageId();
            try {
                log.info("Received message id={} body={}", messageId, m.body());

                // Idempotency check
                if (processedMessageRepository.existsById(messageId)) {
                    log.info("Message {} already processed, deleting from queue", messageId);
                    sqsClient.deleteMessage(DeleteMessageRequest.builder().queueUrl(queueUrl).receiptHandle(m.receiptHandle()).build());
                    continue;
                }

                // parse message to obtain s3Key and uploadId (simple parse, for robust use JSON lib)
                String body = m.body();
                String s3Key = null;
                String uploadId = null;
                if (body != null) {
                    int k = body.indexOf("\"s3Key\"");
                    if (k > -1) {
                        int colon = body.indexOf(':', k);
                        int start = body.indexOf('"', colon+1)+1;
                        int end = body.indexOf('"', start);
                        if (start>0 && end>start) s3Key = body.substring(start,end);
                    }
                    int u = body.indexOf("\"eventId\"");
                    if (u > -1) {
                        int colon = body.indexOf(':', u);
                        int start = body.indexOf('"', colon+1)+1;
                        int end = body.indexOf('"', start);
                        if (start>0 && end>start) uploadId = body.substring(start,end);
                    }
                }

                if (body != null && (body.contains("\"forceFail\":true") || body.contains("FORCE_FAIL"))) {
                    throw new RuntimeException("Forced failure for testing");
                }

                if (s3Key == null) {
                    log.warn("Message {} has no s3Key; deleting." , messageId);
                    sqsClient.deleteMessage(DeleteMessageRequest.builder().queueUrl(queueUrl).receiptHandle(m.receiptHandle()).build());
                    continue;
                }

                // Download object to ensure key exists and file is reachable before marking processed.
                java.nio.file.Path tmp = java.nio.file.Files.createTempFile("scan-", ".bin");
                java.nio.file.Files.deleteIfExists(tmp);
                s3ClientWrapper.downloadToFile(s3Key, tmp);
                java.nio.file.Files.deleteIfExists(tmp);

                // update upload status
                if (uploadId != null) {
                    try {
                        java.util.UUID uid = java.util.UUID.fromString(uploadId);
                        java.util.Optional<Upload> ou = uploadRepository.findById(uid);
                        if (ou.isPresent()) {
                            Upload up = ou.get();
                            up.setStatus(com.fiap.hackathon.upload_service.domain.UploadStatus.SCANNED_OK);
                            uploadRepository.save(up);
                        }
                    } catch (IllegalArgumentException ex) {
                        // ignore bad uuid
                    }
                }

                // Mark processed
                processedMessageRepository.save(new com.fiap.hackathon.upload_service.adapter.persistence.ProcessedMessage(messageId, OffsetDateTime.now()));

                // delete from queue
                sqsClient.deleteMessage(DeleteMessageRequest.builder().queueUrl(queueUrl).receiptHandle(m.receiptHandle()).build());

            } catch (Exception ex) {
                log.error("Error processing message {}", messageId, ex);
                // handle retry / DLQ
                String rcStr = m.attributesAsStrings().getOrDefault("ApproximateReceiveCount", "1");
                int receiveCount = 1;
                try { receiveCount = Integer.parseInt(rcStr); } catch (NumberFormatException ignore) {}
                if (receiveCount >= maxRetries) {
                    log.warn("Message {} exceeded max retries ({}). Sending to DLQ {}", messageId, receiveCount, dlqUrl);
                    if (dlqUrl != null && !dlqUrl.isBlank()) {
                        // send to DLQ and delete original
                        try {
                            sqsClientWrapper.sendMessageToDlq(dlqUrl, m.body());
                        } catch (Exception sendEx) {
                            log.error("Failed to send message {} to DLQ {}", messageId, dlqUrl, sendEx);
                        }
                        try {
                            sqsClient.deleteMessage(DeleteMessageRequest.builder().queueUrl(queueUrl).receiptHandle(m.receiptHandle()).build());
                        } catch (Exception delEx) {
                            log.error("Failed to delete message {} after DLQ send", messageId, delEx);
                        }
                        // mark processed to avoid reprocessing
                        processedMessageRepository.save(new com.fiap.hackathon.upload_service.adapter.persistence.ProcessedMessage(messageId, OffsetDateTime.now()));
                    }
                } else {
                    log.info("Message {} will be retried by SQS (receiveCount={})", messageId, receiveCount);
                    // do not delete message; it will become visible again
                }
            }
        }
    }
}
