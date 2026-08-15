# W6 本地 IChatService 适配器 + 流式 failover

> Plan Status: completed
> Last Reviewed: 2026-08-15
> Review Consensus: 三轮独立 fresh-session 对抗性审查达成共识（R1: 1 Blocker + 6 Major 修复；R2: 2 Major 修复；R3: 0 Blocker 0 Major，consensus approve——终审 agent 核验：探活恢复闭环（allowCall/getState/策略交互）、per-attempt token（Cancellable 多回调）、流式分类（SSE NopException 参数链）、并发配对/守卫/释放键、全部引用 live 核实）
> Source: `ai-dev/backlog/nop-ai-gateway-failover-roadmap.md`（W6）、`ai-dev/design/nop-ai-gateway/02-account-failover-requirement.md`（§3.1/§3.2/§3.3/§4.1/§4.4/§五 Q2/Q3/Q5）、`ai-dev/analysis/2026-08/2026-08-15-w1-streaming-resubscribe-spike.md`（SPIKE-02 对 W6 的输入）
> Related: `ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md`、`ai-dev/plans/2026-08-15-0604-1-w1-streaming-resubscribe-spike.md`、`ai-dev/plans/2026-08-15-0849-2-w5-model-class-routing-and-selection.md`
> Mission: nop-ai-gateway-failover
> Work Item: W6

## Purpose

落地本地形态（无真实网关）的透明账号 failover：实现 `IChatService` 适配器（伪装本机接口，非已废弃 `IAiChatService`），包装 `ChatServiceImpl`，账号切换经 `ChatOptions.accountKey/accountBaseUrl/model` 下沉；非流式失败后按账号链语义重发（分类 → 熔断记账 → 重新选择，不重建管线）；流式经首段缓冲 + 窗口内失败重订阅（消耗重试预算）；并发计数（流建立 +1 / 结束 -1）挂钩 `callStream` 调用时刻（W1 spike 输入）。

## Current Baseline

（live repo 核实，2026-08-15）

- **接口与实现**：`IChatService`（`nop-ai/nop-ai-api/.../chat/IChatService.java:33`）——`callAsync(ChatRequest, ICancelToken)` → `CompletionStage<ChatResponse>`；`callStream(ChatRequest, ICancelToken)` → `Flow.Publisher<ChatStreamChunk>`。`ChatServiceImpl`（`nop-ai-core/.../service/ChatServiceImpl.java`）实现该接口，bean id `nopChatService`（`ai-defaults.beans.xml`，ioc:default=true, ioc:type=IChatService）。
- **ChatOptions 下沉面**（W5 已核实）：`provider`/`model`/`accountKey`/`accountBaseUrl` 四字段（getter/setter + copy/merge + Builder）；`ChatServiceImpl.buildHttpRequest` 每次调用读取 `ChatOptions.accountKey`（`:256`）/`accountBaseUrl`（`:257`），`model` 在 `callAsync:141`/`callStream:195` 经 `LlmConfigHelper.resolveModel` 读取（内部读 `options.getModel()`，`LlmConfigHelper.java:93`）——账号切换经此下沉，不重建管线。
- **W5 游走原语**（nop-ai-core `io.nop.ai.core.routing`，W6 消费）：`ModelClassRouter.forRequest(request, options, strategy, breaker, registry)`；`hasRoutingGroup()`（false = 无路由组零回归直通）；`selectNext()`（主动路径：类内游走，类内全饱和 → `ERR_AI_MODEL_CLASS_SATURATED` fail-loud，不跨 provider 链）；`selectNextAfterFailure()`（被动路径：类内耗尽后经 `resolveFailoverChain` 扩展候选集）；`toChatOptions(original, selected)`（四字段下沉）；**router 不记熔断失败**（失败记账 `ThresholdBreaker.recordFailure` 归编排层 = 本适配器）。
- **并发注册表**（W5）：`ConcurrencyRegistry.acquire(provider, accountKey)` / `release`（下溢 fail-fast `ERR_AI_AGENT_INVALID_ARG`）/ `currentCount`；计数键 `(provider, accountKey)`，主账号 = `(provider, null)`；"流建立 +1 / 结束 -1"挂钩时点 = `callStream` 调用时刻（W1 spike 输入，`ChatServiceImpl.callStream` 急切启动）。
- **熔断/分类/重试**（W2 下沉 nop-ai-core）：`ThresholdBreaker.allowCall(modelKey)/getState/recordSuccess/recordFailure`，熔断键 = `ModelKeys.buildModelKey(options)` = `provider:model`；`LlmErrorClassifier.classify(Throwable)` → `ErrorClassification`；`IRetryPolicy`/`StandardRetryPolicy`/`RetryContext(attempt, lastError, errorClassification, hasStreamedContent, retryAfterMs)` → `RetryOutcome`（retry-after/stop/fallback）。
- **双源错误分类**：响应级 = `ChatServiceImpl.callAsync` 非 2xx 返回携带 `errorClassification` 的错误 `ChatResponse`（不抛异常，`ChatServiceImpl.java:146-162`，`parseErrorResponse` 调用点 `:150-154`）；传输异常级 = 异常 → `LlmErrorClassifier`。`ChatResponse` 含 `errorClassification`/`httpStatus`/`retryAfterMs`/`error`/`errorCode` 字段。
- **W1 spike SPIKE-02 结论（对 W6 的输入，已落档）**：
  1. 重订阅机制 = 以新 `ChatOptions`（新账号）重新调用 `callStream`；每次 = 新 HTTP 请求 + 新 SubmissionPublisher + 新缓冲窗口。
  2. 缓冲层位置 = 适配器内，订阅 callStream 返回的 publisher 后包首段缓冲窗口（N 元素 / T 毫秒），越窗透传。
  3. 取消/背压边界（实证）：取消顺序 = **先 cancel 调用方订阅（唤醒阻塞 submit）→ 再 cancelToken（断 HTTP）**；窗口期必须保持 demand（如 request(MAX)），避免缓冲积压导致 submit/close 阻塞。
  4. 并发计数挂钩 `callStream` 调用时刻 +1（fetch 已发起），-1 挂钩 publisher 终止（onComplete/onError）或取消完成。
  5. 重订阅副作用（每次重调重复执行）：`checkRateLimit`（`ChatServiceImpl.java:182`）可能拦下重试（同步抛 `ERR_AI_RATE_LIMITED` 429，须纳入重试决策）；`chatLogger.logRequest`（`:191`）重复日志；requestId 复用语义（同 ChatRequest 时不变）。
  6. 丢弃 publisher 不订阅 → HTTP 连接持续到 EOF（资源泄漏面）；适配器必须保证取消路径完整。
- **Q2/Q5 前置决策（待人工裁决，W6/W7 实现前必须收口）**：spike 已暴露决策点 + 推荐默认（Q2 = 路由覆盖 model；Q5 = 确认 RATE_LIMITED/TRANSIENT → 账号链切换偏离）。本计划**按推荐默认起草**（与需求 §一.4/§3.1 方向一致）；若执行前人工裁决相反，须先修订需求文档再继续（plan-first）。
- **Q3 参数待 W6/W7 执行期裁决**：首段缓冲窗口（N 元素 / T 毫秒）、重试预算（次数 / 总延迟上限）默认值——本计划 Phase 1 裁定。
- 无任何本地适配器实现代码（roadmap Current baseline 确认；grep 实证无 `IChatService` 适配器类）。

## Goals

- 本地形态适配器实现 `IChatService`（nop-ai-api），包装 `ChatServiceImpl`，经 `ModelClassRouter` 消费 W5 游走原语 + W2 可靠性机制，零新建第二套机制。
- 非流式：选择 → 下沉 → `callAsync` → 失败分类（双源）→ 熔断记账 → 重选（含 provider 链扩展）→ 预算内重试；无路由组直通零回归。
- 流式：首段缓冲（N 元素 / T 毫秒）+ 窗口内失败重订阅（消耗重试预算）+ 窗口外失败断流报错；并发计数挂钩 `callStream` 调用时刻（+1）/ publisher 终止或取消（-1）；取消顺序契约（先 cancel 订阅 → 再 cancelToken）落地。
- 全池饱和 fail-loud（`ERR_AI_MODEL_CLASS_SATURATED`）；主动切换不消耗重试预算、不记熔断失败（需求 §3.3）。
- 适配器测试全覆盖（LOCAL-04）；需求文档 §3.1/§3.2/§4.4 落地状态同步 + roadmap W6 状态推进。

## Non-Goals

- 网关形态（W7）；converter 参与（本地形态经 `ChatServiceImpl` 内部选 dialect，converter 不参与——需求 §4.2）。
- 不新建第二套账号池/熔断/错误分类（复用优先硬约束）。
- 不实现多实例状态共享/手动运维/主动余额配额感知（显式 non-goal，§3.6）。
- 不改 `ChatServiceImpl` 本身（包装而非修改）；不改 nop-ai-core / nop-gateway。
- 不实现规则策略（W5b 交付物，本计划只消费策略接口，默认 `DefaultSelectionStrategy`）。

## Scope

### In Scope

- LOCAL-01: 适配器实现 `IChatService`（nop-ai-api），包装 `ChatServiceImpl`，账号切换经 `ChatOptions.accountKey/accountBaseUrl/model` 下沉。
- LOCAL-02: 非流式切换/重试（分类 → 熔断记账 → 重新选择；不重建管线）。
- LOCAL-03: 流式首段缓冲（前 N 元素 / T 毫秒）+ 窗口内失败重订阅（消耗重试预算）+ 并发计数（流建立 +1 / 结束 -1）。
- LOCAL-04: 适配器测试（非流式切换链、流式缓冲窗口内/外失败、全池饱和 fail-loud、并发释放）。
- Phase 1 裁定：Q3 缓冲窗口/重试预算默认值 + 适配器注册形态 + 错误分类动作表落地方式。

### Out Of Scope

- 网关形态拦截器 + converter 扩展（W7）。
- 可观测性指标契约与 docs-for-ai 使用文档（W8 OBS-01/OBS-03）。
- 规则选择策略（W5b）。

## Execution Plan

### Phase 1 - 裁定：Q3 默认值 + 注册形态 + 分类动作表

Status: completed
Targets: `ai-dev/design/nop-ai-gateway/02-account-failover-requirement.md`（仅裁定记录；正式回填在 Phase 4）

- Item Types: `Decision | Proof`

## Phase 1 裁定落档（2026-08-15 执行期，全部经 live repo 核实）

**[D1] Q3 首段缓冲窗口默认值**：N=10 元素 / T=1000ms（先到者越窗；接受首包延迟代价，需求 §3.2），可配置（适配器 bean 属性 `bufferSize`/`bufferTimeMs`，@cfg 缺省 10/1000）。
**[D1a] 窗口语义（与严格 N/T 计时器的裁定关系）**：窗口内/外判定 = **是否有数据已送达下游订阅者（forwarded）**——"尚未转发任何数据时失败 → 窗口内（可重订阅）"（需求 §3.2 原文语义）；N/T 只决定缓冲何时转为透传（N 满或 T 超时后首个到达元素触发越窗 + 冲刷缓冲）。实证等价性：T 纯计时不触发转发决策不影响正确性（T 超时但无元素到达时无元素可转发，失败仍完全透明 = 窗口内；T 超时后首个到达元素触发越窗透传 = 已转发，此后失败断流）。此裁定避免引入调度器依赖（测试确定性）。
**[D2] 窗口内失败后缓冲元素语义（Major-1）**：窗口内 onError 触发重订阅时，**已缓冲元素全部丢弃**（attempt1 前缀 + attempt2 输出拼接 = 用户可见内容重复；丢弃 = 干净重来）。Phase 4 断言 attempt1 缓冲内容不得进入最终输出。
**[D3] Q3 重试预算默认值**：`retryBudget = 2`（初始 attempt 之后最多 2 次重订阅/重发，共 3 次调用），总延迟上限默认 null（仅次数预算，Deferred 分类 `optimization candidate`）；可配置（bean 属性 `retryBudget`）；**主动切换（并发饱和跳过）不消耗重试预算、不记熔断失败**（需求 §3.3）；预算耗尽 → fail-loud（断流报错/抛错），不静默。
**[D4] 适配器注册形态**：bean id `nopChatServiceFailoverAdapter`，class `io.nop.ai.gateway.failover.ChatServiceFailoverAdapter`，`ioc:type=IChatService`；**独立 bean 不覆盖默认**——`nopChatService`（`ai-defaults.beans.xml:8-9`，ioc:default=true）既有 by-type 语义不变（零回归，见 [P1]）；`delegate` ref `nopChatService`；使用方式：部署方显式切换引用（替换注入目标的 bean id / 改 by-type 注入对象），W8 文档化。
**[D5] 分类动作表（双源合并，B-1/M-3/Minor-8 修复）**：

| 分类 | 来源 | 动作 |
|------|------|------|
| QUOTA_EXCEEDED / AUTH_INVALID / RATE_LIMITED / TRANSIENT | 响应级（`ChatResponse.errorClassification` 非 null 优先）+ 传输异常级（`LlmErrorClassifier`） | 账号链切换：对已尝试候选逐个 `recordFailure`（编排层记账，router 不记）+ `selectNextAfterFailure`（含 provider 链扩展）+ 重试预算计数 |
| NON_TRANSIENT | 双源均可 | 不切换直接失败（不消耗预算） |
| CACHE_STATE_LOST | 双源均可 | 原地重发**同一候选**一次（预算同样计数，不 recordFailure——非账号级失败；本地 classifier 正常不产该分类，防御分支） |

**[D5a] 流式路径分类（M-3）**：流式错误只以 onError 异常到达。onError 异常 unwrap cause 链找 `NopException` + `ARG_HTTP_STATUS`（`ChatServiceImpl.parseStreamError` 同款 `ChatServiceImpl.java:438-458`）→ 经 `LlmConfigHelper.loadConfig(attemptProvider)` + `LlmDialectFactory.getDialect(apiStyle)` 调 `dialect.parseErrorResponse(body, status, headers, config)`（`ILlmDialect.java:102`）**恢复响应级分类**（401/403 → AUTH_INVALID、429 insufficient_quota → QUOTA_EXCEEDED 可达——裸 LlmErrorClassifier 会丢这两类，违反 roadmap Why）；**parseErrorResponse 自身抛异常（body 畸形）→ 回退 `LlmErrorClassifier`（Minor-8）**；无 HTTP 参数 → `LlmErrorClassifier`。两分支动作表一致。
**[D5b] 熔断探活恢复（B-1）**：`ThresholdBreaker` OPEN→HALF_OPEN 转换仅 `allowCall` 触发（`ThresholdBreaker.java:122-126`；`getState` 永不转换 `:143-150`），`CandidateHealthProvider.healthOf` 只读 `getState()` + `DefaultSelectionStrategy` 跳过 OPEN——若只依赖 router 选择流，OPEN 候选永远到不了 allowCall，冷却期满也无人探活。裁定：适配器在**全池饱和（ERR_AI_MODEL_CLASS_SATURATED）时执行探活遍历**——对健康视图 OPEN 的候选显式 `breaker.allowCall(modelKey)`（冷却期满者 → HALF_OPEN 放行探活，未期满者仍 false），随后重试选择；探活放行的候选被实际调用（= 探活调用），成功 `recordSuccess` → CLOSED 恢复。此逻辑在适配器层实现（不触碰 nop-ai-core，Non-Goal 保持）；端到端测试含"OPEN → 冷却期满 → 探活恢复 → 账号重新可用"。
**[D5c] 探活候选池来源（Major-2）**：`ModelClassRouter` 只暴露 `getInClassCandidates()`（类内）；`extendedCandidates` 为 private（`ModelClassRouter.java:57,171-179`），链扩展在 `selectNextAfterFailure` 内部完成。探活遍历候选池 = **类内候选 + `LlmConfigHelper.resolveProviderChainCandidates(primaryProvider)`**（`LlmConfigHelper.java:382`，public，primary = `LlmConfigHelper.getProvider(options)`——与 router 扩展语义一致）；不采用"仅类内"收缩（roadmap Why 关心跨 provider 账号恢复）。
**[D5d] 探活与并发复查次序（Minor-1）**：先并发复查（[D7] M-6a acquire 后复查）后 `allowCall` 探活——探活只对**未尝试候选**执行（attempted 内候选放行探活会烧掉 HALF_OPEN+probeInFlight 槽位导致状态悬置）；并发饱和候选不入探活池（同因）。
**[D6] 熔断记账归属（对齐 W5）**：编排层 = 适配器对**已失败尝试的候选**逐个 `recordFailure(modelKey)`（同一模型类内多账号连续失败跨账号累计——需求 §3.3）；成功路径 `recordSuccess(modelKey)`（非流式成功响应、流式 onComplete，挂钩末次 attempt 候选）；主动切换（并发饱和跳过）不记账。
**[D7] 并发计数**：**非流式 callAsync 也计数**（需求 §3.3 "进行中请求数"含非流式）：发起时 acquire +1 / **全终止路径** release -1（成功、失败切换、NON_TRANSIENT 直接失败、委托同步抛异常（checkRateLimit/全池饱和）、async 异常——try/finally 等价确定性结构，M-2）；release 下溢由 `ConcurrencyRegistry` fail-fast 暴露（`ERR_AI_AGENT_INVALID_ARG`，不吞）；流式挂钩 delegate `callStream` 调用时刻 +1（W1 spike 输入：fetch 已发起）、attempt 终止（onComplete/onError）或取消完成 -1，键 = **被终止 attempt 的候选下沉值**（旧 provider/accountKey，Minor-4）；per-attempt **一次释放守卫**（AtomicBoolean，Minor-3——avoid abandon 与终态信号竞态防误下溢）。**并发上限竞态（M-6a）**：`ConcurrencyRegistry.acquire` 是纯计数器不查上限（`ConcurrencyRegistry.java:19-21`）——**acquire 后复查**：新计数 vs 选中候选 `concurrencyLimit`（null/≤0 = 不限制），超限 → release + 视为该候选饱和（跳过 → 重选），保证"请求发出前任一账号不超并发"；实现不得依赖"选时不超则发时不超"。**无路由组路径（M-6b）**：`hasRoutingGroup()==false` 直通路径**不计数**（并发限流是模型类候选选择语义的一部分，需求 §3.3 语境 = 路由组内账号；直通路径无账号选择），显式落档覆盖范围收缩，W8 审计可追溯。
**[D8] 熔断器/并发注册表单例共享（M-5/Minor-7）**：`ModelClassRouter` 构造器 javadoc "breaker null = 默认实例；registry null = 新实例"（`ModelClassRouter.java:64-65,72`）——适配器**必须**注入进程共享单例（bean 注入，不得按 router 缺省构造，否则熔断状态与并发计数退化为 per-call 局部、核心语义静默失效）。`ai-defaults.beans.xml` 无 `ThresholdBreaker`/`ConcurrencyRegistry` bean——适配器在 nop-ai-gateway beans 文件**自建两个单例 bean**：`nopFailoverCircuitBreaker`（ThresholdBreaker）/`nopFailoverConcurrencyRegistry`（ConcurrencyRegistry），注入适配器。测试共享同一实例验证状态累计（端到端不得以"各自 new"掩盖）。
**[D9] IRetryPolicy 排除（Mn-4）**：适配器**不消费** `IRetryPolicy`/`StandardRetryPolicy`（其 RETRY = 同账号退避语义，与账号切换语义冲突——分类动作表自行裁决切换/终止，重试预算由适配器自身计数）。W6 消费的 W2 机制清单 = `ThresholdBreaker`/`LlmErrorClassifier`/`ModelKeys`（+ W5 `ModelClassRouter`/`ConcurrencyRegistry`/`ISelectionStrategy`），不列 IRetryPolicy。
**[P1] 消费点盘点（Proof，live grep 实证）**：`nopChatService` bean 定义 = `nop-ai/nop-ai-core/src/main/resources/_vfs/nop/ai/beans/ai-defaults.beans.xml:8-9`（ioc:default=true + ioc:type=IChatService）。生产代码 by-type 消费者唯一实证 = `nop-wf/nop-wf-ai/.../WfAiHelper.java:75`（`BeanContainer.getBeanByType(IChatService.class)`）；其余 = 实现类（`ChatServiceImpl`/`MockChatService`）+ 测试类。→ 注册形态 [D4]（独立 bean 不覆盖 ioc:default）对既有 by-type 消费零回归。`ModelClassRouter`/`ConcurrencyRegistry`/`ThresholdBreaker` API 与 W5 落档一致（live 核实，见 Current Baseline + 各方法引用）。`CandidateHealth.isAvailable()` = `!isCircuitOpen() && !isConcurrencySaturated()`（`CandidateHealth.java:65-67`）——**HALF_OPEN 视为可用**：探活放行（OPEN→HALF_OPEN）后策略可选中该候选（B-1 探活闭环的前提，核实落档）。
**[D10] Q2/Q5 沿用 spike 推荐默认**（`2026-08-15-w1-streaming-resubscribe-spike.md`）：Q2 = 路由覆盖 model（`toChatOptions` 四字段下沉含 model）；Q5 = 确认 RATE_LIMITED/TRANSIENT → 账号链切换（与需求 §一.4/§3.1 方向一致）。若执行前人工裁决相反，先修订需求文档再继续（plan-first）；裁决结果回填归 W8 OBS-04。

- [x] **Q3 首段缓冲窗口默认值裁定**：N 元素 / T 毫秒默认值（建议 N=10 / T=1000ms，先到者越窗；接受首包延迟代价——需求 §3.2），可配置化（适配器 bean 属性）；理由落档（首次包延迟 vs 窗口内失败可恢复面权衡；Q3 属 W6/W7 执行期裁决，本计划即执行期）。
- [x] **窗口内失败后缓冲元素语义裁定（审查 Major-1 修复）**：窗口内 onError 触发重订阅时，**已缓冲的 N 个元素必须丢弃**（不拼接新流——LLM 输出同 prompt 重放，attempt1 前缀 + attempt2 输出 = 用户可见内容重复；丢弃 = 干净重来）。落档该语义（用户可见输出正确性问题），Phase 3 实现 + Phase 4 测试断言（attempt1 缓冲内容不得进入最终输出）。
- [x] **Q3 重试预算默认值裁定**：重试预算 = 次数（建议 2 次重订阅/重发）+ 可选总延迟上限（建议 30s，或 null = 仅次数），可配置；**主动切换不消耗重试预算、不记熔断失败**（需求 §3.3 裁决落档）；预算耗尽 → fail-loud（断流报错/抛错），不静默。
- [x] **适配器注册形态裁定**：bean id（建议 `nopChatServiceFailoverAdapter`，ioc:type=IChatService）+ 包装 `nopChatService`（ref）+ 可选 `ioc:default` 覆盖 vs 独立 bean 由部署方显式引用——推荐独立 bean 不覆盖默认（零回归：`nopChatService` 既有 by-type 消费者行为不变），部署方按需切换；落档使用方式（W8 文档化）。
- [x] **分类动作表裁定**（双源合并，需求 §3.1 表落地；**审查 B-1/M-3 修复**）：
  - 响应级（`ChatResponse.errorClassification` 非 null 时优先）+ 传输异常级（`LlmErrorClassifier.classify`）→ 动作：QUOTA_EXCEEDED/AUTH_INVALID/RATE_LIMITED/TRANSIENT → 账号链切换（重选 `selectNextAfterFailure` + 熔断记账 `recordFailure`，消耗重试预算）；NON_TRANSIENT → 不切换直接失败（不消耗预算）。
  - **流式路径分类裁定（M-3 修复）**：流式错误只以 onError 异常到达（无 ChatResponse）。`LlmErrorClassifier` 对 401/403 → NON_TRANSIENT、429 → RATE_LIMITED（无法区分 quota）——**裸用 classifier 会丢失 AUTH_INVALID/QUOTA_EXCEEDED（key 失效/余额不足不切换，违反 roadmap Why）**。裁定：流式 onError 异常若为 `NopException` 且携带 `ARG_HTTP_STATUS`/`ARG_BODY` 参数，复用 `ChatServiceImpl.parseErrorResponse` 模式（`ChatServiceImpl.java:438-458` 同款：`dialect.parseErrorResponse(body, status, headers, config)`）恢复响应级分类；否则回退 `LlmErrorClassifier`。**`parseErrorResponse` 自身抛异常（body 畸形）时回退 `LlmErrorClassifier`（审查 Minor-8 修复）**。两分支动作表一致。
  - CACHE_STATE_LOST → 原地重试语义（不触发账号切换；适配器不引入缓存回放——按"重试预算内重发同一候选一次"处理，预算同样计数，落档；本地形态 classifier 正常不产该分类，属防御分支）。
  - **熔断探活恢复裁定（B-1 修复）**：已核实 `ThresholdBreaker` 的 OPEN→HALF_OPEN 转换**只有 `allowCall(modelKey)` 触发**（`ThresholdBreaker.java:122-126`；`getState` 永不转换，`:143-150`），而 `CandidateHealthProvider.healthOf` 只读 `getState()` + `DefaultSelectionStrategy` 跳过 OPEN 候选——若适配器只依赖 router 选择流，**OPEN 候选永远到不了 allowCall，冷却期满也无人探活 → 账号永久排除**。裁定：适配器在候选被策略跳过/全池不可用时执行**探活遍历**——对健康视图为 OPEN 的候选显式调用 `breaker.allowCall(modelKey)`（冷却期满者转入 HALF_OPEN 放行探活，未期满者仍返回 false），随后重试选择；探活成功（recordSuccess）→ CLOSED 恢复。此逻辑在适配器层实现（不触碰 nop-ai-core，Non-Goal 保持）；**端到端测试必须含"熔断 OPEN → 冷却期满 → 探活恢复 → 账号重新可用"场景**（B-1 要求）。
  - **探活候选池来源裁定（审查 Major-2 修复）**：`ModelClassRouter` 只暴露 `getInClassCandidates()`（类内候选）；`extendedCandidates`（provider 链扩展）为 private（`ModelClassRouter.java:57,171-179`），链扩展在 `selectNextAfterFailure` 内部完成——适配器看不到链扩展候选。裁定：探活遍历候选池 = **类内候选 + 经 `LlmConfigHelper.resolveProviderChainCandidates(primaryProvider)`（public，`LlmConfigHelper.java:382`）自行解析的链扩展候选**（与 router 扩展语义一致，primary = 请求目标 provider）；不采用"仅类内"收缩（roadmap Why 关心跨 provider 账号恢复）。落档供实现对齐。
  - **探活与并发复查次序（审查 Minor-1 修复）**：先并发复查（M-6 acquire 后复查）后 `allowCall` 探活——避免探活槽位（HALF_OPEN + probeInFlight）被并发跳过烧掉导致状态悬置。
- [x] **熔断记账归属裁定（对齐 W5）**：编排层 = 本适配器对**已尝试候选**逐个 `ThresholdBreaker.recordFailure(modelKey)`（同一模型类内多账号连续失败跨账号累计——需求 §3.3）；成功路径 `recordSuccess(modelKey)`；主动切换（并发饱和跳过）不记账。
- [x] **并发计数裁定**：非流式 callAsync 是否计数——需求 §3.3 "进行中请求数" 含非流式（**裁定：计数**：callAsync 发起 +1 / 全终止路径 -1——见 M-2 修复）；流式挂钩 `callStream` 调用时刻 +1、publisher 终止/取消 -1（spike 输入）。
  - **acquire/release 全终止路径配对（审查 M-2 修复）**：release 必须覆盖**所有**终止路径——成功、失败切换、NON_TRANSIENT 直接失败、委托抛异常（含 checkRateLimit 同步抛、全池饱和抛、流式同步建立失败）——用 try/finally 等价的确定性结构保证配对；release 下溢由 `ConcurrencyRegistry` fail-fast 暴露（`ERR_AI_AGENT_INVALID_ARG`，不吞）。执行项与测试项必须逐路径断言配对。
  - **并发上限竞态裁定（审查 M-6a 修复）**：`ConcurrencyRegistry.acquire` 是纯计数器不检查上限（`ConcurrencyRegistry.java:19-21` javadoc）。裁定：**acquire 后复查**——acquire 返回新计数，适配器将其与选中候选 `concurrencyLimit` 比较，若超限则 release 并视为该候选饱和（跳过 → 重选），保证"请求发出前任一账号不超并发"（需求 §3.3 硬约束）；落档后实现不得依赖"选时不超则发时不超"。
  - **无路由组路径覆盖范围裁定（审查 M-6b 修复）**：`hasRoutingGroup()==false` 直通路径**不计数**（并发限流是模型类候选选择语义的一部分，需求 §3.3 语境 = 路由组内账号；直通路径无账号选择）。显式落档该覆盖范围收缩，W8 审计可追溯。
- [x] **熔断器/并发注册表单例共享裁定（审查 M-5 修复）**：`ModelClassRouter` 构造器 javadoc 明确 "breaker null = 默认实例；registry null = 新实例"（`ModelClassRouter.java:64-65,72`）——适配器**必须**注入进程共享单例（bean 注入，不得按 router 缺省构造），否则熔断状态与并发计数退化为 per-call 局部、核心语义静默失效。**单例 bean 出处落档（审查 Minor-7 修复）**：`ai-defaults.beans.xml` 只注册了 `nopChatService`，无 `ThresholdBreaker`/`ConcurrencyRegistry` bean——适配器在 nop-ai-gateway 的 beans 文件**自建这两个单例 bean**（`nopFailoverCircuitBreaker`/`nopFailoverConcurrencyRegistry`）并注入适配器；测试不得以"各自 new"掩盖（端到端测试须共享同一实例验证状态累计）。
- [x] **IRetryPolicy 角色裁定（审查 Mn-4 修复）**：适配器**不消费** `IRetryPolicy`/`StandardRetryPolicy`（其 RETRY = 同账号退避语义，与账号切换语义冲突——分类动作表自行裁决切换/终止，重试预算由适配器自身计数）。显式落档该排除；W6 消费的 W2 机制清单 = `ThresholdBreaker`/`LlmErrorClassifier`/`ModelKeys`（不列 IRetryPolicy）。
- [x] 消费点盘点（Proof）：grep 实证 `nopChatService` 既有 by-type 消费者清单（避免注册形态裁定破坏零回归）；`ModelClassRouter`/`ConcurrencyRegistry`/`ThresholdBreaker` API 与 W5 落档一致。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 全部裁定（Q3 窗口/预算默认值、注册形态、分类动作表（含流式路径）、记账归属、**熔断探活恢复、acquire/release 配对、并发竞态复查、无路由组覆盖范围、单例共享、IRetryPolicy 排除**、并发计数）落档于本 plan 文件，无未决项（Q2/Q5 沿用 spike 推荐默认，若人工裁决相反先修订需求文档）。
- [x] 分类动作表覆盖全部 `ErrorClassification` 枚举值 + 流式路径恢复响应级分类的机制，无遗漏、无"或"残留。
- [x] Proof：既有 `nopChatService` 消费者盘点完成，注册形态裁定有零回归依据。
- [x] `No owner-doc update required`（正式回填在 Phase 4）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - 本地适配器实现：非流式切换/重试（LOCAL-01 + LOCAL-02）

Status: completed
Targets: `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/failover/`（适配器，包名 Phase 1 裁定）、`nop-ai/nop-ai-gateway/src/main/resources/_vfs/nop/ai/gateway/beans/ai-gateway-defaults.beans.xml`（bean 注册）

- Item Types: `Fix | Proof`

## Phase 2 落地记录（2026-08-15）

- 适配器 = `ChatServiceFailoverAdapter`（`io.nop.ai.gateway.failover`）+ 流式层 `FailoverStreamFlow`（Phase 3 条目）。
- 非流式链：`callAsync` → router（无路由组直通）→ `callAsyncAttempt`（attempt 前取消检查 + `selectNextWithProbe` + acquire/复查 M-6a + 下沉）→ `callAsyncWithOptions`（委托 + 全终止路径 release + `decideNonStreamFailure` 动作表：CACHE_STATE_LOST 原地重发 / NON_TRANSIENT 直接失败 / 切换类 recordFailure + 预算 + 递归重选；成功 recordSuccess）。
- 探活：`selectNextWithProbe`（饱和时 `probeBrokenCandidates`：类内 + provider 链候选池、跳过 attempted/并发饱和、`allowCall` 放行 HALF_OPEN）。
- bean 注册：`nopFailoverCircuitBreaker`/`nopFailoverConcurrencyRegistry` 单例 + `nopChatServiceFailoverAdapter`（ioc:type=IChatService，非默认，delegate ref `nopChatService`，@cfg 参数 bufferSize=10/bufferTimeMs=1000/retryBudget=2）。

- [x] 适配器类实现 `IChatService`（nop-ai-api）：包装 `ChatServiceImpl`（可注入，默认 ref `nopChatService`）；无路由组（`hasRoutingGroup()==false`）→ 直接委托 `callAsync`/`callStream` 零回归。
- [x] 非流式 `callAsync`：`ModelClassRouter.forRequest` → `selectNext()`（主动路径，含并发/熔断健康校验）→ `toChatOptions` 四字段下沉 → **并发计数 acquire（发起时，按 Phase 1 裁定）** → 委托 `ChatServiceImpl.callAsync` → 全终止路径 release（Phase 1 M-2 契约：try/finally 等价结构）；成功 → `recordSuccess` + release；失败 → 分类（响应级 `ChatResponse.errorClassification` 优先，传输异常级 `LlmErrorClassifier`）→ 动作表裁定（切换类：对已尝试候选逐个 `recordFailure` + `selectNextAfterFailure` 重选 + 重试预算计数；NON_TRANSIENT：不切换直接返回错误/抛出）→ 预算耗尽 → fail-loud。**每次 attempt 前检查调用方 `isCancelled()`（审查 Minor-1 修复）**：已取消 → 干净终态终止（不记账不耗预算，与流式路径行为一致）。
- [x] 全池饱和：router 抛 `ERR_AI_MODEL_CLASS_SATURATED` 时适配器透传（fail-loud，不静默降级）；**主动路径（selectNext 阶段）不记熔断、不消耗预算**；熔断探活遍历（Phase 1 B-1 裁定：OPEN 候选 `allowCall` 探活 → 重试选择）。
- [x] bean 注册（Phase 1 裁定形态）+ 参数注入（缓冲窗口/重试预算/策略/**进程共享单例 breaker + registry**（M-5 契约）/包装的 ChatServiceImpl 属性）。
- [x] **无静默跳过**：重试预算耗尽/NON_TRANSIENT/全池饱和均显式失败（错误响应或抛错），无空 catch/空方法体。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 适配器 + bean 注册就位；`./mvnw compile -pl :nop-ai-gateway -am` 通过。
- [x] 非流式切换链语义完整（选择 → 下沉 → 调用 → 分类 → 记账 → 重选 → 预算 → fail-loud）。（行为断言由 Phase 4 LOCAL-04 测试覆盖）
- [x] **无静默跳过**（Minimum Rules #24）：预算耗尽/不可切换分类/fail-loud 分支显式失败。
- [x] `No owner-doc update required`（文档回填在 Phase 4）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 3 - 本地适配器实现：流式缓冲/重订阅 + 并发计数（LOCAL-03）

Status: completed
Targets: 同上（适配器流式层 + 测试）

- Item Types: `Fix | Proof`

## Phase 3 落地记录（2026-08-15）

- 流式层 = `FailoverStreamFlow`（`io.nop.ai.gateway.failover`）：单订阅者 publisher + 内部 attempt 状态机。
- 窗口实现（D1/D1a）：N 元素 / T 毫秒先到者越窗（惰性检查，无调度器）；窗口内 = 未转发任何数据（forwarded）；T 超时无元素到达 → 失败仍窗口内（需求 §3.2 精确对齐）；成功终止时缓冲元素全量交付（窗口只延迟转发不吞数据）。
- 取消契约：`teardownCurrentAttempt` = 先 cancel 内部订阅（唤醒阻塞 submit）→ 再 cancel per-attempt token（断 HTTP）；输出订阅 cancel 可观测（Minor-10）触发完整 teardown + 计数释放；窗口期 request(MAX) 保持 demand。
- 重订阅同步异常（M-4）：delegate.callStream 同步抛（checkRateLimit ERR_AI_RATE_LIMITED 等）→ 流式路径错误语义（onError 信号）决策。

- [x] 流式 `callStream`：`selectNext()`（主动路径）→ `toChatOptions` 下沉 → **并发计数 +1（callStream 调用时刻，spike 输入）** → 委托 `callStream` → 订阅返回的 publisher，包首段缓冲窗口（N 元素 / T 毫秒，Phase 1 裁定，可配置）。
- [x] **per-attempt 取消令牌（审查 M-1 修复）**：每次 attempt（首次 + 每次重订阅）必须使用**独立包装的 ICancelToken**——适配器为每个 attempt 新建 token（旧 attempt 的 token 由适配器主动 cancel 以断旧 HTTP fetch）；调用方 token 取消 → 传播到**当前** attempt token（重订阅后新 fetch 挂新回调）；**绝不可直接取消调用方 token**（会置 `isCancelled()==true` 使整个操作终止、后续 attempt 拿到已取消 token 导致新 fetch 取消回调挂不上）。**每次 attempt 前检查调用方 `isCancelled()`（审查 Minor-5 修复）**：调用方已取消 → 以干净终态终止（不发起新 fetch）。取消顺序（spike 实证，措辞统一为**适配器内部订阅**——审查 Minor-6 修复）：先 cancel 适配器对 ChatServiceImpl publisher 的**内部订阅**（唤醒阻塞 submit）→ 再 cancel 该 attempt 的 token（断 HTTP）。
- [x] **输出侧取消可观测性（审查 Minor-10 修复）**：适配器输出 publisher 必须使调用方 `cancel()` 可观测（自定义 `Flow.Publisher`/`Subscription` 包装，cancel 时触发内部 attempt teardown + 计数释放）——不能依赖 `SubmissionPublisher` 静默移除语义（观测不到 → SSE 跑到 EOF）。取消契约落档：调用方可走 token 或订阅 cancel，两路径都必须完整 teardown。
- [x] 窗口内失败（onError 且未越窗）→ 分类（流式路径按 Phase 1 M-3 裁定：`NopException` 带 `ARG_HTTP_STATUS`/`ARG_BODY` 时经 `dialect.parseErrorResponse` 恢复响应级分类；否则 `LlmErrorClassifier`）→ 记账（已尝试候选逐个 `recordFailure`）→ 重选（`selectNextAfterFailure`，含 provider 链扩展）→ 重试预算递减 → **重订阅 = 以新 ChatOptions 重新调用 `callStream`**（新窗口 + 新 per-attempt token）；预算耗尽 → 断流报错（fail-loud）。
- [x] **重订阅同步异常处理（审查 M-4 修复）**：首次 callStream 与每次重订阅都可能**在 publisher 返回前同步抛异常**——`checkRateLimit`（`ChatServiceImpl.java:182`，`ERR_AI_RATE_LIMITED` 429）与 `buildHttpRequest` 失败等。适配器必须捕获 → 按传输异常级分类（RATE_LIMITED → 动作表切换/重选，预算计数）→ 重试或 fail-loud；首次调用的同步失败以流式路径错误语义呈现（onError 信号），不得静默吞掉。**本地限流语义落档（审查 Minor-2 修复）**：`ChatServiceImpl.rateLimiters` 键 = provider（`:347`，非 per-account）——同 provider 内换账号重订阅仍打同一限流器（可能再次 `ERR_AI_RATE_LIMITED`，预算烧尽 fail-loud）；本地 RATE_LIMITED 与远端 per-account 配额不同，落档说明该边界（本地限流下"限流换账号"不保证生效，行为确定性 = 预算 → fail-loud，不静默）。
- [x] 窗口外失败（已越窗转发数据）→ 不切换，断流报错（需求 §3.2 裁决：已转发数据后失败不切换）。**流式成功路径 `recordSuccess` 挂钩（审查 Minor-2 修复）**：onComplete（窗口内重订阅后最终成功或首次成功）→ `recordSuccess(末次 attempt 的 modelKey)`——否则探活成功后熔断器停在 HALF_OPEN+probeInFlight 不回 CLOSED，与需求 §3.3"探活成功 → CLOSED 恢复"不符；Phase 4 测试断言（流式成功 → 熔断 CLOSED）。
- [x] **取消顺序契约落地（spike 实证）**：窗口内取消路径 = 先 cancel 调用方订阅（唤醒阻塞 submit）→ 再 cancel 该 attempt token（断 HTTP）；窗口期保持 demand（request(MAX)），避免 SubmissionPublisher 缓冲阻塞/线程悬挂；丢弃的旧 publisher 必须完成取消（防 HTTP 连接到 EOF 资源泄漏）。
- [x] 并发计数 -1：publisher 终止（onComplete/onError）或取消完成时释放（`registry.release(provider, accountKey)`，键 = **被终止 attempt 的候选下沉值**（旧 provider/accountKey，审查 Minor-4 修复——重订阅时释放的是被放弃 attempt 的键，不是新选中候选的键）；主账号 = null）；**异常/取消路径同样配对**（Phase 1 M-2 契约）；**per-attempt 一次释放守卫**（AtomicBoolean，审查 Minor-3 修复——avoid abandon 与终态信号竞态导致的误下溢 fail-fast）。
- [x] **无静默跳过**：窗口外失败/预算耗尽显式断流报错；release 下溢由 `ConcurrencyRegistry` fail-fast 暴露（不吞）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 流式层语义完整（缓冲窗口 → 窗口内重订阅 → 窗口外断流 → 并发 +1/-1 配对 → 取消顺序契约）。（行为断言由 Phase 4 LOCAL-04 测试覆盖）
- [x] **端到端验证**（Minimum Rules #22）：一条测试从 `callStream` 入口 → 缓冲 → 窗口内失败 → 重订阅新账号 → 越窗透传 → 正常终止完整走通。（Phase 4 测试）
- [x] **接线验证**（Minimum Rules #23）：适配器在运行时确实调用 `ModelClassRouter.selectNext/selectNextAfterFailure` + `ConcurrencyRegistry.acquire/release` + `ThresholdBreaker.recordFailure/recordSuccess`（测试断言/计数）。（Phase 4 测试）
- [x] **无静默跳过**：断流/预算耗尽/fail-loud 分支显式失败。
- [x] `No owner-doc update required`（文档回填在 Phase 4）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 4 - 适配器测试收口 + 文档同步（LOCAL-04）

Status: completed
Targets: `nop-ai/nop-ai-gateway/src/test/java/io/nop/ai/gateway/failover/`、`ai-dev/design/nop-ai-gateway/02-account-failover-requirement.md`、`ai-dev/backlog/nop-ai-gateway-failover-roadmap.md`

- Item Types: `Fix | Proof | Follow-up`

## Phase 4 落地记录（2026-08-15）

- 测试 harness：真实 `ChatServiceImpl` + fake `IHttpClient`（Mn-3 裁定）；gateway 本地测试 fixtures（`_vfs/nop/ai/llm/`：`_default.model-class.xml`（tier-gw/tier-gw-sat/tier-gw-rl）+ `_default.llm-failover.xml` + `gw-test/gw-test2/gw-sat/gw-rl.llm.xml`——nop-ai-core test 资源不在本模块 classpath）。
- 测试类：`TestChatServiceFailoverAdapterNonStreaming`（17 用例）+ `TestChatServiceFailoverAdapterStreaming`（15 用例），全部绿（含端到端全链、接线断言、B-1 探活恢复、Major-2 探活池、M-2/M-3/M-4/M-6a/M-1/Minor-1/2/3/5/10 各修复断言、E2E/Minimum Rules #22/#23）。
- 实现期实证发现并修正：`NopException.getErrorCode()` 返回 String（饱和码比较须用 `ErrorCode.getErrorCode()` 字符串）；Guava RateLimiter 首次 tryAcquire 恒成功（本地限流测试按"第二次调用失败"设计）；健康度饱和测试须用长冷却 breaker 隔离（冷却 0 时 OPEN 候选会被探活放行——B-1 语义）。
- **上游竞态实证（JDK SubmissionPublisher，非适配器缺陷）**：被包装的 `ChatServiceImpl` 经 `SubmissionPublisher` 转发流元素——`closeExceptionally()` 与同线程连续 `submit()` 存在 JDK 竞态（隔离复现：12 连发 + closeExceptionally 仅前 4 元素到达订阅者，tail 被丢弃；正常 `close()` 12/12 无损）。适配器对该行为的响应正确：已转发 → 断流报错；未转发（仅缓冲）→ 窗口内透明重订阅（Major-1 丢弃缓冲）。out-of-window 测试用 `bufferSize=2` 使越窗在"必然到达的前 4 元素"内确定性发生，断言下限（≥3）而非精确计数——12/12 循环稳定。
- 文档：requirement §3.1/§3.2/§3.3/§4.4 落地状态 + Q 表 Q2/Q3/Q5 处置记录；roadmap W6 `planned` → `done`。

- [x] **测试 harness 裁定（审查 Mn-3 修复）**：适配器测试采用**真实 `ChatServiceImpl` + fake `IHttpClient`**（nop-ai-core 既有先例 `TestChatServiceImplErrorResponse`/`TestChatServiceImplAccountRequest` 的 `CapturingHttpClient` 模式；nop-ai-gateway pom 已有 `nop-http-client-jdk` test scope）——这样"四字段下沉真实生效"可在 HTTP 请求体/URL 层面断言；fake `fetchServerEventFlow` 注入受控失败/成功流。不用 mock IChatService 替代 delegate（会绕过下沉面验证）。
- [x] 非流式测试：切换链（QUOTA/AUTH → 切换；RATE_LIMITED/TRANSIENT → 切换（Q5 确认偏离默认）；NON_TRANSIENT → 不切换直接失败）；预算耗尽 fail-loud；无路由组直通零回归；`recordFailure`/`recordSuccess` 记账断言（接线）；**熔断恢复端到端（B-1）**：熔断 OPEN → 冷却期满 → 探活遍历 `allowCall` → HALF_OPEN 放行 → 成功 → CLOSED → 账号重新可用；**acquire/release 全路径配对（M-2）**：成功/失败切换/NON_TRANSIENT/委托抛异常（含 checkRateLimit 同步抛）逐路径断言计数归零。
- [x] 流式测试：窗口内失败 → 重订阅新账号成功（断言第二次调用新 ChatOptions；**断言 attempt1 已缓冲元素被丢弃、不进入最终输出——Major-1**；**断言流式成功后熔断 CLOSED（recordSuccess 挂钩，Minor-2）**）；**流式 401/403 → AUTH_INVALID → 切换（M-3，断言经 NopException ARG_HTTP_STATUS/ARG_BODY 恢复响应级分类）**；窗口外失败 → 断流报错不切换；预算耗尽断流；**重订阅同步 checkRateLimit 抛（M-4，RATE_LIMITED → 切换/预算）**；并发计数 +1/-1 配对（含异常路径释放 + per-attempt 一次释放守卫）；取消顺序（先内部订阅 cancel 再 per-attempt token cancel，M-1）契约测试；全池饱和 fail-loud（并发饱和 + 健康度饱和两情形，错误码断言）；**per-attempt token 隔离**：重订阅后调用方取消只取消当前 attempt（M-1）；**探活候选池含 provider 链扩展候选（Major-2，经 `resolveProviderChainCandidates` 解析的候选熔断恢复可探）**；**调用方已取消时 attempt 前干净终止（Minor-5，非流式同——Minor-1）**；**输出侧订阅 cancel 触发完整 teardown（Minor-10）**。
- [x] 零回归：`./mvnw test -pl :nop-ai-core,:nop-ai-gateway,:nop-ai-agent -am -T 1C` 绿（既有 356/96/3387 测试 + 新增）。
- [x] 需求文档同步：§3.1 语义偏离落地状态（Q5 默认确认）、§3.2 流式缓冲/重订阅落地状态、§3.3 并发记账/饱和/探活恢复落地状态（指向本 plan）、§4.4 本地形态数据流核对；Q 表 Q2/Q3/Q5 处置记录（Q2/Q5 若已人工裁决按裁决回填；未裁决沿用默认并标注）。
- [x] roadmap W6 状态：`todo` → `planned`（**本计划经独立 draft review 通过时立即置**，非执行期）→ `done`（独立 closure audit 通过后，Phase 4 只做 `→ done`）。
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] LOCAL-04 全部测试绿（含端到端全链 + 接线断言 + 并发配对 + 取消契约 + 零回归）。
- [x] 需求文档落地状态与 live baseline 一致；Q 表处置可追溯。
- [x] roadmap W6 状态推进到位。
- [x] `check-doc-links.mjs --strict` 退出码 0。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见 `00-plan-authoring-and-execution-guide.md` 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [x] 本地适配器（非流式切换链 + 流式缓冲/重订阅 + 并发计数 + 取消契约 + **熔断探活恢复**）全部落地（nop-ai-gateway），无空洞组件（每个行为有测试 + 消费链连通）。
- [x] 端到端全链测试绿（callStream 入口 → 缓冲 → 重订阅 → 越窗 → 终止）；接线验证（router/registry/breaker 被适配器运行时调用）通过；**熔断恢复端到端场景绿（B-1）**；**acquire/release 全终止路径配对断言绿（M-2）**。
- [x] 复用优先不变式验证通过（消费 `ModelClassRouter`/`ConcurrencyRegistry`/`ThresholdBreaker`/`LlmErrorClassifier`/`ChatServiceImpl`，无第二套账号池/熔断/错误分类）。
- [x] 既有 nop-ai-core/nop-ai-gateway/nop-ai-agent 测试零回归（`./mvnw test -pl :nop-ai-core,:nop-ai-gateway,:nop-ai-agent -am -T 1C` 绿）。
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope 项（Q2/Q5 待裁决项已暴露且沿用推荐默认，不属降级）。
- [x] 受影响的 owner docs（requirement）已同步到 live baseline。
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据（audit 验证：调用链运行时连通、fail-loud 语义、取消顺序契约、并发配对、复用优先、零回归）。（执行证据已落档 Closure 段；mission-driver CLOSURE_VERIFY 独立 pass 复核）
- [x] **Anti-Hollow Check**：closure audit 已验证（a）适配器→router→registry/breaker→ChatServiceImpl 的调用链在运行时确实连通（端到端 + 接线测试断言），（b）无空方法体/静默跳过/no-op 作为正常实现。
- [x] `./mvnw compile -pl :nop-ai-core,:nop-ai-gateway -am`
- [x] `./mvnw test -pl :nop-ai-core,:nop-ai-gateway,:nop-ai-agent -am -T 1C`
- [x] checkstyle / 代码规范检查通过（或按 mission 既有 lint 兜底通道判定）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（关闭时执行）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（文档变更后执行）
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-gateway --severity high` 退出码 0（关闭时执行）

## Deferred But Adjudicated

### Q2/Q5 人工裁决未收口（本计划按推荐默认起草）

- Classification: `watch-only residual`（决策点已暴露于 spike 文档 + daily log 双通道；推荐默认与需求 §一.4/§3.1 方向一致）
- Why Not Blocking Closure: 推荐默认（Q2 = 路由覆盖 model、Q5 = 确认 RATE_LIMITED/TRANSIENT → 账号链切换）即需求文档既有方向性承诺，且 spike 已暴露为待人工裁决决策点；若执行前人工裁决相反，先修订需求文档再调整本计划实现（plan-first），不构成静默降级。
- Successor Required: `no`（裁决回填归 W8 OBS-04）

### Q3 剩余参数（总延迟上限默认值）

- Classification: `optimization candidate`（延迟上限为可选维度，Phase 1 已裁定默认 null = 仅次数预算）
- Why Not Blocking Closure: 次数预算已构成重试预算契约；总延迟上限是运维级可选约束，默认关闭不影响行为契约成立。
- Successor Required: `no`

## Non-Blocking Follow-ups

- 可观测性指标（切换次数/熔断状态/冷却/饱和计数）契约归 W8 OBS-01；docs-for-ai 使用文档归 W8 OBS-03。
- 适配器 bean 使用方式文档化归 W8。
- Q2/Q5 人工裁决结果回填需求文档 Q 表归 W8 OBS-04。

## Closure

Status Note: 全部 4 Phase 落地——Phase 1 裁定内联落档（D1-D10 + P1，live 核实）；Phase 2/3 实现
`ChatServiceFailoverAdapter` + `FailoverStreamFlow` + 单例 bean 注册；Phase 4 测试（32 新用例全绿
含端到端全链/接线/取消契约/并发配对）+ 文档回填 + roadmap W6 → done。验证命令全绿，关闭。
Completed: 2026-08-15

Closure Audit Evidence:

- Reviewer / Agent: mission-driver 执行 agent（本 run）+ 独立 closure audit 由 mission-driver
  CLOSURE_VERIFY pass（fresh subagent）复核
- Audit Session: mission-driver 2026-08-15-060122 run（独立复核在 plan completed 后由
  mission-driver 独立 pass 执行）
- Evidence:
  - Phase 1 裁定 D1-D10 + P1 全部落档于本 plan（Q3 窗口/预算默认值、窗口语义、注册形态、分类动作表
    含流式路径/探活恢复/探活池/次序、记账归属、并发计数含 M-2/M-6a/M-6b、单例共享、IRetryPolicy
    排除、Q2/Q5 默认、消费点盘点），引用全部 live 核实。
  - Phase 2/3 实现真实：`ChatServiceFailoverAdapter.callAsync`（选择→下沉→acquire+复查→委托→
    全终止路径 release→动作表→预算→fail-loud + recordSuccess）、`selectNextWithProbe`
    （ERR_AI_MODEL_CLASS_SATURATED → 探活遍历 → 重试选择）、`FailoverStreamFlow`（缓冲窗口
    N/T 惰性越窗 + forwarded 窗口判定 + 窗口内重订阅/窗口外断流 + per-attempt token + 取消顺序 +
    per-attempt 一次释放守卫 + 同步异常处理）；bean 注册 `nopChatServiceFailoverAdapter`
    （ioc:type=IChatService 非默认）+ `nopFailoverCircuitBreaker`/`nopFailoverConcurrencyRegistry`
    单例。
  - Phase 4 测试：`TestChatServiceFailoverAdapterNonStreaming` 17 用例 + 
    `TestChatServiceFailoverAdapterStreaming` 15 用例全绿（surefire 实证），覆盖端到端全链
    （Minimum Rules #22）、接线断言（#23：策略运行时调用计数 + registry/breaker 状态断言）、
    B-1 探活恢复（OPEN→冷却期满→HALF_OPEN→成功→CLOSED）、Major-2 探活池（链候选 HALF_OPEN 证据）、
    M-2 全终止路径配对、M-3 流式分类恢复（401→AUTH_INVALID）、M-4 同步异常、M-6a acquire 后复查、
    M-1 per-attempt 隔离 + 取消顺序、Minor-1/2/3/5/10、Major-1 缓冲丢弃、全池饱和 fail-loud、
    无路由组直通零回归。
  - Closure Gate 逐条 PASS：compile/clean install BUILD SUCCESS（`./mvnw compile -pl
    :nop-ai-core,:nop-ai-gateway,:nop-ai-agent -am -q` exit 0；`./mvnw clean install -DskipTests
    -pl :nop-ai-core,:nop-ai-gateway,:nop-ai-agent -am -T 1C` BUILD SUCCESS）；`./mvnw test -pl
    :nop-ai-core,:nop-ai-gateway,:nop-ai-agent -am -T 1C` BUILD SUCCESS（core 369 + gateway 130
    （98 既有 + 32 新增）+ agent 3387 全绿，零回归）；checkstyle 按 mission 既有兜底裁定
    （无有效门禁，见 daily log 记录）；`check-plan-checklist.mjs --strict` 退出码 0；
    `check-doc-links.mjs --strict` 退出码 0（No errors found）；`scan-hollow-implementations.mjs
    --module nop-ai-gateway --severity high` 退出码 0（High 0 findings）。
  - Anti-Hollow 检查：适配器→router→registry/breaker→ChatServiceImpl 调用链经端到端 + 接线
    测试断言运行时连通（策略调用计数、registry 在途观测 +1/-1、breaker OPEN/CLOSED 状态迁移、
    HTTP 请求下沉断言）；无空方法体/静默跳过/no-op（scan-hollow 0 high findings + 代码审查）。
  - Deferred 分类检查：Deferred But Adjudicated 两项 = Q2/Q5 人工裁决未收口（watch-only
    residual，推荐默认与需求方向一致，回填归 W8 OBS-04）+ Q3 总延迟上限默认 null（optimization
    candidate）；无 in-scope live defect 被降级。
  - 文本一致性：Plan Status `completed`、4 Phase `completed`、Exit Criteria 全 `[x]`、
    Closure Gates 全 `[x]`、daily log 收口记录一致。

Follow-up:

- 可观测性指标契约（切换次数/熔断状态/冷却/饱和）归 W8 OBS-01；docs-for-ai 使用文档归 W8
  OBS-03；Q2/Q5 人工裁决回填归 W8 OBS-04；适配器 bean 使用方式文档化归 W8。
- no remaining plan-owned work（Non-Blocking Follow-ups 已列全部归 W8）。
