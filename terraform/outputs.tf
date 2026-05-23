output "rds_endpoint" {
  description = "RDS database endpoint"
  value       = module.rds.endpoint
}

output "rds_address" {
  description = "RDS database host"
  value       = module.rds.address
}

output "rds_port" {
  description = "RDS database port"
  value       = module.rds.port
}

output "rds_database_name" {
  description = "RDS database name"
  value       = module.rds.database_name
}

output "rds_username" {
  description = "RDS master username"
  value       = module.rds.username
}

output "rds_jdbc_url" {
  description = "JDBC connection string for application"
  value       = module.rds.jdbc_url
  sensitive   = true
}

output "rds_master_secret_arn" {
  description = "ARN of the RDS master user secret"
  value       = module.rds.master_secret_arn
}

output "s3_bucket_id" {
  description = "S3 bucket for file uploads"
  value       = module.s3.bucket_id
}

output "s3_bucket_arn" {
  description = "S3 bucket ARN"
  value       = module.s3.bucket_arn
}

output "sqs_queue_url" {
  description = "SQS queue URL for upload events"
  value       = module.sqs.queue_url
}

output "sqs_queue_arn" {
  description = "SQS queue ARN"
  value       = module.sqs.queue_arn
}

output "service_account_annotations" {
  description = "Annotations to add to Kubernetes service account"
  value       = module.iam.annotations
}
