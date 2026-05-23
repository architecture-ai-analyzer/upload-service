# Guia de Teste de Ponta a Ponta (E2E) — Manual

Este guia demonstra como executar o fluxo completo de upload e análise localmente, utilizando o frontend, o backend Spring Boot, o LocalStack (S3 + SQS) e banco de dados H2 em memória.

---

## Pré-requisitos

| Ferramenta     | Versão mínima |
| -------------- | ------------- |
| Docker         | 24+           |
| Docker Compose | v2            |
| Node.js        | 18+           |
| Java           | 21+           |
| Maven          | 3.9+          |

---

## Arquitetura Local

```
┌─────────────────────┐        ┌──────────────────────────────────────┐
│   Frontend           │        │   upload-service (porta 8080)        │
│   React / Vite       │◄──────►│   Spring Boot + H2 (banco em memória)│
│   porta 5173         │        └──────────────┬───────────────────────┘
└─────────────────────┘                        │
                                               │ AWS SDK
                                  ┌────────────▼──────────────┐
                                  │   LocalStack (porta 4566)  │
                                  │   S3  → bucket: upload     │
                                  │   SQS → upload-queue       │
                                  │   SQS → analysis-result-queue│
                                  └────────────────────────────┘
```

---

## Parte 1 — Subindo o Backend e a Infraestrutura

### 1.1 Configurar o arquivo de ambiente

Copie o arquivo de exemplo e ajuste se necessário:

```bash
cp upload-service/envs/.env.example upload-service/envs/.env.local
```

Conteúdo esperado do `envs/.env.local`:

```env
# Spring
SPRING_APPLICATION_NAME=upload-service
SPRING_PROFILES_ACTIVE=dev
SPRING_JPA_HIBERNATE_DDL_AUTO=update
SPRING_DATASOURCE_URL=
SPRING_DATASOURCE_USERNAME=
SPRING_DATASOURCE_PASSWORD=
SPRING_DATASOURCE_DRIVER_CLASS_NAME=
SPRING_FLYWAY_ENABLED=false

# AWS (LocalStack)
CLOUD_AWS_REGION=us-east-2
CLOUD_AWS_ENDPOINT_S3=http://localstack:4566
CLOUD_AWS_ENDPOINT_SQS=http://localstack:4566
CLOUD_AWS_ACCESS_KEY=test
CLOUD_AWS_SECRET_KEY=test

# Application
APPLICATION_S3_BUCKET=upload
APPLICATION_SQS_QUEUE_URL=http://localstack:4566/000000000000/upload-queue
APP_SQS_RESULT_LISTENER_ENABLED=true
APP_SQS_RESULT_LISTENER_QUEUE_URL=http://localstack:4566/000000000000/analysis-result-queue
APP_SQS_RESULT_LISTENER_POLL_INTERVAL_SECONDS=5
APP_SQS_RESULT_LISTENER_MAX_MESSAGES=10
APP_SQS_RESULT_LISTENER_WAIT_TIME_SECONDS=5
```

### 1.2 Subir os containers

```bash
cd upload-service
docker compose up -d
```

Aguarde até os três serviços estarem saudáveis:

```bash
docker compose ps
```

Saída esperada:

```
NAME                           STATUS          PORTS
upload-service-localstack-1    running (healthy)   0.0.0.0:4566->4566/tcp
upload-service-setup-1         exited (0)
upload-service-app-1           running             0.0.0.0:8080->8080/tcp
```

### 1.3 Verificar saúde do backend

```bash
curl -s http://localhost:8080/actuator/health
```

Resposta esperada:

```json
{
  "status": "UP",
  "groups": ["liveness", "readiness"]
}
```

### 1.4 Verificar filas SQS no LocalStack

```bash
docker exec \
  -e AWS_DEFAULT_REGION=us-east-2 \
  -e AWS_ACCESS_KEY_ID=test \
  -e AWS_SECRET_ACCESS_KEY=test \
  upload-service-localstack-1 \
  aws --endpoint-url=http://localhost:4566 sqs list-queues
```

Resposta esperada:

```json
{
  "QueueUrls": [
    "http://localhost:4566/000000000000/upload-queue",
    "http://localhost:4566/000000000000/analysis-result-queue"
  ]
}
```

---

## Parte 2 — Subindo o Frontend

```bash
cd secure-systems-frontend
npm install
npm run dev
```

Acesse: **http://localhost:5173**

> **Nota:** a variável `VITE_API_URL` aponta por padrão para `http://localhost:8080`. Nenhuma configuração adicional é necessária para o ambiente local.

---

## Parte 3 — Fluxo Manual Pelo Frontend

### Passo 1 — Criar um Projeto

1. Acesse a aba **Projetos** no menu lateral.
2. Preencha o formulário:
   - **Nome:** `Hackathon Demo`
   - **Descrição:** `Projeto para demonstração do fluxo completo`
   - **Owner ID:** `usuario-teste` (qualquer string identificadora)
3. Clique em **Criar Projeto**.
4. O projeto será criado e selecionado automaticamente.

**Payload enviado para o backend:**

```http
POST /v1/projects
Content-Type: application/json

{
  "name": "Hackathon Demo",
  "description": "Projeto para demonstração do fluxo completo",
  "ownerId": "usuario-teste"
}
```

**Resposta esperada (HTTP 200):**

```json
{
  "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "name": "Hackathon Demo",
  "description": "Projeto para demonstração do fluxo completo"
}
```

---

### Passo 2 — Fazer Upload de Arquivo

1. Acesse a aba **Upload** no menu lateral.
2. Verifique que o projeto criado está selecionado no topo.
3. Arraste ou selecione um arquivo suportado:
   - **Formatos aceitos:** `.pdf`, `.png`, `.jpg`, `.jpeg`
   - **Tamanho máximo:** 1 GB
4. Clique em **Enviar Arquivo**.

**Payload multipart enviado para o backend:**

```http
POST /v1/uploads
Content-Type: multipart/form-data; boundary=----FormBoundary

------FormBoundary
Content-Disposition: form-data; name="file"; filename="diagrama.pdf"
Content-Type: application/pdf

<bytes do arquivo>
------FormBoundary
Content-Disposition: form-data; name="metadata"
Content-Type: application/json

{
  "filename": "diagrama.pdf",
  "projectId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "uploaderId": "usuario-teste"
}
------FormBoundary--
```

**Resposta esperada (HTTP 201):**

```json
{
  "uploadId": "b7e32c47-1a2b-4f88-9d45-0e6c3f112a8b",
  "s3Key": "upload/projects/3fa85f64-5717-4562-b3fc-2c963f66afa6/usuario-teste/b7e32c47-1a2b-4f88-9d45-0e6c3f112a8b.pdf",
  "presignedUrl": null,
  "expiresInSeconds": 0
}
```

Após o upload bem-sucedido, o frontend redireciona automaticamente para a página de **Status** daquele upload.

---

### Passo 3 — Verificar Status Inicial

Na página de status, o upload aparecerá com status **`COMPLETED`** (upload armazenado no S3 com sucesso, aguardando análise).

Você também pode consultar diretamente via API:

```bash
curl -s http://localhost:8080/v1/uploads/b7e32c47-1a2b-4f88-9d45-0e6c3f112a8b
```

**Resposta esperada:**

```json
{
  "id": "b7e32c47-1a2b-4f88-9d45-0e6c3f112a8b",
  "s3Key": "upload/projects/3fa85f64-5717-4562-b3fc-2c963f66afa6/usuario-teste/b7e32c47-1a2b-4f88-9d45-0e6c3f112a8b.pdf",
  "filename": "diagrama.pdf",
  "contentType": "application/pdf",
  "sizeBytes": 12345,
  "uploaderId": "usuario-teste",
  "status": "COMPLETED",
  "createdAt": "2026-05-06T02:24:09.199779Z",
  "completedAt": "2026-05-06T02:24:09.199789Z",
  "projectId": "3fa85f64-5717-4562-b3fc-2c963f66afa6"
}
```

---

## Parte 4 — Simulando o Serviço de Análise (via SQS)

Em produção, um serviço externo de análise consome o arquivo do S3, processa e publica o resultado na fila `analysis-result-queue`. Localmente, esse passo é feito manualmente.

### Publicar resultado de análise na fila

Substitua o `UPLOAD_ID` pelo ID retornado no passo anterior.

```bash
UPLOAD_ID="b7e32c47-1a2b-4f88-9d45-0e6c3f112a8b"

docker exec \
  -e AWS_DEFAULT_REGION=us-east-2 \
  -e AWS_ACCESS_KEY_ID=test \
  -e AWS_SECRET_ACCESS_KEY=test \
  upload-service-localstack-1 \
  aws --endpoint-url=http://localhost:4566 sqs send-message \
  --queue-url http://localhost:4566/000000000000/analysis-result-queue \
  --message-body '{
    "upload_id": "'"$UPLOAD_ID"'",
    "analysis_result": "OK",
    "risk_score": 10,
    "findings": ["no_threats_detected"],
    "timestamp": '"$(date +%s)"'000,
    "processing_service_id": "analyzer-service-v1"
  }'
```

**Resposta esperada (confirmação de envio):**

```json
{
  "MD5OfMessageBody": "b238ebe82298c236b356595a67f030a5",
  "MessageId": "c942be43-5547-413b-8c3a-003c5f755875"
}
```

---

### Possíveis valores de `analysis_result`

| Valor          | Status resultante no upload | Significado                              |
| -------------- | --------------------------- | ---------------------------------------- |
| `OK`           | `SCANNED_OK`                | Arquivo analisado sem ameaças            |
| `QUARANTINED`  | `QUARANTINED`               | Arquivo com ameaças detectadas           |
| `INCONCLUSIVE` | `ANALYSIS_REVIEW_REQUIRED`  | Análise inconclusiva, revisão necessária |

---

### Exemplo: arquivo com ameaça detectada

```bash
UPLOAD_ID="b7e32c47-1a2b-4f88-9d45-0e6c3f112a8b"

docker exec \
  -e AWS_DEFAULT_REGION=us-east-2 \
  -e AWS_ACCESS_KEY_ID=test \
  -e AWS_SECRET_ACCESS_KEY=test \
  upload-service-localstack-1 \
  aws --endpoint-url=http://localhost:4566 sqs send-message \
  --queue-url http://localhost:4566/000000000000/analysis-result-queue \
  --message-body '{
    "upload_id": "'"$UPLOAD_ID"'",
    "analysis_result": "QUARANTINED",
    "risk_score": 95,
    "findings": ["malware_detected", "suspicious_macro"],
    "timestamp": '"$(date +%s)"'000,
    "processing_service_id": "analyzer-service-v1"
  }'
```

---

## Parte 5 — Verificar Transição de Status

O `AnalysisResultSqsListener` faz polling da fila a cada **5 segundos** (configurado em `APP_SQS_RESULT_LISTENER_POLL_INTERVAL_SECONDS`). Aguarde alguns segundos e verifique:

### Via API

```bash
curl -s http://localhost:8080/v1/uploads/b7e32c47-1a2b-4f88-9d45-0e6c3f112a8b
```

**Resposta após processamento (análise OK):**

```json
{
  "id": "b7e32c47-1a2b-4f88-9d45-0e6c3f112a8b",
  "status": "SCANNED_OK",
  "completedAt": "2026-05-06T02:24:14.001234Z",
  ...
}
```

### Via Frontend

Na aba **Status**, atualize a página. O status será atualizado automaticamente pelo mecanismo de auto-refresh do frontend.

Na aba **Lista de Processamento**, o upload aparecerá com status atualizado.

---

## Parte 6 — Teste E2E Automatizado via Linha de Comando

Para validar o fluxo completo sem o frontend, utilize o script abaixo:

```bash
#!/bin/bash

echo "=== E2E Test ==="

# 1) Criar projeto
PROJECT=$(curl -s -X POST http://localhost:8080/v1/projects \
  -H "Content-Type: application/json" \
  -d '{"name":"E2E Test","description":"Teste automatizado","ownerId":"e2e-user"}')
PID=$(echo "$PROJECT" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
echo "Projeto criado: $PID"

# 2) Criar arquivo PDF de teste
printf '%%PDF-1.7\n%%Test content' > /tmp/teste.pdf

# 3) Fazer upload
UPLOAD=$(curl -s -X POST http://localhost:8080/v1/uploads \
  -F "file=@/tmp/teste.pdf;type=application/pdf" \
  -F 'metadata={"filename":"teste.pdf","projectId":"'"$PID"'","uploaderId":"e2e-user"};type=application/json')
UPL_ID=$(echo "$UPLOAD" | grep -o '"uploadId":"[^"]*"' | cut -d'"' -f4)
echo "Upload criado: $UPL_ID"
echo "Status inicial: $(curl -s http://localhost:8080/v1/uploads/$UPL_ID | grep -o '"status":"[^"]*"' | cut -d'"' -f4)"

# 4) Publicar resultado de análise na fila
docker exec \
  -e AWS_DEFAULT_REGION=us-east-2 \
  -e AWS_ACCESS_KEY_ID=test \
  -e AWS_SECRET_ACCESS_KEY=test \
  upload-service-localstack-1 \
  aws --endpoint-url=http://localhost:4566 sqs send-message \
  --queue-url http://localhost:4566/000000000000/analysis-result-queue \
  --message-body '{"diagram_id":"'"$UPL_ID"'","status":"SCANNED_OK"}'
echo "Mensagem SQS enviada"

# 5) Aguardar processamento e verificar status final
echo "Aguardando listener processar (max 15s)..."
for i in {1..15}; do
  sleep 1
  STATUS=$(curl -s http://localhost:8080/v1/uploads/$UPL_ID | grep -o '"status":"[^"]*"' | cut -d'"' -f4)
  if [ "$STATUS" = "SCANNED_OK" ]; then
    echo "✓ Sucesso! Status final: $STATUS (após ${i}s)"
    exit 0
  fi
done
echo "✗ Timeout - status não atualizou para SCANNED_OK"
exit 1
```

---

## Parte 7 — Inspecionando Logs

### Logs do backend em tempo real

```bash
cd upload-service
docker compose logs -f app
```

### Filtrar logs do listener SQS

```bash
docker compose logs app | grep "AnalysisResultSqsListener"
```

Saída esperada ao processar uma mensagem:

```
INFO  c.f.h.u.i.aws.AnalysisResultSqsListener  : Processing analysis result for upload: b7e32c47-...
INFO  c.f.h.u.i.aws.AnalysisResultSqsListener  : Successfully processed and deleted message: c942be43-...
```

### Logs do LocalStack

```bash
docker compose logs localstack
```

---

## Parte 8 — Reiniciando o Ambiente

Para reiniciar completamente do zero (limpar banco H2, filas e bucket S3):

```bash
cd upload-service
docker compose down -v  # remove volumes do LocalStack
docker compose up -d
```

> **Atenção:** `down -v` apaga todos os dados do LocalStack (filas, bucket, mensagens). Use apenas quando quiser um ambiente limpo.

---

## Referência dos Endpoints

| Método | Endpoint                 | Descrição                               |
| ------ | ------------------------ | --------------------------------------- |
| `POST` | `/v1/projects`           | Cria um novo projeto                    |
| `POST` | `/v1/uploads`            | Envia arquivo com metadados (multipart) |
| `GET`  | `/v1/uploads/{uploadId}` | Consulta status e metadados do upload   |
| `GET`  | `/actuator/health`       | Saúde do serviço                        |
| `GET`  | `/swagger-ui/index.html` | Documentação interativa da API          |

---

## Mapeamento de Status do Upload

| Status (backend)           | Descrição                                               |
| -------------------------- | ------------------------------------------------------- |
| `COMPLETED`                | Arquivo recebido e armazenado no S3, aguardando análise |
| `SCANNED_OK`               | Análise concluída — nenhuma ameaça encontrada           |
| `QUARANTINED`              | Análise concluída — arquivo colocado em quarentena      |
| `ANALYSIS_REVIEW_REQUIRED` | Análise inconclusiva — requer revisão manual            |
| `ANALYSIS_INVALID`         | Resultado de análise inválido recebido                  |
