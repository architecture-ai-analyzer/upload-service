resource "aws_db_subnet_group" "main" {
  name       = "${var.project_name}-rds-subnet-group-${var.environment}"
  subnet_ids = var.private_db_subnet_ids

  tags = {
    Name = "${var.project_name}-rds-subnet-group"
  }
}

resource "aws_security_group" "rds" {
  name        = var.security_group_name
  description = "Security group for ${var.project_name} RDS"
  vpc_id      = var.vpc_id

  ingress {
    from_port   = 5432
    to_port     = 5432
    protocol    = "tcp"
    cidr_blocks = ["10.0.0.0/16"] # VPC CIDR block
    description = "PostgreSQL from VPC"
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
    description = "Allow all outbound traffic"
  }

  tags = {
    Name = var.security_group_name
  }
}

resource "aws_db_instance" "main" {
  identifier     = "${var.project_name}-rds-${var.environment}"
  engine         = "postgres"
  engine_version = var.engine_version
  instance_class = var.instance_class

  allocated_storage     = var.allocated_storage
  storage_type          = "gp2"
  publicly_accessible   = false
  db_subnet_group_name  = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.rds.id]

  db_name  = var.database_name
  username = var.db_username
  password = var.db_password

  backup_retention_period = var.backup_retention_days
  backup_window           = "03:00-04:00"
  maintenance_window      = "sun:04:00-sun:05:00"
  copy_tags_to_snapshot   = true
  delete_automated_backups = true
  skip_final_snapshot     = var.skip_final_snapshot

  tags = {
    Name = "${var.project_name}-rds"
  }

  depends_on = [aws_security_group.rds]
}
