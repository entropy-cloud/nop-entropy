# nop-ai 不变式目录（Invariant Catalog）

> 状态: I0 基线盘点产物 + I1 门禁机制裁定，2026-08-12 定稿（经独立子 agent 审查至共识；I1 Phase 1 六项决策 2026-08-12 裁定并入）
> 来源: `ai-dev/backlog/nop-ai-invariant-loop-roadmap.md` §I0；`ai-dev/plans/2026-08-12-1120-1-ai-invariant-i0-inventory-baseline.md`
> 消费方: I1 门禁沉淀（`ai-dev/plans/2026-08-12-1120-2-ai-invariant-i1-gate-codification.md`）——每条不变式的「检测方法」与 I1 的五族门禁一一对应；4 张审计目标集表构成 I1 门禁的表完备性数据源
> 维护规则: 本目录为审计基础数据，只增不改；修订必须附 live 证据。目标集表条目随产品演进变化时由 I2/I5 按 Loop Rule 复触发处理（见 roadmap §Loop Rule）

## 1. 基线结论：零可执行不变式门禁（live 证据）

以下三项结论均于 2026-08-12 在 live repo 上机械核实，非凭记忆：

| # | 结论 | 核实命令 | 结果 |
|---|------|---------|------|
| 1 | 全仓 `pom.xml` 无 ArchUnit 依赖 | `grep -rn "archunit" --include=pom.xml .` | 退出码 1（零命中） |
| 2 | `ai-dev/tools/` 无针对五失败族的 mjs 静态检查 | `ls ai-dev/tools/` | 现有 `check-ai-dict-consistency` / `check-import-order` / `check-ibiz-interfaces` / `check-vfs-violations` 等均与五族无关；`scan-hollow-implementations.mjs` 为通用空壳扫描，非族系门禁 |
| 3 | nop-ai 测试无「目标集 × 不变式」穷举门禁 | `grep -rln "ParameterizedTest" nop-ai/ --include="*.java"`（排除 target/_gen） | 仅 1 处：`nop-ai-toolkit/.../ssrf/TestSsrfAddressGuard.java`——SSRF 守卫实例级参数化测试，非方法表/类表穷举契约 |

推论：**当前零可执行不变式门禁**——已修复实例的既有测试（`TestSecureByDefault`、`TestLayer23SecureDefaults`、`TestPlan271AsyncTimeoutReliability`、`TestEngineEntryCleanupSymmetry` 等，均位于 `nop-ai-agent/src/test/java/io/nop/ai/agent/engine/`）是「已修复实例的行为验证」，新类/新方法不会自动进入其检查范围；lessons 识别的失败模式未沉淀为可执行 CI 门禁，重构或新增方法时同族缺陷可回归。

## 2. 不变式（5 条）

> 五条不变式对应 roadmap §目的 的五失败族，每条四要素：陈述 / 覆盖失败族 / 历史证据（audit-finding-ID → 修复 plan → live 代码，双向核对）/ 检测方法（与 I1 计划 `2026-08-12-1120-2` 的五族门禁一一对应）。

### INV-1 Secure-by-default：每个 Default* 类必须声明安全默认配置

- **陈述**：nop-ai 中每个 `Default*` 类（目标集见 §3.1）必须存在可观测的「安全默认配置」声明——声明性注解 + 构造期兜底语义（或等价的显式机制，具体判定标准由 I1 Phase 1 裁定并记录于此的「检测方法」）。门禁只验证声明存在性与类别属性，**不验证行为语义**（行为级验证属 I2 接线抽查与探查职责，防门禁退化为「注解存在即通过」的空转检查——Lesson 08 声明 ≠ 接线教训同源）。
- **覆盖失败族**：Secure-by-default 缺失族（6 个兄弟实例）。
- **历史证据**（finding-ID → plan → live，抽查 4 条）：
  - AUDIT-13-01 → plan `ai-dev/archived/2026-06/193-nop-ai-agent-secure-by-default.md` → live `AgentStartupWarnings.java:64` `warnIfInsecureDefaults`（构造期 AllowAll 实例触发 `LOG.warn`，deny-list 工具默认拒绝），调用点 `DefaultAgentEngine.java:240/:368` + `TestSecureByDefault`（6 tests 端到端）。
  - AUDIT-13-02 → plan `ai-dev/archived/2026-06/194-nop-ai-agent-audit-logger-default.md` → live `DefaultAgentEngine.auditLogger` 默认 `Slf4jAuditLogger`。
  - AUDIT-13-04 / L23-SDI → plan `ai-dev/archived/2026-06/199-nop-ai-agent-layer23-secure-defaults.md` + `ai-dev/plans/200-nop-ai-agent-layer23-secure-default-impls.md` → live 4 个 Layer 2/3 Default* 组件（`DefaultPermissionMatrix` / `DefaultSecurityLevelResolver` / `DefaultApprovalGate` / `DefaultDenialLedger` / `DefaultPostDenialGuard`，`DefaultAgentEngineConfig.java:114-118` 字段默认值用 Default*）+ `TestLayer23SecureDefaults` / `TestLayer23SecureDefaultImpls`。
  - AUDIT-14-01（runningExecutions）→ plan `ai-dev/archived/2026-06/197-nop-ai-agent-session-concurrency-guard.md` → live `DefaultAgentEngine.java:786` `runningExecutions.putIfAbsent(sessionId, handle)`（并发执行 fail-fast）。
  - AUDIT-09-01 → plan `ai-dev/archived/2026-06/196-nop-ai-agent-exception-base-class.md` → live `NopAiAgentException extends NopException`。
- **检测方法**（I1 门禁①）：ArchUnit 规则（`com.tngtech.archunit:archunit-junit5:1.5.0`，**全仓当前无 ArchUnit 依赖，I1 需先引入**）或 JUnit 反射扫描：对 §3.1 目标集表全部 33 个 Default* 类断言「声明安全默认配置」，判定标准与跨模块 classpath 约束（agent/core/toolkit/shell 四模块分布，nop-ai-agent 依赖 core+toolkit 但不依赖 shell）由 I1 Phase 1 裁定；表完备性接线（新 Default* 类不入表即 red）。
  - **I1 裁定（2026-08-12）**：声明形式 = 类级 marker 注解 `@SecureDefault`（`nop-ai-api` 包 `io.nop.ai.api.secure`，纯声明无行为）。门禁只验证注解存在性 + 类别属性（`src/main` 具体类、`Default` 前缀、非生成类），**不验证行为语义**（行为验证 = I2）。落地 = `TestInvariantGate1SecureDefault`（nop-ai-agent，覆盖 agent+core+toolkit 31 类）+ `TestInvariantGate1SecureDefaultShell`（nop-ai-shell，覆盖 shell 2 类）；表完备性 = 源树机械反查（复刻 §4 find 命令）与表 diff。首跑 33 类均无注解 → 全部登记 `gate-gaps.yaml`（`missing-declaration`，等 I4 补注解，不修复）。ArchUnit 与 junit-platform 6 / Java 26 兼容性实测失败则回退 JUnit 反射扫描（本目录记录裁定）。

### INV-2 异步编排：每个编排入口必须声明 timeout

- **陈述**：每个异步编排入口（目标集见 §3.2）必须存在可观测的 timeout 声明——方法签名参数 / 配置项 / 注解，至少一种（具体判定标准由 I1 Phase 1 裁定）。缺 timeout 的编排入口会挂起子 agent 执行并持续消耗 LLM API 配额与数据库资源。
- **覆盖失败族**：异步编排缺 timeout 族（5 个兄弟实例）。
- **历史证据**（finding-ID → plan → live，抽查 3 条）：
  - AUDIT-14-01（callAgent child timeout）→ plan `ai-dev/plans/271-nop-ai-agent-reliability-async-timeout.md` → live `CallAgentExecutor.java:459` `future.orTimeout(timeoutMs, ...)` + `:473` 超时路径调 `engine.cancelSession(childSessionId, ...)` 取消子 agent 实际执行。
  - AUDIT-14-03（LLM/工具 timeout）→ 同上 plan 271 → live `LlmCallCoordinator.java:605` `callChatWithTimeout`（retry loop 调用点 :218）+ `AgentToolDispatcher.java:232` per-tool `orTimeout(toolTimeoutMs, ...)`（MA4.2-05 引擎拆分后 timeout 逻辑自 `ReActAgentExecutor` 迁入）；配置默认值 `DefaultAgentEngineConfig.java:147-150` `callAgentTimeoutMs=120000 / llmTimeoutMs=120000 / toolTimeoutMs=300000`（均非 0；**AR-7 统一（plan 2026-08-12-2311-1）**：四个超时 setter——`setCallAgentTimeoutMs`/`setLlmTimeoutMs`/`setToolTimeoutMs`/`setMemberExecTimeoutMs`——全部拒绝 `<=0`，与本文档契约声明一致）。
  - AUDIT-14-06（session takeover lock heartbeat）→ plan `ai-dev/plans/273-nop-ai-agent-takeover-lock-heartbeat-renewal.md` → live `SessionLockRenewal.java:76` `scheduleWithFixedDelay(...)` 心跳续期（见 §3.4）。
  - AUDIT-14-02（DBMessageService at-least-once）/ -04（专用 cached 守护线程池）→ plan 271 → live `DBMessageService` / `DefaultAgentEngine.getAgentExecutor()`（`nop-ai-agent-exec` 守护线程池，非 commonPool）。
- **检测方法**（I1 门禁②）：JUnit `@ParameterizedTest` + 方法表穷举 §3.2 编排入口表，逐入口断言「已声明 timeout」（判定标准见 I1 Phase 1）；表完备性接线（新编排入口未登记即 red）。
  - **I1 裁定（2026-08-12）**：timeout 声明判定标准（满足任一即「已声明」）：(a) 方法签名含 timeout 形参（`long`/`Duration`/`*TimeoutMs`）；(b) 入口实现路径文件引用 timeout 机制标记（`orTimeout`/`callChatWithTimeout`/`get(..., TimeUnit`/`TimeoutException`）；(c) 声明类/其配置持有非零 timeout 配置项（`DefaultAgentEngineConfig` `callAgentTimeoutMs=120000`/`llmTimeoutMs`/`toolTimeoutMs`）。无异步执行等待面的入口（`forkSession`/`cancelSession`/`close`/`getSessionStatus`）登记 `not-applicable` + 理由（I3 裁决输入），不静默 pass。落地 = `TestInvariantGate2OrchestrationTimeout`（@ParameterizedTest 穷举 14 入口 + 表完备性机械反查 §3.2 判定标准）。首跑实判：9/14 已声明（配置/机制标记）；4 个 `not-applicable`（forkSession/cancelSession/close + getSessionStatus——I1 表完备性发现 I0 漏计，live 接口公共方法 10 个）；`SingleTurnExecutor.execute`（单轮执行器无 timeout 标记）登记 `missing-declaration` 缺口。

### INV-3 资源清理：每个资源 entry-point 必须 acquire/release 对称

- **陈述**：每个资源 entry-point（目标集见 §3.4，acquire/start 与 release/stop 成对面）必须对称清理：acquire 失败或中途异常时，已获取的资源（锁 / 心跳 / actor / checkpoint / 线程池）全部释放，sessionId 可再次执行；引擎有显式生命周期终止入口（AutoCloseable）。
- **覆盖失败族**：资源清理不对称族（3 个兄弟）。
- **历史证据**（finding-ID → plan → live，抽查 3 条）：
  - AR-02（resumeSession postDenialGuard.reset）→ plan `ai-dev/plans/278-nop-ai-agent-engine-resource-lifecycle-recovery-delegation-bounds.md` → live `AgentSessionLifecycle.java:273` `config.getDenialLedger().reset(sessionId)` + `:282` `config.getPostDenialGuard().reset(sessionId)`（resumeSession :242 的 tenant-scoped try 内；MA4.2-05 拆分后恢复逻辑在 `AgentSessionLifecycle`，`DefaultAgentEngine.resumeSession` 为一行委派）。
  - AR-04（三入口 createActor+autoBindTeam 清理对称性）→ plan 278 → live 四入口（`DefaultAgentEngine.doExecute` :707 inner try `createActor` :832 / `autoBindTeam` :837；`AgentSessionLifecycle.resumeSession` :242 inner try :357/:362；`restoreSession` :510 inner try :669/:674；`wakeSession` :410 :474/:477）createActor+autoBindTeam 移进内层 try，失败时对称释放 handle/actor/lock/心跳；`TestEngineEntryCleanupSymmetry`（4 tests）。
  - AR-09（IAgentEngine AutoCloseable）→ plan 278 → live `IAgentEngine.java:8` `extends AutoCloseable` + `:237` default no-op `close()`；`DefaultAgentEngine.java:926` `close()` 仅关闭自创建池（幂等）。
  - AR-10（ICheckpointManager.remove）→ plan 278 → live `ICheckpointManager.java:116` `default void remove(String sessionId)`；`FileBackedCheckpointManager` override 清理 cache；引擎仅对终态 session 调 remove（paused 保留供 restore）。
- **检测方法**（I1 门禁③）：JUnit `@ParameterizedTest` + 方法表穷举 §3.4 资源 entry-point 表，逐入口断言 acquire/release 对称（以既有 `TestEngineEntryCleanupSymmetry` 语义为基线）；表完备性接线。
  - **I1 裁定（2026-08-12）**：清理对称性判定标准 = entry-point 行的 acquire 标记 + 对称 release 标记在同一指定源文件内都存在（行锚定，live 核实）；行为级对称验证（异常路径真实清理、sessionId 可重执行）= 既有 `TestEngineEntryCleanupSymmetry` 基线 + I2 接线抽查。落地 = `TestInvariantGate3EntryPointCleanup`（@ParameterizedTest 穷举 8 行 + 表完备性机械反查 §3.4 六条 grep）。首跑实判：8 行全部 acquire/release 成对存在（plan 278 修复面），零缺口。

### INV-4 ToolExecutor 安全边界：每个 ToolExecutor 实现必须声明并接线安全边界校验

- **陈述**：每个 ToolExecutor 实现（目标集见 §3.3）必须存在「安全边界声明」= 安全校验声明存在 + 校验调用点出现（机械可判：SSRF / 路径逃逸 / 命令注入防护的校验入口在类内被引用——按 Lesson 08 判定规则「Tool executor 参数一律按不可信输入处理」）。**校验函数存在 ≠ 已接线**：运行时接线验证属 I2 接线抽查职责，I1 门禁④不承诺静态证明运行时接线。
- **覆盖失败族**：ToolExecutor 安全边界族（MA6.2 四 P1 同源）。
- **历史证据**（finding-ID → plan → live，抽查 4 条）：
  - P1-MA6.2-001（HttpRequestExecutor SSRF）→ MR3（plan `ai-dev/plans/2026-07-31-1300-5-arm-mr3-fix.md`）→ live `HttpRequestExecutor.java:63` `validateUrl(url)`（scheme 白名单 + `SsrfAddressGuard.validateHost` 内网/loopback 黑名单），`validateUrl` 定义 :73。
  - P1-MA6.2-002（GraphqlQueryExecutor SSRF）→ MR3 → live `GraphqlQueryExecutor.java:30` `validateUrl` 定义，调用点 `:71`（`validateUrl(endpoint)`）。
  - P1-MA6.2-003（LocalToolFileSystem 路径逃逸未接线）→ MR3 → live `LocalToolFileSystem.java:53-54` `resolveFile()` 首先调 `isPathAllowed(path)`（接线修复），`isPathAllowed` 定义 :41。
  - P1-MA6.2-004（BashExecutor 命令注入）→ MR3 → live `BashExecutor.java:103` `validateCommand(command)`（`DESTRUCTIVE_COMMAND` 正则 :51 + `DANGEROUS_ENV_VARS` 过滤）；回归 `BashExecutorTest`。
- **检测方法**（I1 门禁④）：`ai-dev/tools/check-ai-tool-executor-boundary.mjs` 静态扫描 §3.3 目标集表，断言每个实现「声明 + 校验调用点出现」；清单外新执行器未登记即 fail；门禁④只验证声明与调用点存在，不验证运行时接线（归 I2）。
  - **I1 裁定（2026-08-12）**：声明判定按 I0 表「安全敏感面」列——网络：类内含 URL/HTTP 处理（`HttpClient`/`URL`/`validateUrl` 等）必须引用 SSRF 校验入口（`SsrfAddressGuard`/`validateUrl`/`validateHost`），纯委托注入后端（`ISearchEngine`）视为有界抽象声明；文件：必须使用有界抽象（`IToolFileSystem`/`VirtualFileSystem`/`IResource`）且无裸 `java.io.File`/`java.nio.file` 路径构造（`BashExecutor` 归命令面例外），边界实现 `LocalToolFileSystem` 的 `isPathAllowed`→`resolveFile` 接线单独检查；命令：必须引用 `validateCommand`；内存/会话：无外部 IO 面，声明成立。**运行时接线验证不属本门禁**（归 I2 接线抽查）。落地 = `check-ai-tool-executor-boundary.mjs`（表内嵌 + 源文件机械检查 + 表完备性 grep 反查 + `--self-test` 正反例）。首跑实判：29/30 声明成立；`AskOracleExecutor`（oracle client 未实现，P2-MA1-011 fail-fast，无实际网络 I/O）登记 `not-applicable` 缺口；`UpdateTodosExecutor` 表标注「文件」但 live 为内存 ConcurrentHashMap 实现——按 live 面（内存）判声明成立，偏差显式记录供 I2 核对。

### INV-5 虚假关闭：fix-commit 必须包含实质 diff

- **陈述**：任何声称「已修复」的提交（`fix(nop-ai)` commit）必须包含实质 diff > 0（排除纯版权头 / 空白 / 生成文件）；零实质变更的 fix commit = overclaimed closure，按 Lesson 05 判定规则处理。
- **覆盖失败族**：虚假关闭族（Lesson 05 整篇 3 例）。
- **历史证据**（evidence → plan → live，抽查 3 条）：
  - MR2 overclaim（声称 MA4.3 P1 已展开进 arm-index，live 无行）→ MR4 才补入 → 记录于 `ai-dev/audits/arm-index.md` §MR4 P1 表逐行核验。
  - MR1 overclaim（声称 `_dao.beans.xml` 已加解释注释）→ `git log` 显示生成文件从未被修改 → MR4 裁定记录于 arm-index §P1-MA2-005。
  - MR3 overclaim（声称 `DefaultAiChatExchangePersister` 已加 AES 加密，live 纯明文）→ MR4 commit `249f89cf7` 才真正落地 → arm-index §P1-MA6.5-002 + 回归 `nop-ai-core/.../persist/DefaultAiChatExchangePersisterTest.java`。
- **检测方法**（I1 门禁⑤）：`ai-dev/tools/check-fix-commit-diff.mjs` 扫描 git 历史中 `fix(nop-ai)` 模式 commit（范围 = 2026-07-31 审计关闭后全部，含 PR 内 commit），断言实质 diff > 0；门禁⑤为 commit 卫生门禁，输出 = 违规 commit 清单（不适用 known-gaps 清单，由 I2/I3 消费）。
  - **I1 裁定（2026-08-12）**：实质 diff = `git diff <commit>^ <commit>` 增删行数（`--numstat`）排除生成/非产品路径（`/target/`、`/_gen/`、`_` 前缀生成文件）与纯空白/版权头行后的净变化 > 0。范围：本地模式 = `--since 2026-07-31` 全部 `fix(nop-ai)` commit；CI 模式 = PR 提交区间（`fetch-depth: 0`，默认 shallow clone 会让扫描静默为空——gate 空转风险已在 CI 接线规避）。落地 = `check-fix-commit-diff.mjs`（含 `--self-test`：_tmp 构造正/反例 git 仓库）。首跑实判（本地仓库）：2026-07-31 后 5 个 `fix(nop-ai)` commit（2026-08-01，MA4.2 系列）全部实质 diff > 0，零违规。
  - **I4 修复（R-5-1，2026-08-12）**：候选过滤收窄为 **subject-only**——移除 `--grep=fix(nop-ai)`（`git log --grep` 逐行匹配 body，body 首行提及字样的 chore/feat commit 会被误扫），改为 `git log --no-merges --format=%H %s` 全量取 subject + JS 侧 `subject.startsWith('fix(nop-ai)')` 唯一过滤；self-test 增补「subject 非 fix(nop-ai) 但 body 首行含字样」反例并更新计数断言。修复后实跑 `--since 2026-07-31` 零违规，`5ebad065e`（chore(ci)）/`c1362dc77`（feat(ai)）不再被误报为候选。

### INV-6 候选（探查工具化候选 — 同族实例联动重审纪律，`INV-6 候选`，不计入 §2 标题「5 条」计数）

> 登记：2026-08-12，来源 = I2/I5 遗留观察项「探查工具化候选」评估裁定（`ai-dev/audits/nop-ai-invariants/sixth-gate-family-evaluation.md`，plan `2026-08-12-1700-1`）。**裁定 = `watch-only residual`**——不沉淀为第六族门禁，零代码面；复触发条件见下（已交 I6 复触发登记，catalog §6）。

- **候选定义**：将 I2 人工执行的「兄弟路径探查」与 I4 的「类别清扫」纪律自动化（对某族任一实例被新增/修改时，强制检查该族全部兄弟实例）。非「门禁覆盖新实例声明」（已有①-④ 表完备性承担）。
- **覆盖失败族（候选）**：同族兄弟漏网（secure-default 6 兄弟 / timeout 5 兄弟 / 清理 3 兄弟 / Lesson 05 / Lesson 08 的历史复发模式）。
- **证据盘点结论**：**非高频**——Cycle 1 正证 1 起（R-2-3，根因 = catalog §3.2 排除理由 owner-doc drift，已由补表 + 更正 + 门禁②表完备性机械承接，live `MemberFanOutDispatcher.java:326` / `TeamTaskFlowOrchestrator.java:618` orTimeout 在位）；I4 类别清扫零新缺口；门禁①-④ 每次 CI 全表机械复查声明级。
- **裁定理由（watch-only）**：机械可验证核心（全表声明复查 + 表完备性）已由门禁①-④ 覆盖；行为级联动重审不可机械验证 → 声明式重审门禁必然空转（Anti-Hollow / Lesson 08 声明 ≠ 接线）；成本 > 收益。
- **复触发条件（可判定，交 I6 登记）**：(1) 周期复探发现 **≥2 起**同族兄弟漏网实例 → 复核 tool-ize；(2) 出现**第 1 起**「同族实例修改后兄弟行为级回归且五族门禁全绿」→ 复核 tool-ize；(3) Cycle 2 派生新族且**首轮审计 ≥2 起兄弟漏网** → 清扫纪律 tool-ize 并入新族设计。
- **检测方法（候选规格存档，供复触发后 Cycle 2 消费）**：见评估文档 §3（形态 (a) commit 面重审声明 / (b) 审计流程门禁 + Anti-Hollow 评估结论）。

## 3. 审计目标集（4 张表）

> 每张表头含「判定标准原文 + 复现命令」；条目数为 live 枚举数与表行数一致（差异零或显式记录）。所有行号均于 2026-08-12 live 核实。

### 3.1 Default* 类表（33 条）

> 门禁①对应：known-gaps family `gate-1-default-secure`（机制见 §3.5）；I1 首跑 33/33 无 `@SecureDefault` 声明已全部登记（I4 补注解）。

- **判定标准**：文件名以 `Default` 开头且位于 `src/main`（非测试、非生成）的 Java 类。文件在仓库中的位置由下方复现命令机械决定；是否参与 I1 门禁①以本表为准（表完备性 = 命令输出 == 表行数）。
- **复现命令**：
  ```bash
  find nop-ai -name "Default*.java" -not -path "*/target/*" -not -path "*/test/*" -not -path "*/_gen/*" | sort
  ```
  （输出 33 行，与本表行数一致）
- **边缘类裁定**：`nop-ai-agent/src/main/java/io/nop/ai/agent/guardrail/test/DefaultGuardrailGrader.java` 位于 `src/main` 但包路径含 `/test/` 段，被复现命令排除（`-not -path "*/test/*"`）。**裁定：排除**（理由：该 class 是 guardrail 测试辅助 grader（`GuardrailGrader` 实现，对 AttackCase 打分），非引擎/安全组件 Default* 类，不承担 secure-default 语义；若后续升级为生产组件，由 I2/I5 按 Loop Rule 复触发重新裁定）。本裁定使「命令输出 == 表行数」成立（33 行，不含 DefaultGuardrailGrader）。

| # | 模块 | 类（`包路径/文件`） | IoC 注入候选标注 |
|---|------|---------------------|------------------|
| 1 | nop-ai-agent | `engine/DefaultAgentEngine.java` | 构造注入候选（Builder 构建，应用层装配；引用方 `AdapterBackedAiMemoryStore` 等） |
| 2 | nop-ai-agent | `engine/DefaultAgentEngineConfig.java` | 构造注入候选（Builder 持有；`AgentTeamBinder`/`AgentStartupWarnings` 引用） |
| 3 | nop-ai-agent | `engine/DefaultAgentEventPublisher.java` | 构造注入候选（`DefaultAgentEngine` 内部装配） |
| 4 | nop-ai-agent | `fencing/DefaultFencingTokenService.java` | 接口 `IFencingTokenService` 引用方；`NoOpFencingTokenService` 并列存在 |
| 5 | nop-ai-agent | `hook/DefaultHookRegistry.java` | 构造注入候选（`MiddlewareChain`/`FilterChainResolver` 引用） |
| 6 | nop-ai-agent | `quota/DefaultResourceGuard.java` | 构造注入候选（`InMemoryActorRuntime`/`DbTeamManager` 引用） |
| 7 | nop-ai-agent | `reliability/DefaultWaitCoordinator.java` | 构造注入候选（wait 原语装配） |
| 8 | nop-ai-agent | `runtime/recovery/DefaultOrphanRecoveryHandler.java` | 构造注入候选（`ScheduledRecoveryManager` 引用） |
| 9 | nop-ai-agent | `runtime/recovery/DefaultSessionTimeoutHandler.java` | 构造注入候选（`ScheduledRecoveryManager` 引用） |
| 10 | nop-ai-agent | `runtime/recovery/DefaultTeamTaskRecoveryHandler.java` | 构造注入候选（`ScheduledRecoveryManager` 引用） |
| 11 | nop-ai-agent | `security/DefaultApprovalGate.java` | 构造注入候选（`ReActAgentExecutorBuilder` 装配；plan 199/200 产物） |
| 12 | nop-ai-agent | `security/DefaultContentTrustEvaluator.java` | 构造注入候选（Layer 2/3 组件族） |
| 13 | nop-ai-agent | `security/DefaultDenialLedger.java` | 构造注入候选（`ReActAgentExecutorBuilder`/`DefaultAgentEngineConfig` 引用） |
| 14 | nop-ai-agent | `security/DefaultLevelHintsProducer.java` | 构造注入候选（`DefaultSecurityLevelResolver` 引用） |
| 15 | nop-ai-agent | `security/DefaultPathAccessChecker.java` | 构造注入候选（Layer 2/3 组件族；P1-MA6.5-005 修复载体） |
| 16 | nop-ai-agent | `security/DefaultPermissionMatrix.java` | 构造注入候选（Layer 2/3 组件族，plan 200 产物） |
| 17 | nop-ai-agent | `security/DefaultPermissionProvider.java` | 构造注入候选（`DefaultSecurityLevelResolver` 引用） |
| 18 | nop-ai-agent | `security/DefaultPostDenialGuard.java` | 构造注入候选（AR-02 修复载体） |
| 19 | nop-ai-agent | `security/DefaultSecurityLevelResolver.java` | 构造注入候选（Layer 2/3 组件族，plan 200 产物） |
| 20 | nop-ai-agent | `security/DefaultToolAccessChecker.java` | 构造注入候选（`ReActAgentExecutorBuilder`/`AgentStartupWarnings` 引用） |
| 21 | nop-ai-agent | `team/DefaultMemberSpawner.java` | 构造注入候选（`NoOpMemberSpawner` 并列存在） |
| 22 | nop-ai-agent | `team/DefaultTeamAclChecker.java` | 构造注入候选（`DbTeamManager` 引用） |
| 23 | nop-ai-core | `api/tool/DefaultAiChatFunctionTool.java` | 构造注入候选（`GraphQLToolProvider` 等引用） |
| 24 | nop-ai-core | `api/tool/DefaultAiChatToolSet.java` | 构造注入候选（`GraphQLToolSetFactoryBean` 引用） |
| 25 | nop-ai-core | `persist/DefaultAiChatExchangePersister.java` | **IoC bean 注册**（`ai-defaults.beans.xml`）；P1-MA6.5-002 修复载体 |
| 26 | nop-ai-core | `persist/DefaultAiChatResponseCache.java` | **IoC bean 注册**（`ai-defaults.beans.xml`）；MA6.5-AR-7 修复载体 |
| 27 | nop-ai-core | `prompt/DefaultSystemPromptLoader.java` | 构造注入候选（`AiCommand` 引用） |
| 28 | nop-ai-core | `service/DefaultAiChatService.java` | **IoC bean 注册**（`ai-defaults.beans.xml`，废弃 API 保留向后兼容）；MR1/MR2 修复载体 |
| 29 | nop-ai-core | `service/DefaultAiChatSession.java` | 构造注入候选（`DefaultAiChatService` 内部） |
| 30 | nop-ai-core | `service/DefaultChatLogger.java` | **IoC bean 注册**（`ai-defaults.beans.xml`）；P1-MA6.1-003 修复载体 |
| 31 | nop-ai-shell | `checker/DefaultCommandChecker.java` | 构造注入候选（`ShellCommandExecutor` 两参构造默认装配；P2-MA3-023 修复载体） |
| 32 | nop-ai-shell | `commands/DefaultShellExecutionContext.java` | 构造注入候选（`ShellCommandExecutor`/`CdCommand` 引用） |
| 33 | nop-ai-toolkit | `executor/DefaultToolExecutorProvider.java` | **IoC bean 注册**（`ai-tools-defaults.beans.xml`；test beans 引用） |

### 3.2 编排入口表（17 条）

> 门禁②对应：known-gaps family `gate-2-orchestration-timeout`（机制见 §3.5）；I1 首跑 4 not-applicable（forkSession/cancelSession/close/getSessionStatus）+ 1 missing（SingleTurnExecutor.execute）已登记；I3 裁决（R-2-1/R-2-3）补表 3 条（gateway 派发 + team/flow fan-out），I4 修复后 missing 条目已移除（2026-08-12）。

- **判定标准（机械派生，原文 + I3 补表扩展）**：编排入口 = ① `IAgentEngine` 接口的全部公共方法（10 个：`sendMessage`/`execute`/`forkSession`/`getSessionStatus`/`cancelSession`/`resumeSession`/`restoreSession`/`wakeSession`/`restorePendingSessions`/`close`——接口是编排契约面，实现类（`DefaultAgentEngine`/`AgentSessionLifecycle`）的 `@Override` 方法不重复计数；**I1 修正：live 接口公共方法实为 10 个，I0 漏计 `getSessionStatus`（同步状态查询，非异步编排面），由 I1 门禁②表完备性发现，补入表并登记 not-applicable**）；② `io.nop.ai.agent.engine` 包内声明类上方法名 ∈ {`execute`, `executeAllowedCalls`} 的公共方法（机械 grep 结果：`ReActAgentExecutor.execute` :339、`SingleTurnExecutor.execute` :54、`AgentToolDispatcher.executeAllowedCalls` :165——executeAllowedCalls 返回 void，但语义为工具 fanout 编排步骤且带 timeout/join 契约，列入）；③ `CallAgentExecutor.executeAsync`（`io.nop.ai.agent.tool` 包唯一特例：其声明类实现 IToolExecutor，但语义为「启动子 agent 执行路径」，是 AUDIT-14-01 timeout 契约的直接主题，故同时出现在本表；§3.3 的安全边界检查不替代其 timeout 声明检查）；④ **I3 R-2-3 扩展：team/flow 包 fan-out 入口**——`MemberFanOutDispatcher.dispatch`（public static，经 `agentEngine.execute` :326 启动 agent 执行路径）+ `TeamTaskFlowOrchestrator.executeAsync`（daemon 级编排入口，result future 整体 deadline 兜底）；⑤ **I3 R-2-1 扩展：channel 派发入口**——`ChannelMessageServiceImpl.dispatchInbound`（gateway 包，mode-1 listener fan-out / mode-2 sendAsync 等待面，统一有界化）。**排除**（按本标准机械排除，非主观判断）：tool executors 的 `executeAsync`（归 §3.3）、messenger/DB 传输方法（`LocalAgentMessenger.request`、`DBMessageService.sendAsync`）。**R-2-2 watch 记录（I3 裁决）**：plan/runtime 包（`PlanExecutor`/`TaskRunner`/`PlanScheduler` 等 20 文件）生产零接线（唯一 src/main 引用 = `AgentPlan` → `AgentPlanValidator` 模型校验），不构成编排入口、不入表；**触发条件** = plan/runtime 接线时（出现 `TaskRunner` 产品实现 / 引擎引用 `PlanExecutor.execute`）须补 timeout 契约 + 本表登记（missing-declaration），复触发 I2/I5。
- **复现命令**：
  ```bash
  # ① engine 包内返回 future 的公共方法（11 行 = DefaultAgentEngine ×6 @Override + AgentSessionLifecycle ×3 @Override + ReAct ×1 + SingleTurn ×1）
  grep -rn "public.*\(CompletableFuture\|CompletionStage\)<" nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine --include="*.java"
  # ② 按判定标准②过滤（方法名 ∈ {execute} 且非接口 @Override）→ ReActAgentExecutor.execute :339、SingleTurnExecutor.execute :54
  # ③ executeAllowedCalls 返回 void，独立 grep → AgentToolDispatcher.java:165
  grep -n "public void executeAllowedCalls" nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/AgentToolDispatcher.java
  # ④ CallAgentExecutor.executeAsync（tool 包唯一特例）→ :144
  grep -n "public CompletionStage<AiToolCallResult> executeAsync" nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/tool/CallAgentExecutor.java
  # ⑤ IAgentEngine 公共入口（9 个，read engine/IAgentEngine.java，行号见下表）
  # ⑥ I3 R-2-3 team/flow fan-out 入口（机械派生）
  grep -n "public static CompletableFuture<MemberDispatchOutcome> dispatch(" nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/team/flow/MemberFanOutDispatcher.java
  grep -n "public CompletableFuture<TeamTaskFlowResult> executeAsync(" nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/team/flow/TeamTaskFlowOrchestrator.java
  # ⑦ I3 R-2-1 channel 派发入口（gateway 模块）
  grep -n "public void dispatchInbound(InboundChannelMessage message)" nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/channel/ChannelMessageServiceImpl.java
  ```

| # | 入口（`文件:行`） | 说明 |
|---|-------------------|------|
| 1 | `IAgentEngine.java:10` `sendMessage(AgentMessageRequest)` | 同步入口（非 future 返回，但为引擎入口） |
| 2 | `IAgentEngine.java:12` `execute(AgentMessageRequest)` | 主执行入口 |
| 3 | `IAgentEngine.java:38` `forkSession(...)` | 会话 fork |
| 4 | `IAgentEngine.java:46` `cancelSession(...)` | 会话取消 |
| 5 | `IAgentEngine.java:76` `resumeSession(...)` | sticky-pause 恢复 |
| 6 | `IAgentEngine.java:115` `restoreSession(...)` | crash-restart 恢复 |
| 7 | `IAgentEngine.java:142` `wakeSession(...)` | WAIT_FOR 唤醒 |
| 8 | `IAgentEngine.java:214` `restorePendingSessions(...)` | 批量恢复编排 |
| 9 | `IAgentEngine.java:42` `getSessionStatus(...)` | **I1 补录**：同步状态查询入口（非异步编排面，timeout 不适用；I0 漏计，live 接口公共方法 10 个） |
| 10 | `IAgentEngine.java:237` `close()` | 生命周期终止入口（AutoCloseable，见 §3.4） |
| 11 | `engine/ReActAgentExecutor.java:339` `execute(AgentExecutionContext ctx)` | ReAct 主循环（timeout 家族修复点） |
| 12 | `engine/SingleTurnExecutor.java:54` `execute(AgentExecutionContext ctx)` | 单轮执行器（**I4 修复**：llmTimeoutMs + timeoutExecutor 机制，`get(llmTimeoutMs, TimeUnit)` + TimeoutException 失败路径） |
| 13 | `engine/AgentToolDispatcher.java:165` `executeAllowedCalls(...)` | 工具 fanout 调度 |
| 14 | `tool/CallAgentExecutor.java:144` `executeAsync(AiToolCall, IToolExecuteContext)` | 子 agent 调用入口（timeout 家族修复点） |
| 15 | `team/flow/MemberFanOutDispatcher.java:157` `dispatch(...)` | **I3 R-2-3 补录**：team-flow fan-out 入口（static，`agentEngine.execute` :326 启动 agent 执行路径；**I4 修复**：per-member `orTimeout(memberExecTimeoutMs)` + 超时取消子会话，deadline 唯一来源 = `DefaultAgentEngineConfig.memberExecTimeoutMs`）。**分支级标注（plan 2026-08-12-2050-2 / AR-2 拆分）**：两条执行分支各自声明 timeout marker —— BOUND 分支 `executeBoundMember`（:326 `orTimeout` + :334 超时 `cancelSession`）+ SPAWN 分支 `spawnOneTarget`（:387 per-target `orTimeout`；spawner 层 `DefaultMemberSpawner` 另有有界 `get(memberExecTimeoutMs, TimeUnit)`）。门禁②判定 = **代码级（去注释/去字符串）`orTimeout` 计数 ≥ 2**（门禁测试 `TestInvariantGate2OrchestrationTimeout` 该行 `minOccurrences=2`） |
| 16 | `team/flow/TeamTaskFlowOrchestrator.java:581` `executeAsync(String teamId)` | **I3 R-2-3 补录**：daemon 级编排入口（**I4 修复**：result future 整体 `orTimeout` deadline 兜底；:443 `awaitTermination(2, TimeUnit.SECONDS)` 为 close 路径关闭等待，非编排 timeout）。**整体 deadline 语义（plan 2026-08-12-2050-2 / AR-3 升级）**：整体 deadline = `memberExecTimeoutMs × maxDepth`（maxDepth = 图最长依赖链，build 期从 blockedBy DAG 计算）——与 per-member 解耦，合法 N 层顺序 DAG（每层合法跑满 per-member）不被整体 deadline 误杀；整体超时（unwrap 后 `TimeoutException` 限定）→ `ITaskRuntime.cancel(CANCEL_REASON_TIMEOUT)` 取消底层图（nop-task `ICancellable` 能力）+ 诚实失败结果，取消失败显式 LOG（不静默）；普通节点失败不触发整图 cancel（保持 GraphTaskStep 短路语义） |
| 17 | `nop-ai-gateway/.../channel/ChannelMessageServiceImpl.java:329` `dispatchInbound(...)` | **I3 R-2-1 补录**（gateway 包，判定标准扩展为「channel 派发入口」）：**按 mode 拆两条目（plan 2026-08-12-2050-2 / AR-4 + WS4）**——mode-1（`fanOutToListeners` :371-373 per-listener `runAsync(...).orTimeout(dispatchTimeoutMs)`，**AR-4 修复**：超时 cancel **原始 runAsync future**（保留 `raw` 引用）+ 显式中断 worker 线程（JDK `CompletableFuture.cancel` 不保证中断运行中任务——worker 线程引用在 runnable 内捕获、超时路径直接 `interrupt()`，合作型 listener 释放池线程，对齐库内 SingleTurnExecutor/AgentToolDispatcher 中断 cancel 模式；**局限**：marker 计数无法检测 cancel 回归，cancel 语义由 WS3 行为测试 `TestChannelFanOutTimeoutCancel` 拦截）；mode-2（`dispatchInbound` :348 `sendAsync(...).orTimeout(dispatchTimeoutMs)`——持久化等待面，at-least-once 契约，**裁定不 cancel**（cancel 可能中止慢但进行中的 persist，破坏投递；超时只停等待，消费者须幂等——I3 R-2-1 原判），理由记录 plan daily log）。配置旋钮 `nop.ai.gateway.channel.dispatchTimeoutMs` 默认 30000。门禁②判定 = **按 mode 拆条**（`[mode-1]` / `[mode-2]` 各 ≥1 个代码级 marker，门禁测试 `TestInvariantGate2GatewayTimeout` 拆两条目） |

> 说明：`DefaultAgentEngine` 的 execute/resumeSession/restoreSession/wakeSession/forkSession/cancelSession（`DefaultAgentEngine.java:702` 等）为接口实现的 `@Override`，不重复计数（接口行为面即 §3.2 的契约面；实现类的行号供接线抽查使用）。**I3 更正（R-2-3 owner-doc drift）**：I0 排除理由「team-flow 内部方法（`TeamTaskFlowOrchestrator.executeAsync`、`MemberFanOutDispatcher.dispatch`）——不启动 agent 执行路径、无 timeout 契约面」与 live 矛盾（fan-out 经 `MemberFanOutDispatcher.java:326 agentEngine.execute` 启动 agent 执行路径）——已删除，改为 live 事实：「fan-out 启动 agent 执行路径，属编排入口面，须声明 timeout」。

> **`SpawnMemberAgentTaskStep` 登记裁定（plan 2026-08-12-2050-2 / AR-2 / WS4，2026-08-12）**：单播 spawn 节点 step（orchestrator DAG 节点级，`TeamTaskFlowOrchestrator.java:934` 创建）的 timeout 契约**由 spawner 层承载**——`SpawnMemberRequest.memberExecTimeoutMs`（step 构造器自 orchestrator 传入）→ `DefaultMemberSpawner` 有界 `get(memberExecTimeoutMs, TimeUnit)` → 超时 `SPAWN_FAILED` → 节点失败；step 自身无独立异步等待面（其 supplyAsync future 的 settle 由 spawner 同步契约内部的有界 get 决定，fixture B `TestSpawnStepTimeoutHonestFailure` 行为级证据：挂起 engine + 真实 spawner → step 在 memberExecTimeoutMs 内失败 + 单线程池 worker 释放断言）→ **登记 not-applicable**（不新增 gate-2 表条目，timeout 契约已由表 15 SPAWN 分支 + spawner 层覆盖；若未来判定标准扩展为「每个 spawn 路径须自有 marker」再补表——裁定记录）。

### 3.3 ToolExecutor 实现表（31 行 = 27 直接具体 + 1 抽象基类 + 3 间接子类）

> 门禁④对应：known-gaps family `gate-4-tool-boundary`（机制见 §3.5）；I1 首跑 AskOracleExecutor（not-applicable）已登记；UpdateTodosExecutor 表标注已更正为内存/会话面（I4 Phase 6，依 I3 裁决 §4 委托——live 为内存 ConcurrentHashMap 实现，无文件 IO）。

- **判定标准（原文）**：直接 `implements IToolExecutor` 的具体类（grep 用 `implements\s+IToolExecutor\b` 避免误匹配 `IToolExecutorProvider`）为「直接实现」；直接实现中声明为 abstract 的为「抽象基类」，其 `extends` 子类为「间接子类」，一并纳入本表（归类标注在行内）。接口 `IToolExecutor` = `nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/api/IToolExecutor.java`（`getToolName` + `executeAsync`）。
- **复现命令**：
  ```bash
  grep -rn "implements\s\+IToolExecutor\b" nop-ai --include="*.java" | grep -v target
  # 输出 28 行（含 1 个 abstract 基类 AbstractMemoryToolExecutor）
  grep -rln "extends AbstractMemoryToolExecutor" nop-ai --include="*.java" | grep -v target
  # 输出 3 行（ReadMemory/WriteMemory/SearchMemoryExecutor，间接子类）
  ```
- **安全敏感面标注**：网络（SSRF）/ 文件（路径逃逸）/ 命令（注入）/ 内存（会话内数据）四类。

| # | 模块 | 实现（`文件:行`） | 归类 | 安全敏感面 |
|---|------|-------------------|------|-----------|
| 1 | nop-ai-agent | `tool/CallAgentExecutor.java:84` | 直接实现 | 内存/会话（委派子 agent；AR-05 深度守卫） |
| 2 | nop-ai-agent | `tool/SendMessageExecutor.java:34` | 直接实现 | 内存/会话 |
| 3 | nop-ai-agent | `tool/SetActiveTagsExecutor.java:35` | 直接实现 | 内存/会话 |
| 4 | nop-ai-agent | `tool/TeamExecuteFlowExecutor.java:104` | 直接实现 | 内存/会话（team flow） |
| 5 | nop-ai-agent | `tool/TeamSendMessageExecutor.java:52` | 直接实现 | 内存/会话 |
| 6 | nop-ai-agent | `tool/TeamStatusExecutor.java:46` | 直接实现 | 内存/会话 |
| 7 | nop-ai-agent | `tool/TeamTaskCreateExecutor.java:55` | 直接实现 | 内存/会话 |
| 8 | nop-ai-agent | `tool/TeamTaskUpdateExecutor.java:61` | 直接实现 | 内存/会话 |
| 9 | nop-ai-agent | `tool/AbstractMemoryToolExecutor.java:28` | **抽象基类**（不参与门禁④实例判定，判定标准显式排除 abstract） | 内存 |
| 10 | nop-ai-agent | `tool/ReadMemoryExecutor.java:36`（`extends AbstractMemoryToolExecutor`） | 间接子类 | 内存 |
| 11 | nop-ai-agent | `tool/WriteMemoryExecutor.java:37` | 间接子类 | 内存 |
| 12 | nop-ai-agent | `tool/SearchMemoryExecutor.java:32` | 间接子类 | 内存 |
| 13 | nop-ai-toolkit | `tools/ApplyDeltaExecutor.java:14` | 直接实现 | 文件 |
| 14 | nop-ai-toolkit | `tools/AskOracleExecutor.java:14` | 直接实现 | 网络（外部端点） |
| 15 | nop-ai-toolkit | `tools/BashExecutor.java:40` | 直接实现 | **命令注入**（P1-MA6.2-004 修复载体） |
| 16 | nop-ai-toolkit | `tools/CopyFileExecutor.java:10` | 直接实现 | 文件（路径逃逸面） |
| 17 | nop-ai-toolkit | `tools/CreateDirectoryExecutor.java:10` | 直接实现 | 文件 |
| 18 | nop-ai-toolkit | `tools/DeleteFileExecutor.java:10` | 直接实现 | 文件 |
| 19 | nop-ai-toolkit | `tools/GraphqlQueryExecutor.java:19` | 直接实现 | **网络 SSRF**（P1-MA6.2-002 修复载体） |
| 20 | nop-ai-toolkit | `tools/HttpRequestExecutor.java:26` | 直接实现 | **网络 SSRF**（P1-MA6.2-001 修复载体） |
| 21 | nop-ai-toolkit | `tools/ListDirectoryExecutor.java:12` | 直接实现 | 文件 |
| 22 | nop-ai-toolkit | `tools/MoveFileExecutor.java:10` | 直接实现 | 文件 |
| 23 | nop-ai-toolkit | `tools/PatchFileExecutor.java:18` | 直接实现 | 文件 |
| 24 | nop-ai-toolkit | `tools/ReadFileExecutor.java:13` | 直接实现 | 文件 |
| 25 | nop-ai-toolkit | `tools/ReadRefExecutor.java:49` | 直接实现 | 文件 |
| 26 | nop-ai-toolkit | `tools/SearchContentExecutor.java:12` | 直接实现 | 文件 |
| 27 | nop-ai-toolkit | `tools/SearchEngineExecutor.java:26` | 直接实现 | 网络 |
| 28 | nop-ai-toolkit | `tools/SearchFilesExecutor.java:12` | 直接实现 | 文件 |
| 29 | nop-ai-toolkit | `tools/SkillExecutor.java:18` | 直接实现 | 文件/技能加载 |
| 30 | nop-ai-toolkit | `tools/UpdateTodosExecutor.java:16` | 直接实现 | 内存/会话（I2 委托 I4 更正：表标注文件面 vs live 纯内存 ConcurrentHashMap 实现、无文件 IO，按 live 面更正；gate-4 判定不受影响） |
| 31 | nop-ai-toolkit | `tools/WriteFileExecutor.java:10` | 直接实现 | 文件 |

> 计数核对：直接实现 grep 输出 28 行 = 27 具体（含 1 抽象基类 AbstractMemoryToolExecutor）+ 8 个 agent tool + 19 个 toolkit tool；间接子类 3 条并入后表共 31 行。I1 门禁④实例判定面 = 27 个直接具体实现 + 3 个间接子类（30 个），抽象基类显式排除（记录于判定标准）。

> **R-4-1 dead-code 记录（I3 裁决 not-applicable-confirmed，2026-08-12）**：`SsrfGuardDnsResolver`（`nop-ai-toolkit/tools/ssrf/SsrfGuardDnsResolver.java:34`，实现 `IDnsResolver`，resolve + resolveCanonicalHostname + fail-closed）**生产未接线**——默认 HTTP client `JdkHttpClient`（nop-http-client-jdk）不消费 `HttpClientConfig.dnsResolver`；消费点仅 `ApacheHttpClientHelper:100-109`（nop-http-client-apache，非任何 nop-ai 模块依赖）。**接线路径说明**：装配至消费 dnsResolver 的 client 实现时启用（Apache 族装配 / JDK 扩展支持点）。**触发条件（watch 复触发）**：若引入消费 dnsResolver 的 HTTP client → 接线 `SsrfGuardDnsResolver`（复触发 I4/I5）。host 级主防线（`SsrfAddressGuard.validateHost`，`HttpRequestExecutor:84` / `GraphqlQueryExecutor:71` 已接线）不受影响。gate-gaps **无变更**（非 ToolExecutor 实例，不在门禁④表）。

### 3.4 资源 entry-point 方法表（8 条）

> 门禁③对应：known-gaps family `gate-3-entry-point-cleanup`（机制见 §3.5）；I1 首跑 8/8 成对存在，零缺口。

- **判定标准（原文）**：以既有修复面（plan 278 AR-02/AR-04/AR-09/AR-10 + plan 271/273 的 async 可靠性面）为起点，枚举「acquire/start 与 release/stop 成对」的资源生命周期面：① 引擎级会话执行注册/注销（runningExecutions putIfAbsent/remove）；② 会话接管锁 acquire/release（tryAcquire/releaseLockQuietly）；③ 心跳续期 start/cancel（SessionLockRenewal）；④ checkpoint 清理（ICheckpointManager.remove）；⑤ 引擎生命周期（IAgentEngine AutoCloseable close）。方法级判定：acquire 面 = 成功后才开始执行路径的入口步骤；release 面 = 所有退出路径（含异常/paused/终态）必须触发的对称清理。
- **复现命令**：
  ```bash
  grep -n "putIfAbsent\|runningExecutions.remove" nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/DefaultAgentEngine.java
  grep -n "tryAcquire\|releaseLockQuietly" nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/DefaultAgentEngine.java
  grep -n "startLockRenewal\|cancelLockRenewalQuietly\|scheduleWithFixedDelay" nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/SessionLockRenewal.java
  grep -n "default void remove" nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/reliability/ICheckpointManager.java
  grep -n "public void close\|default void close" nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/{IAgentEngine,DefaultAgentEngine}.java
  ```

| # | entry-point（acquire/start 面） | 对称 release/stop 面（`文件:行`） | 来源修复 |
|---|-------------------------------|----------------------------------|---------|
| 1 | 会话执行注册 `runningExecutions.putIfAbsent`（`DefaultAgentEngine.java:786`，与 tryAcquire 包装，putIfAbsent 失败即释放锁 :793） | `runningExecutions.remove(sessionId, handle)`（`DefaultAgentEngine.java:844`（cancel 路径）/ `:898`（inner finally）） | AUDIT-14-01（plan 197）；plan 278 AR-04 |
| 2 | 会话接管锁 `config.getSessionTakeoverLock().tryAcquire(...)`（`DefaultAgentEngine.java:781`） | `lifecycle.releaseLockQuietly(sessionId, instanceId)`（`:793`（注册失败兜底）/ `:868`（退出路径）） | plan 278 AR-04 |
| 3 | 心跳续期 `SessionLockRenewal.startLockRenewal`（`SessionLockRenewal.java:76` `scheduleWithFixedDelay`） | `SessionLockRenewal.cancelLockRenewalQuietly(Future)`（静态，release 路径调用）；`renewOnceSafe` 发现 lease lost 时 `handleLeaseLost` 中止本地执行 | AUDIT-14-06（plan 273）；plan 278 AR-04 |
| 4 | 引擎内 try（四入口：`DefaultAgentEngine.doExecute` :707 / `AgentSessionLifecycle.resumeSession` :242 / `restoreSession` :510 / `wakeSession` :410，createActor+autoBindTeam 移进内层 try） | 内层 finally 对称清理（doExecute inner try createActor :832 / autoBindTeam :837；resume :357/:362；restore :669/:674；wake :474/:477） | plan 278 AR-04 |
| 5 | checkpoint 注册/增长（FileBackedCheckpointManager 五 cache 只写） | `ICheckpointManager.remove(String sessionId)` default（`ICheckpointManager.java:116`），FileBacked/ToolExecution override；引擎仅对**终态** session 调 remove（paused 保留） | plan 278 AR-10 |
| 6 | 引擎启动/自创建池（lockRenewExecutor/agentExecutor 懒创建） | `IAgentEngine.close()` default no-op（`IAgentEngine.java:237`）+ `DefaultAgentEngine.close()`（`:926`，仅关闭自创建池，幂等，不取消在途执行） | plan 278 AR-09 |
| 7 | resumeSession 治理状态（`AgentSessionLifecycle.java:273` denialLedger.reset） | `postDenialGuard.reset(sessionId)`（`:282`，同一 tenant-scoped try 内补全） | plan 278 AR-02 |
| 8 | cancel-without-handle 分支（`DefaultAgentEngine.java:606` `session.setStatus(AgentExecStatus.cancelled)`，未注册 handle、不进 inner finally） | `config.getCheckpointManager().remove(sessionId)`（`:613`，对称清理 checkpoint cache；无 handle 即无锁/心跳可释放——对称面 = checkpoint 清理） | plan 278 AR-10 |

## 3.5 known-gaps 清单机制（I1 Phase 1 裁定，2026-08-12）

> 文件：`gate-gaps.yaml`（本目录下）。门禁对「表内实例缺声明」的判定 = 实例在清单（同族）则 pass（已登记缺口，零增长棘轮），不在清单则 fail（新缺口，表完备性/棘轮违约）。

- **结构**（YAML，族 → 实例 → 原因 → reason-type → 日期 → 登记方）：
  - 族：`gate-1-default-secure` / `gate-2-orchestration-timeout` / `gate-3-entry-point-cleanup` / `gate-4-tool-boundary`（门禁⑤为 commit 卫生门禁，无清单，输出 = 违规 commit 清单）。
  - reason-type：`missing-declaration`（真实缺口，I2/I3 裁决后 I4 修复）/ `not-applicable`（判定标准外，I3 裁决输入，不得静默 pass）。
- **判定规则**：表内实例 = 声明通过（注解/标记/对称对存在）或 在清单（同族）→ pass；两者皆无 → fail（门禁红）。
- **变更策略（防静默白名单）**：登记入口仅两种——(1) I1 首跑登记（本 plan）；(2) I2/I3 裁决后的 finding 登记（附裁决证据）。**任何人不允许为保持 CI 绿而向清单添加条目**（添加即 = 缺口在 I4 修复而非豁免）。清单移除 = I4 修复后（移除后门禁恢复对此实例的实判，未真修复即 fail——自校验）。
- **门禁自校验**：清单内实例若源文件已不存在（类被删/改名）→ 门禁输出 stale 条目警告（供 I2/I5 复核，不阻塞绿——删除即缺口消失，允许清理）。
- **消费方**：I2（`2026-08-12-1120-3`）以清单为 red list 输入；I4 修复后移除；I5 验证零命中。

## 4. 复现命令索引

| 用途 | 命令 |
|------|------|
| Default* 类表 | `find nop-ai -name "Default*.java" -not -path "*/target/*" -not -path "*/test/*" -not -path "*/_gen/*" \| sort`（33 行） |
| 编排入口表 | `grep -rn "public.*\(CompletableFuture\|CompletionStage\)<" nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/{engine,tool} --include="*.java"` + IAgentEngine 公共入口 |
| ToolExecutor 表 | `grep -rn "implements\s\+IToolExecutor\b" nop-ai --include="*.java" \| grep -v target`（28 行）；`grep -rln "extends AbstractMemoryToolExecutor" nop-ai --include="*.java"`（3 行） |
| 资源 entry-point 表 | §3.4 六条 grep 命令 |
| 零门禁基线 1 | `grep -rn "archunit" --include=pom.xml .`（exit 1 = 零命中） |
| 零门禁基线 2 | `ls ai-dev/tools/`（无五族 mjs 检查） |
| 零门禁基线 3 | `grep -rln "ParameterizedTest" nop-ai/ --include="*.java" \| grep -v target`（仅 TestSsrfAddressGuard） |

## 5. 独立子 agent 审查记录（共识达成）

- **审查轮次**：2 轮（均为 fresh session 独立审查，review-only 禁改文件），按 `ai-dev/skills/invariant-loop-audit-prompt.md` 步骤 2 强制纪律（基线核对 live、无悬空引用、计数/编号 live 实测）。
- **第 1 轮**：发现 4 Blocker（§3.4 证据列与 INV-2/INV-3 live 引用使用 MA4.2-05 拆分前的过期行号）+ 2 Major + 2 Minor——已全部修订（行号改为 live 位置：`AgentSessionLifecycle`/`LlmCallCoordinator`/`AgentToolDispatcher`/`AgentStartupWarnings`，timeout 默认值 120000 修正）。
- **第 2 轮**：零 Blocker，2 Minor（`DefaultAgentEngine.java:613` off-by-one；GraphqlQueryExecutor validateUrl 定义/调用点表述）——已修订。
- **审查结论**：`approved`（第 2 轮 verdict）；修订记录见 `ai-dev/logs/2026/08-12.md`（「plan I0 nop-ai 不变式盘点」条目）。

## 6. 复触发条件登记（I6 循环收口定稿，2026-08-12）

> 唯一落点 = 本段（只增不改既有 §1-§5 规则；roadmap §Loop Rule 只留指针）。登记来源：I5 Phase 4 复触发条件基线 + I3 Follow-up watch 项 + 第六门禁族评估裁定（`sixth-gate-family-evaluation.md` §4.3）。执行路径：满足任一条件 → 按 Loop Rule 复触发（下轮 I2/I3 → I4）。

### 6.1 四类基线触发条件

| # | 触发条件 | 触发判据（可判定） | 复触发动作 |
|---|---------|-------------------|-----------|
| 1 | **CI 变红** | `invariant-gates` job fail（门禁④⑤ mjs）/ mvn test 中门禁①②③ surefire fail（`TestInvariantGate1SecureDefault` / `TestInvariantGate1SecureDefaultShell` / `TestInvariantGate2OrchestrationTimeout` / `TestInvariantGate2GatewayTimeout` / `TestInvariantGate3EntryPointCleanup`） | 定位失败族 → 下轮 I2/I3 裁决 → I4 修复 |
| 2 | **结构变更** | 新增/重命名 Default* 类（不入 §3.1 表即 red）/ 新增编排入口（不入 §3.2 表即 red）/ 新增或修改 ToolExecutor 实现（不入 §3.3 表或边界声明缺失即 red）/ entry-point 增删（§3.4） | 表完备性拦截 → 登记 + 裁决 → I4 |
| 3 | **周期复探** | **周期 = 30 天**（I6 裁定，理由：扫描成本近似零——mjs 秒级 + JUnit 门禁随 mvn test 零增量；结构变更已有表完备性即时拦截，周期复探主要兜底行为级漂移与 known-gaps 状态复核，短周期发现窗口小）。执行 = 五族门禁全量 + 四表完备性 + known-gaps drift 核对 | 发现缺口 → 下轮 I2/I3 → I4 |
| 4 | **CI fallback 补验** | GitHub 通道首次可用时（GitHub remote / Actions 触发建立）补验 `invariant-gates` job 实跑；**非空转判据** = since-mode 日志出现「扫描到 fix(nop-ai) commit: N」且 N>0（subject-only 后 `5ebad065e`/`c1362dc77` 不再被扫入） | 补验记录 + 失败则复触发 |

### 6.2 watch 项触发条件（I3 Follow-up 登记并入）

| 项 | 触发条件 | 触发动作 |
|----|---------|---------|
| AskOracleExecutor oracle client 落地 | oracle client 实现（移除 P2-MA1-011 fail-fast）时 | 补 SSRF 校验入口（gate-4 known-gaps 登记移除 → 门禁恢复实判），复触发 I2/I3/I4 |
| R-4-1 dnsResolver 接线 | 引入消费 `HttpClientConfig.dnsResolver` 的 HTTP client（如 Apache 族装配）时 | 接线 `SsrfGuardDnsResolver`（`nop-ai-toolkit/tools/ssrf/`，fail-closed），复触发 I4/I5 |
| R-2-2 plan/runtime 接线 | plan/runtime 包生产接线时（出现 `TaskRunner` 产品实现 / 引擎引用 `PlanExecutor.execute`） | 补 timeout 契约 + catalog §3.2 表登记（missing-declaration），复触发 I2/I5 |

### 6.3 第六门禁族候选复评估触发条件（消费 `2026-08-12-1700-1` 裁定 = `watch-only residual`，可判定，不得断裂）

1. **周期复探锚点**：周期复探执行时发现 **≥2 起**同族兄弟漏网实例（声明级或行为级，即门禁全绿但兄弟实例存在未重审缺口）→ 复核 tool-ize。
2. **事件锚点**：出现**第 1 起**「同族实例被修改后，其兄弟实例发生行为级回归且五族门禁全绿」（= 门禁机械面盲区实际击穿）→ 复核 tool-ize。
3. **结构性锚点**：Cycle 2 派生新不变式族（任何原因）且**首轮审计发现 ≥2 起兄弟漏网** → 将清扫纪律 tool-ize 并入新族门禁设计。

> 复核 tool-ize 后按评估文档 §3 检测方法候选规格（commit 面 / 审计流程两种形态）在 Cycle 2 / I1 裁定实现。
