# 2 P2 可靠性/可观测性批次（MA6.3 + MA6.1 + MA5.6 可靠性类 P2）

> Plan Status: active
> Last Reviewed: 2026-07-31
> Source: `ai-dev/backlog/audit-remediation-roadmap.md` §P2/P3 Deferred Successors（watch-only residual，按严重度排序另行规划）、`ai-dev/audits/2026-07-31-0000-arm-MA6.3-nop-ai-token-reliability.md`、`2026-07-31-1240-arm-MA6.1-nop-ai-llm-config-security.md`、`2026-07-31-arm-MA5.6-nop-ai-test-isolation.md`
> Related: `ai-dev/plans/2026-07-31-1834-1-arm-p2-security-hardening.md`（安全批次，独立无依赖）、`ai-dev/plans/2026-07-31-1300-5-arm-mr3-fix.md`

## Purpose

修复 MA6.3（token 计量与 LLM 调用可靠性）、MA6.1（LLM 配置安全）、MA5.6（测试隔离）审计遗留的**可靠性/可观测性类 P2 finding**（均经 live repo 复核仍成立）：重试退避无 jitter、usage recorder 默认 no-op、限流无 tryAcquire、token 估算粗糙、LlmConfigHelper 静态可变状态、测试类共享静态可变状态。这是 roadmap 规则 1 下 P2/P3 deferred successor 的第四批，按严重度排序承接 watch-only residual。

## Current Baseline

- MR3 已修复 P1-MA6.3-001（`ChatServiceImpl` 超时）与 P1-MA6.3-002（`StandardRetryPolicy`/`ThresholdBreaker` 成为默认装配）。
- **MA6.3-AR-5（live 确认）**：`nop-ai-agent/.../reliability/StandardRetryPolicy.java:131-148` `computeBackoff()` 纯确定性指数退避 `min(baseDelayMs * 2^attempt, maxDelayMs)`，无 jitter 无随机化。**注意**：`TestStandardRetryPolicy.java:135-165` 有 9 处精确延迟断言（:135/138/141/144/152/153/154/156/163），任何 jitter 都会破坏这些断言——本计划 Phase 1 必须同步改写为区间断言。
- **MA6.3-AR-4（live 确认）**：`ReActAgentExecutor.java:425` `usageRecorder != null ? usageRecorder : NoOpUsageRecorder.noOp()`；`NoOpUsageRecorder` 的 `record()` 空实现静默丢弃 UsageRecord；`DbUsageRecorder` 存在但需显式装配；无启动 WARN。`nop-ai-agent/.../usage/` 下现有 `DbUsageRecorder`/`IUsageRecorder`/`NoOpUsageRecorder`/`NopAiChatResponseTable`。**既有 `TestUsageRecorderWiring`（引擎级，断言 record 每 LLM 调用被调用 + 字段正确）已覆盖接线**——本计划引用而非重复。
- **MA6.3-AR-6（live 复核修正）**：`ChatServiceImpl.java:240-258` `checkRateLimit` 每 provider 一个 `DefaultRateLimiter`（in-memory token bucket），调用 `rateLimiter.acquire()`（**阻塞式**）。**audit 声称"无 tryAcquire()"不准确**：`nop-kernel/nop-commons/.../concurrent/ratelimit/IRateLimiter.java:23-38` 已提供 `tryAcquire(int,long)`/`tryAcquire(int)`/`tryAcquire()`（`acquire()` 是 `tryAcquire(1, Long.MAX_VALUE)` 的默认方法）。真实缺口是：(a) `ChatServiceImpl` 用无限阻塞 `acquire()` 而非限时 `tryAcquire`；(b) 失败面语义未定义（耗尽时抛什么错误、如何与 `LlmErrorClassifier` 联动）；(c) 无 per-tenant 配额；(d) `DefaultAiChatService.java:105-118`（废弃类）同样阻塞限流。
- **MA6.3-AR-3（live 确认）**：`ILlmDialect.java:161-182` `estimateTokensDefault` = `chars/4` 启发式；`CalibratedTokenEstimator` 单 EMA factor 覆盖所有 ApiStyle（`MAX_FACTOR=4.0` 仅为 EMA 钳位上限，未校准时实际低估可远超 4x）；`ITokenCountEstimator`（core api 包）0 实现（**MV 已裁定为 SPI 扩展点契约**，P1-MA5-003 不在此计划重开；本计划只处理估算精度文档/校准的残余风险）。
- **MA6.1-AR-6（live 确认）**：`LlmConfigHelper.java:28-38` `private static final ICache<String,String> secretCache` + `private static File secretDir` + `static setSecretDir()`，测试间状态泄漏，无 `@Before` 复位机制。`clearSecretCache()` 已存在但只清 cache 不清 `secretDir`。静态方法被 `ChatServiceImpl` 7+ 调用点使用（:80/84/98/99/116/140/141/158/202/203）。
- **MA6.1-AR-7（logMessage 默认 true）已由安全批次承接**：见 `2026-07-31-1834-1-arm-p2-security-hardening.md` Phase 4——本计划不重复处理。
- **MA5.6-AR-2/AR-3（live 确认）**：`TestWorkingMemoryEndToEnd.java:84-85`、`TestAdapterBackedMemoryEndToEnd.java:94-95` `static final AtomicReference`/`AtomicInteger` 测试期变更无 `@BeforeEach` 复位。
- MA5.6-AR-1（`CoreInitialization` 生命周期竞态）已由 MR3（P1-MA5.6-001，volatile 字段）修复。
- 全量基线：`./mvnw test -pl nop-ai -am -T 1C` 绿（3444 tests / 0 failures，2026-07-31 记录）。

## Goals

- `StandardRetryPolicy` 退避带 jitter（full jitter 或 capped jitter），消除并发重试同步风暴。
- LLM 调用用量默认可观测：NoOp 默认时启动 WARN（或提供结构化日志 SimpleUsageRecorder 选项），消除静默无计量。
- 限流支持 fail-fast：`tryAcquire()` 路径 + 每 tenant 配额配置（或裁定 + 文档化扩展点）。
- `LlmConfigHelper` 静态可变状态收敛：测试可复位或 instance 化，消除测试序依赖。
- 两个测试类静态捕获字段复位为 `@BeforeEach`/instance 字段。
- token 估算粗糙度获得显式文档化误差声明（不重开 SPI 裁定）。

## Non-Goals

- 不实现 ITokenCountEstimator 生产实现（MV 已裁定为 SPI 扩展点契约，P1-MA5-003）。
- 不实现分布式/Redis 限流器（MA6.3-AR-6 建议的跨 JVM 扩展点仅文档化）。
- 不引入 vault 集成（MA6.1-AR-8，安全批次已裁定 out-of-scope）。
- 不迁移所有测试类的 CoreInitialization 模式（已由 MR3 修复竞态；全量重构属 P3）。

## Scope

### In Scope

- `nop-ai-agent`：`StandardRetryPolicy` jitter；usage recorder 默认 WARN + SimpleUsageRecorder（结构化日志）；`DefaultAgentEngine`/`ReActAgentExecutor` 装配路径。
- `nop-ai-core`：`ChatServiceImpl` 限流限时 `tryAcquire()` fail-fast + 失败形状裁定（per-tenant 配额扩展点文档化）；`LlmConfigHelper` 静态状态复位 hook（或 instance 化裁定）。
- `nop-ai-agent` 测试：`TestWorkingMemoryEndToEnd`、`TestAdapterBackedMemoryEndToEnd` 静态字段复位。
- token 估算：`ILlmDialect`/`CalibratedTokenEstimator` 误差声明文档化（Javadoc/design）。

### Out Of Scope

- SPI 接口生产实现（IVectorStore/IEmbeddingModel/ITokenCountEstimator）。
- 跨 JVM 分布式限流。
- vault/secret-store 集成。
- 安全批次（`2026-07-31-1834-1-arm-p2-security-hardening.md`）与契约批次（`2026-07-31-1834-3-arm-p2-contract-dependency-cleanup.md`）内容。

## Execution Plan

### Phase 1 — 重试退避 jitter（MA6.3-AR-5）

Status: planned
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/reliability/StandardRetryPolicy.java`、`nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/reliability/`

- Item Types: `Fix | Proof`

- [ ] （Decision）裁定 jitter 策略：full jitter（`random(0, min(base*2^n, max))`）或 capped jitter（半区间 ± 随机）——按仓库已有测试稳定性选型。
- [ ] （Fix）`computeBackoff()` 加入 jitter，保持 `maxDelayMs` 上限与溢出保护语义。
- [ ] （Fix）**同步改写 `TestStandardRetryPolicy.java:135-165` 的 9 处精确延迟断言（:135/138/141/144/152/153/154/156/163，含两处 3000）为区间断言**（`0 <= delay <= min(base*2^n, max)`，attempt 0 的 0ms 断言除外），并同步 `StandardRetryPolicy` 类 Javadoc 的退避契约描述（`min(base*2^attempt, max)` → 带 jitter 的区间描述）。
- [ ] （Fix）**同步 owner docs 中的退避公式**：`ai-dev/design/nop-ai-agent/nop-ai-agent-llm-layer.md:262/:307`（§7.3 精确公式 `min(baseDelay * 2^attempt, maxDelay)`）与 `nop-ai-agent-reliability.md:160`——改为 jitter 区间描述或显式注明"确定性公式为基线，jitter 细节见类 javadoc"。
- [ ] （Fix）新增 jitter 测试：退避落在 `[0, cap]` 区间、多次调用随机性（不同值出现）、`baseDelayMs=0` 仍返回 0 的边界。
- [ ] 全量 build + test 验证。

Exit Criteria:

- [ ] `computeBackoff` 返回值带 jitter 且在 `[0, min(base*2^attempt, max)]` 区间（测试断言）
- [ ] `TestStandardRetryPolicy` 精确断言已改写为区间断言且全绿（无 flake：连续 2 次运行通过）
- [ ] `StandardRetryPolicy` Javadoc 契约描述与 jitter 行为一致
- [ ] owner docs（`nop-ai-agent-llm-layer.md` §7.3、`nop-ai-agent-reliability.md`）退避公式已同步或显式注明基线语义
- [ ] 边界保持：`baseDelayMs=0 → 0`、溢出保护不回归（测试断言）
- [ ] **无静默跳过**：jitter 为真实随机源（非恒定偏移，测试断言不同值出现）
- [ ] `No owner-doc update required`（行为增强，接口契约不变）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 — usage recorder 可观测性（MA6.3-AR-4）

Status: planned
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/usage/`、`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/ReActAgentExecutor.java`、`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/DefaultAgentEngine.java`

- Item Types: `Fix | Decision | Proof`

- [ ] （Decision）裁定 NoOp 默认的可观测性策略：(a) 启动 WARN；(b) 提供 `SimpleUsageRecorder`（SLF4J 结构化日志行）供装配；(c) 两者都做。
- [ ] （Fix）按裁定落地。**WARN 判定点必须注意 Builder 接线时序**：`DefaultAgentEngine` 字段默认 NoOp（:201），`Builder.build()`（:574）在构造**之后**才 `setUsageRecorder`（Builder 默认值 :482 恒非 null）——若 WARN 放构造器，Builder 路径会误报。正确放置：(i) `Builder.build()` 接线完成后判定 + (ii) 直接构造路径判定，两处；或首次 execute 懒判定。**不要只放构造器**。
- [ ] （Proof）接线验证复用既有 `TestUsageRecorderWiring`（引擎级断言 record 每 LLM 调用被调用 + 字段正确）——不重复新建接线测试；若裁定 (b) 新增 SimpleUsageRecorder，则为其补日志输出断言；同时断言 Builder 正确装配后**不**产生 WARN（防误报）。
- [ ] 全量 build + test 验证。

Exit Criteria:

- [ ] 默认 `NoOpUsageRecorder` 时 WARN 可见（测试断言或日志捕获，repo-observable），且 **Builder 装配真实 recorder 后不误报 WARN**（测试断言）
- [ ] **接线验证**：既有 `TestUsageRecorderWiring` 全绿（装配 `IUsageRecorder` 后 ReAct loop 的 record 落到 recorder）；如新增 SimpleUsageRecorder 有对应输出断言
- [ ] **无静默跳过**：不再静默丢弃 UsageRecord（至少 WARN 声明缺口；若实现 SimpleUsageRecorder 则默认可观测）；`NoOpUsageRecorder.record()` 空方法体为 pass-through 设计（javadoc + `usage-and-billing.md` §3.1），closure 时按 Anti-Hollow 豁免记录
- [ ] `No owner-doc update required`（装配契约不变，仅默认观测性增强）或 design 文档同步（若新增 SimpleUsageRecorder 装配说明）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 — 限流 tryAcquire + 配额（MA6.3-AR-6）

Status: planned
Targets: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/service/ChatServiceImpl.java`、`nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/service/DefaultAiChatService.java`、`nop-ai/nop-ai-core/src/test/java/io/nop/ai/core/service/`

- Item Types: `Fix | Decision | Proof`

- [ ] （Decision）裁定限流缺口处理（**基线修正**：`IRateLimiter.tryAcquire()` 系列已存在，见 `nop-kernel/nop-commons/.../ratelimit/IRateLimiter.java:23-38`；真缺口是 `ChatServiceImpl` 用无限阻塞 `acquire()` + 失败面未定义）：
  - (a) 失败形状：配额耗尽时抛什么错误——新 ErrorCode（如 `ERR_AI_RATE_LIMITED`）并使其被 `LlmErrorClassifier` 识别为可重试（transient），或复用带 429 语义的错误。**注意联动细节**：`LlmErrorClassifier`（`nop-ai-agent/.../reliability/LlmErrorClassifier.java:68-111`）对未知错误默认返回 TRANSIENT（兜底可重试），真正风险是新错误若携带 `ARG_HTTP_STATUS` 落在 [400,500)（非 429）会被判 NON_TRANSIENT——裁定须明确新错误不携带 429 之外 4xx 状态，避免误判；
  - (b) per-tenant 配额：**`ChatServiceImpl` 当前无 tenant 身份来源**（无 ITenantResolver、无请求头解析）——要么引入 tenant 解析（扩 scope，需说明），要么裁定为文档化扩展点（`No owner-doc update required` 除外：需在 design 记录）；
  - (c) 跨 JVM 分布式限流仅文档化扩展点。
- [ ] （Fix）按裁定落地：`ChatServiceImpl.checkRateLimit` 改用限时 `tryAcquire`（如 `tryAcquire(1, timeout)`），耗尽时按 (a) 裁定抛错；`acquire()` 兼容路径保留。
- [ ] （Fix）`DefaultAiChatService`（废弃类，阻塞 `acquire()` 在 :165，limiter 工厂 :106-118）同款限流处理按同一裁定对齐（或记录为废弃路径豁免，两者择一并说明）。
- [ ] （Fix）测试：配额耗尽 → 按裁定错误（可重试语义）、配额内 → 通过、限时等待路径不回归。**注意**：不要断言 `DefaultRateLimiter.getAcquireFailCount()`（`nop-kernel/nop-commons/.../ratelimit/DefaultRateLimiter.java:40` 存在复制粘贴 bug 返回 success 计数——nop-kernel 属平台保护区，本计划不修）。
- [ ] 全量 build + test 验证。

Exit Criteria:

- [ ] 限流耗尽时按裁定失败（测试断言错误形状；若裁定为新 ErrorCode，`LlmErrorClassifier` 联动测试或理由记录）
- [ ] per-tenant 配额按裁定落地（tenant 来源明确）或文档化扩展点（design 记录）
- [ ] `DefaultAiChatService` 对齐处理有明确处置（修复或废弃豁免记录）
- [ ] **无静默跳过**：限流失败不静默吞掉（错误可被上层观察/重试）
- [ ] `No owner-doc update required`（内部行为，公开 API 不变）或 design 同步
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 — LlmConfigHelper 静态状态收敛（MA6.1-AR-6）

Status: planned
Targets: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/service/LlmConfigHelper.java`、`nop-ai/nop-ai-core/src/test/java/io/nop/ai/core/service/`

- Item Types: `Fix | Decision | Proof`

- [ ] （Decision）裁定收敛方式：(a) instance 化（`ChatServiceImpl` 注入实例）；(b) 静态保留 + 提供 `reset()`/`clearSecretCache()` 复位 hook（**需同时复位 `secretDir`**，现有 `clearSecretCache()` 只清 cache）+ 测试 `@Before` 调用。评估静态 API 现有调用面（`ChatServiceImpl` 7+ 调用点、废弃 `DefaultAiChatService`、`TestLlmConfigHelper` 8 例）后选型——**默认倾向 (b)**（成本低、不改变公开静态 API）。
- [ ] （Fix）按裁定落地：instance 化或复位 hook。
- [ ] （Fix）测试：测试间 secretCache/secretDir 状态不泄漏（复位后断言缓存空/新 dir 生效）。
- [ ] 全量 build + test 验证。

Exit Criteria:

- [ ] `LlmConfigHelper` 静态状态可复位（或已 instance 化），测试序无关（测试断言）
- [ ] 既有调用面不回归：`ChatServiceImpl.setSecretDir` 注入等（若选型 (a) instance 化改变了公开静态 API，需同步 `docs-for-ai/` 相关文档，此时不适用 `No owner-doc update required`）
- [ ] **无静默跳过**：复位语义明确（清缓存 + 重置 dir），无残留静态
- [ ] `No owner-doc update required`（选型 (b) 时；选型 (a) 时改为同步 owner doc）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 5 — 测试静态字段复位（MA5.6-AR-2/AR-3）+ token 估算误差文档化（MA6.3-AR-3 残余）

Status: planned
Targets: `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/tool/TestWorkingMemoryEndToEnd.java`、`TestAdapterBackedMemoryEndToEnd.java`、`nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/dialect/ILlmDialect.java`、`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/CalibratedTokenEstimator.java`

- Item Types: `Fix | Decision | Proof`

- [ ] （Fix）`TestWorkingMemoryEndToEnd`/`TestAdapterBackedMemoryEndToEnd` 静态捕获字段 → `@BeforeEach` 复位（或 instance 字段）。
- [ ] （Fix）`ILlmDialect.estimateTokensDefault`/`CalibratedTokenEstimator` Javadoc 增加误差声明：chars/4 为基线启发式；**误差上界声明须限定为"校准收敛后 ≤4x"**（`MAX_FACTOR=4.0` 仅为 EMA 钳位上限，未校准（factor=1.0）时实际低估可远超 4x）；compaction trigger 调优提示。不改 SPI 裁定。
- [ ] （Proof）两测试类连续 2 次全量 `-pl nop-ai-agent` 测试通过（确定性验证；JUnit 默认不支持随机类顺序，不做不可兑现的"随机顺序"验证）。
- [ ] 全量 build + test 验证。

Exit Criteria:

- [ ] 两个测试类无测试期共享静态可变状态（代码审查 + 连续 2 次 agent 模块测试通过）
- [ ] token 估算误差声明落盘（Javadoc 可读，措辞限定"校准收敛后"）
- [ ] **无静默跳过**：无残留静态捕获字段
- [ ] `No owner-doc update required`（纯测试修复 + Javadoc 声明）
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [ ] 所有 in-scope 可靠性 P2 finding（MA6.3-AR-3 残余/4/5/6、MA6.1-AR-6、MA5.6-AR-2/3）已修复或裁定落盘
- [ ] 无 in-scope live defect 被静默降级到 deferred / follow-up
- [ ] 关键行为（jitter、usage recorder 接线、限流 fail-fast、静态状态复位）均有 focused 测试
- [ ] 不存在空方法体/静默跳过/no-op 作为正常实现（Anti-Hollow）
- [ ] 受影响 owner docs（`ai-dev/design/nop-ai-agent/` reliability 文档如适用、`arm-index.md`）已同步到 live baseline
- [ ] 独立子 agent closure audit 已完成并记录证据
- [ ] `./mvnw compile -pl nop-ai -am`
- [ ] `./mvnw test -pl nop-ai -am -T 1C`
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai --severity high` 退出码 0
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2026-07-31-1834-2-arm-p2-reliability-observability.md --strict` 退出码 0

## Deferred But Adjudicated

### ITokenCountEstimator 生产实现（MA6.3-AR-3 主体）

- Classification: `watch-only residual`
- Why Not Blocking Closure: MV 已裁定（P1-MA5-003）为 SPI 扩展点契约：接口 javadoc 明确"平台无生产实现属设计意图，集成方提供实现"。本计划只文档化估算误差，不重开 SPI 裁定。
- Successor Required: `no`

### 跨 JVM 分布式限流（MA6.3-AR-6 建议 c）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 单 JVM `tryAcquire` fail-fast 已消除阻塞问题；分布式协调（Redis）属部署基础设施演进。
- Successor Required: `no`

## Non-Blocking Follow-ups

- MA6.1-AR-7（logMessage 默认 true）由安全批次 `2026-07-31-1834-1-arm-p2-security-hardening.md` Phase 4 承接（本批次不重复处理）。
- MA5.6-AR-4/AR-7（temp dir deleteOnExit 资源泄漏，P3）：低优先，后续测试基础设施批次。
- MA5.6-AR-5（PassThroughModelRouter 单例耦合，P3）：低优先。

## Closure

Status Note: （完成时填写）
Completed:

Closure Audit Evidence:

- Reviewer / Agent:
- Evidence:

Follow-up:

-

## Optional Sections

## Risks And Rollback

- jitter 引入随机性：测试断言区间而非精确值；失败可回滚单 commit。
- usage recorder WARN：一次性启动日志，无每调用开销；误报可关闭。
- tryAcquire fail-fast：改变限流耗尽行为（阻塞→快速失败），上层 ReAct loop 需能处理可重试错误——按裁定保留 `acquire()` 兼容路径，回滚成本低。
- LlmConfigHelper instance 化若选型（a）：触及 `ChatServiceImpl` 注入面，需同步测试；选型（b）复位 hook 成本更低，作为默认倾向。
