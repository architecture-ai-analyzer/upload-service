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
  default     = "upload-service:latest"
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
