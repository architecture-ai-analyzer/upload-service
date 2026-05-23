resource "kubernetes_horizontal_pod_autoscaler" "upload_service" {
  metadata {
    name      = "upload-service-hpa"
    namespace = kubernetes_namespace.upload_service.metadata[0].name
    labels = {
      app = "upload-service"
    }
  }

  spec {
    max_replicas = 5
    min_replicas = 2

    scale_target_ref {
      api_version = "apps/v1"
      kind        = "Deployment"
      name        = kubernetes_deployment.upload_service.metadata[0].name
    }

    # CPU-based scaling (70% target utilization)
    target_cpu_utilization_percentage = 70
  }

  depends_on = [
    kubernetes_deployment.upload_service
  ]
}
