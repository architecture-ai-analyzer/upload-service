terraform {
  backend "s3" {
    bucket         = "tf-state-ai-architecture-analyzer"
    key            = "v1/upload-service/dev/terraform.tfstate"
    region         = "us-east-2"
  }
}
