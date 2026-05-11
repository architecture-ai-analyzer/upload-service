data "terraform_remote_state" "vpc" {
  backend = "s3"

  config = {
    bucket         = var.vpc_remote_state_bucket
    key            = "${var.vpc_remote_state_key}/${var.environment}/terraform.tfstate"
    region         = var.region_default
    dynamodb_table = "tf-state-lock"
    encrypt        = true
  }
}

data "terraform_remote_state" "eks" {
  backend = "s3"

  config = {
    bucket         = var.eks_remote_state_bucket
    key            = "${var.eks_remote_state_key}/${var.environment}/terraform.tfstate"
    region         = var.region_default
    dynamodb_table = "tf-state-lock"
    encrypt        = true
  }
}

data "aws_caller_identity" "current" {}

data "aws_eks_cluster_auth" "cluster" {
  name = local.eks_data.cluster_name
}
