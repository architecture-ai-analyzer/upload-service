# SQS Analysis Result Consumer - Integration Guide

## Overview

The upload-service now supports consuming analysis results from an external processing service via AWS SQS. This decouples the upload service from direct HTTP callback integration, allowing for asynchronous processing and better scalability.

## Architecture Flow

```
┌─────────────────────────┐
│  upload-service         │
│  1. Receive file        │
│  2. Store in S3         │
│  3. Publish event       │
└────────────┬────────────┘
             │
             ▼
┌────────────────────────────────┐
│  SQS: upload-queue             │
│  (event with file metadata)    │
└────────────┬───────────────────┘
             │
             ▼
┌──────────────────────────────────┐
│  External Service                │
│  (Processing/Analysis Service)   │
│  - Consumes upload event         │
│  - Analyzes file                 │
│  - Publishes result              │
└────────┬──────────────────────────┘
         │
         ▼
┌────────────────────────────────────┐
│  SQS: analysis-result-queue        │
│  (analysis result message)         │
└────────┬───────────────────────────┘
         │
         ▼
┌──────────────────────────────────────┐
│  upload-service (AnalysisResultSqs   │
│  Listener)                           │
│  - Polls result queue               │
│  - Processes results               │
│  - Updates upload status            │
│  - Emits audit events              │
└──────────────────────────────────────┘
```

## Configuration

### Enable the SQS Listener

```properties
# Enable SQS result listener
app.sqs.result-listener.enabled=true

# Queue URL where processing service publishes results
app.sqs.result-listener.queue-url=https://sqs.us-east-2.amazonaws.com/123456789/analysis-result-queue

# Polling interval (seconds)
app.sqs.result-listener.poll-interval-seconds=10

# Max messages to fetch per poll
app.sqs.result-listener.max-messages=10

# Long-polling wait time (seconds)
app.sqs.result-listener.wait-time-seconds=20
```

### Environment Variables (Production)

```bash
APP_SQS_RESULT_LISTENER_ENABLED=true
APP_SQS_RESULT_LISTENER_QUEUE_URL=https://sqs.us-east-2.amazonaws.com/123456789/analysis-result-queue
APP_SQS_RESULT_LISTENER_POLL_INTERVAL_SECONDS=10
APP_SQS_RESULT_LISTENER_MAX_MESSAGES=10
APP_SQS_RESULT_LISTENER_WAIT_TIME_SECONDS=20
```

## Message Format

The processing service must publish messages to the result queue as JSON with `diagram_id` (same value as the upload id) and `status` (a canonical Portuguese [`UploadStatus`](../src/main/java/com/fiap/hackathon/upload_service/domain/UploadStatus.java) string: `RECEBIDO`, `EM_PROCESSAMENTO`, `ANALISADO`, `ERRO`):

```json
{
  "diagram_id": "00000000-0000-0000-0000-000000000001",
  "status": "ANALISADO"
}
```

CamelCase `diagramId` is also accepted for deserialization.

### Message Fields

| Field         | Type            | Required | Description                                                                 |
| ------------- | --------------- | -------- | --------------------------------------------------------------------------- |
| `diagram_id`  | String (UUID)   | Yes      | Upload id (`Upload.id`) to update                                          |
| `status`      | String          | Yes      | Target status: `RECEBIDO`, `EM_PROCESSAMENTO`, `ANALISADO`, `ERRO` (case-insensitive; spaces may be normalized to `_`) |

`completed_at` on the upload is set only when the new status is terminal (`ANALISADO` or `ERRO`).

## Message Processing

### Polling Mechanism

- **Polling Interval**: Configurable, default 10 seconds
- **Wait Time**: Long-polling enabled (20s default) for efficient resource usage
- **Batch Size**: Up to 10 messages per poll (configurable)
- **Automatic Retries**: Failed messages are NOT deleted and will be retried after visibility timeout (default SQS visibility timeout is 30 seconds)

### Message Handling

1. **Receive**: Listener polls the queue at regular intervals
2. **Deserialize**: Message JSON is deserialized to `DiagramStatusMessage` (`diagram_id`, `status`)
3. **Validate**: `diagram_id` and non-blank `status` required
4. **Process**:
   - Upload is loaded by id (`diagram_id`)
   - `status` string is parsed to `UploadStatus` and persisted
   - Idempotency: rejects if upload already in a terminal analysis state (`UploadConflictException`)
   - Audit event is emitted on success or denial
5. **Delete**: Message is deleted from queue on successful processing
6. **Retry**: On failure, message is NOT deleted and will be retried

### Error Handling

| Error Scenario                       | Behavior                                                                             |
| ------------------------------------ | ------------------------------------------------------------------------------------ |
| Invalid JSON                         | Message retained, audit event logged, next poll tries again after visibility timeout |
| Missing `diagram_id` or `status`   | Message retained, audit event logged, next poll tries again                          |
| Invalid `status` (not a valid `UploadStatus` value) | Message retained, audit event logged, next poll tries again                        |
| Upload not found                     | Message retained, audit event logged, may need manual cleanup                        |
| Concurrent processing (409 conflict) | Message deleted (idempotent), audit event logged                                     |
| SQS connectivity error               | Polling fails gracefully, next interval retries                                      |

### Idempotency

The listener ensures idempotent message processing:

- If a message is successfully processed but network fails before deletion confirmation, the message will be redelivered
- The `AnalysisCallbackService` detects uploads already in final state and returns `409 Conflict`
- Message is still deleted (idempotent operation)
- Audit trail records both attempts

## Audit Events

All message processing events are recorded in the `audit_events` table:

- `ANALYSIS_CALLBACK_RECEIVED`: When a result message is successfully processed
- `SQS_PUBLISH_FAILURE`: When polling or processing fails

Example audit event:

```
eventType: ANALYSIS_CALLBACK_RECEIVED
userId: null (system process)
clientIp: sqs-listener
action: Analysis result message processing
actionResult: SUCCESS
details: Analysis result processed. New status: ANALISADO. Risk score: 15. Findings: low_risk. Analysis service: analyzer-service-v1
```

## Monitoring & Observability

### Metrics

- Queue depth (SQS CloudWatch metric)
- Message processing rate
- Processing latency
- Error rate

### Logs

The listener logs:

- Message receipt count
- Processing errors with message ID and details
- Queue polling failures
- Successful status transitions

Enable debug logging:

```properties
logging.level.com.fiap.hackathon.upload_service.infra.aws.AnalysisResultSqsListener=DEBUG
```

## Configuration Examples

### Development (Local Testing with LocalStack)

```properties
app.sqs.result-listener.enabled=true
app.sqs.result-listener.queue-url=http://localhost:4566/000000000000/analysis-result-queue
app.sqs.result-listener.poll-interval-seconds=5
app.sqs.result-listener.max-messages=10
app.sqs.result-listener.wait-time-seconds=5
```

### Production

```properties
app.sqs.result-listener.enabled=true
app.sqs.result-listener.queue-url=https://sqs.us-east-2.amazonaws.com/123456789/analysis-result-queue
app.sqs.result-listener.poll-interval-seconds=10
app.sqs.result-listener.max-messages=20
app.sqs.result-listener.wait-time-seconds=20
```

## Testing

### Unit Tests

Located in `AnalysisResultSqsListenerTest.java`:

- Successful message processing (OK, QUARANTINED, INCONCLUSIVE)
- Error handling and retry logic
- Empty message list handling
- Batch message processing
- Invalid JSON handling
- Missing required fields
- Listener disable/enable logic

### Integration Testing

To test end-to-end with LocalStack:

1. Start LocalStack with SQS:

```bash
docker-compose up
```

2. Create the result queue:

```bash
aws --endpoint-url=http://localhost:4566 sqs create-queue --queue-name analysis-result-queue
```

3. Send a test message:

```bash
aws --endpoint-url=http://localhost:4566 sqs send-message \
  --queue-url http://localhost:4566/000000000000/analysis-result-queue \
  --message-body '{
    "upload_id": "00000000-0000-0000-0000-000000000001",
    "analysis_result": "OK",
    "risk_score": 15,
    "findings": ["test"],
    "timestamp": 1712750400000,
    "processing_service_id": "test-analyzer"
  }'
```

4. Check logs for processing confirmation

## Migration from HTTP Callback

The HTTP callback endpoint (`POST /v1/uploads/{uploadId}/analysis-callback`) is still available and functional. You can:

1. **Use both**: HTTP for direct calls, SQS for async processing
2. **Migrate gradually**: Start with HTTP, add SQS listener, gradually move traffic
3. **Switch completely**: Disable HTTP auth requirement if only using SQS

To use only SQS, set:

```properties
app.sqs.result-listener.enabled=true
# HTTP endpoint still works but optional
```

## Troubleshooting

### Listener Not Processing Messages

1. Check if listener is enabled:

   ```bash
   curl http://localhost:8080/actuator/env | grep sqs.result-listener
   ```

2. Verify queue URL is correct and accessible:

   ```bash
   aws sqs get-queue-attributes --queue-url <URL> --attribute-names All
   ```

3. Check application logs for polling errors:
   ```bash
   tail -f logs/app.log | grep AnalysisResultSqsListener
   ```

### Messages Not Deleting

Messages that fail processing are intentionally NOT deleted (for retry). Check:

- Are there processing errors in logs?
- Is the upload ID valid and exists in database?
- Is the upload already in a final state?

### High Queue Depth

If messages are accumulating:

- Increase `max-messages` up to 10 (default)
- Decrease `poll-interval-seconds` for more frequent polling
- Check logs for processing failures
- Scale up processing capacity if needed

## Security Considerations

- **Message Authentication**: Ensure processing service uses AWS IAM credentials
- **Queue Access**: Restrict queue permissions to upload-service and processing service only
- **Message Encryption**: Enable SQS server-side encryption (SSE)
- **Audit Trail**: All processing is audited in `audit_events` table
- **Replay Protection**: Idempotency ensures duplicate messages don't cause issues
