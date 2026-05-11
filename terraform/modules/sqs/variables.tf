variable "queue_name" {
  description = "SQS queue name"
  type        = string
}

variable "message_retention_seconds" {
  description = "Message retention in seconds"
  type        = number
  default     = 1209600
}

variable "visibility_timeout_seconds" {
  description = "Visibility timeout in seconds"
  type        = number
  default     = 300
}

variable "receive_wait_time_seconds" {
  description = "Long polling wait time in seconds"
  type        = number
  default     = 0
}

variable "environment" {
  description = "Environment name"
  type        = string
}

variable "project_name" {
  description = "Project name"
  type        = string
}
