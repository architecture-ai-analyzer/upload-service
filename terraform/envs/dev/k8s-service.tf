resource "kubernetes_service" "upload_service" {
  metadata {
    name      = "upload-service"
    namespace = kubernetes_namespace.upload_service.metadata[0].name
    labels = {
      app = "upload-service"
    }

    annotations = {
      "service.beta.kubernetes.io/aws-load-balancer-type"            = "nlb"
      "service.beta.kubernetes.io/aws-load-balancer-nlb-target-type" = "instance"
      "service.beta.kubernetes.io/aws-load-balancer-scheme"          = "internal"
      "service.beta.kubernetes.io/aws-load-balancer-manage-backend-security-group-rules" = "true"
    }
  }

  spec {
    type = "LoadBalancer"

    selector = {
      app = "upload-service"
    }

    port {
      name        = "http"
      protocol    = "TCP"
      port        = 80
      target_port = 8080
    }

    session_affinity = "None"
  }

  wait_for_load_balancer = true

  timeouts {
    create = "10m"
  }

  depends_on = [
    kubernetes_deployment.upload_service
  ]
}
