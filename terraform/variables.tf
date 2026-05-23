variable "environment" {
  description = "Environment name (dev, production)"
  type        = string
  default     = "dev"
}

variable "project_name" {
  description = "Project name for resource naming"
  type        = string
  default     = "upload-service"
}

variable "region_default" {
  description = "Default AWS region"
  type        = string
  default     = "us-east-2"
}

variable "vpc_remote_state_bucket" {
  description = "S3 bucket containing the networking remote state"
  type        = string
  default     = "tf-state-ai-architecture-analyzer"
}

variable "vpc_remote_state_key" {
  description = "S3 key for the networking remote state"
  type        = string
  default     = "v1/networking"
}

variable "eks_remote_state_bucket" {
  description = "S3 bucket containing the EKS remote state"
  type        = string
  default     = "tf-state-ai-architecture-analyzer"
}

variable "eks_remote_state_key" {
  description = "S3 key for the EKS remote state"
  type        = string
  default     = "v1/eks"
}

# RDS Configuration
variable "rds_engine_version" {
  description = "PostgreSQL engine version"
  type        = string
  default     = "16.3"
}

variable "rds_instance_class" {
  description = "RDS instance class"
  type        = string
  default     = "db.t3.micro"
}

variable "rds_allocated_storage" {
  description = "RDS allocated storage in GB"
  type        = number
  default     = 20
}

variable "rds_backup_retention_days" {
  description = "RDS backup retention in days"
  type        = number
  default     = 7
}

# S3 Configuration
