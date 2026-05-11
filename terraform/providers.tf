terraform {
  required_version = ">= 1.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.20"
    }
  }
}

provider "aws" {
  region = var.region_default

  default_tags {
    tags = {
      Environment = var.environment
      ManagedBy   = "Terraform"
      Repository  = "upload-service"
      Project     = var.project_name
    }
  }
}

provider "kubernetes" {
  host                   = local.eks_data.cluster_endpoint
  cluster_ca_certificate = base64decode(local.eks_data.cluster_certificate_authority_data)

  exec {
    api_version = "client.authentication.k8s.io/v1beta1"
    args        = ["eks", "get-token", "--cluster-name", local.eks_data.cluster_name]
    command     = "aws"
  }
}
