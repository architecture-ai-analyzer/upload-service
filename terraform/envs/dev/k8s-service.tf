resource "kubernetes_service" "upload_service" {
  metadata {
    name      = "upload-service"
    namespace = kubernetes_namespace.upload_service.metadata[0].name
    labels = {
      app = "upload-service"
    }
  }

  spec {
    type = "ClusterIP"

    selector = {
      app = "upload-service"
    }

    port {
      name        = "http"
      protocol    = "TCP"
      port        = 8080
      target_port = 8080
    }

    session_affinity = "None"
  }

  depends_on = [
    kubernetes_deployment.upload_service
  ]
}
