# Configure the environment as 'dev'
locals {
  environment = "dev"
}

module "rds" {
  source = "../../modules/rds"

  database_name           = "uploaddb"
  db_username             = "postgres"
  db_password             = var.rds_password
  instance_class          = var.rds_instance_class
  allocated_storage       = var.rds_allocated_storage
  engine_version          = "16.3"
  backup_retention_days   = 7
  skip_final_snapshot     = true
  environment             = local.environment
  project_name            = "upload-service"
  vpc_id                  = data.terraform_remote_state.vpc.outputs.vpc_id
  private_db_subnet_ids   = data.terraform_remote_state.vpc.outputs.private_db_subnet_ids
  security_group_name     = "upload-service-rds-sg-${local.environment}"
}

module "s3" {
  source = "../../modules/s3"

  bucket_name             = "upload-service-bucket-${local.environment}"
  versioning_enabled      = true
  server_side_encryption  = "AES256"
  environment             = local.environment
  project_name            = "upload-service"
}

# SQS Module - COMMENTED OUT: Queue will be created manually in AWS
# To enable:
# 1. Uncomment the module below
# 2. Uncomment sqs_queue_arn reference in iam module
# 3. Uncomment AWS_SQS_QUEUE_NAME in k8s-configmap.tf
#
# module "sqs" {
#   source = "../../modules/sqs"
#
#   queue_name                   = "upload-service-queue-${local.environment}"
#   message_retention_seconds    = 1209600  # 14 days
#   visibility_timeout_seconds   = 300      # 5 minutes
#   receive_wait_time_seconds    = 0
#   environment                  = local.environment
#   project_name                 = "upload-service"
# }

# IAM Module - COMMENTED OUT: Using Kubernetes Secret with static credentials instead of IRSA
# Simpler approach for academic project
#
# module "iam" {
#   source = "../../iam"
#
#   cluster_name               = data.terraform_remote_state.eks.outputs.cluster_name
#   oidc_provider_arn          = data.terraform_remote_state.eks.outputs.oidc_provider_arn
#   oidc_provider_url          = data.terraform_remote_state.eks.outputs.oidc_provider_url
#   kubernetes_namespace       = "upload-service"
#   kubernetes_service_account = "upload-service"
#   s3_bucket_arn              = module.s3.bucket_arn
#   # sqs_queue_arn              = module.sqs.queue_arn  # Commented: SQS manual creation
#   environment                = local.environment
#   project_name               = "upload-service"
# }

# Data sources for remote state
data "terraform_remote_state" "vpc" {
  backend = "s3"

  config = {
    bucket         = "tf-state-ia-arch-analyzer"
    key            = "v1/networking/${local.environment}/terraform.tfstate"
    region         = "us-east-2"
  }
}

data "terraform_remote_state" "eks" {
  backend = "s3"

  config = {
    bucket         = "tf-state-ia-arch-analyzer"
    key            = "v1/eks/${local.environment}/terraform.tfstate"
    region         = "us-east-2"
  }
}
