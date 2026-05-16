resource "kubernetes_config_map" "upload_service" {
  metadata {
    name      = "upload-service-config"
    namespace = kubernetes_namespace.upload_service.metadata[0].name
  }

  data = {
    # Spring Configuration
    SPRING_APPLICATION_NAME            = "upload-service"
    SPRING_PROFILES_ACTIVE             = var.spring_profile
    SPRING_JPA_HIBERNATE_DDL_AUTO      = var.hibernate_ddl_auto
    SPRING_DATASOURCE_DRIVER_CLASS_NAME = "org.postgresql.Driver"
    SPRING_FLYWAY_ENABLED              = var.flyway_enabled

    # RDS Configuration
    SPRING_DATASOURCE_URL      = module.rds.jdbc_url
    SPRING_DATASOURCE_USERNAME = module.rds.username

    # AWS Configuration
    CLOUD_AWS_REGION           = var.aws_region
    CLOUD_AWS_ENDPOINT_S3      = "https://${var.aws_region}.console.aws.amazon.com/s3/buckets/${var.s3_bucket_name}"
    CLOUD_AWS_ENDPOINT_SQS     = "https://sqs.${var.aws_region}.amazonaws.com"

    # S3 Configuration
    APPLICATION_S3_BUCKET = var.s3_bucket_name

    # SQS Configuration - Upload Queue
    APPLICATION_SQS_QUEUE_URL = "https://sqs.${var.aws_region}.amazonaws.com/${var.aws_account_id}/${var.upload_queue_name}"

    # SQS Configuration - Status Update Queue (Result Listener)
    APP_SQS_RESULT_LISTENER_ENABLED              = var.sqs_result_listener_enabled
    APP_SQS_RESULT_LISTENER_QUEUE_URL            = "https://sqs.${var.aws_region}.amazonaws.com/${var.aws_account_id}/${var.status_update_queue_name}"
    APP_SQS_RESULT_LISTENER_POLL_INTERVAL_SECONDS = tostring(var.sqs_result_listener_poll_interval_seconds)
    APP_SQS_RESULT_LISTENER_MAX_MESSAGES         = tostring(var.sqs_result_listener_max_messages)
    APP_SQS_RESULT_LISTENER_WAIT_TIME_SECONDS    = tostring(var.sqs_result_listener_wait_time_seconds)

    # Logging
    LOG_LEVEL = "INFO"
  }

  depends_on = [
    kubernetes_namespace.upload_service,
    module.rds,
    data.aws_s3_bucket.main
  ]
}
