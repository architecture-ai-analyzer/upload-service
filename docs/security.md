# Segurança da Solução - Upload Service

## Objetivo

Este documento descreve os controles mínimos de segurança adotados no upload-service, os riscos atuais e um plano de implementação para os pontos ainda não cobertos.

---

## 1) Requisitos básicos de segurança adotados na solução

### 1.1 Controles já implementados

**Validação de entrada:**

- Validação de tipo de arquivo por allowlist explícita (`application/pdf`, `image/png`, `image/jpg`, `image/jpeg`).
- Limite de tamanho máximo de 1 GiB aplicado em duas camadas (controller + use case).
- Validação de consistência entre extensão do arquivo e content-type declarado.
- Validação de assinatura real do arquivo via magic bytes (PDF `%PDF-`, PNG 8-byte header, JPEG `FF D8 FF`) — previne bypass de content-type.
- Sanitização e validação de `filename`: regex `^[a-zA-Z0-9._-]+$`, max 255 chars, bloqueio de 27 padrões perigosos incluindo path traversal (`..`), null bytes, separadores de diretório e injeção de comandos.
- Bean Validation (`@Valid`) nos metadados JSON do multipart.
- Respostas de erro padronizadas com códigos explícitos sem vazamento de detalhes internos.

**Autenticação e autorização:**

- Modelo de confiança no API Gateway via `GatewayTrustAuthenticationFilter`: valida headers `X-Authenticated-User` + `X-Authenticated-Scopes` injetados pelo Lambda Authorizer.
- Assinatura HMAC-SHA256 obrigatória com proteção anti-replay por timestamp (skew máximo configurável, padrão 300 s).
- Autorização por escopo: `SCOPE_upload:write`, `SCOPE_upload:read`, `SCOPE_audit:read`, `SCOPE_admin` — cada endpoint restrito individualmente.
- CORS restrito a origens explícitas (não wildcard); sessão stateless (`SessionCreationPolicy.STATELESS`).
- Gateway trust e rate limiting ativados automaticamente nos perfis `prod` e `homologation`.

**Resiliência e rastreabilidade:**

- Trilha de auditoria assíncrona persistida em banco (`audit_events`) para todos os eventos relevantes: autenticação, assinatura inválida, timestamp vencido, rate limit excedido, falhas de validação, upload com sucesso, falha SQS.
- Rate limiting por IP/usuário com janela fixa configurável e header `Retry-After` na resposta 429.
- Timeout e retry configurados no AWS SDK (S3 + SQS): `apiCallTimeout=30s`, `apiCallAttemptTimeout=10s`, `numRetries=3`.
- Criptografia em repouso no S3 (SSE-AES256) habilitada por padrão.
- Retry exponencial para falhas transientes do SQS (até 3×, back-off 200ms base); falhas permanentes persistidas em tabela DLQ (`sqs_dead_letter_queue`) para reprocessamento manual.
- Pipeline CI/CD com autenticação AWS via OIDC/assume-role (sem credenciais estáticas); análise SonarQube integrada.
- Testes de autenticação/autorização compreensivos: validação de HMAC, proteção contra replay, negação de escopo insuficiente (testes unitários e integração).

### 1.2 Evidências no código

- `config/SecurityConfig.java` — filter chain, CORS, autorização por escopo
- `config/GatewayTrustAuthenticationFilter.java` — autenticação HMAC + identity context + validação obrigatória de shared-secret
- `config/GatewayTrustProperties.java` — configuração externalizada por ambiente
- `config/CorsProperties.java` — CORS configurável via propriedade por ambiente
- `config/UploadRateLimitFilter.java` — rate limiting com auditoria
- `config/AwsConfig.java` — timeout/retry no SDK AWS
- `usecase/SingleUploadUseCase.java` — orquestração de todas as validações
- `usecase/FileSignatureValidator.java` — magic bytes
- `usecase/FilenameValidator.java` — sanitização e validação de filename
- `adapter/controller/AnalysisCallbackController.java` — endpoint para receber resultado de análise IA
- `service/AnalysisCallbackService.java` — processamento seguro de callback com idempotência (409 Conflict em replay)
- `adapter/dto/AnalysisCallbackRequest.java` — schema de validação para callback IA
- `infra/audit/AuditEvent.java`, `AuditEventPublisher.java`, `AuditEventRepository.java` — trilha de auditoria
- `infra/aws/SqsEventPublisher.java`, `SqsFailureClassifier.java`, `SqsDeadLetterEntry.java` — DLQ e retry SQS
- `infra/aws/S3ClientWrapper.java` — SSE-AES256
- `adapter/controller/AuditEventController.java` — API de consulta de auditoria
- `adapter/controller/ApiExceptionHandler.java` — tratamento de exceções custom (409 Conflict, 404 Not Found)
- `resources/db/migration/` — migrações V1–V6 (incluindo `audit_events` e `sqs_dead_letter_queue`)
- `.github/workflows/ci-cd.yml` — OIDC AWS, SonarQube
- `config/GatewayTrustAuthenticationFilterTest.java` — 9 testes de autenticação/autorização
- `service/AnalysisCallbackServiceTest.java` — 15 testes de callback com idempotência

---

## 2) Estratégias de validação e tratamento de entradas não confiáveis

### 2.1 Estratégias implementadas

- Validação de presença do arquivo multipart (`file` obrigatório e não vazio) — `FILE_REQUIRED`.
- Validação de tamanho máximo (1 GiB) em duas camadas: controller + use case — `FILE_SIZE_EXCEEDED`.
- Validação de content-type por allowlist — `INVALID_CONTENT_TYPE`.
- Validação de assinatura real do arquivo via magic bytes para PDF/PNG/JPEG — `MIME_SIGNATURE_MISMATCH`.
- Validação de consistência extensão × content-type — `EXTENSION_CONTENT_TYPE_MISMATCH`.
- Sanitização e validação de `filename`: allowlist regex, max 255 chars, 27 padrões perigosos bloqueados (path traversal, null bytes, injeção de comandos) — `INVALID_FILENAME`.
- Bean Validation (`@Valid`) nos metadados JSON do multipart — `INVALID_MULTIPART_METADATA`.
- Rate limiting de upload com janela fixa por IP/usuário, configurável por ambiente; resposta 429 com `Retry-After`.
- Todos os eventos de rejeição emitem entrada na trilha de auditoria assíncrona.

### 2.2 Lacunas identificadas

- **Rate limiting distribuído (multi-instância):** versão atual usa `ConcurrentHashMap` in-memory por instância. Em deploy com múltiplas réplicas, cada nó tem contador independente. Mitigação operacional: rate limiting de borda via API Gateway / WAF antes de chegar ao serviço.

### 2.3 Plano de implementação (entradas não confiáveis)

#### Pendente

1. Evoluir rate limiting para abordagem distribuída (API Gateway WAF / Redis) quando a arquitetura escalar para múltiplas réplicas sem API Gateway como única entrada.

Critérios de aceite:

- Upload com extensão/tipo inconsistentes deve falhar com `400` e código de erro específico. ✅ Implementado.
- Requisições acima do limite devem retornar `429 Too Many Requests`. ✅ Implementado.
- Filename com path traversal ou caracteres perigosos deve falhar com `400`. ✅ Implementado.

---

## 3) Tratamento seguro de falhas ou comportamentos inesperados da IA

## Contexto de arquitetura

Neste ecossistema, existem quatro responsabilidades separadas:

- API Gateway ou BFF (borda/autenticação de entrada)
- Serviço de Upload e Orquestração (este projeto)
- Serviço de Processamento (análise e mudança de status de processamento)
- Serviço de Relatórios (consolidação e exposição de resultado)

O upload-service deve focar unicamente em recebimento/validação/armazenamento/publicação de evento. A mudança de status pós-processamento é responsabilidade do serviço de processamento.

Status implementados atualmente no serviço:

- `PENDING`
- `COMPLETED`
- `SCANNED_OK`
- `QUARANTINED`
- `ANALYSIS_INVALID`
- `ANALYSIS_REVIEW_REQUIRED`

Responsabilidade por status:

- Upload-service: controla diretamente `PENDING` e `COMPLETED` no fluxo atual de upload.
- Serviço de Processamento: é responsável pela evolução pós-processamento para `SCANNED_OK`, `QUARANTINED`, `ANALYSIS_INVALID` e `ANALYSIS_REVIEW_REQUIRED`.

### 3.1 Medidas implementadas no upload-service

- Desacoplamento via SQS: falhas de análise não bloqueiam o recebimento do upload.
- Ciclo de vida rastreável com status implementados: `PENDING` → `COMPLETED` (upload-service), com evolução pós-processamento no serviço de processamento (`SCANNED_OK` / `QUARANTINED` / `ANALYSIS_INVALID` / `ANALYSIS_REVIEW_REQUIRED`).
- **✅ Endpoint de callback:** `/v1/uploads/{uploadId}/analysis-callback` implementado em `AnalysisCallbackController` para receber resultado IA com validação de schema.
- **✅ Idempotência de callback:** rejeição com `409 Conflict` (`UPLOAD_ALREADY_ANALYZED`) se upload já está em estado final — previne replay attacks.
- **✅ Persistência de decisão:** resultado IA persistido em banco com auditoria (`ANALYSIS_CALLBACK_RECEIVED` event); transição de status (`COMPLETED` → `SCANNED_OK` / `QUARANTINED` / `ANALYSIS_REVIEW_REQUIRED`).
- Retry exponencial + DLQ para falhas de publicação SQS: o arquivo está seguro no S3 mesmo quando a fila falha.
- Tratamento centralizado de exceções sem vazamento de stack trace ou detalhes internos nas respostas HTTP.
- Auditoria de todas as falhas de publicação SQS com tipo de falha (transiente/permanente) e contagem de tentativas.

### 3.2 Lacunas atuais para segurança de IA

- **Boundary entre serviços ainda sem contrato formal versionado:** embora o upload-service publique evento para processamento, o contrato de eventos e campos obrigatórios entre Upload ↔ Processamento ↔ Relatórios ainda depende de alinhamento de integração entre times/serviços.
- **Sem trilha unificada cross-service para decisão final:** o upload-service audita seu próprio boundary (validação, S3, SQS), mas não centraliza a decisão final do processamento por desenho arquitetural.

### 3.3 Plano de implementação (falhas da IA)

#### Prioridade P1 — integração entre serviços (fora do escopo deste serviço)

1. Definir contrato versionado de evento entre Upload e Processamento (schema, campos obrigatórios, semântica de erro, estratégia de evolução).
2. Garantir idempotência no serviço de processamento para evitar dupla atualização de status quando houver reentrega de mensagem.
3. Definir trilha de auditoria no serviço de processamento e/ou camada observabilidade para registrar decisão final de análise.

Critérios de aceite:

- Upload-service deve continuar respondendo ao cliente após upload válido sem depender do resultado da IA. ✅
- Serviço de processamento deve ser o único responsável por estados pós-análise. ✅ Boundary definido.
- Contrato de integração entre serviços deve estar versionado e testado em integração.

---

## 4) Práticas mínimas de segurança na comunicação entre serviços

### 4.1 Práticas já adotadas

- Integrações com S3 e SQS feitas exclusivamente pelo AWS SDK v2 oficial; sem chamadas HTTP diretas.
- Todos os parâmetros sensíveis (secrets, URLs, bucket, queue) externalizados via variáveis de ambiente — sem hardcode.
- **Autenticação/autorização:** `GatewayTrustAuthenticationFilter` valida identidade propagada pelo API Gateway (Lambda Authorizer) via headers HMAC-SHA256 assinados; endpoints protegidos por escopo em homolog/prod.
- **✅ Shared-secret obrigatório:** validação em `@PostConstruct` que garante `app.security.gateway-trust.shared-secret` é configurado quando `gateway-trust.enabled=true`; falha rápida na inicialização se não configurado em produção.
- **✅ CORS parametrizado:** origens permitidas configuráveis via `app.security.cors.allowed-origins` (env: `APP_SECURITY_CORS_ALLOWED_ORIGINS`) por ambiente; padrão: `localhost:5173,localhost:5174`; não usa wildcard.
- **Sessão stateless:** `SessionCreationPolicy.STATELESS`; CSRF desabilitado intencionalmente (API REST sem cookie de sessão).
- **AWS SDK resiliente:** timeout global (30s), timeout por tentativa (10s), 3 retries com back-off — `ClientOverrideConfiguration` aplicado tanto no `S3Client` quanto no `SqsClient`.
- **SQS com retry + DLQ:** `SqsEventPublisher` classifica falhas em transientes (retry exponencial) e permanentes (DLQ em banco); falha SQS não impede resposta ao cliente.
- **S3 com SSE-AES256:** criptografia em repouso habilitada por padrão via `PutObjectRequest.serverSideEncryption(AES256)`.
- **Pipeline OIDC:** deploy AWS sem credenciais estáticas — autenticação via `aws-actions/configure-aws-credentials` com OIDC/assume-role (remover `AWS_ACCESS_KEY_ID` e `AWS_SECRET_ACCESS_KEY` secrets do GitHub, usar `AWS_ACCOUNT_ID` e `AWS_ROLE_NAME`).
- **Auditoria de comunicação:** falhas de autenticação, assinaturas inválidas, timestamps vencidos e falhas SQS registradas em `audit_events`.

### 4.2 Lacunas e riscos

- **TLS gerenciado por infraestrutura:** o upload-service não termina TLS diretamente; depende do ALB/CloudFront configurado via Terraform. Não há configuração de redirecionamento HTTP→HTTPS na camada de aplicação.

### 4.3 Plano de implementação (comunicação segura)

#### \u2705 Implementado

1. **CORS parametrizado:** origens permitidas externalizado via `APP_SECURITY_CORS_ALLOWED_ORIGINS`; nenhuma mudan\u00e7a de c\u00f3digo necess\u00e1ria ao mudar dom\u00ednio do frontend.
2. **Mandatory shared-secret:** valida\u00e7\u00e3o em tempo de inicializa\u00e7\u00e3o previne deploy acidental sem segredo em produ\u00e7\u00e3o.
3. **OIDC pipeline:** CI/CD usa assume-role com OIDC, eliminando necessidade de armazenar credenciais est\u00e1ticas em GitHub.

#### Pendente

1. Documentar formalmente o contrato de TLS: ALB com certificado ACM + redirecionamento HTTP 80→HTTPS 443 como pré-requisito de deploy em produção.

Critérios de aceite:

- Endpoint sem token deve retornar `401`/`403` conforme regra. ✅ Implementado (quando `gateway-trust.enabled=true`).
- Falha transitória em AWS deve obedecer política de retry configurada. ✅ Implementado.
- Falha permanente em SQS não deve perder a mensagem. ✅ Implementado via DLQ.
- CORS deve ser configurável por ambiente sem alteração de código. ✅ Implementado.
- Shared-secret deve ser obrigatório em produção. ✅ Implementado com validação @PostConstruct.
- Pipeline deve usar OIDC, não credenciais estáticas. ✅ Implementado.

---

## 5) Principais riscos e limitações de segurança

## Matriz resumida

| Risco                                                        | Status atual     | Impacto | Observação                                                                                  |
| ------------------------------------------------------------ | ---------------- | ------- | ------------------------------------------------------------------------------------------- |
| API sem autenticação/autorização efetiva                     | ✅ Implementado  | Alto    | `GatewayTrustAuthenticationFilter` + HMAC-SHA256 + autorização por escopo                   |
| Bypass de content-type (magic bytes)                         | ✅ Implementado  | Alto    | `FileSignatureValidator` — PDF, PNG, JPEG                                                   |
| Path traversal / injeção via filename                        | ✅ Implementado  | Alto    | `FilenameValidator` — regex + 27 padrões bloqueados                                         |
| Ausência de rate limiting                                    | ✅ Implementado  | Médio   | In-memory por instância; limitação em deploy multi-réplica sem API Gateway                  |
| CORS permissivo                                              | ✅ Implementado  | Médio   | Origens parametrizáveis; não wildcard; configurável via `APP_SECURITY_CORS_ALLOWED_ORIGINS` |
| Falta de timeout/retry para integrações AWS                  | ✅ Implementado  | Médio   | SDK com 30s/10s/3 retries; SQS com retry exponencial + DLQ                                  |
| Perda de mensagem SQS em falha permanente                    | ✅ Implementado  | Alto    | DLQ em banco (`sqs_dead_letter_queue`); audit event emitido                                 |
| Sem trilha de auditoria                                      | ✅ Implementado  | Alto    | `audit_events` — autenticação, validação, upload, SQS, callback                             |
| Pipeline com credenciais AWS estáticas                       | ✅ Implementado  | Alto    | Migrado para OIDC/assume-role (requer GitHub OIDC trust + role IAM)                         |
| S3 sem criptografia em repouso                               | ✅ Implementado  | Médio   | SSE-AES256 em todo `PutObjectRequest`                                                       |
| Shared-secret não obrigatório em produção                    | ✅ Implementado  | Alto    | Validação `@PostConstruct` garante secret quando `gateway-trust.enabled=true`               |
| Sem endpoint para resultado IA                               | ✅ Implementado  | Alto    | `/v1/uploads/{uploadId}/analysis-callback` com idempotência (409 Conflict em replay)        |
| Contrato de integração Upload ↔ Processamento não versionado | ⏳ Pendente      | Alto    | Gap de integração entre serviços, não do endpoint de upload                                 |
| Mudança de status no upload-service                          | ✅ Não aplicável | Médio   | Por arquitetura, status pós-análise é responsabilidade do serviço de processamento          |
| Rate limiting distribuído (multi-réplica)                    | ⏳ Pendente      | Médio   | Mitigação: API Gateway WAF antes das réplicas                                               |
| TLS explícito na camada de aplicação                         | ⏳ Pendente      | Médio   | Responsabilidade do ALB/Terraform; não gerenciado pelo serviço                              |

### Limitações conhecidas

- **Boundary de segurança da IA:** o upload-service garante a integridade do arquivo recebido, sua publicação na fila e o processamento de resultado com idempotência. Não controla o motor externo de análise, apenas valida o schema do callback.
- **Escopo intencional do serviço:** o upload-service não deve executar lógica de decisão final nem promover/quarentenar resultado de IA; ele apenas entrega o evento com segurança para o próximo estágio.
- **Rate limiting local:** o `UploadRateLimitFilter` usa `ConcurrentHashMap` in-memory. Em topologias com múltiplas instâncias sem API Gateway como único ponto de entrada, o limite efetivo se multiplica pelo número de réplicas. A mitigação operacional é o throttling no API Gateway/ALB antes de chegar ao serviço.
- **TLS delegado à infraestrutura:** o upload-service não gerencia certificados TLS. Em produção, o ALB deve ser configurado com certificado ACM e redirecionamento HTTP→HTTPS. A ausência desse setup de infra não é detectável pelo código da aplicação.
- **Credenciais AWS em desenvolvimento:** o perfil `dev` usa `cloud.aws.accessKey` / `cloud.aws.secretKey` via variável de ambiente local. Em produção, o deploy usa IAM role via OIDC sem chaves estáticas.

---

## Backlog consolidado de implementação

### Concluído

- ✅ P0 — Autenticação/autorização: `GatewayTrustAuthenticationFilter` + HMAC-SHA256 + escopos por endpoint.
- ✅ P0 — CORS: origens explícitas, não wildcard.
- ✅ P0 — Magic bytes: `FileSignatureValidator` para PDF/PNG/JPEG.
- ✅ P0 — Rate limiting: janela fixa por IP/usuário com audit event e `Retry-After`.
- ✅ P1 — Timeout/retry AWS SDK: `ClientOverrideConfiguration` em S3 + SQS.
- ✅ P1 — Criptografia S3: SSE-AES256 em todo upload.
- ✅ P1 — Pipeline OIDC: deploy AWS sem credenciais estáticas.
- ✅ P2 — Auditoria: `audit_events` assíncrona cobrindo todo o ciclo de vida de upload.
- ✅ P2 — Sanitização de filename: `FilenameValidator` com regex + 27 padrões bloqueados.
- ✅ P2 — SQS retry + DLQ: retry exponencial para transientes; DLQ em banco para permanentes.

### Pendente

- ✅ Contrato versionado de callback endpoint para resultado IA (análise_callback com idempotência).
- ⏳ Contrato versionado de eventos Upload ↔ Processamento ↔ Relatórios (ownership compartilhado entre serviços).
- ⏳ Estratégia de auditoria distribuída para decisão final do processamento (correlation-id fim a fim).
- ⏳ Rate limiting distribuído (quando não houver API Gateway como único ponto de entrada).
- ✅ CORS configurável via propriedade de ambiente (`APP_SECURITY_CORS_ALLOWED_ORIGINS`).

---

## Checklist de validação

- ✅ Testes automatizados cobrindo uploads inválidos, tipo real incompatível e limite de taxa (`UploadIntegrationTest`).
- ✅ Testes de filename com path traversal, null bytes e injeção de comandos (`FilenameValidatorTest`).
- ✅ Testes de classificação de falha SQS (transiente vs permanente) e roteamento para DLQ (`SqsEventPublisherTest`).
- ✅ Testes de persistência de auditoria (`AuditEventTest`).
- ✅ Testes de autenticação/autorização (assinatura HMAC, timestamp, escopo insuficiente, X-Forwarded-For) — `GatewayTrustAuthenticationFilterTest` com 9 cenários de teste.
- ✅ Testes de idempotência de callback (409 Conflict em replay, validação de uploadId, transição de status) — `AnalysisCallbackServiceTest` com 15 cenários de teste.
- ⏳ Evidência de comunicação segura em produção (HTTPS, IAM mínimo necessário) — dependente de ambiente provisionado.
