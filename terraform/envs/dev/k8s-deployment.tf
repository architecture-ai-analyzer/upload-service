resource "kubernetes_deployment" "upload_service" {
  depends_on = [
    kubernetes_namespace.upload_service,
    kubernetes_service_account.upload_service,
    kubernetes_config_map.upload_service,
    kubernetes_secret.upload_service,
    module.rds,
    module.s3
  ]

  metadata {
    name      = "upload-service"
    namespace = kubernetes_namespace.upload_service.metadata[0].name
    labels = {
      app     = "upload-service"
      version = "v1"
    }
  }

  wait_for_rollout = false

  spec {
    replicas = 2

    selector {
      match_labels = {
        app = "upload-service"
      }
    }

    template {
      metadata {
        labels = {
          app     = "upload-service"
          version = "v1"
        }

        annotations = {
          "prometheus.io/scrape" = "true"
          "prometheus.io/port"   = "8080"
        }
      }

      spec {
        service_account_name = kubernetes_service_account.upload_service.metadata[0].name

        container {
          name              = "upload-service"
          image             = var.container_image
          image_pull_policy = "IfNotPresent"

          port {
            name           = "http"
            container_port = 8080
            protocol       = "TCP"
          }

          # Loading variables from ConfigMap
          env_from {
            config_map_ref {
              name = kubernetes_config_map.upload_service.metadata[0].name
            }
          }

          # Loading secrets
          env_from {
            secret_ref {
              name = kubernetes_secret.upload_service.metadata[0].name
            }
          }

          # Health checks
          liveness_probe {
            http_get {
              path   = "/actuator/health"
              port   = 8080
              scheme = "HTTP"
            }

            initial_delay_seconds = 60
            period_seconds        = 30
            timeout_seconds       = 5
            failure_threshold     = 3
          }

          readiness_probe {
            http_get {
              path   = "/actuator/health/readiness"
              port   = 8080
              scheme = "HTTP"
            }

            initial_delay_seconds = 30
            period_seconds        = 10
            timeout_seconds       = 5
            failure_threshold     = 3
          }

          # Resource requests and limits
          resources {
            requests = {
              cpu    = "250m"
              memory = "256Mi"
            }

            limits = {
              cpu    = "500m"
              memory = "512Mi"
            }
          }

          # Volume mounts
          volume_mount {
            name       = "tmp"
            mount_path = "/tmp"
          }
        }

        # Volumes
        volume {
          name = "tmp"
          empty_dir {}
        }

        # Security context
        security_context {
          run_as_non_root = true
          run_as_user     = 1000
          fs_group        = 2000
        }

        restart_policy = "Always"
      }
    }
  }
}
