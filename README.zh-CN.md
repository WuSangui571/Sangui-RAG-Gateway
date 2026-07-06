# Sangui-RAG-Gateway

> 轻量级 OpenAI 兼容 RAG 增强网关。
>
> 本项目支持 OpenAI Chat Completions API 的兼容子集。

让现有业务系统用较低改造成本、较低用户感知获得私有文档 RAG 能力。

## 当前状态

**V0.2 beta** - 已具备完整 RAG 流程和管理控制台。

### 已实现

- **后端**：Spring Boot 3.4、Java 21、Flyway + PostgreSQL/pgvector、Redis、MyBatis-Plus
- **前端**：React 18、TypeScript、Vite、Ant Design 管理控制台
- **网关**：`GET /v1/models`、`POST /v1/chat/completions`（非流式和流式）
- **管理功能**：登录、模型配置、知识库、文档上传/状态/重试/删除、应用、API Key、冒烟/测试对话、请求日志
- **RAG**：应用绑定知识库检索、提示词增强、请求日志安全证据、可选来源引用
- **限流**：面向公开聊天调用的 Redis 支持 API-key 请求/Token 配额
- **认证**：公开 `/v1/*` 使用应用 API Key（Bearer `sk-sangui-*`），管理端使用 JWT
- **密钥**：上游 API Key 静态加密存储（AES-256-GCM），应用 Key 哈希存储，完整 Key 仅展示一次
- **部署**：全栈 Docker Compose 一条命令启动

### 路线图（尚未实现）

- PDF / DOCX 解析
- 异步文档处理
- Rerank 和混合检索
- 面向生产的 MinIO / S3 兼容对象存储

## 快速开始

### 前置依赖

| 依赖 | 版本 |
|---|---|
| Docker | 24+ |
| Docker Compose | 2.x |

### 1. 准备环境

```bash
git clone https://github.com/WuSangui571/Sangui-RAG-Gateway.git
cd Sangui-RAG-Gateway
cp .env.example .env
```

### 2. 启动全部服务

```bash
docker compose --env-file .env -f deploy/docker-compose.yml up -d --build
```

该命令会启动 PostgreSQL/pgvector、Redis、后端（端口 8080）和前端（端口 3000）。首次启动时，Flyway 会自动执行数据库迁移。

默认情况下，PostgreSQL 和 Redis 只在 Compose 网络内部可访问。如果你需要从本机访问它们（例如使用数据库 GUI），请添加显式的 host-port 覆盖文件：

```bash
docker compose --env-file .env -f deploy/docker-compose.yml -f deploy/docker-compose.host-ports.yml up -d --build
```

### 3. 验证健康状态

```bash
curl http://localhost:8080/api/health
```

预期响应：

```json
{"code":"OK","message":"success","data":{"status":"UP","service":"sangui-rag-gateway"}}
```

### 4. 打开管理控制台

```
http://localhost:3000
```

默认开发账号：`admin` / `admin123`。

> 生产环境请替换 `.env` 中的所有密钥，尤其是 `RAG_ADMIN_AUTH_JWT_SECRET` 和 `RAG_GATEWAY_ENCRYPTION_SECRET_KEY`，并使用至少 32 个字符的强密钥。

## 首次管理端配置流程

部署完成后，通过管理控制台配置网关：

1. **创建模型配置**：进入 Model Configs，添加一个 OpenAI 兼容 provider。配置 `base_url`、`chat_model` 和 provider API key。
2. **创建知识库**：设置 embedding 模型名称和维度（例如 `text-embedding-v4` / 1024）。上传 `.txt` 或 `.md` 文件，并等待状态变为 `READY`。
3. **创建应用**：填写应用名称，然后在应用详情页绑定模型配置和知识库。
4. **创建 API Key**：在应用下生成 Key。**立即复制完整 Key** - 它不会再次显示。
5. **调用网关**：

   ```bash
   curl -s http://localhost:8080/v1/chat/completions \
     -H "Content-Type: application/json" \
     -H "Authorization: Bearer $SANGUI_APP_API_KEY" \
     -d '{"model":"ignored","messages":[{"role":"user","content":"Summarize the uploaded document."}]}'
   ```

6. **验证**：在该应用的 Request Logs 页面检查状态、延迟、Token 用量和命中的 chunk ID。

## 网关 API

### 支持的端点

| 端点 | 方法 | 说明 |
|---|---|---|
| `/v1/models` | `GET` | 列出认证应用可用的模型 |
| `/v1/chat/completions` | `POST` | RAG 增强聊天（非流式和流式） |

### 支持的请求字段

`model`、`messages`、`temperature`、`max_tokens`、`top_p`、`stream`

### 来源引用

默认情况下，非流式聊天响应不包含 `sangui_citations`。如需返回有界的引用元数据，请发送：

```http
X-Sangui-Return-Citations: true
```

流式响应不会发送 citation SSE 事件；请求日志仍会保留安全的检索证据。

### 不支持的能力

以下 OpenAI API 和功能**不支持**：

`/v1/responses`、`/v1/embeddings`、`/v1/images`、tools、function calling、vision、audio、`response_format`

### 认证

公开网关（`/v1/*`）：

```http
Authorization: Bearer <app-api-key>
```

生成的应用 Key 使用 `sk-sangui-` 前缀，并且只展示一次。

管理 API（`/api/admin/*`）：

```http
Authorization: Bearer <admin-jwt>
```

两个认证域相互独立 - 管理员 JWT 不能访问公开网关端点，应用 API Key 也不能访问管理 API。

### 集成方式

将现有系统的 LLM `base_url` 替换为 `http://<gateway-host>:8080`，并使用应用 API Key。聊天请求会自动附加知识库上下文。

## 文档支持

V0.2 beta 支持文本类文档：**txt、md、markdown**。

PDF 和 DOCX 解析仍是路线图项目。复杂 PDF、Excel 文件、表格问答和结构化抽取尚不支持。

最大文件大小：1 MB（可通过 `RAG_DOCUMENT_MAX_FILE_SIZE_BYTES` 配置）。

## 安全

- 完整应用 API Key **只展示一次**；以哈希形式存储，不可恢复。
- 上游 provider Key 使用 AES-256-GCM 静态加密存储。
- 管理 API 使用 JWT；公开 `/v1/*` 使用应用 API Key - 两者是独立认证域。
- 请求日志只包含安全元数据（状态、延迟、Token 计数、命中的 chunk ID、问题摘要）。完整 prompt、原始回答、chunk 内容、provider 响应体、堆栈跟踪、API Key 和存储路径绝不会被记录或返回。
- 所有检索和管理操作都按 `user_id` 和 `app_id` 做租户隔离。

Key 轮换、吊销和泄露恢复请参见 [docs/key-management-runbook.md](docs/key-management-runbook.md)。

## 项目结构

```text
backend/                          # Spring Boot 3.4 后端
  src/main/java/com/sangui/raggateway/
    common/                       # 配置、异常、响应、安全、工具
    app/                          # 应用管理
    apikey/                       # API Key 管理
    model/                        # 模型配置管理
    knowledge/                    # 知识库管理
    document/                     # 文档上传和处理
    embedding/                    # Embedding 客户端
    retrieval/                    # 向量检索
    rag/                          # RAG prompt 和流水线
    gateway/                      # OpenAI 兼容公开 API
    log/                          # 请求日志持久化和查询
  Dockerfile                      # 多阶段 Maven + Java 21 镜像
frontend/                         # React 18 + TypeScript + Vite + Ant Design
  src/
    api/                          # HTTP 客户端
    pages/                        # 管理控制台页面
    components/                   # 共享 UI 组件
    types/                        # TypeScript 类型定义
  Dockerfile                      # 多阶段 Node + Nginx 镜像
deploy/                           # Docker Compose 和基础设施配置
scripts/                          # 自动化脚本
docs/                             # 扩展文档
.trellis/                         # AI 辅助开发工作流
```

## 开发

### 仅启动基础设施

```bash
docker compose --env-file .env -f deploy/docker-compose.yml -f deploy/docker-compose.host-ports.yml up -d postgres redis
```

### 后端

```bash
cd backend
mvn spring-boot:run
```

### 前端

```bash
cd frontend
npm ci
npm run dev
```

Vite 开发服务器会将 `/api` 和 `/v1` 代理到 `http://localhost:8080`。

### 运行测试

**后端：**

```bash
cd backend
mvn test                              # 完整测试套件
mvn -q -DskipTests compile            # 仅编译检查
```

**前端：**

```bash
cd frontend
cmd /c npm run lint                   # ESLint
cmd /c npm run test                   # Vitest
cmd /c npm run typecheck              # TypeScript 检查
cmd /c npm run build                  # 生产构建
```

**Diff 检查：**

```bash
git diff --check
```

**Compose 配置检查：**

```bash
docker compose --env-file .env.example -f deploy/docker-compose.yml config
```

## 环境变量

`.env.example` 中的关键变量：

| 变量 | 默认值 | 说明 |
|---|---|---|
| `POSTGRES_DB` | `sangui_rag_gateway` | 数据库名称 |
| `POSTGRES_USER` | `sangui` | 数据库用户 |
| `POSTGRES_PASSWORD` | `sangui_password` | 数据库密码（生产环境需覆盖） |
| `BACKEND_PORT` | `8080` | 后端宿主机端口 |
| `FRONTEND_PORT` | `3000` | 前端宿主机端口 |
| `RAG_ADMIN_AUTH_JWT_SECRET` | dev placeholder | 管理端 JWT HS256 签名密钥（至少 32 字符） |
| `RAG_GATEWAY_ENCRYPTION_SECRET_KEY` | dev placeholder | 上游 provider Key 的 AES-256-GCM 密钥（至少 32 字符） |
| `FILE_STORAGE_TYPE` | `local` | 存储后端：`local` 或 `object` |
| `RAG_DOCUMENT_CHUNK_SIZE` | `800` | 文本分块大小 |
| `RAG_DOCUMENT_CHUNK_OVERLAP` | `100` | 分块重叠长度 |
| `RAG_RETRIEVAL_DEFAULT_TOP_K` | `5` | 默认检索 top-K |

`.env.example` 只包含安全的开发默认值。生产环境请通过环境变量或部署 `.env` 文件覆盖密钥。生产 profile（`prod`/`production`）会拒绝使用开发默认值启动。

## 截图

当前仓库未提交截图资源。建议未来插入位置：

- **管理控制台概览**：放在 "What it does now" 或配置流程之后，展示包含模型配置、知识库、应用、API Key、请求日志的侧边栏。
- **模型配置 / 应用详情**：展示应用、模型配置和知识库之间的绑定关系。
- **请求日志**：展示只包含安全元数据字段的列表/详情视图（不包含 prompt、回答、Key 或 chunk 内容）。

任何截图都必须打码 API Key、上游 Key、prompt、回答、chunk 内容、provider 响应体、堆栈跟踪和存储路径。

## 延伸阅读

- [Key Management Runbook](docs/key-management-runbook.md) - API Key 轮换、吊销和泄露恢复
- [Gateway Error Codes](docs/gateway-error-codes.md) - 公开 `/v1/*` 错误响应参考
- [Runtime Evidence Checklist](docs/runtime-evidence-checklist.md) - 演示验收证据模板
- [Admin API Reference](docs/admin-api-reference.md) - 完整管理端点参考
- [CI Workflow](.github/workflows/ci.yml) - CI 流水线定义
- 冒烟脚本：`scripts/demo-smoke.ps1` - 自动化验收验证

## 许可证

MIT
