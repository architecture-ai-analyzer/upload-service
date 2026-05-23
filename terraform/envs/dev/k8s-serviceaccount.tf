resource "kubernetes_service_account" "upload_service" {
  metadata {
    name      = "upload-service"
    namespace = kubernetes_namespace.upload_service.metadata[0].name
  }

  depends_on = [
    kubernetes_namespace.upload_service
  ]
}
