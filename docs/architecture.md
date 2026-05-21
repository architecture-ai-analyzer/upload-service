# Diagrama de Arquitetura - Upload Service

## Visão Geral

```mermaid
graph TD
    User["👤 Usuário"]
    Frontend["🖥️ Frontend\nReact + Vite\n:5173"]
    Controller["UploadController\nProjectController"]
    ExHandler["ApiExceptionHandler"]
    UseCase["SingleUploadUseCase\nCreateUploadUseCase"]
    S3Wrapper["S3ClientWrapper"]
    SqsWrapper["SqsClientWrapper"]
    UploadRepo["UploadRepository"]
    ProjectRepo["ProjectRepository"]
    S3["☁️ AWS S3\n(arquivos)"]
    SQS["☁️ AWS SQS\nupload-queue"]
    DB["🗄️ PostgreSQL\n(metadados)"]
    ExtService["⚙️ Serviço Externo\n(análise/processamento)"]

    User -->|HTTP| Frontend
    Frontend -->|REST /v1/uploads\n/v1/projects| Controller
    Controller -->|valida| ExHandler
    Controller -->|orquestra| UseCase
    UseCase --> S3Wrapper
    UseCase --> SqsWrapper
    UseCase --> UploadRepo
    UseCase --> ProjectRepo
    S3Wrapper -->|upload arquivo| S3
    SqsWrapper -->|publica evento JSON| SQS
    UploadRepo -->|persiste metadados| DB
    ProjectRepo -->|persiste projeto| DB
    SQS -->|consome evento| ExtService
    ExtService -->|atualiza status| DB
```

---

## Camadas da Aplicação

```
upload-service/
├── adapter/
│   ├── controller/         # Entrada HTTP (REST)
│   │   ├── UploadController       POST /v1/uploads, GET /v1/uploads/{id}
│   │   ├── ProjectController      POST /v1/projects
│   │   └── ApiExceptionHandler    Tratamento centralizado de erros
│   ├── dto/                # Request/Response objects
│   └── persistence/        # Interfaces JPA
│       ├── UploadRepository
│       └── ProjectRepository
│
├── domain/                 # Entidades de negócio
│   ├── Upload.java
│   ├── Project.java
│   └── UploadStatus.java   (RECEBIDO, EM_PROCESSAMENTO, ANALISADO, ERRO)
│
├── usecase/                # Casos de uso (regras de negócio)
│   ├── SingleUploadUseCase   valida → S3 → persiste → SQS
│   └── CreateUploadUseCase
│
└── infra/
    └── aws/                # Integrações externas
        ├── S3ClientWrapper
        └── SqsClientWrapper
```

---

## Diagrama de Sequência - Upload de Arquivo

```mermaid
sequenceDiagram
    actor User as Usuário
    participant FE as Frontend
    participant UC as UploadController
    participant SUC as SingleUploadUseCase
    participant S3 as S3ClientWrapper
    participant Repo as UploadRepository
    participant SQS as SqsClientWrapper
    participant Ext as Serviço Externo

    User->>FE: seleciona arquivo PDF/PNG
    FE->>UC: POST /v1/uploads (multipart)
    UC->>UC: valida content-type, tamanho, metadata
    alt inválido
        UC-->>FE: 400 Bad Request
    end
    UC->>SUC: execute(file, metadata)
    SUC->>S3: upload(file, s3Key)
    S3-->>SUC: ok
    SUC->>Repo: save(Upload{status=COMPLETED})
    Repo-->>SUC: Upload salvo
    SUC->>SQS: publish(event JSON)
    SQS-->>SUC: ok
    SUC-->>UC: UploadResponse
    UC-->>FE: 201 Created + Location header
    FE->>FE: redireciona para /status/{uploadId}
    loop polling cada 5s
        FE->>UC: GET /v1/uploads/{uploadId}
        UC-->>FE: {status}
    end
    SQS->>Ext: consome evento
    Ext->>Repo: atualiza status (SCANNED_OK / QUARANTINED)
    FE->>FE: exibe relatório
```

---

## Infraestrutura

```mermaid
graph LR
    subgraph Local["Desenvolvimento Local"]
        App["Spring Boot\n:8080"]
        LS["LocalStack\n:4566\n(S3 + SQS)"]
        H2["H2 In-Memory\n(testes)"]
        App <--> LS
        App <--> H2
    end

    subgraph AWS["AWS (Produção)"]
        ECS["ECS Fargate\nupload-service"]
        RDS["RDS PostgreSQL"]
        S3P["S3 Bucket"]
        SQSP["SQS Queue"]
        ECS --> RDS
        ECS --> S3P
        ECS --> SQSP
    end

    subgraph CICD["CI/CD (GitHub Actions)"]
        GHA["Build → Test → Quality\n→ Docker Push → ECS Deploy"]
        DH["Docker Hub\nupload-service:branch"]
        GHA --> DH
        DH --> ECS
    end
```

---

## Estados do Upload

```mermaid
stateDiagram-v2
    [*] --> PENDING : arquivo recebido
    PENDING --> COMPLETED : S3 + BD ok
    PENDING --> ERRO : validação falhou
    COMPLETED --> SCANNED_OK : serviço externo processou
    COMPLETED --> QUARANTINED : risco detectado
    SCANNED_OK --> [*]
    QUARANTINED --> [*]
    ERRO --> [*]
```
