resource "aws_sqs_queue" "main" {
  name                       = var.queue_name
  message_retention_seconds  = var.message_retention_seconds
  visibility_timeout_seconds = var.visibility_timeout_seconds
  receive_wait_time_seconds  = var.receive_wait_time_seconds
  sqs_managed_sse_enabled    = true

  tags = {
    Name = var.queue_name
  }
}
