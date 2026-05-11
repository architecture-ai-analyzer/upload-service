locals {
  # Resource naming convention
  rds_name          = "${var.project_name}-rds-${var.environment}"
  s3_bucket_name    = "${var.project_name}-bucket-${var.environment}"
  sqs_queue_name    = "${var.project_name}-queue-${var.environment}"
  irsa_role_name    = "${var.project_name}-irsa-${var.environment}"
  security_group_name = "${var.project_name}-rds-sg-${var.environment}"

  # Common tags
  common_tags = {
    Name        = var.project_name
    Environment = var.environment
  }

  # Data sources for networking and EKS
  vpc_data = data.terraform_remote_state.vpc.outputs
  eks_data = data.terraform_remote_state.eks.outputs
}
