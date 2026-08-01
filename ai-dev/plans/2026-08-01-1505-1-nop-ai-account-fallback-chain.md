# nop-ai 账号回退链 + QUOTA/AUTH→FALLBACK（W2e-4-余 + W2e-5）

> Plan Status: completed
> Mission: nop-ai-agent-harness-evolution
> Work Item: W2e-4-余 + W2e-5（QUOTA_EXCEEDED/AUTH_INVALID→FALLBACK + provider 级账号回退链 + 重试循环按 errorClassification 区分账号链 vs 模型 tier 链；W2e 收口，W2 前置必须项的剩余部分）
> Last Reviewed: 2026-08-01
> Draft Review: 两轮独立子 agent 对抗性审查（round-1 发现 2 Major + 6 Minor 已修；round-2 verdict READY FOR ACTIVE，无 Blocker/Major）。共识达成。
> Source: `ai-dev/design/nop-ai-agent/nop-ai-llm-error-normalization-design.md`（§3.6 备用账号链与 FALLBACK 通道区分、§4.4 拒绝复用 IModelRouter/FALLBACK、§6.8 StandardRetryPolicy 行为变更前置、§6.9 账号链 fail-loud）；`ai-dev/backlog/nop-ai-agent-harness-evolution-roadmap.md` W2e-4/W2e-5
> Related: 前置 plan `2026-08-01-1440-1`（W2e-0..3 信号通路 + RATE_LIMITED floor，已 completed，本计划是其显式 deferred successor）；后续 W2-4 ProviderFailoverQueue（消费本计划产出的分类 + 账号链做跨 provider 切换）；plan 209（SmartModelRouter，模型 tier 回退通道）

## Purpose

把 W2e 信号通路收口：错误分类信号（已在 `ChatResponse.errorClassification`）驱动**账号级**故障恢复。`QUOTA_EXCEEDED`/`AUTH_INVALID` 不再 STOP，而是沿 provider 声明的有序账号回退链切换到下一个账号重试（链耗尽 fail-loud）；并明确区分**账号回退链**与**模型 tier 回退链**（`IModelRouter.getFallback`）两条独立通道，使二者不再复用同一 `FALLBACK` 信号导致"把好模型降级"的错误恢复（design §3.6/§4.4 警告）。

## Current Baseline

> 已逐条核对 live repo（字段、行号、pom 依赖方向），非沿用旧文档。事实来源：本计划起草前的两个独立 explore 子 agent 报告。

**已落地（plan 1440-1，核对属实）**：

- 信号通路前半段：provider 错误 body 经 `ILlmDialect.parseErrorResponse` → `ChatResponse.errorClassification`/`retryAfterMs`/`httpStatus`（`ChatResponse.java:77,84,89`）；`ErrorClassification` 已迁到 `nop-ai-api`（`io.nop.ai.api.chat.ErrorClassification`，6 值）。
- `LlmCallCoordinator`（`nop-ai-agent/.../engine/LlmCallCoordinator.java`）`!isSuccess()` 分支已读 `errorClassification` 调 `retryPolicy.shouldRetry`（`:121-157`）；RATE_LIMITED 按 retryAfterMs floor 重试已落地。
- `StandardRetryPolicy.shouldRetry`（`StandardRetryPolicy.java:107-143`）：RATE_LIMITED/TRANSIENT 走 RETRY；**QUOTA_EXCEEDED/AUTH_INVALID 走 `:119-122` 白名单守卫返回 STOP**（今日行为，零回归）。

**架构事实（阻断项，已核实）**：

- **`StandardRetryPolicy` 今日从不产出 FALLBACK**——`shouldRetry` 只返回 RETRY/STOP（`:119-122` 白名单守卫）。`RetryDecision.FALLBACK`（`RetryDecision.java:21-25`）虽存在但标准策略不可达。design §6.8 明确：§3.6 的"按分类路由账号链"今日**不可达**，必须先改 `StandardRetryPolicy` 对 QUOTA/AUTH 返回 FALLBACK。
- **`LlmCallCoordinator` 的 FALLBACK 分支今日只走模型 tier**——`outcome.isFallback()` → `doFallbackSwitch`（`:149-151` 响应级、`:180-186` 异常级）→ `modelRouter.getFallback(routedOptions)`（`:219`），返回 null 则抛 `NopAiAgentException` fail-loud（`:226-230`）。**无任何按 `errorClassification` 区分账号链 vs 模型 tier 的路由**。`doFallbackSwitch` 注释（`:155-156`）明示"QUOTA/AUTH 今日保持 STOP，账号链延期"。
- **`IModelRouter.getFallback`（`IModelRouter.java:36`）只认 `ChatOptions`（provider+model），无 apiKey/account 概念**。`SmartModelRouter.getFallback`（`:121-152`）按 complexity tier 序列降级模型。`ChatOptions`（`nop-ai-api/.../chat/ChatOptions.java`）**无 apiKey/account/chain 字段**。

**账号配置现状（核对属实，是真实 gap）**：

- `llm.xdef`（`nop-kernel/nop-xdefs/.../_vfs/nop/schema/ai/llm.xdef`）有 `apiKeyHeader`（`:13`，header **名**非 key 值）、`baseUrl`（`:32`）；**无 `<accounts>` 元素，无重复 `<account>`，无任何账号清单概念**。
- `LlmConfigHelper.resolveApiKey(provider)`（`nop-ai-core/.../service/LlmConfigHelper.java:121-140`）读**单个** config 变量 `ai.service.llm.{provider}.api-key`（或单个 secret 文件），返回单个 `String`。无 list、无 chain、无 per-account 元数据。
- **apiKey 在请求构造内部按 provider 解析一次，重试循环无法控制用哪个账号**——这是本计划的核心交付难点（裁定 B 必须解决）：
  - `ChatServiceImpl`（`nop-ai-core/.../service/ChatServiceImpl.java:217`，新 `IChatService` 路径，**账号链实际作用路径**）在 `buildHttpRequest` 内调 `LlmConfigHelper.resolveApiKey(provider)`（`LlmConfigHelper.java:121-140`）取单个 key。
  - `DefaultAiChatService`（`nop-ai-core/.../service/DefaultAiChatService.java`）是 `@Deprecated`（`:78`）旧路径，实现**不同的** legacy 接口 `IAiChatService`，用自带 `getApiKey(llmName)`（`:275-292`，同 config-var+secret-file 模式但独立方法，**不调** `LlmConfigHelper`），**不可注入为 `LlmCallCoordinator.chatService`（其类型是新 `IChatService`）→ 不在账号链重试路径上**。
- **跨层边界（裁定 B 的硬约束）**：依赖方向 `nop-ai-agent → nop-ai-core → nop-ai-api`；`IChatService.call(ChatRequest,ICancelToken)` **无 account 参数**；`ChatOptions`（nop-ai-api）**无 apiKey/account 字段**。故驱动账号切换的 agent 层无法把所选 apiKey 注入 nop-ai-core 的 `buildHttpRequest`——账号身份（apiKey + 可选 baseUrl）**必须经一个 nop-ai-api 载体**（`ChatOptions` 或 `ChatRequest` 上的新字段）跨层下沉。
- DB 侧 `NopAiModel.apiKey` 列存在（`nop-ai-dao`）但**不被 live call path 使用**（仅 DB 存储模型配置，且从所有 xmeta 层 scrub）。

**真正剩余的 gap（逐项核实）**：

1. **账号链数据模型缺失**：`llm.xdef` 无有序账号清单；无 account identity（id + apiKey 引用 + 可选独立 baseUrl + 额度元数据）。
2. **账号解析是单值**：`LlmConfigHelper` 返回单 key；重试循环无法按账号切换。
3. **`StandardRetryPolicy` 对 QUOTA/AUTH 仍 STOP**（`:119-122`）：§3.6 路由不可达。
4. **`LlmCallCoordinator` FALLBACK 路由无分类区分**：`doFallbackSwitch` 一律走 `modelRouter.getFallback`（模型 tier）；QUOTA/AUTH 切账号会错误降级模型。
5. **`RetryContext`（`reliability/RetryContext.java:44-48`）无账号位跟踪**：链游走需记录当前账号/已失败账号集合。
6. design §3.6（`:252`）把"账号链持久化形态（是否落库、额度元数据结构）"显式留给本 plan 裁定。

## Goals

- provider 可声明**有序账号回退链**（每个账号 = apiKey 引用 + 可选独立 baseUrl + 可选额度元数据）。
- 账号身份（apiKey + 可选 baseUrl）经一个 **nop-ai-api 载体**（`ChatOptions`/`ChatRequest` 新字段）跨层下沉到 `ChatServiceImpl.buildHttpRequest`，使 agent 层重试循环能按账号切换。
- `QUOTA_EXCEEDED`/`AUTH_INVALID` 经 `StandardRetryPolicy` 返回 FALLBACK，重试循环按 `errorClassification` 路由：QUOTA/AUTH→账号链切换下一个账号重试（attempt 重置、usage 归属新账号）；TRANSIENT→`IModelRouter.getFallback`（模型 tier，行为不变）。
- 账号链耗尽 fail-loud（design §6.9，Minimum Rules #24）。
- 解除 design §3.6 `:252` 的"留给 plan"占位：裁定账号链持久化形态与跨层访问契约，回写 design。

## Non-Goals

- **跨 provider 故障转移队列（W2-4 ProviderFailoverQueue）**——消费本计划产出的分类+账号链做 P1→P2→P3 跨 provider 切换，属独立 work item。本计划账号链是**同一 provider 内**的多账号。
- **`IModelRouter` 模型 tier 回退改造**——保持 `getFallback` 现有行为，本计划只新增账号链通道与之并存。
- **改造 `DefaultAiChatService`（`@Deprecated` legacy `IAiChatService` 路径）**——它不在账号链重试路径上（见 Current Baseline），本计划只改新 `IChatService` 路径（`ChatServiceImpl`）。
- HTTP 200 带 error body 规范化（design §3.4 末 successor）。
- 重写 `LlmErrorClassifier`（传输异常仍走启发式）。
- 账号链的运行时额度熔断/动态配额调度（额度元数据仅作声明/诊断，不做主动熔断）。

## Risks And Rollback

- **apiKey 暴露风险**：账号 apiKey 若作为 `ChatOptions` 字段跨层，`ChatOptions` 是 `@DataBean` 且 getter 带 `@JsonInclude`，须确保 apiKey 不泄漏进序列化请求体/日志/审计（裁定 B 与 Phase 2 须显式处理：序列化排除 + 日志脱敏）。
- **Phase 3 内部原子性**（见 Phase 3 末约束）：`StandardRetryPolicy` STOP→FALLBACK 改动须与 `LlmCallCoordinator` 分类路由改动同批落地，否则 QUOTA/AUTH 错误降级模型（design §4.4）。

## Scope

### In Scope

- 账号链**持久化形态 Decision** + **链访问契约 Decision**（design §3.6 `:252` 裁定）。
- `llm.xdef` 有序 `<accounts>` 配置 + codegen 模型；`LlmConfigHelper` 有序链解析。
- per-call 账号选择：请求构造层 `ChatServiceImpl`（新 `IChatService` 路径，账号链实际作用路径）按指定账号构造请求（apiKey + 可选 baseUrl 覆盖），使重试循环可切换账号。`DefaultAiChatService`（`@Deprecated` legacy，不在重试路径）不在范围。
- `StandardRetryPolicy`：QUOTA/AUTH → FALLBACK（design §6.8）。
- `LlmCallCoordinator`：FALLBACK 按 `errorClassification` 路由（账号链 vs `getFallback`）+ 账号链游走 + 链耗尽 fail-loud；`RetryContext` 账号位跟踪。
- 全程测试 + 端到端 + 零回归红线。

### Out Of Scope

- W2-1/W2-2/W2-3（checkpoint）与 W2-4（跨 provider 队列）——属 W2 其他 work item。
- W3+ 全部。
- nop-task 迁移、plan 运行时（属 W1-4）。

## Execution Plan

### Phase 1 - 账号链形态与访问契约裁定（Decision）

Status: completed
Targets: `ai-dev/design/nop-ai-agent/nop-ai-llm-error-normalization-design.md`（§3.6 `:252` 占位回写；§3.6/§4.4 补链访问契约）

- Item Types: `Decision`

- [x] **裁定 A：账号链持久化形态**。推荐基线 = **纯配置文件**（`{provider}.llm.xml` 的 `<accounts>` 有序列表，apiKey 经 config 变量/secret 文件引用，与现有 `LlmConfigHelper.resolveApiKey` 同源模式）。拒绝理由需记录：DB-backed（`NopAiModel.apiKey` 不被 live call path 使用 + ORM 结构变更属 protected area + 无必要的外部依赖）。额度元数据结构（如 `quotaLimit`/`renewAt`）定为**可选声明字段、诊断用、不做主动熔断**。
- [x] **裁定 B：跨层访问契约**（核心交付难点）。`LlmCallCoordinator`（agent 层）驱动切换，但 apiKey 在 `ChatServiceImpl.buildHttpRequest`（nop-ai-core）内解析、`IChatService.call` 无 account 参数、依赖方向 `agent→core→api`——**agent 层无法直接注入 apiKey**。裁定须给出**层间一致的**机制，至少裁定：(1) 账号身份（apiKey + 可选 baseUrl）经哪个 **nop-ai-api 载体**跨层（`ChatOptions` 新字段 or `ChatRequest`）——**不是** agent 层自持协作者能单独解决的；(2) 链在何处解析、重试循环如何取下一个账号（agent 层协作者负责"下一个"语义 + 经 api 载体把所选账号下沉）；(3) 若选 `ChatOptions` 新字段，须裁定其 `copy()`/`merge()`/`Builder` 同步 + 序列化排除（apiKey 不进请求体/日志）。备选（chat-service 内部自管理推进）需记录取舍。**裁定必须层间一致**：单独一个 agent 层 `IAccountChain` 不解决下沉，不可作为完整解。
- [x] 回写 design §3.6 `:252`：把"留给 plan"替换为裁定 A+B 结论与理由；§4.4 补"两通道区分"运行时落地路径（QUOTA/AUTH→账号链，TRANSIENT→getFallback）；顺手核对 §6.6 残留的"装饰器"措辞（与 §4.0 已拒绝装饰器矛盾，统一为 ChatServiceImpl 内置）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] design §3.6 `:252` 占位已被裁定 A（持久化形态）+ 裁定 B（跨层访问契约）结论替换，含拒绝 DB-backed 的理由
- [x] **裁定 B 层间一致性已验证**：裁定明确点出**哪个 nop-ai-api 载体**承载账号身份、其在 live repo 的 `copy()`/`merge()`/`Builder` 落点、apiKey 序列化/日志排除方案；且与 live 事实一致（`IChatService.call` 无 account 参数、`ChatOptions` 无 account 字段、`IModelRouter.getFallback` 只认 `ChatOptions`）
- [x] 裁定 A 与 live 一致：`LlmConfigHelper.resolveApiKey`（config 变量 + secret 文件）模式
- [x] No owner-doc update beyond design（本 phase 是纯 design 裁定；`docs-for-ai/` 无需改动——账号链尚未成为平台用户可见 API）
- [x] No new test required: design-only phase（Rule #25）
- [x] `ai-dev/logs/2026/08-01.md` 已追加本 phase 裁定

### Phase 2 - 账号链数据与解析（无行为变更）

Status: completed
Targets: `nop-kernel/nop-xdefs/.../_vfs/nop/schema/ai/llm.xdef`、`nop-ai/nop-ai-core/.../service/LlmConfigHelper.java`、`nop-ai/nop-ai-core/.../service/ChatServiceImpl.java`、Phase 1 裁定的 nop-ai-api 载体（`ChatOptions`/`ChatRequest`）与 agent 层账号链协作者（`DefaultAiChatService` 不在范围——`@Deprecated` legacy 路径，不在重试路径上）

- Item Types: `Fix | Proof`

- [x] `llm.xdef` 增加 provider 级有序 `<accounts>` 元素（每条 = `apiKey` 引用 + 可选 `baseUrl` 覆盖 + 可选额度元数据字段，形态依 Phase 1 裁定 A）。改 xdef 后**必须先 `./mvnw install -pl nop-kernel/nop-xdefs -am -DskipTests`** 重打包，下游 `_Llm*` codegen 模型才含新结构（project-context xdef 提示）
- [x] codegen 模型（`_LlmAccountModel` 或同类）生成并验证（`./mvnw clean compile -pl nop-ai/nop-ai-core -am`）
- [x] `LlmConfigHelper` 增加有序账号链解析（返回有序账号清单，非单 key）；保留 `resolveApiKey(provider)` 单值方法兼容无链配置（零回归）
- [x] Phase 1 裁定 B 的 nop-ai-api 载体落地（如 `ChatOptions` 新增 account/apiKey 字段）：**同步更新手写的 `copy()`/`merge()`/`Builder`**（否则复制/合并静默丢账号，Rule #11 陷阱），并确保序列化排除 apiKey（不进请求体/日志）
- [x] 请求构造层按指定账号构造请求：`ChatServiceImpl.buildHttpRequest` 读 api 载体上的账号身份（apiKey + 可选 baseUrl 覆盖）而非只按 provider 解析单 key；无指定时退回链首/单 key（今日行为）
- [x] Phase 1 裁定 B 的 agent 层账号链协作者落地（从 provider 配置解析有序链 + "next account" 语义，经 api 载体下沉所选账号）
- [x] 单测：N 账号链按声明顺序解析出 N 个不同 apiKey；无 `<accounts>` 配置退回单 key（零回归）；baseUrl 覆盖生效；`ChatOptions.copy()/merge()` 保留账号字段

Exit Criteria:

- [x] `llm.xdef` 存在有序 `<accounts>`，codegen 模型已生成（`./mvnw clean compile -pl nop-ai/nop-ai-core -am` 通过）
- [x] `LlmConfigHelper` 存在有序账号链解析（非单值），有单测断言 N 账号 → N key 有序
- [x] nop-ai-api 载体（裁定 B 所选）承载账号身份，其 `copy()`/`merge()`/`Builder` 已同步（单测断言复制/合并不丢账号），apiKey 序列化排除已验证（`@JsonIgnore`）
- [x] `ChatServiceImpl.buildHttpRequest` 按 api 载体账号构造请求（apiKey + baseUrl 覆盖），有单测断言不同账号 → 不同请求 key/url
- [x] **零回归**：无 `<accounts>` 配置的 provider 行为与今日一致（单 key；QUOTA/AUTH 在 Phase 3 后变为 FALLBACK，但未配置 `<errorMappings>` 时 QUOTA/AUTH 仍不可达）
- [x] **无静默跳过**：账号链解析未配置时显式退回空列表（非返回 null 当正常）
- [x] **接线验证**：per-call 账号选择确实被 `ChatServiceImpl` 请求构造使用（`TestChatServiceImplAccountRequest` 断言指定 accountKey → 请求 Authorization token）
- [x] design §3.6/§4.4 已记录链数据形态与跨层契约（与 Phase 1 裁定一致）
- [x] `ai-dev/logs/2026/08-01.md` 已追加本 phase 进展

### Phase 3 - QUOTA/AUTH→FALLBACK + 分类路由 + 账号链游走（原子落地）

Status: completed
Targets: `nop-ai/nop-ai-agent/.../reliability/StandardRetryPolicy.java`、`nop-ai/nop-ai-agent/.../reliability/RetryContext.java`、`nop-ai/nop-ai-agent/.../engine/LlmCallCoordinator.java`、`nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/reliability/TestStandardRetryPolicy.java`、`nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/reliability/TestRetryPolicyWiring.java`

- Item Types: `Fix | Proof`

- [x] `StandardRetryPolicy.shouldRetry`：QUOTA_EXCEEDED/AUTH_INVALID → 返回 `RetryOutcome.fallback()`（design §6.8）；RATE_LIMITED/TRANSIENT 保持 RETRY；NON_TRANSIENT/CACHE_STATE_LOST 保持 STOP
- [x] 账号链游走状态：`RetryContext` 是 `final` 不可变 per-attempt 载体，故**已失败账号集合属重试循环的局部可变状态**（`AccountChain` 游走器 + cursor，类比今日 `routedOptions` 在循环内重赋值），跨迭代保留；`RetryContext` 不携带账号身份（policy 不需要——它只决定 RETRY/STOP/FALLBACK，账号身份由循环在 FALLBACK 后经 `ChatOptions` 下沉）
- [x] `LlmCallCoordinator` FALLBACK 路由改造：`outcome.isFallback()` 时先看 `errorClassification`——QUOTA/AUTH→账号链协作者取下一个账号（attempt 重置、usage 归属新账号、循环局部记录已失败账号）；TRANSIENT→`modelRouter.getFallback`（模型 tier，行为不变）。**注意分类来源不对称**：响应级（经配置 `<errorMappings>` **可达** QUOTA/AUTH）vs 异常级（`LlmErrorClassifier.classify(ex)`，启发式**从不产** QUOTA/AUTH per §6.1）——账号链路由**只在响应级路径可达**，实现与测试须对齐此事实
- [x] 账号链耗尽 fail-loud：链尾再失败 → 抛 `NopAiAgentException`（design §6.9，不静默 STOP/降级）。实现：fail-loud 错误在循环外抛出（`fallbackExhausted` 局部变量 + 循环外 `throw`），避免被 `catch` 误当传输异常重试
- [x] 更新 `TestStandardRetryPolicy.quotaExceededFailsFastImmediately`（今日断言 `isStop()`）→ 断言 QUOTA/AUTH `isFallback()`；新增 AUTH_INVALID 用例（今日该文件无 AUTH 用例）
- [x] 更新 `TestRetryPolicyWiring`：FALLBACK 分类路由——QUOTA/AUTH 走账号链、TRANSIENT 走 getFallback

Exit Criteria:

- [x] `StandardRetryPolicy` 对 QUOTA_EXCEEDED/AUTH_INVALID 返回 FALLBACK（单测断言 `isFallback()`，取代旧 `isStop()` 断言）
- [x] `LlmCallCoordinator` 的 FALLBACK 分支按 `errorClassification` 路由（QUOTA/AUTH→账号链；TRANSIENT→getFallback），有测试断言两通道分流；分类来源不对称（QUOTA/AUTH 仅响应级可达）已在测试路径中体现（`TestAccountFallbackChain` 响应级路径 + `TestRetryPolicyWiring` 传输级仍走模型 tier）
- [x] **端到端验证**：构造 QUOTA_EXCEEDED 错误 ChatResponse（响应级路径） → 账号链切到下一个账号 → 重试成功（多账号链）；链耗尽 → fail-loud 抛异常（不静默、不降级模型）。`TestAccountFallbackChain` 从 `LlmCallCoordinator.doLlmCallWithRetry` 入口到最终成功/fail-loud 完整跑通
- [x] **接线验证**：账号链协作者在运行时确实被 FALLBACK+QUOTA/AUTH 分支调用（`TestAccountFallbackChain.quotaRoutesToAccountChainNotModelTier` 断言 `getFallback` 计数==0）；`getFallback` 仅在 TRANSIENT 分支被调用
- [x] **无静默跳过**：链耗尽显式抛异常（非返回 STOP/null 当正常）；未配置账号链时 QUOTA/AUTH→FALLBACK→无链→fail-loud（design §6.9，`TestAccountFallbackChain.noAccountChainConfiguredQuotaFailsLoud`）
- [x] **零回归**：未配置 `<errorMappings>` 的 provider，QUOTA/AUTH 仍不可达（默认启发式 401/403→NON_TRANSIENT，§6.1 不变量）；TRANSIENT 模型 tier 回退行为不变（`TestRetryPolicyWiring.fallbackDecisionFailsLoudAtRuntime` 传输级仍走模型 tier）
- [x] **契约变更已记录**：配置了产生 QUOTA/AUTH 的 `<errorMappings>` 的 provider，QUOTA/AUTH 从今日 STOP 有意变为账号切换（这是 feature，非回归）；design §3.6/§4.4 + `nop-ai-agent-reliability.md` 同步
- [x] `ai-dev/logs/2026/08-01.md` 已追加本 phase 进展

> **Phase 3 内部原子性约束**：Phase 3 把 QUOTA/AUTH 从 STOP 改为 FALLBACK。若 `StandardRetryPolicy` 改返回 FALLBACK 而 `LlmCallCoordinator` 分类路由未同步，FALLBACK 会落到 `doFallbackSwitch`→`getFallback` = **模型 tier 降级（错误恢复，design §4.4 警告）**。故 Phase 3 的策略变更（QUOTA/AUTH→FALLBACK）与循环分类路由改造必须**在同一批提交**落地，消除 QUOTA/AUTH→错误模型降级的中间窗口。（Phase 2 是行为中性的链/载体落地，与此原子性无关。）

## Closure Gates

> 本计划涉及代码变更，构建验证条目保留。

- [x] 账号回退链端到端成立：provider 声明 N 账号 → QUOTA_EXCEEDED 触发依次切换 → 成功或链耗尽 fail-loud，测试覆盖（`TestAccountFallbackChain`）
- [x] 两通道区分成立：QUOTA/AUTH→账号链、TRANSIENT→`getFallback`，无错误降级（`TestAccountFallbackChain.quotaRoutesToAccountChainNotModelTier` 断言 getFallback 计数==0）
- [x] 零回归红线：未配置 `<errorMappings>` 的 provider 行为不变（QUOTA/AUTH 不可达）；未配置 `<accounts>` 的 provider 退回单 key
- [x] 流式保护不变：已流出内容后的错误不触发 FALLBACK（`hasStreamedContent` 守卫保留，`StandardRetryPolicy` 顶部 `isHasStreamedContent` → STOP 不变）
- [x] 无静默跳过：链耗尽 fail-loud；未配置链时显式失败而非静默
- [x] design §3.6 `:252` 占位已回写裁定结论；§4.4 两通道区分运行时路径已记录；`nop-ai-agent-reliability.md` 同步
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 验证（a）账号链协作者在运行时被 FALLBACK+QUOTA/AUTH 分支调用，（b）`StandardRetryPolicy` 确实对 QUOTA/AUTH 产 FALLBACK，（c）端到端从错误响应到账号切换/fail-loud 完整连通——端到端测试 + 代码追踪
- [x] `./mvnw install -pl nop-kernel/nop-xdefs -am -DskipTests`（xdef 改动后）+ `./mvnw test -pl nop-ai/nop-ai-core,nop-ai/nop-ai-agent -am` 通过（2963 tests 0 failures）
- [x] `./mvnw compile`（全量，确保跨模块 API 变更不破坏下游）通过
- [x] checkstyle / 代码规范检查通过（import 分组符合 AGENTS.md；checkstyle 未绑定构建生命周期）

## Deferred But Adjudicated

### 跨 provider 故障转移队列（W2-4 ProviderFailoverQueue）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 属 W2-4（独立 work item），消费本计划产出的分类信号 + 同 provider 账号链做 P1→P2→P3 **跨 provider** 切换。本计划账号链是同一 provider 内多账号；跨 provider 是独立维度。
- Successor Required: yes
- Successor Path: W2 roadmap work item W2-4

### 账号运行时额度熔断 / 动态配额调度

- Classification: `optimization candidate`
- Why Not Blocking Closure: 额度元数据仅作声明/诊断，不做主动熔断（本计划 Non-Goal）。主动额度熔断需配额观测/调度子系统，属独立优化。
- Successor Required: no

## Non-Blocking Follow-ups

- `CACHE_STATE_LOST` 特殊重放语义单独实现（恢复动作同 TRANSIENT，配置层留位）——不阻塞本计划。
- HTTP 200 带 error body 规范化（design §3.4 末 successor）——不阻塞。

## Closure

Status Note: W2e 信号通路收口完成。错误分类信号（`ChatResponse.errorClassification`）驱动账号级故障恢复：`QUOTA_EXCEEDED`/`AUTH_INVALID` 经 `StandardRetryPolicy` 返回 FALLBACK，重试循环按分类路由——QUOTA/AUTH→provider 声明的有序账号回退链（经 `ChatOptions.accountKey` 跨层下沉），TRANSIENT→`IModelRouter.getFallback`（模型 tier）。两条通道区分使 QUOTA/AUTH 不再错误降级模型（design §4.4）。链耗尽 fail-loud（design §6.9）。Phase 3 策略变更与循环分类路由原子落地（消除中间窗口 QUOTA→FALLBACK→错误模型降级）。零回归：未配置 `<errorMappings>` 的 provider QUOTA/AUTH 不可达；未配置 `<accounts>` 的 provider 退回单 key。
Completed: 2026-08-01

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent closure-audit（explore，task ses_04339d5eaffehCwVIYtGjKSkCx）
- Audit Session: ses_04339d5eaffehCwVIYtGjKSkCx
- Evidence:
  - **10/10 claims PASS**（逐条 live code 证据）：
    1. `llm.xdef` `<accounts xdef:key-attr="id">` + `<account apiKey baseUrl quotaLimit renewAt>`（llm.xdef:46-53）— PASS
    2. `_LlmAccountModel`（5 字段 getter）+ `_LlmModel._accounts`/`getAccounts()` — PASS
    3. `LlmConfigHelper.resolveAccountChain` 返回有序 list，无链返回 emptyList（非 null）（LlmConfigHelper.java:158-168）— PASS
    4. `ChatOptions.accountKey`/`accountBaseUrl` 均 `@JsonIgnore`，copy()/merge()/Builder 三处同步（ChatOptions.java:130,138,398,449,564）— PASS
    5. `ChatServiceImpl.buildHttpRequest` 读 accountKey（非空用它，空退 resolveApiKey）+ accountBaseUrl 覆盖（ChatServiceImpl.java:222-229,251-273）— PASS
    6. `StandardRetryPolicy` QUOTA/AUTH → `RetryOutcome.fallback()`（非 stop）（StandardRetryPolicy.java:126-129）— PASS
    7. `LlmCallCoordinator` IAccountChainResolver ctor param + 响应级 FALLBACK 按 classification 分流（QUOTA/AUTH→doAccountSwitch，else→doModelTierFallback）+ 链耗尽 fail-loud（循环外 throw）+ 传输级恒模型 tier + 无 doFallbackSwitch（已重命名）— PASS
    8. `AccountChain`（stateful walker）+ `IAccountChainResolver`（@FunctionalInterface）— PASS
    9. 5 个测试文件存在且非空壳：TestStandardRetryPolicy（quotaExceededReturnsFallback+authInvalidReturnsFallback 断言 isFallback）、TestAccountFallbackChain（端到端 QUOTA→切换→成功 / 链耗尽 fail-loud / 无链 fail-loud / QUOTA 不调 getFallback 计数==0）、TestLlmConfigHelperAccountChain、TestChatServiceImplAccountRequest、TestChatOptions account 字段 — PASS
    10. design §3.6 占位已替换为裁定 A+B（不再"留给 plan"）/ §4.4 两通道运行时路径 / §6 item 6 改为"非装饰器" — PASS
  - **Anti-Hollow**：端到端调用链连通（xdef → codegen → config helper → ChatOptions 载体 → ChatServiceImpl 请求构造 → LlmCallCoordinator 两通道分流 → fail-loud），每个行为变更有对应测试断言正确结果（非仅无异常）。`TestAccountFallbackChain.quotaRoutesToAccountChainNotModelTier` getFallback 计数==0 是强力 anti-hollow 断言。
  - **构建验证**：`./mvnw test -pl nop-ai/nop-ai-core,nop-ai/nop-ai-agent -am` = 2963 tests 0 failures；`./mvnw compile`（全量）exit 0；`./mvnw clean install -DskipTests -pl nop-ai -am` BUILD SUCCESS。
  - **Deferred 项分类检查**：W2-4 ProviderFailoverQueue（跨 provider）+ 账号运行时额度熔断 均为 `out-of-scope improvement`/`optimization candidate`，无 in-scope live defect 被降级。

Follow-up:

- W2-4 ProviderFailoverQueue（跨 provider 故障转移 P1→P2→P3）消费本计划产出的分类信号 + 同 provider 账号链做跨 provider 切换（successor）。
- `CACHE_STATE_LOST` 特殊重放语义、HTTP 200 带 error body 规范化 为 Non-Blocking Follow-ups（不阻塞本计划）。no remaining plan-owned work。
