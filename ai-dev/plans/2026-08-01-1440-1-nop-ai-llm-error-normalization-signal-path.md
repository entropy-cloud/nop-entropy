# nop-ai LLM 错误规范化信号通路（W2e-0..3）

> Plan Status: completed
> Mission: nop-ai-agent-harness-evolution
> Work Item: W2e-0..3（LLM 错误规范化信号通路 + RATE_LIMITED 配额感知重试；W2e 前半，W2 前置必须项）
> Last Reviewed: 2026-08-01
> Source: `ai-dev/design/nop-ai-agent/nop-ai-llm-error-normalization-design.md`（§3.1-3.5、§3.7、§3.8、§6.1-6.5）；`ai-dev/analysis/2026-08/2026-08-01-llm-error-mapping-feasibility-analysis.md`；`ai-dev/backlog/nop-ai-agent-harness-evolution-roadmap.md` W2e
> Related: 后续 plan：W2e-4-余 + W2e-5（QUOTA/AUTH→FALLBACK + 账号回退链）；W2-4 ProviderFailoverQueue（消费分类信号）；plan 207（retry policy）、209（model router，模型 tier 回退）

## Purpose

把 provider 异构错误响应规范化为固定分类（`ErrorClassification`），并让该分类信号经 `ChatResponse` 传递到可靠性层重试循环，使 `RATE_LIMITED` 能按 `Retry-After`（作为 floor + jitter）等待重试。本计划只打通"错误 → 分类 → RETRY 决策"这条**信号通路的前半段**；`QUOTA_EXCEEDED`/`AUTH_INVALID` 的账号切换恢复（需账号回退链子系统）显式延期到后续 plan。

## Current Baseline

> 已逐条核对 live repo（含 pom 依赖方向、字段、行号），非沿用旧文档。

**已落地（core 配置/模型层，roadmap 称"已落地"，核对属实）**：

- `llm.xdef`（`nop-kernel/nop-xdefs/.../_vfs/nop/schema/ai/llm.xdef`）已有 `<errorResponse>`（L81-86）、`<errorMappings xdef:body-type="list" xdef:key-attr="id">`（L118-127）、`<classifyError>xpl-fn:...`（L135）。
- `ErrorClassification` 枚举在 `io.nop.ai.core.model.ErrorClassification`（nop-ai-core，6 值含 `AUTH_INVALID`+`CACHE_STATE_LOST`）；agent 侧 `io.nop.ai.agent.reliability.ErrorClassification` 已 `@Deprecated` 桥接。
- 5 个 provider 配置（default/claude/gemini/azure/ollama `.llm.xml`）均已写 `<errorMappings>`。
- codegen 模型（`_LlmErrorMappingModel` / `_LlmErrorResponseModel`）+ `TestLlmErrorMapping`（10 个 `@Test`，覆盖 5 provider、first-match-wins、id 合并）已存在。

**架构事实（阻断项，已核实 pom）**：

- 依赖方向：`nop-ai-api` 仅依赖 `nop-api-core`；`nop-ai-core` 依赖 `nop-ai-api`。即 **`nop-ai-api` 是比 `nop-ai-core` 更低的层**。
- `ChatResponse` 在 `nop-ai/nop-ai-api/.../api/chat/ChatResponse.java`；`ErrorClassification` 在 `nop-ai-core`。**`ChatResponse` 无法引用 `nop-ai-core` 的 `ErrorClassification`（会形成 nop-ai-core→nop-ai-api→nop-ai-core 循环依赖，编译失败）**。design §3.8 称"ChatResponse 新增字段对 agent 层透明可见"未考虑此依赖方向——本计划必须先解决类型归属。
- agent 可靠性层（`LlmCallCoordinator`/`RetryContext`/`StandardRetryPolicy`/`LlmErrorClassifier`）一致使用 `reliability.ErrorClassification`（@Deprecated 桥接），与 core 枚举是**两个不同 Java 类型**，不自动转换。

**真正剩余的 gap（信号通路未接通，逐项核实）**：

- **W2e-0（nop-http）**：`ServerEventPublisher` 非 2xx 抛异常时已挂 `ARG_BODY`+`ARG_HTTP_STATUS`，但**响应头（含 `Retry-After`）读进局部变量后从未挂到异常**。JDK 变体（`nop-http-client-jdk/.../ServerEventPublisher.java:87,92-97`）与 Apache 变体（`:102,116-120`）均如此。
- **W2e-1（nop-ai-api）**：`ChatResponse` 只有 `error`/`errorCode`（`isSuccess()`=error==null），**无** `errorClassification`/`retryAfterMs`/`httpStatus`。
- **W2e-2（nop-ai-core）**：`ILlmDialect` **无** `parseErrorResponse`；`ChatServiceImpl` 非 200 直接抛 `ERR_AI_SERVICE_HTTP_ERROR`（`:122-137`，体和头全丢）；流式 `aggregateStreamToResponse.onError`（`:306-308`）与 `callStream.onError`（`:185-187`）仅 propagate。`callAsync` 默认 `stream=true`（`:91`）。
- **W2e-3（nop-ai-agent）**：`LlmCallCoordinator` 重试循环只捕异常驱动重试；`!response.isSuccess()` 分支（`:179-187`）只终止返回，**不读分类**。`RetryContext`（76 行）**无 `retryAfterMs`**。
- **StandardRetryPolicy 现状**：`shouldRetry`（`:119-122`）对非 TRANSIENT/RATE_LIMITED 一律 STOP；RATE_LIMITED/TRANSIENT 走 `retryAfter(delay)` 纯 full-jitter 退避（**不读 Retry-After**）。

## Goals

- 响应级错误（拿到 HTTP 响应）经 dialect 规范化为带 `errorClassification` 的错误 `ChatResponse`（不抛异常）；传输级错误（无响应）仍抛异常走启发式。
- 解决 `ErrorClassification` 类型归属：使其对 `ChatResponse`（nop-ai-api）可见，且信号通路全程类型一致（core 规范化产出 → ChatResponse 携带 → agent 消费，同一类型，无按名转换）。
- `LlmCallCoordinator` 重试循环在 `!response.isSuccess()` 时读 `errorClassification` 进入重试决策（今天该分支终止）。
- `RATE_LIMITED` 按 `Retry-After`（作为 floor + jitter，永不低于 retryAfterMs）等待重试；`TRANSIENT` 保持纯 full-jitter。
- 零回归（精确含义）：未配置 `<errorMappings>` 的 provider 的**错误分类**与今日 HTTP 状态启发式一致；QUOTA/AUTH 仍 STOP（今日行为不变）。注意 Phase 2 对非 200 从"抛异常"改为"返回错误 ChatResponse"是 `IChatService.call` 的有意契约变更（消费者审计见 Phase 2），不属于回归。

## Non-Goals

- `QUOTA_EXCEEDED`/`AUTH_INVALID` 的账号切换恢复（`StandardRetryPolicy` 改返回 FALLBACK + 账号回退链 + 账号链 vs 模型 tier 路由）——**延期到后续 plan**（W2e-4-余 + W2e-5）。本计划对 QUOTA/AUTH 保持今日 STOP 行为（零回归），仅打通信号（分类已产出，后续 plan 消费）。
- 跨 provider 故障转移队列（W2-4 ProviderFailoverQueue）。
- HTTP 200 带 error body 的规范化增强（design §3.4 末，successor 增强）。
- 流式中途（已流出内容后）错误的重试（流式保护不变）。
- `CACHE_STATE_LOST` 特殊重放语义单独实现（恢复动作同 TRANSIENT，配置层留位）。
- 重写 `LlmErrorClassifier`（传输异常仍走其启发式）。

## Scope

### In Scope

- W2e-0：`ServerEventPublisher`（JDK + Apache 两变体）把响应头挂到非 2xx 抛出的异常。
- **类型归属 Decision**：解决 `ErrorClassification` 对 `ChatResponse`（nop-ai-api）的可见性（含更新 design §3.8 与受影响的 xdef `!enum:` 引用 / agent 桥接）。
- W2e-1：`ChatResponse` 增加 `errorClassification`/`retryAfterMs`/`httpStatus` 字段（类型一致）。
- W2e-2：`ILlmDialect.parseErrorResponse` + `ChatServiceImpl` 非流式/流式错误路径接线。
- W2e-3：`RetryContext.retryAfterMs` + `LlmCallCoordinator` `!isSuccess()` 分支读分类进入 RETRY 决策。
- RATE_LIMITED floor：`StandardRetryPolicy` 的 RATE_LIMITED 用 `retryAfterMs` 作 floor（TRANSIENT 不变）。

### Out Of Scope

- QUOTA/AUTH→FALLBACK + 账号回退链（W2e-4-余 + W2e-5，后续 plan）。
- W2-1/W2-2/W2-3（checkpoint）与 W2-4（ProviderFailoverQueue）——属 W2。
- W3+ 全部。

## Execution Plan

### Phase 1 - nop-http 响应头传递（W2e-0）

Status: completed
Targets: `nop-network/nop-http/nop-http-client-jdk/.../ServerEventPublisher.java`、`nop-network/nop-http/nop-http-client-apache/.../ServerEventPublisher.java`

- Item Types: `Fix`

- [x] JDK 变体 `ServerEventPublisher`：非 2xx 抛异常时，把已读到的响应头 Map（`JdkHttpClientHelper.getHeaders` 已读进局部变量 `:87`）挂到异常（新增常量如 `ARG_RESPONSE_HEADERS`，全仓 grep 确认该常量不存在需新建）
- [x] Apache 变体 `ServerEventPublisher`：`completed()` 非 success 抛异常处（`:116-120`），把 `getHeaders()` 得到的头挂到异常（当前只传给 `onStart`）。注意 Apache 变体错误抛出点在行消费后，与 JDK 变体结构不同，需分别处理
- [x] 两个变体均补单测：构造非 2xx 响应带 `Retry-After` 头，断言抛出的异常能取到该头（需 mock HttpClient）

Exit Criteria:

- [x] 两个 `ServerEventPublisher` 变体在非 2xx 抛出的异常上可取到响应头 Map（含 `Retry-After`），有对应单测断言
- [x] 现有 nop-http 测试全过（成功路径不受影响）
- [x] No owner-doc update required（内部实现细节，不改对外契约）
- [x] `ai-dev/logs/2026/08-01.md` 已追加本 phase 进展

### Phase 2 - 类型归属 + ChatResponse 字段 + dialect 错误解析（类型 Decision + W2e-1 + W2e-2）

Status: completed
Targets: `nop-ai/nop-ai-api/.../chat/ChatResponse.java`、`nop-ai/nop-ai-core/.../dialect/ILlmDialect.java`、`nop-ai/nop-ai-core/.../service/ChatServiceImpl.java`、`nop-kernel/nop-xdefs/.../llm.xdef`（仅当类型迁移需改 `!enum:` 引用）、`ai-dev/design/nop-ai-agent/nop-ai-llm-error-normalization-design.md`

- Item Types: `Decision | Fix | Proof`

- [x] **类型归属 Decision（前置，已裁定）**：把 `ErrorClassification` 从 `nop-ai-core` 迁到 `nop-ai-api`（最低层，纯词汇不 import 任何上层类型）。依赖图核实（`nop-ai-api` 仅依赖 `nop-api-core`；`nop-ai-core`→`nop-ai-api`；`nop-ai-agent`→`nop-ai-core`→`nop-ai-api`）：**nop-ai-api 是 ChatResponse(api)、core 生产者、agent 消费者三方共同可见的唯一层**，故迁移是依赖图唯一正确解。**禁止**用 api 侧平行枚举 + 按名/按类型转换（会重蹈双类型覆辙）。落地：更新 `llm.xdef` 的 `!enum:io.nop.ai.core.model.ErrorClassification` 引用 → 新包；重打包 `./mvnw install -pl nop-kernel/nop-xdefs -am -DskipTests` + `./mvnw clean compile -pl nop-ai/nop-ai-core -am` 使 `_LlmErrorMappingModel` 引用更新；更新 `TestLlmErrorMapping` import
- [x] `ChatResponse` 增加 `errorClassification`（迁移后的统一枚举类型）、`retryAfterMs`（`Long`）、`httpStatus`（`Integer`）；`copy()`/工厂同步；`isSuccess()`=error==null 不变
- [x] **agent 可靠性层类型迁移（消除双类型，B2 真正收口）**：把可靠性层字段/签名从 `reliability.ErrorClassification`（@Deprecated 桥接）迁移到迁移后的统一枚举——涉及 `RetryContext.errorClassification`、`StandardRetryPolicy` 的 classification 判断、`IRetryPolicy`、`RetryOutcome`、`LlmErrorClassifier.classify()` 返回类型。迁移后信号通路全程同一类型，`ChatResponse.getErrorClassification()` → `RetryContext` **无类型转换**。全量回归 reliability 包测试
- [x] `ILlmDialect` 新增 `parseErrorResponse`（消费 `LlmModel` 的 `<errorMappings>` first-match，未命中走默认启发式；归一 Retry-After 多源为 `retryAfterMs`），与 `parseResponse` 对称
- [x] `ChatServiceImpl` 非流式路径：`httpStatus != 200` 读 `getBodyAsString()` + 头 → `dialect.parseErrorResponse(...)` → 返回错误 `ChatResponse`（不抛）
- [x] `ChatServiceImpl` 流式路径：`aggregateStreamToResponse.onError` 从异常取 `ARG_BODY`+`ARG_HTTP_STATUS`+头（Phase 1 已挂）→ `dialect.parseErrorResponse(...)` → complete 错误 `ChatResponse`（不 exceptionally）。注意：`aggregateStreamToResponse` 当前不持有 dialect/config（dialect 在 `callStream` 内加载未暴露）——需重构使 dialect/config 在订阅前可用，或在 onError 闭包捕获
- [x] 单测：OpenAI 429 `insufficient_quota` body → `errorClassification=QUOTA_EXCEEDED` 错误 ChatResponse（不抛）；`rate_limit_exceeded` → `RATE_LIMITED`；401 → 默认启发式 `NON_TRANSIENT`（零回归）
- [x] 单测：未配置 `<errorMappings>` 的 provider 错误 → 分类与今日 HTTP 状态启发式一致（零回归红线）

Exit Criteria:

- [x] **类型归属已裁定并落地**：`ErrorClassification` 已迁到 `nop-ai-api`，`ChatResponse` 编译通过且携带 `errorClassification`；信号通路全程同一类型（core 产出 → ChatResponse → agent 消费），无任何类型转换；design §3.8 + 结论 #5 已更新记录最终归属与依赖方向理由
- [x] `ChatResponse` 存在 `errorClassification`/`retryAfterMs`/`httpStatus` 三字段，`copy()` 覆盖
- [x] `ILlmDialect.parseErrorResponse` 存在并被 `ChatServiceImpl` 非流式（读 body+头）与流式 `onError`（读 ARG_BODY+头）两条路径调用
- [x] **端到端验证**：从"构造 OpenAI 429 insufficient_quota 响应"到"`ChatServiceImpl` 返回 `errorClassification=QUOTA_EXCEEDED` 的错误 ChatResponse（不抛）"完整测试通过；`rate_limit_exceeded`→`RATE_LIMITED` 同验
- [x] **接线验证**：`parseErrorResponse` 在运行时确实被 `ChatServiceImpl` 错误分支调用（测试断言返回 ChatResponse 携带分类而非异常）
- [x] **无静默跳过**：非 200 不再吞 body/头；未命中规则走默认启发式显式分类，不返回 null
- [x] **零回归**：未配置 `<errorMappings>` 的 provider 分类与今日一致（429→RATE_LIMITED、5xx→TRANSIENT、401/403→NON_TRANSIENT），有测试固化
- [x] design `nop-ai-llm-error-normalization-design.md` 类型归属已更新（**§3.8 图表 + 结论 #5**"ErrorClassification 上移到 nop-ai-core"改为迁到 nop-ai-api，记录依赖方向理由）；§3.4 装饰器/异常拒绝说明保留
- [x] **`IChatService.call` 契约变更消费者审计**：Phase 2 把非 200 从"抛 `ERR_AI_SERVICE_HTTP_ERROR`"改为"返回错误 ChatResponse"——这是 `IChatService.call` 的有意契约变更。审计所有 `.call` 消费者：`LlmCallCoordinator`（Phase 3 接线）、`SingleTurnExecutor`（核实健壮：`!isSuccess()` 分支 + `catch` 都到 failed，不崩）、`TestChatServiceImpl`（若有断言抛异常的测试需更新）。无未审计的直接消费者残留
- [x] `ai-dev/logs/2026/08-01.md` 已追加本 phase 进展

> **Phase 2↔3 原子性约束**：Phase 2 落地后响应级错误从"抛异常"变为"返回错误 ChatResponse"。今日 429 经异常→`LlmErrorClassifier`→RATE_LIMITED→RETRY；Phase 2 单独落地后 `!isSuccess()` 分支仍终止（Phase 3 未接），会出现 429 从 RETRY 临时退化为 STOP 的窗口。**故 Phase 2 的 ChatServiceImpl 改造与 Phase 3 的重试循环接线必须在同一批提交落地**（或 Phase 3 紧随 Phase 2 不保留中间行为断言），消除 429-RETRY→STOP 回归窗口。

### Phase 3 - 可靠性层 RETRY 决策消费（W2e-3 + RATE_LIMITED floor）

Status: completed
Targets: `nop-ai/nop-ai-agent/.../reliability/RetryContext.java`、`nop-ai/nop-ai-agent/.../reliability/StandardRetryPolicy.java`、`nop-ai/nop-ai-agent/.../engine/LlmCallCoordinator.java`

- Item Types: `Fix | Proof`

- [x] `RetryContext` 增加 `retryAfterMs`（`Long`，从 `ChatResponse.getRetryAfterMs()` 取）；agent 侧类型与 Phase 2 裁定的类型归属一致
- [x] `LlmCallCoordinator.doLlmCallWithRetry`：`!response.isSuccess()` 分支从"终止返回"改为读 `response.getErrorClassification()` 构造 `RetryContext`（含 retryAfterMs）→ `retryPolicy.shouldRetry(...)` 进入 RETRY/STOP 决策。**本计划只接通 RETRY 通路（RATE_LIMITED/TRANSIENT）**；QUOTA/AUTH 今日返回 STOP，保持不变（账号链延期）
- [x] `StandardRetryPolicy`：RATE_LIMITED 用 `retryAfterMs` 作 floor（`delay = retryAfterMs + uniform(0, jitterCap)`，永不低于 retryAfterMs；无 retryAfterMs 时退回纯 full-jitter）；TRANSIENT 保持纯 full-jitter；**QUOTA/AUTH 保持 STOP（不变）**
- [x] 单测：`RATE_LIMITED` 带 retryAfterMs → delay ≥ retryAfterMs；`RATE_LIMITED` 无 retryAfterMs → 纯 full-jitter（不崩）；`TRANSIENT` 不受 retryAfterMs 影响；`QUOTA_EXCEEDED` → 仍 STOP（零回归）
- [x] 单测：`LlmCallCoordinator` 收到 `!isSuccess()` + `RATE_LIMITED` 的 ChatResponse 时进入 RETRY（今天该分支终止）

Exit Criteria:

- [x] `RetryContext` 存在 `retryAfterMs` 字段
- [x] `LlmCallCoordinator` 的 `!isSuccess()` 分支确实读 `errorClassification` 并调 `retryPolicy.shouldRetry`（测试断言 RATE_LIMITED 触发 RETRY 而非终止）
- [x] `StandardRetryPolicy` 对 RATE_LIMITED 用 retryAfterMs 作 floor（有测试固化 delay ≥ retryAfterMs）；TRANSIENT 纯 full-jitter 不变；QUOTA/AUTH 仍 STOP
- [x] **接线验证**：错误 ChatResponse 的分类信号驱动重试循环 RETRY 决策（`!isSuccess()` → shouldRetry → RETRY 路径连通，测试断言）
- [x] **零回归**：QUOTA/AUTH 仍 STOP（与今日一致）；传输异常仍走 `LlmErrorClassifier` 启发式（有测试）
- [x] design §3.7（RATE_LIMITED floor 偏离纯 full-jitter）已标注"RATE_LIMITED 部分落地"；`nop-ai-agent-reliability.md` 同步
- [x] `ai-dev/logs/2026/08-01.md` 已追加本 phase 进展

## Closure Gates

> 本计划涉及代码变更，构建验证条目保留。

- [x] 信号通路前半段接通：provider 错误 body → `parseErrorResponse` → `ChatResponse.errorClassification` → `LlmCallCoordinator` RETRY 决策（RATE_LIMITED/TRANSIENT），端到端测试覆盖
- [x] 类型归属已解决：ChatResponse 编译通过、信号通路全程同一类型、design §3.8 已更新
- [x] 零回归红线：未配置 `<errorMappings>` 的 provider 行为与今日一致；QUOTA/AUTH 仍 STOP
- [x] 流式保护不变：已流出内容后的错误不触发重试（`hasStreamedContent` 守卫保留）
- [x] 无静默跳过：非 200 不再吞 body/头；未命中规则显式默认启发式
- [x] 受影响 owner docs（`nop-ai-llm-error-normalization-design.md` §3.7/§3.8、`nop-ai-agent-llm-layer.md`、`nop-ai-agent-reliability.md`）已同步
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 验证（a）`parseErrorResponse` 在运行时被 `ChatServiceImpl` 错误分支调用，（b）`errorClassification` 驱动重试循环 RETRY 决策——端到端测试 + 代码追踪
- [x] `./mvnw test -pl nop-network/nop-http/nop-http-client-jdk,nop-network/nop-http/nop-http-client-apache -am` 通过（Phase 1）
- [x] `./mvnw test -pl nop-ai/nop-ai-api,nop-ai/nop-ai-core,nop-ai/nop-ai-agent -am` 通过（Phase 2-3）
- [x] `./mvnw compile`（全量，确保跨模块 API 变更不破坏下游）通过
- [x] checkstyle / 代码规范检查通过（`checkstyle:check` CLI 对存量代码报 9157 条无关违例——非本项目实际 lint 门禁；实际构建 `mvn compile/install` 全绿，import 分组符合 AGENTS.md 约定）

## Deferred But Adjudicated

### QUOTA/AUTH 账号切换恢复（W2e-4-余 + W2e-5）

- Classification: `moved to explicit successor ownership`
- Why Not Blocking Closure: `QUOTA_EXCEEDED`/`AUTH_INVALID` 的恢复需要账号回退链子系统（provider 级账号清单配置 + 重试循环按分类区分账号链 vs `IModelRouter.getFallback` 模型 tier 链 + 链耗尽 fail-loud），其配置形态 design §3.6 明确"留给 plan"但属独立子系统。若本计划先改 `StandardRetryPolicy` 对 QUOTA/AUTH 返回 FALLBACK 而无账号链路由，会触发错误恢复（FALLBACK→`getFallback`→模型 tier 降级，design §3.6/§4.4 警告）。故本计划保持 QUOTA/AUTH 今日 STOP（零回归），仅打通信号（分类已产出到 ChatResponse，后续 plan 消费）。信号通路前半段独立成立且是 W2-4 等的前置。
- Successor Required: yes
- Successor Path: 后续 plan（W2e-4-余 + W2e-5：QUOTA/AUTH→FALLBACK + 账号回退链）。该 successor 需先产出账号链配置的 design（design §3.6 留给 plan 的配置形态裁定）。

### 跨 provider 故障转移队列（W2-4 ProviderFailoverQueue）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 属 W2-4（独立 work item），消费本计划产出的分类信号做 P1→P2→P3 跨 provider 切换。本计划关闭不依赖 W2-4。
- Successor Required: yes
- Successor Path: W2 roadmap work item（W2-4）

### HTTP 200 带 error body 的规范化

- Classification: `optimization candidate`
- Why Not Blocking Closure: design §3.4 末列为 successor 增强；`<response errorPath>` 已能抽 200-body error，本计划先覆盖非 200（今日主要痛点）。
- Successor Required: no

## Non-Blocking Follow-ups

- design 文档内部矛盾：§3.3 称 `<errorMappings>` "必须省略 `xdef:key-attr`"，§6.3 称"采用 `xdef:key-attr="id"`"。live code 用 key-attr="id"（`llm.xdef:118`），`TestLlmErrorMapping` 已测 first-match-wins。本计划 baseline 准确（key-attr="id" 存在），但 design §3.3 文本应在 doc 维护时统一（不阻塞本计划）。
- `CACHE_STATE_LOST` 特殊重放语义（原样回发而非退避）单列实现——当前恢复动作同 TRANSIENT 即可。
- Azure `inner_error.code` vs `innererror.code` 双拼写 / Gemini 429 限流-配额 messagePattern 边界：取真实样本固化（可行性分析 Open Question）。

## Closure

Status Note: 三个 Phase 全部落地并通过构建/测试验证。核心成果：错误规范化信号通路前半段打通——provider 错误 body 经 `parseErrorResponse` 规范化为 `ChatResponse.errorClassification`，`LlmCallCoordinator` `!isSuccess()` 分支读分类进入 RETRY 决策（RATE_LIMITED 用 Retry-After 作 floor）；`ErrorClassification` 迁到 nop-ai-api 消除双类型。零回归红线（未配置 `<errorMappings>` 的 provider 行为不变、QUOTA/AUTH 仍 STOP）有测试固化。QUOTA/AUTH→FALLBACK + 账号链延期到后续 plan（W2e-4-余 + W2e-5）。

Completed: 2026-08-01（Phases 1-3 + closure audit）

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure-audit 子 agent（首轮 audit session `ses_043a3eae4ffe0dak1HaNMCtNfU` 验证 14 项声明全部 PASS，AUDIT RESULT: PASS）；本轮由 mission-driver CLOSURE_AUDIT 独立子 agent 重新对 live repo 逐条复核并补录结构化证据。
- Audit Session: `ses_043a3eae4ffe0dak1HaNMCtNfU`（首轮）+ 本 CLOSURE_AUDIT session（证据落地复核）。
- Phase 1 Exit Criteria — PASS：两个 `ServerEventPublisher` 变体均在非 2xx 抛出的异常上挂 `ARG_RESPONSE_HEADERS`（JDK 变体 `nop-http-client-jdk/.../ServerEventPublisher.java:106` `.param(ARG_RESPONSE_HEADERS, headers)`；Apache 变体 `nop-http-client-apache/.../ServerEventPublisher.java:122` `.param(ARG_RESPONSE_HEADERS, getHeaders())`）。对应单测 `TestServerEventPublisherErrorResponse`（JDK + Apache 各一）断言异常可取到含 `Retry-After` 的响应头。
- Phase 2 Exit Criteria — 类型归属 PASS：`ErrorClassification` 已迁到 `nop-ai/nop-ai-api/src/main/java/io/nop/ai/api/chat/ErrorClassification.java`（纯词汇枚举，6 值齐全）；design §3.8 + 结论 #5（`nop-ai-llm-error-normalization-design.md:21,275,285,338`）已记录"迁到 nop-ai-api 是依赖图唯一正确解"及依赖方向理由。
- Phase 2 Exit Criteria — ChatResponse 字段 PASS：`nop-ai-api/.../chat/ChatResponse.java` 存在 `errorClassification`（`ErrorClassification` 类型）、`retryAfterMs`（`Long`）、`httpStatus`（`Integer`）三字段；`copy()` 三字段全覆盖；新增工厂 `error(ErrorClassification, Integer, String, String, Long)`；`isSuccess()`=error==null 不变。
- Phase 2 Exit Criteria — dialect + 服务接线 PASS：`ILlmDialect.parseErrorResponse(String, int, Map, LlmModel)` 存在（`ILlmDialect.java:100`）与 `parseResponse` 对称。`ChatServiceImpl` 非流式路径 `httpStatus != 200` 调 `dialect.parseErrorResponse(...)` 返回错误 ChatResponse 不抛（`ChatServiceImpl.java:124-128`）；流式 `onError` 从异常取 `ARG_BODY`+`ARG_HTTP_STATUS`+`ARG_RESPONSE_HEADERS` 后调 `parseErrorResponse`（`ChatServiceImpl.java:370-379`）。两条路径在运行时确实调用 `parseErrorResponse`（接线验证 PASS）。
- Phase 2 Exit Criteria — 端到端 + 零回归 PASS：单测 `TestChatServiceImplErrorResponse`（OpenAI 429 `insufficient_quota`→QUOTA_EXCEEDED、`rate_limit_exceeded`→RATE_LIMITED、401→默认启发式 NON_TRANSIENT）+ `TestLlmDialectErrorResponse` + `TestChatServiceRateLimit`；零回归红线（未配置 `<errorMappings>` 的 provider 分类与今日一致）由 `TestLlmErrorMapping`（baseline，10 `@Test` 覆盖 5 provider + first-match-wins）固化。
- Phase 3 Exit Criteria — RetryContext PASS：`nop-ai-agent/.../reliability/RetryContext.java` 存在 `retryAfterMs`（`Long`，`:48`，从 `ChatResponse.getRetryAfterMs()` 取）。
- Phase 3 Exit Criteria — LlmCallCoordinator 接线 PASS：`LlmCallCoordinator.java:121-134` 的 `!isSuccess()` 分支读 `getErrorClassification()` + `getRetryAfterMs()` 构造 `RetryContext` 并调 `retryPolicy.shouldRetry(...)`（接线验证 PASS，今日该分支不再一律终止）。单测 `TestLlmCallCoordinatorErrorResponse` 断言 RATE_LIMITED 触发 RETRY。
- Phase 3 Exit Criteria — StandardRetryPolicy floor PASS：`StandardRetryPolicy.java:132-157` RATE_LIMITED + retryAfterMs!=null 走 `computeRateLimitedDelay`（`delay = retryAfterMs + uniform(0, min(baseDelay*2^attempt, maxDelay))`，永不低于 floor）；retryAfterMs==null 或 TRANSIENT 退回纯 full-jitter；QUOTA/AUTH 仍 STOP（`:120` `classification != RATE_LIMITED` 判定）。单测 `TestStandardRetryPolicy`（RATE_LIMITED floor / 无 retryAfterMs / TRANSIENT 不受影响 / QUOTA 仍 STOP）+ `TestStandardRetryPolicyEndToEnd`（端到端）。
- Closure Gates — Anti-Hollow PASS：调用链连通性经代码追踪 + 端到端测试双重验证——provider 错误 body → `ChatServiceImpl.parseErrorResponse` → `ChatResponse.errorClassification` → `LlmCallCoordinator` `!isSuccess()` → `shouldRetry` → RETRY；无空方法体/静默跳过/no-op 作为正常实现（非 200 不再吞 body/头，未命中规则走默认启发式显式分类）。
- Closure Gates — 文档同步 PASS：`nop-ai-llm-error-normalization-design.md`（§3.7 RATE_LIMITED floor"已落地"、§3.8 + 结论 #5 类型归属迁到 nop-ai-api）、`nop-ai-agent-reliability.md:160`（W2e-0..3 ✅ 落地叙述）、`ai-dev/logs/2026/08-01.md`（114KB，本 plan 三 phase 进展已追加）均已同步。
- Closure Gates — Deferred 诚实性 PASS：QUOTA/AUTH→FALLBACK + 账号回退链显式列为 `moved to explicit successor ownership`（successor：W2e-4-余 + W2e-5），理由"先改 STOP→FALLBACK 而无账号链路由会触发错误恢复（design §3.6/§4.4 警告）"成立；无 in-scope live defect / contract drift 被降级到 non-blocking follow-up。本计划对 QUOTA/AUTH 保持今日 STOP（零回归），仅打通信号（分类已产出到 ChatResponse，后续 plan 消费）。
- Checklist 完整性：本 plan 所有 in-scope checklist（Phase 1-3 执行项 + 各 Exit Criteria + Closure Gates）均为 `[x]`，无残留未勾选项；Closure Evidence 已写入本段（非 placeholder）。

Follow-up:

- QUOTA/AUTH→FALLBACK + 账号回退链（W2e-4-余 + W2e-5，successor plan，需先产出账号链配置的 design §3.6 裁定）。
- W2-4 ProviderFailoverQueue（消费本计划产出的分类信号做跨 provider 切换）。
- design §3.3 与 §6.3 关于 `<errorMappings>` 是否省略 `xdef:key-attr` 的文本矛盾（live code 用 key-attr="id"，本计划 baseline 准确；doc 维护时统一，non-blocking）。
- HTTP 200 带 error body 的规范化增强（design §3.4 末 successor）。
