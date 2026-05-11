resource "kubernetes_namespace" "upload_service" {
  metadata {
    name = "upload-service-${local.environment}"

    labels = {
      name = "upload-service"
    }
  }

  depends_on = [
    data.terraform_remote_state.eks
  ]
}
