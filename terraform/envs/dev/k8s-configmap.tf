resource "kubernetes_config_map" "upload_service" {
  metadata {
    name      = "upload-service-config"
    namespace = kubernetes_namespace.upload_service.metadata[0].name
  }

  data = {
    SPRING_APPLICATION_NAME = "upload-service"
    AWS_REGION             = "us-east-1"  # Match your AWS region
    LOG_LEVEL              = "INFO"

    # RDS Configuration
    SPRING_DATASOURCE_URL      = module.rds.jdbc_url
    SPRING_DATASOURCE_USERNAME = module.rds.username

    # S3 Configuration
    AWS_S3_BUCKET_NAME = module.s3.bucket_id

    # SQS Configuration - COMMENTED OUT: Queue will be created manually in AWS
    # AWS_SQS_QUEUE_NAME   = module.sqs.queue_id
    # AWS_SQS_QUEUE_REGION = "us-east-1"
    # 
    # After creating the queue manually, add these values:
    # AWS_SQS_QUEUE_NAME   = "upload-service-queue-dev"  # Update with actual queue name
    # AWS_SQS_QUEUE_REGION = "us-east-1"
  }

  depends_on = [
    kubernetes_namespace.upload_service,
    module.rds,
    module.s3
  ]
}
