# Upload Service - Analysis Result Consumer Integration

## Summary

The upload-service now supports consuming analysis results from an external processing service via AWS SQS, in addition to the HTTP callback endpoint.

## What Changed

### Before

- AI/Processing service would directly POST results to `/v1/uploads/{uploadId}/analysis-callback`
- Synchronous integration with direct HTTP calls

### After (Current)

- Upload-service publishes upload event to SQS: `upload-queue`
- External Processing Service consumes from `upload-queue`, analyzes file, publishes to: `analysis-result-queue`
- Upload-service now has an `AnalysisResultSqsListener` that polls `analysis-result-queue` and processes results asynchronously
- Still supports HTTP callback endpoint for direct integration if needed

## New Components

### 1. **SqsResultListenerProperties** (`config/`)

Configuration class for SQS listener behavior:

- Queue URL
- Polling interval
- Batch size
- Long-polling wait time

### 2. **DiagramStatusMessage** (`adapter/dto/`)

DTO representing the minimal message format published by the processing service:

```json
{
  "diagram_id": "uuid",
  "status": "PENDING|COMPLETED|SCANNED_OK|QUARANTINED|ANALYSIS_INVALID|ANALYSIS_REVIEW_REQUIRED"
}
```

(`diagram_id` matches the upload row id; `status` must match a [`UploadStatus`](src/main/java/com/fiap/hackathon/upload_service/domain/UploadStatus.java) enum name.)

### 3. **AnalysisResultSqsListener** (`infra/aws/`)

Scheduled service that:

- Polls the result queue at regular intervals (configurable)
- Deserializes and validates messages
- Delegates to `AnalysisCallbackService.applyDiagramStatusFromQueue` for processing
- Handles errors gracefully (messages not deleted on failure, will retry)
- Emits audit events for all operations
- Deletes messages after successful processing

### 4. **AnalysisResultSqsListenerTest** (`test/`)

Comprehensive test suite with 10+ test cases:

- Successful message processing (all result types)
- Error handling and no-delete-on-failure
- Empty queue handling
- Batch message processing
- Invalid JSON handling
- Missing field validation
- Listener enable/disable logic

## Benefits

1. **Decoupling**: Upload service no longer depends on processing service being available at callback time
2. **Scalability**: Processing service can scale independently
3. **Resilience**: Failed messages automatically retry without manual intervention
4. **Idempotency**: Duplicate messages handled safely (409 Conflict response)
5. **Audit Trail**: All processing tracked in `audit_events` table
6. **Flexibility**: Both HTTP callback and SQS polling can coexist

## Configuration

### Minimal Configuration (Development)

```bash
export APP_SQS_RESULT_LISTENER_ENABLED=true
export APP_SQS_RESULT_LISTENER_QUEUE_URL=http://localhost:4566/000000000000/analysis-result-queue
```

### Production Configuration

```bash
export APP_SQS_RESULT_LISTENER_ENABLED=true
export APP_SQS_RESULT_LISTENER_QUEUE_URL=https://sqs.us-east-2.amazonaws.com/123456789/analysis-result-queue
export APP_SQS_RESULT_LISTENER_POLL_INTERVAL_SECONDS=10
export APP_SQS_RESULT_LISTENER_MAX_MESSAGES=20
export APP_SQS_RESULT_LISTENER_WAIT_TIME_SECONDS=20
```

## Architecture Diagram

```
Processing Flow:

User Upload
    │
    ▼
┌─────────────────────┐
│  upload-service     │
│  ▪ Validate         │
│  ▪ Store in S3      │
│  ▪ Publish event    │
└────────┬────────────┘
         │ SQS Event (upload-queue)
         ▼
┌──────────────────────────────┐
│  External Service            │
│  ▪ Consume upload event      │
│  ▪ Analyze file              │
│  ▪ Publish result            │
└────────┬─────────────────────┘
         │ SQS Message (analysis-result-queue)
         ▼
┌──────────────────────────────┐
│  upload-service              │
│  ▪ Poll result queue         │
│  ▪ Validate message          │
│  ▪ Update upload status      │
│  ▪ Audit event emission      │
└──────────────────────────────┘
```

## Message Flow Example

### 1. File Upload

```
POST /v1/uploads (multipart)
→ Stored in S3
→ Event published to upload-queue
→ Returns 201 with uploadId
```

### 2. Processing Service Consumes and Analyzes

```
Polls upload-queue
→ Processes file from S3
→ Publishes result to analysis-result-queue:
{
  "upload_id": "abc-123",
  "analysis_result": "OK",
  "risk_score": 15,
  "findings": ["no_issues"],
  "timestamp": 1712750400000,
  "processing_service_id": "analyzer-v1"
}
```

### 3. Upload Service Consumes Result

```
Listener polls analysis-result-queue every 10s
→ Deserializes message
→ Validates upload exists
→ Updates status: COMPLETED → SCANNED_OK
→ Emits audit event
→ Deletes message from queue
```

### 4. Frontend Queries Status

```
GET /v1/uploads/abc-123
→ Returns status: SCANNED_OK (or QUARANTINED, ANALYSIS_REVIEW_REQUIRED)
→ Frontend displays result
```

## Backward Compatibility

The HTTP callback endpoint remains fully functional:

- POST `/v1/uploads/{uploadId}/analysis-callback`
- Still requires `SCOPE_upload:write`
- Can be used alongside SQS consumer
- Same idempotency and error handling

## Error Handling & Recovery

| Scenario                | Handling                               |
| ----------------------- | -------------------------------------- |
| Invalid JSON            | Not deleted → Retry on next poll       |
| Missing uploadId        | Not deleted → Retry on next poll       |
| Upload not found        | Not deleted → Retry on next poll       |
| Concurrent update (409) | Deleted (idempotent) → No retry        |
| SQS unavailable         | Polling fails → Retry on next interval |
| Processing exception    | Not deleted → Retry on next poll       |

## Monitoring

### Logs to Watch

```bash
# Message processing
tail -f logs/app.log | grep "AnalysisResultSqsListener"

# Enable debug logging
logging.level.com.fiap.hackathon.upload_service.infra.aws=DEBUG
```

### CloudWatch Metrics (SQS)

- Queue depth: Number of pending messages
- Message age: Time since message was sent
- Processing rate: Messages processed per minute

### Audit Events

All operations logged in `audit_events` table:

- Event type: `ANALYSIS_CALLBACK_RECEIVED` or `SQS_PUBLISH_FAILURE`
- User: `null` (system process)
- Client IP: `sqs-listener`
- Details: Full result information

## Testing

### Local Testing with LocalStack

```bash
# Start LocalStack
docker-compose up

# Create result queue
aws --endpoint-url=http://localhost:4566 \
  sqs create-queue --queue-name analysis-result-queue

# Send test message
aws --endpoint-url=http://localhost:4566 \
  sqs send-message \
  --queue-url http://localhost:4566/000000000000/analysis-result-queue \
  --message-body '{...}'

# Run tests
mvn test -Dtest=AnalysisResultSqsListenerTest
```

## Migration Path

1. **Phase 1**: Deploy with listener enabled but using HTTP callbacks for now
2. **Phase 2**: Processing service publishes to result queue
3. **Phase 3**: Monitor SQS queue depth and message processing
4. **Phase 4**: Gradually increase percentage of messages via SQS
5. **Phase 5**: Optional - disable HTTP callback endpoint if only using SQS

## Files Added/Modified

### New Files

- `AnalysisResultSqsListener.java` - Main listener service
- `SqsResultListenerProperties.java` - Configuration properties
- `DiagramStatusMessage.java` - Message DTO
- `AnalysisResultSqsListenerTest.java` - Test suite
- `SQS_RESULT_CONSUMER.md` - Detailed documentation

### Modified Files

- `application.properties` - Added SQS listener configuration
- `application-prod.properties` - Added SQS listener config
- `application-homologation.properties` - Added SQS listener config

## See Also

- [SQS_RESULT_CONSUMER.md](./SQS_RESULT_CONSUMER.md) - Detailed configuration and troubleshooting
- [security.md](./security.md) - Security considerations and audit trails
- `AnalysisCallbackService` - Business logic for processing results
- `AnalysisCallbackController` - HTTP callback endpoint (optional)
