resource "kubernetes_secret" "upload_service" {
  metadata {
    name      = "upload-service-secret"
    namespace = kubernetes_namespace.upload_service.metadata[0].name
    
  }

  data = {
    # RDS Password
    SPRING_DATASOURCE_PASSWORD = var.rds_password

    # AWS Credentials (for S3 and SQS access from pod)
    AWS_ACCESS_KEY_ID     = var.aws_access_key_id
    AWS_SECRET_ACCESS_KEY = var.aws_secret_access_key
  }

  depends_on = [
    kubernetes_namespace.upload_service
  ]
  
  type      = "Opaque"
}
