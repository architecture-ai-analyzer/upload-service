variable "rds_instance_class" {
  description = "RDS instance class"
  type        = string
  default     = "db.t3.micro"
}

variable "rds_allocated_storage" {
  description = "RDS allocated storage"
  type        = number
  default     = 20
}

variable "rds_password" {
  description = "RDS master password"
  type        = string
  sensitive   = true
}

variable "container_image" {
  description = "Container image for upload-service"
  type        = string
  default     = "posfiap/upload-service:latest"
}

variable "aws_access_key_id" {
  description = "AWS Access Key ID for pod credentials"
  type        = string
  sensitive   = true
}

variable "aws_secret_access_key" {
  description = "AWS Secret Access Key for pod credentials"
  type        = string
  sensitive   = true
}

variable "aws_region" {
  description = "AWS region"
  type        = string
  default     = "us-east-2"
}

variable "s3_bucket_name" {
  description = "S3 bucket name"
  type        = string
}

variable "aws_account_id" {
  description = "AWS account ID"
  type        = string
  sensitive   = true
}

variable "upload_queue_name" {
  description = "SQS upload queue name"
  type        = string
  default     = "upload-queue"
}

variable "status_update_queue_name" {
  description = "SQS status update queue name"
  type        = string
  default     = "status-update-queue"
}

variable "spring_profile" {
  description = "Spring profiles active"
  type        = string
  default     = "dev"
}

variable "hibernate_ddl_auto" {
  description = "Hibernate DDL auto setting"
  type        = string
  default     = "update"
}

variable "flyway_enabled" {
  description = "Enable Flyway database migrations"
  type        = string
  default     = "false"
}

variable "sqs_result_listener_enabled" {
  description = "Enable SQS result listener"
  type        = string
  default     = "true"
}

variable "sqs_result_listener_poll_interval_seconds" {
  description = "SQS result listener poll interval in seconds"
  type        = number
  default     = 5
}

variable "sqs_result_listener_max_messages" {
  description = "SQS result listener max messages per poll"
  type        = number
  default     = 10
}

variable "sqs_result_listener_wait_time_seconds" {
  description = "SQS result listener wait time in seconds"
  type        = number
  default     = 5
}

# ========================
# DATADOG
# ========================
variable "datadog_enabled" {
  description = "Enable Datadog tracing"
  type        = bool
  default     = true
}

variable "datadog_service" {
  description = "Datadog service name"
  type        = string
  default     = "upload-service"
}

variable "datadog_version" {
  description = "Application version for Datadog"
  type        = string
  default     = "0.0.1-SNAPSHOT"
}

variable "datadog_agent_host" {
  description = "Datadog Agent hostname in the cluster"
  type        = string
  default     = "datadog-agent.datadog-agent.svc.cluster.local"
}
