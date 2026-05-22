resource "kubernetes_deployment" "upload_service" {
  depends_on = [
    kubernetes_namespace.upload_service,
    kubernetes_service_account.upload_service,
    kubernetes_config_map.upload_service,
    kubernetes_secret.upload_service,
    module.rds,
    data.aws_s3_bucket.main
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
          "prometheus.io/scrape"                    = "true"
          "prometheus.io/port"                      = "8080"
          "tags.datadoghq.com/env"                  = local.environment
          "tags.datadoghq.com/service"              = var.datadog_service
          "tags.datadoghq.com/version"              = var.datadog_version
          "admission.datadoghq.com/enabled"         = "true"
        }
      }

      spec {
        service_account_name = kubernetes_service_account.upload_service.metadata[0].name

        volume {
          name = "dd-java-agent"
          empty_dir {}
        }

        init_container {
          name    = "dd-java-agent-init"
          image   = "curlimages/curl:8.10.1"
          command = ["sh", "-c", "curl -L -o /dd/dd-java-agent.jar https://dtdg.co/latest-java-tracer"]
          volume_mount {
            name       = "dd-java-agent"
            mount_path = "/dd"
          }
        }

        container {
          name              = "upload-service"
          image             = var.container_image
          image_pull_policy = "Always"

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

          # Datadog Configuration
          env {
            name  = "JAVA_TOOL_OPTIONS"
            value = var.datadog_enabled ? "-javaagent:/dd/dd-java-agent.jar" : ""
          }
          env {
            name  = "DD_SERVICE"
            value = var.datadog_service
          }
          env {
            name  = "DD_ENV"
            value = local.environment
          }
          env {
            name  = "DD_VERSION"
            value = var.datadog_version
          }
          env {
            name  = "DD_LOGS_INJECTION"
            value = var.datadog_enabled ? "true" : "false"
          }
          env {
            name  = "DD_APPSEC_ENABLED"
            value = var.datadog_enabled ? "true" : "false"
          }
          env {
            name  = "DD_IAST_ENABLED"
            value = var.datadog_enabled ? "true" : "false"
          }
          env {
            name  = "DD_AGENT_HOST"
            value = var.datadog_agent_host
          }
          env {
            name  = "DD_DOGSTATSD_PORT"
            value = "8125"
          }
          env {
            name  = "DATADOG_STATSD_HOST"
            value = var.datadog_agent_host
          }
          env {
            name  = "DATADOG_STATSD_PORT"
            value = "8125"
          }
          env {
            name  = "DD_TRACE_DEBUG"
            value = "false"
          }
          env {
            name  = "DD_TRACE_AGENT_PORT"
            value = "8126"
          }
          env {
            name  = "DD_AGENT_PORT"
            value = "8126"
          }

          volume_mount {
            name       = "dd-java-agent"
            mount_path = "/dd"
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
