# 01 K1 Embedding API 客户端（OpenAI 兼容 IEmbeddingModel 实现）

> Plan Status: completed
> Last Reviewed: 2026-09-28
> Source: `ai-dev/backlog/knowledge-rag-roadmap.md` K1；用户 2026-09-28 指示（knowledge-rag 前置工作项纳入 nop-code feature-completion 执行队列）
> Related: `ai-dev/backlog/nop-code-feature-completion-roadmap.md` N4.2（下游消费者，另立 plan）

## Purpose

落地 knowledge-rag roadmap K1：在 nop-entropy 平台内实现 OpenAI 兼容的 embedding 客户端（实现 `IEmbeddingModel` SPI），复用平台既有的 llm.xml 配置面、限流、重试、账号链 failover 范式。本计划同时是 nop-code feature-completion roadmap N4.2（`ITextEmbedding` 生产实现）的模型后端前置——K1 不就绪则 N4.2 无法启动。

## Current Baseline

- `IEmbeddingModel` SPI 已存在（nop-ai-core `io.nop.ai.core.api.embedding`）：`embedAsync`/`embedAllAsync(AiDocument, EmbeddingOptions) → CompletionStage<VectorData>`，含 sync default 方法。全仓 main 零生产实现；唯一消费者 `EmbeddingModelBasedClassifier` 经构造注入。接口 javadoc 声明"平台无生产实现属设计意图"——本计划按 roadmap K1 裁定落地首个生产实现，javadoc 需同步修订。
- `EmbeddingOptions` 现有字段仅 `model`、`tenantId`（P2 round-4 裁定的 reserved 契约族；additive 扩展不违反"删除需单独 plan"约束）。
- `VectorData`（`double[] vector` + `Metadata`）已存在。
- `llm.xdef`（`nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/ai/llm.xdef`）已声明 `<embedUrl>` 字段；`_LlmModel.getEmbedUrl()` 已生成；全仓无任何代码消费它。
- `ChatServiceImpl`（nop-ai-core `service/`）展示了完整可复用范式：`LlmConfigHelper.loadConfig(provider)`、`IRateLimiter.tryAcquire(1, timeout)` 超时抛 `ERR_AI_RATE_LIMITED`、`IHttpClient.fetchAsync`、apiKey 优先级链 `accountKey > credentialResolver > resolveApiKey`、baseUrl 解析链 `accountBaseUrl > config 变量 > config.getBaseUrl()`。
- `ChatServiceImpl` 自身不含重试循环（重试位于 ReAct 层/gateway failover adapter）；K1 交付词明确含 failover/限流/重试，故 embedding 客户端需自带**有界重试 + 账号链回退**。
- reliability 包（nop-ai-core `reliability/`）已有可复用件：`AccountChain`（有序备用账号游走器，`LlmConfigHelper.resolveAccountChain` 供给）、`StandardRetryPolicy`（TRANSIENT/RATE_LIMITED→RETRY full-jitter+Retry-After floor；QUOTA_EXCEEDED/AUTH_INVALID→FALLBACK；NON_TRANSIENT→STOP；无状态可并发共享）、`NoRetryPolicy`、`LlmErrorClassifier`、`RetryContext`/`RetryOutcome`。
- bean 注册点：`nop-ai-core/src/main/resources/_vfs/nop/ai/beans/ai-defaults.beans.xml`（`nopChatService` 即 `ioc:default="true"` + `ioc:type` 接口绑定范式）。
- K1 owner doc `ai-dev/design/nop-ai/embedding.md` 不存在（roadmap 标注 NEW）。
- `ai-dev/design/nop-ai/04-rag-module-position.md` 裁定 `nop-ai-rag` 是 `IVectorStore`/`IEmbeddingModel` 实现的预期落点，触发条件"出现第一个真实消费方"；roadmap K1 落点列明 `nop-ai-core`（与 `ChatServiceImpl` 同构先例一致）。两文档的张力由本计划 Phase 3 的 design 增注收敛裁定。

## Goals

- G1：`EmbeddingServiceImpl implements IEmbeddingModel`（nop-ai-core），OpenAI 兼容 `/embeddings` 协议：请求体 `{"model": ..., "input": [...]}`，响应解析 `data[].embedding`（按 `index` 排序还原输入顺序）→ `VectorData`。
- G2：provider 路由：`EmbeddingOptions` 新增可选 `provider` 字段；缺省回退新配置项 `nop.ai.embedding.default-llm`；两者皆缺省时 fail-loud。
- G3：可靠性面接入平台范式——限流（`LlmModel.rateLimit`，`tryAcquire` 超时抛 `ERR_AI_RATE_LIMITED`，同 chat 语义）、重试（默认 `StandardRetryPolicy`，TRANSIENT/RATE_LIMITED 退避重试，有界）、账号链 failover（QUOTA_EXCEEDED/AUTH_INVALID → `AccountChain.next()` 的 apiKey/baseUrl 覆盖，链耗尽 fail-loud，FALLBACK 步数有界）。
- G4：owner doc `ai-dev/design/nop-ai/embedding.md` 新建（含落点裁定、协议契约、扩展点）+ `IEmbeddingModel` javadoc 修订 + `docs-for-ai/03-modules/nop-ai.md`（或最小所属文档）同步。
- G5：focused 单测覆盖成功/失败/failover 路径（stub `IHttpClient`，参照 `TestChatServiceImplCallPathContract`/`TestChatServiceImplAccountRequest` 既有测试范式）。

## Non-Goals

- 流式 embedding（协议本身无流式）、token 计量记账、成本预算面板。
- Gemini/Ollama/Anthropic 原生 embedding 方言——v1 唯一方言为 OpenAI 兼容；方言扩展点在 owner doc 中文档化（后续按需增 `IEmbeddingDialect` 或复用 apiStyle 路由，不在本计划内实现）。
- `IVectorStore` 实现、pgvector/Milvus 驱动（K2 承接）。
- RAG 摄取-检索管线（K3 承接）。
- per-tenant 分布式限流（与 chat 同裁定：文档化扩展点）。
- GraphQL/BizModel 暴露（K3 承载）。

## Scope

### In Scope

- nop-ai-core：`EmbeddingOptions` 加 `provider` 字段；`AiCoreConfigs` 加 `CFG_AI_EMBEDDING_DEFAULT_LLM`；新 `service/EmbeddingServiceImpl`；`NopAiCoreErrors` 增 embedding 错误码；`ai-defaults.beans.xml` 注册 bean。
- nop-ai-core 测试：`src/test/java/io/nop/ai/core/service/` 新增 embedding 单测。
- 文档：`ai-dev/design/nop-ai/embedding.md`（NEW）、`IEmbeddingModel` javadoc、`docs-for-ai` 最小所属文档。

### Out Of Scope

- nop-search 侧 `ITextEmbedding` 适配（N4.2 另立 plan）。
- nop-ai-rag 模块内任何实现（本计划不改该模块）。
- credentialResolver 之外的新凭据机制（复用 `IAiModelCredentialResolver` 可选注入范式）。

## Execution Plan

### Phase 1 - SPI 配置面扩展

Status: completed
Targets: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/api/embedding/EmbeddingOptions.java`、`nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/AiCoreConfigs.java`

- Item Types: `Fix`

- [x] `EmbeddingOptions` 增加可选 `provider` 字段（null = 回退默认配置），javadoc 写明路由优先级 `options.provider > nop.ai.embedding.default-llm > fail-loud`
- [x] `AiCoreConfigs` 增加 `CFG_AI_EMBEDDING_DEFAULT_LLM`（`nop.ai.embedding.default-llm`，String，默认 null）

Exit Criteria:

- [x] 两字段编译可见，javadoc 完整（`./mvnw compile -pl nop-ai/nop-ai-core -am -T 1C` 通过）
- [x] No owner-doc update required（owner doc 于 Phase 3 统一新建，覆盖本 Phase 语义）
- [x] `ai-dev/logs/2026/09-28.md` 对应条目已更新

### Phase 2 - EmbeddingServiceImpl 实现

Status: completed
Targets: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/service/EmbeddingServiceImpl.java`（新）、`io/nop/ai/core/NopAiCoreErrors.java`、`nop-ai-core/src/test/java/io/nop/ai/core/service/`（新测试）、`nop-ai-core/src/test/resources/_vfs/nop/ai/llm/`（新测试 fixture llm.xml）

- Item Types: `Fix`

- [x] `embedAsync(doc, options)`：解析 provider → `LlmConfigHelper.loadConfig` → 校验 `embedUrl`（缺失抛 `ERR_AI_EMBEDDING_NO_EMBED_URL`，fail-loud）→ 校验 `apiStyle` 为 openai（非 openai 抛 `ERR_AI_EMBEDDING_UNSUPPORTED_API_STYLE`，fail-loud，与 Non-Goals v1 唯一方言一致）→ 限流检查（复用 chat `checkRateLimit` 语义：`tryAcquire` 超时抛 `ERR_AI_RATE_LIMITED`）→ POST `{baseUrl}{embedUrl}`，OpenAI 兼容 body `{"model": <model>, "input": [<text>]}`，headers 经 `LlmDialectFactory.getDialect(config.getApiStyle()).setHeaders(...)`（apiKey bearer + `apiKeyHeader` 覆盖）
- [x] 响应解析：`data[].embedding` → `VectorData`（按 `index` 字段排序；缺失 index 时按数组序），metadata 记录 `model`/usage tokens；`data` 缺失或空 → 错误码 fail-loud
- [x] `embedAllAsync`：单请求批量 `input: [texts...]`，逐条映射返回（顺序保真）；空列表直接返回空（不经网络）
- [x] **错误分类双通道裁定（与 chat `LlmCallCoordinator` 同构）**：响应级非 200 错误经 `ILlmDialect.parseErrorResponse(body, httpStatus, headers, config)` 分类——该通道应用 llm.xml `<errorMappings>` 配置面，`QUOTA_EXCEEDED`/`AUTH_INVALID` 由配置驱动到达（与 chat 完全同构，failover 分支的可达性由配置面保证）；传输级异常（无 HTTP 响应）经 `LlmErrorClassifier.classify(Throwable)` 分类。两条通道产出的 `ErrorClassification` 统一驱动重试/failover 决策
- [x] 重试循环：持有 `IRetryPolicy` 字段，**默认 `StandardRetryPolicy`，普通 setter 可覆盖为 `NoRetryPolicy`（不经 IoC 注入——`ai-defaults.beans.xml` 无 `IRetryPolicy` bean，避免按类型注入失败）**；TRANSIENT/RATE_LIMITED 按退避重试；重试间尊重 `Retry-After` floor；总尝试次数有界。**执行语义裁定**：重试延迟经可注入的单线程 `ScheduledExecutorService` 调度（`CompletableFuture` 延迟链），不在 IO 完成线程内同步阻塞；executor 字段可注入，测试传直接执行 executor
- [x] 账号链 failover：QUOTA_EXCEEDED/AUTH_INVALID → `LlmConfigHelper.resolveAccountChain` + `AccountChain.next()`，用账号 `apiKey`/`baseUrl` 覆盖重建请求；链耗尽 fail-loud（异常上抛，不静默降级）；FALLBACK 总步数有界
- [x] `NopAiCoreErrors` 新增错误码：`ERR_AI_EMBEDDING_NO_EMBED_URL`、`ERR_AI_EMBEDDING_NO_PROVIDER`、`ERR_AI_EMBEDDING_EMPTY_RESPONSE`、`ERR_AI_EMBEDDING_ACCOUNT_CHAIN_EXHAUSTED`、`ERR_AI_EMBEDDING_UNSUPPORTED_API_STYLE`（内联 `ErrorCode.define` 模式，与既有 45 个错误码同构，无外部资源文件需同步；英文 message）
- [x] baseUrl 解析复用 chat 优先级链（config 变量 `nop.ai.llm.{provider}.base-url` → `config.getBaseUrl()` → 抛 `ERR_AI_SERVICE_NO_BASE_URL`）
- [x] 测试 fixture：`src/test/resources/_vfs/nop/ai/llm/` 新增带 `<embedUrl>` 的测试 provider llm.xml（含 `<errorMappings>` 401→AUTH_INVALID 映射 + `<accounts>` 备用账号，参照既有 `test-accounts.llm.xml`）
- [x] 单测（stub `IHttpClient`，同包既有测试 `CapturingHttpClient`/`EmptyFlowHttpClient` 范式）：
  - 成功单条 / 批量（含 `index` 乱序还原顺序）
  - `embedUrl` 缺失 fail-loud / provider 未配置 fail-loud / apiStyle 非 openai fail-loud
  - 限流超时抛 `ERR_AI_RATE_LIMITED`
  - 503 → 重试后成功（断言尝试次数 = 2）
  - 401 → 经 `<errorMappings>` 判 AUTH_INVALID → 账号链切换后成功（断言第二请求携带备用 apiKey/baseUrl）
  - 账号链耗尽 → `ERR_AI_EMBEDDING_ACCOUNT_CHAIN_EXHAUSTED`
  - NON_TRANSIENT（如 400 且无匹配映射）→ 立即 STOP 不重试

Exit Criteria:

- [x] 上述单测全绿（`./mvnw test -pl nop-ai/nop-ai-core -am -T 1C`）
- [x] **无静默跳过**：`embedUrl`/`baseUrl`/provider 缺失、响应体异常、链耗尽均为显式 `NopException`，无空方法体/吞异常/返回 null placeholder
- [x] **接线验证**：单测断言 stub `IHttpClient` 收到的请求 URL（baseUrl+embedUrl 拼接）、method=POST、body（model/input 字段）、Authorization 头——证明 `EmbeddingServiceImpl` → `IHttpClient` 运行时调用链连通
- [x] `EmbeddingOptions.provider` 字段被实现真实消费（非死字段）
- [x] No owner-doc update required（owner doc 于 Phase 3 统一新建，覆盖本 Phase 的协议与可靠性语义）
- [x] `ai-dev/logs/2026/09-28.md` 对应条目已更新

### Phase 3 - 装配、owner doc 与契约修订

Status: completed
Targets: `nop-ai-core/src/main/resources/_vfs/nop/ai/beans/ai-defaults.beans.xml`、`ai-dev/design/nop-ai/embedding.md`（NEW）、`ai-dev/design/nop-ai/04-rag-module-position.md`（增注）、`IEmbeddingModel` javadoc、`docs-for-ai/03-modules/nop-ai.md`

- Item Types: `Fix`、`Decision`

- [x] `ai-defaults.beans.xml` 以 `ioc:default="true"` + `ioc:type="io.nop.ai.core.api.embedding.IEmbeddingModel"` 注册 `EmbeddingServiceImpl` bean（镜像同文件 `nopChatService` 既有范式；未配置 default-llm 时 bean 可创建、首次调用 fail-loud，保持消费方 wiring 时 fail-fast 语义可观察）
- [x] bean 装配冒烟测试：beans 加载后 `IEmbeddingModel` 可按类型解析为 `EmbeddingServiceImpl`（IoC 容器测试或 beans 加载测试，与 bean 注册同 Phase 落地）
- [x] `IEmbeddingModel` javadoc 修订："平台无生产实现"表述更新为指向 `EmbeddingServiceImpl`（K1 已落地）+ 集成方仍可替换
- [x] 新建 `ai-dev/design/nop-ai/embedding.md`：落点裁定（nop-ai-core，`ChatServiceImpl` 同构；对 `04-rag-module-position.md` 触发条件的承接说明）、协议契约（OpenAI 兼容 v1）、可靠性语义（限流/重试/failover 各自的配置面与默认值）、方言扩展点、与 K2/K3/N4.2 的边界
- [x] `04-rag-module-position.md` 增注：K1 触发"第一个真实消费方"条件，embedding 客户端按 roadmap K1 落点 nop-ai-core；`nop-ai-rag` 仍是 `IVectorStore`（K2）与 RAG 管线（K3）实现落点
- [x] `docs-for-ai/03-modules/nop-ai.md`（或最小所属文档）补 embedding 客户端使用说明
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

Exit Criteria:

- [x] bean 注册后 `./mvnw test -pl nop-ai/nop-ai-core -am -T 1C` 全绿（含 beans 加载测试）
- [x] owner doc 为最终态设计文档（无 Proposed vs Current 对比叙事）
- [x] doc link checker 0 errors
- [x] `ai-dev/logs/2026/09-28.md` 对应条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] K1 交付词逐项核对：OpenAI 兼容客户端、`IEmbeddingModel` 实现、failover/限流/重试三面均落地且有测试
- [x] 全仓 `IEmbeddingModel` 生产实现从 0 → 1（`EmbeddingServiceImpl`），`EmbeddingModelBasedClassifier` 既有消费路径不受影响（编译+既有测试绿）
- [x] 无被静默降级到 deferred/follow-up 的 in-scope live defect 或 contract drift
- [x] 受影响 owner docs 已同步（embedding.md 新建、04-rag-module-position.md 增注、javadoc、docs-for-ai）
- [x] 独立子 agent closure-audit 已完成并记录证据（含 Anti-Hollow 检查：调用链连通 + 无空壳）
- [x] `./mvnw compile -pl nop-ai/nop-ai-core -am -T 1C` 通过
- [x] `./mvnw test -pl nop-ai/nop-ai-core -am -T 1C` 全绿
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/knowledge-rag/01-k1-embedding-api-client.md --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai/nop-ai-core --severity high`：**K1 新增代码零 high/critical finding**。工具整体退出码 1，唯一命中 `NoOpProviderFailoverQueue.java:34`（P6b）为 2026-08-15 提交 f6dcf2239c 引入的 pre-existing 文件，系 plan-2026-08-01-1905-3 §13.4 显式裁定的 pass-through 默认实现（javadoc 注明合规依据）——非 K1 in-scope 项，予以豁免记录（closure audit 2026-09-28 复核确认）

## Deferred But Adjudicated

（无——本计划无延期项）

## Non-Blocking Follow-ups

- 方言扩展（Gemini/Ollama 原生 embedding 格式）——当前唯一消费方（N4.2 代码索引、K3 RAG）均可经 OpenAI 兼容端点服务；出现真实需求时另立 plan。Classification: `out-of-scope improvement`。

## Closure

Status Note: K1 全部交付面（OpenAI 兼容客户端 / IEmbeddingModel 生产实现 / 限流 / 重试 / 账号链 failover）落地且有 focused 测试；独立 closure audit（agent_17240b96，2026-09-28）初裁 REJECT 提出 4 项文本/流程修复（hollow gate 勾选失实、usage/dimension metadata 声明未实现、docs-for-ai 旧 reserved 表述未同步、产物未提交），全部已修订：metadata 已补实现 + 测试断言（usage map + dimension，12/12 绿），hollow gate 改为如实记录 pre-existing finding + 豁免依据，docs-for-ai nop-ai-rag 行已同步，产物随本提交入库。功能代码零改动需求（audit 原话：无需改动任何行为代码）。
Completed: 2026-09-28

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent agent_17240b96-368b-49d1-9267-9958755fc06f（fresh session，与本会话实现者无关）
- Evidence:
  - Phase 1 全部 Exit Criteria PASS（EmbeddingOptions.provider + CFG_AI_EMBEDDING_DEFAULT_LLM live 编译可见，javadoc 完整）
  - Phase 2 全部 Exit Criteria PASS（12/12 单测审计者本机复跑绿；7 条 fail-loud 路径逐一核对显式 NopException；wire-level 接线断言 URL/POST/body/bearer；provider 字段真实消费）
  - Phase 3 全部 Exit Criteria PASS（bean 冒烟经真实 IoC 容器双断言；javadoc/owner doc/增注/docs-for-ai 四处齐备；check-doc-links exit 0）
  - Closure Gates：除 hollow-scan 项初裁 FAIL（勾选失实）外全 PASS；已按 audit 前置条件 1 修订为如实记录
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/knowledge-rag/01-k1-embedding-api-client.md --strict` 退出码 0（audit 复跑确认）
  - Anti-Hollow 检查：端到端调用链（embedAllAsync → 配置校验 → 限流 → buildRequest → IHttpClient → parseSuccess → VectorData）有测试断言覆盖；实现无空方法体/TODO/no-op；K1 新增代码零 hollow finding
  - Deferred 项分类检查：Non-Blocking Follow-ups 仅方言扩展（out-of-scope improvement），无 in-scope live defect 被降级（audit 确认属实）

Follow-up:

- Embedding 方言扩展（Gemini/Ollama 原生 embedding 格式）：出现真实需求时另立 plan（out-of-scope improvement，Non-Goals 已登记）。no remaining plan-owned work。
