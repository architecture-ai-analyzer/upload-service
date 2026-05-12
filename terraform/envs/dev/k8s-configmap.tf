resource "kubernetes_config_map" "upload_service" {
  metadata {
    name      = "upload-service-config"
    namespace = kubernetes_namespace.upload_service.metadata[0].name
  }

  data = {
    SPRING_APPLICATION_NAME = "upload-service"
    AWS_REGION             = "us-east-2"  # Match your AWS region
    LOG_LEVEL              = "INFO"

    # RDS Configuration
    SPRING_DATASOURCE_URL      = module.rds.jdbc_url
    SPRING_DATASOURCE_USERNAME = module.rds.username

    # S3 Configuration
    AWS_S3_BUCKET_NAME = data.aws_s3_bucket.main.id

    # SQS Configuration - Upload Queue
    AWS_UPLOAD_QUEUE_URL = "https://sqs.us-east-2.amazonaws.com/${data.aws_caller_identity.current.account_id}/upload-queue"

    # SQS Configuration - Status Update Queue
    AWS_STATUS_UPDATE_QUEUE_URL = "https://sqs.us-east-2.amazonaws.com/${data.aws_caller_identity.current.account_id}/status-update-queue"
  }

  depends_on = [
    kubernetes_namespace.upload_service,
    module.rds,
    data.aws_s3_bucket.main
  ]
}
