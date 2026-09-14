# Nop AI Agent 扩展点矩阵

**日期**：2026-09-14（修订）
**范围**：`nop-ai-agent` 模块全部 Java 接口（`io.nop.ai.agent` 主源码，排除 `_gen`）
**状态**：active

**修订记录**：2026-09-14 依据 WI5 登记的 6 项勘误线索（`ai-dev/analysis/compare-agent-design/04-extension-capability-matrix.md` §⑤）与 live 代码复核修订：① `IContentGuardrail` 状态与实现列更新（`PromptInjectionGuardrail` / `RuleGraphGuardrail` 已落地）；②③ 接口计数与扩展点数量重算（67 → 72，含 4 个接口迁出登记与 5 个 live 接口补登）；④ `REASONING_CHUNK` 死点标注；⑤ `HookToMiddlewareAdapter` 死代码标注；⑥ "主要消费者"列按实际消费类抽查更新并登记免责注记。

---

## 一、设计结论

1. 本篇是 vision §4「渐进式增强」的**反向审计工具**——用一张表回答"每个扩展点当前是否闭合"
2. 全模块共 **72 个顶层 public interface**（`io.nop.ai.agent` 主源码，排除 `_gen`；另有 1 个嵌套接口 `StoreFactory`，共 73 个声明，2026-09-14 live 复核），按角色四分：核心契约 4 / 真正扩展点 57 / 回调契约 4 / NoOp-only 2 / 预留原语 1 / 矩阵外接口 4（错误码容器 `NopAiAgentErrors` + 3 个 guardrail 测试 SPI，不参与闭合分析）
3. 当前 **2 处半闭合** + **1 处未闭合** + **5 处命名异味** + **3 处接线缺口/死代码** 是 vision §4 落实的主要缺口
4. 本篇与 `glossary.md`（术语层）、`nop-ai-agent-roadmap.md`（状态层）互补——glossary 列概念，roadmap 列阶段，本篇**列闭合度**

## 二、定位与边界

### 2.1 本篇回答什么

- `glossary.md` 不回答：每个接口有几个实现？消费者在哪？是否已闭合？
- `nop-ai-agent-roadmap.md` 不回答：每个具体扩展点的当前落地状态？（roadmap 只列 Layer 级粗粒度状态）
- 本篇回答：**每个接口的 NoOp / 功能 / DB 实现 + 消费者注入点 + 闭合状态**

### 2.2 本篇不回答什么

- **不重复字段列表与代码签名**——看源码
- **不重复 Layer 阶段状态**——看 roadmap
- **不重复术语定义**——看 glossary
- **不展开实现细节**——本篇是闭合度索引，不是实现手册

### 2.3 阅读对象

- **架构审计者**：用本篇识别"接口已就位、消费者未落地"的断点
- **新扩展点设计者**：用本篇对照已有命名/三件套惯例
- **集成商**：用本篇查找"哪个接口有 DB 实现、哪个只有 InMemory"

## 三、接口分类（先分类后列表）

按"角色"分类，决定是否进入扩展点矩阵的主体：

| 类别 | 定义 | 数量 | 是否进入矩阵主体 |
|---|---|---|---|
| **核心契约** | 框架骨架，不可替换（即使有 default 实现也是单源） | 4 | ❌ 列在 §4.0 但不参与闭合分析 |
| **真正扩展点** | 消费者在框架内、实现可被集成商替换 | 57 | ✅ 主体 |
| **回调契约** | 框架只声明签名，由下游/插件实现 | 4 | ❌ 列在 §4.0 |
| **NoOp-only 扩展点** | 消费者已接通，但唯一实现是 NoOp（半闭合） | 2 | ✅ 主体（红色高亮） |
| **预留原语** | 接口 + NoOp + 功能实现均已落地，但零消费者 | 1 | ✅ 主体（红色高亮） |
| **矩阵外接口** | 错误码容器 / 测试 SPI，非扩展点 | 4 | ❌ 不进入矩阵 |

> 分类合计 68 = 4 + 57 + 4 + 2 + 1；另有 4 个矩阵外接口（`NopAiAgentErrors` 错误码容器 + `guardrail/test` 包 3 个测试 SPI：`AttackPlugin` / `AttackTransform` / `GuardrailGrader`）。模块顶层 public interface 总数 72 = 68 + 4（2026-09-14 live 复核）。

## 四、扩展点矩阵主体

### 4.0 不参与闭合分析的接口（核心契约 + 回调）

| 接口 | 类别 | 说明 |
|---|---|---|
| `IAgentEngine` | 核心契约 | 顶层 Gateway；唯一生产实现 `DefaultAgentEngine` |
| `IAgentExecutor` | 核心契约 | 执行策略；`ReActAgentExecutor` / `SingleTurnExecutor` 二选一 |
| `IAgentEventPublisher` | 核心契约 | 事件发布器；`DefaultAgentEventPublisher` 单源 |
| `ActorRegistry` | 核心契约 | Actor 注册表；`InMemoryActorRegistry` 单源（`NoOpActorRuntime` 不需要） |
| `IAgentEventSubscriber` | 回调契约 | 下游订阅契约；零生产实现，由集成商实现 |
| `IAgentLifecycleHook` | 回调契约 | Hook 回调契约；下游/XDSL 实现 |
| `IAgentMessageHandler` | 回调契约 | 消息处理回调；`@FunctionalInterface` |
| `TaskRunner` | 回调契约 | plan 执行 SPI；唯一消费者 `PlanExecutor`（包内注入），生产实现由下游/集成商提供（2026-09-14 复核：主源码零 `implements TaskRunner`） |

### 4.1 Layer 1 Core Interfaces（10 个扩展点）

> **L1 的契约**：系统运行的最低要求。`DefaultAgentEngine` 必须装配全部 L1 扩展点，且默认值是 functional（非 NoOp）。

| 接口 | 包 | NoOp / pass-through 默认 | 功能默认实现 | InMemory/DB 实现 | 主要消费者 | 状态 |
|---|---|---|---|---|---|---|
| `ISessionStore` | session | 无（直接默认 InMemory） | `InMemorySessionStore` | `FileBackedSessionStore` / `DBSessionStore` | DAE, RAE, AgentCompactionCoordinator | ✅ |
| `ITokenEstimator` | engine | 无 | `CalibratedTokenEstimator` | 无 | DAE, RAE, compact.* | ✅ |
| `IAiMemoryStore` | memory | 无 | `InMemoryAiMemoryStore` | `AdapterBackedAiMemoryStore` | ATEC, memory tools | ✅ |
| `IMemoryStoreProvider` | memory | 无 | `InMemoryMemoryStoreProvider` | `AdapterBackedMemoryStoreProvider` | DAE, RAE | ✅ |
| `IToolAccessChecker` | security | `AllowAllToolAccessChecker`（opt-out，触发 WARN） | `DefaultToolAccessChecker` | 无 | SecurityCheckpointChain（7-checkpoint）, RAE, DAE | ✅ |
| `IPathAccessChecker` | security | `AllowAllPathAccessChecker`（opt-out，触发 WARN） | `DefaultPathAccessChecker` | 无 | SecurityCheckpointChain（7-checkpoint）, RAE, DAE | ✅ |
| `IPermissionProvider` | security | `AllowAllPermissionProvider`（opt-out） | `DefaultPermissionProvider` | 无 | SecurityCheckpointChain（7-checkpoint）, RAE, DAE | ✅ |
| `IContentTrustEvaluator` | security | 无 | `DefaultContentTrustEvaluator` | 无 | `DefaultLevelHintsProducer` | ✅ |
| `IAuditLogger` | security | `NoOpAuditLogger`（触发 WARN） | `Slf4jAuditLogger` | 无 | DAE, RAE | ✅ |
| `ITenantResolver` | security | `NullTenantResolver` | `ThreadLocalTenantResolver` | 无 | 13 个 DB-backed 类 | ✅ |

> **L1 注 1**：`IAuditLogger` 在 `nop-ai-agent-security-audit-readiness-analysis.md` 中被识别为"曾错放 L4"——现已纠正到 L1（glossary 已同步）。
> **L1 注 2**：`ITenantResolver` 的"空实现"命名为 `NullTenantResolver` 而非 `NoOpTenantResolver`——是横切依赖的命名反例（见 §6）。

### 4.2 Layer 2 Execution Extensions（16 个扩展点）

> **L2 的契约**：扩展接口 + pass-through 默认。集成商按需替换。

| 接口 | 包 | NoOp / pass-through 默认 | 功能实现 | InMemory/DB 实现 | 主要消费者 | 状态 |
|---|---|---|---|---|---|---|
| `IAgentLifecycleHook` | hook | 无（回调契约） | （XDSL IEvalFunction 经 `EvalFunctionHookAdapter`） | 无 | IHookRegistry | ⚪ 见 §4.0 |
| `IHookRegistry` | hook | `NoOpHookRegistry` | `DefaultHookRegistry` | 无 | AgentHookInvoker（经 RAE / AgentExecutorResolver 注入） | ✅ |
| `ISkillProvider` | skill | `NoOpSkillProvider` | `FileSystemSkillProvider` | 无 | DAE, RAE, SkillResolver | ✅ |
| `ISkillCurator` | skill | `NoOpSkillCurator` | `LLMCurator` | 无 | DAE | ✅ |
| `ITalent` | talent | `NoOpTalent`（且 DAE 默认空 List） | 无 | 无 | DAE (List<ITalent>), RAE | 🟡 **半闭合** |
| `IModelRouter` | router | `PassThroughModelRouter`（直通实现） | `SmartModelRouter` | 无 | DAE, RAE | ✅ |
| `IToolCallRepairer` | repair | `NoOpToolCallRepairer` | `ChainRepairer`（4 stages） | 无 | DAE, RAE | ✅ |
| `IContextCompactor` | compact | `NoOpContextCompactor` | `PipelineCompactor`（默认）/ `MicroCompressionCompactor` | 无 | DAE, RAE | ✅ |
| `IContentGuardrail` | guardrail | `NoOpContentGuardrail`（DAE 默认装配，触发 WARN） | `PromptInjectionGuardrail` / `RuleGraphGuardrail`（opt-in，经 `DefaultAgentEngine.setContentGuardrail` / Builder 装配；`AgentPromptAssembly` INPUT/OUTPUT 两处调用 check） | 无 | DAE, RAE | ✅ |
| `IBudgetProvider` | budget | `NoOpBudgetProvider` | 无 | 无 | DAE, RAE | 🟡 **半闭合** |
| `IUsageRecorder` | usage | `NoOpUsageRecorder` | 无 | `DbUsageRecorder` | DAE, RAE | ✅ |
| `ICompletionJudge` | completion | `NoOpCompletionJudge` | `RuleBasedCompletionJudge` / `LlmCompletionJudge` | 无 | RAE | ✅ ⚠️ 接线缺口见 §6 |
| `ISecurityLevelResolver` | security | `NoOpSecurityLevelResolver`（触发 WARN） | `DefaultSecurityLevelResolver` | 无 | DAE, RAE | ✅ |
| `IPermissionMatrix` | security | `PassThroughPermissionMatrix`（触发 WARN） | `DefaultPermissionMatrix` | 无 | DAE, RAE | ✅ |
| `IAgentMiddleware` | middleware | 无（未注册时零开销直通 `invokeHooks`） | XDSL `<middlewares>` / `<filter-chain>` DSL 解析（`ResolvedFilterChain`） | 无 | AgentHookInvoker（经 `IHookRegistry.getMiddlewares`） | ✅ |
| `SecurityCheckpoint` | security | 无（链由 `AgentSecurityConsultation.buildCheckpointChain` 装配） | 7 个 checkpoint lambda（`AgentSecurityConsultation` 内） | 无 | `SecurityCheckpointChain`, `AgentSecurityConsultation` | ✅ |

> **L2 注**：`ICompressionStrategy` 在 glossary 标 L3（可插拔压缩策略，默认管道不使用）；本篇沿用 glossary 归类，见 §4.3。
> **L2 注（2026-09-14）**：`IModelSwitchedMessageWriter` 接口定义已迁至 `nop-ai-core`（`io.nop.ai.core.agent`），本模块保留 `NoOpModelSwitchedMessageWriter` / `DbModelSwitchedMessageWriter` 实现，按 live 口径不再计入本矩阵；`ILevelHintsProducer` 接口已于 plan 304 合并入 `ISecurityLevelResolver`（`DefaultLevelHintsProducer` 现为普通类），不再计入本矩阵。
> **L2 注（2026-09-14）**：`AgentLifecyclePoint.REASONING_CHUNK` **declared but never triggered**（全库零触发点，仅枚举 `AgentLifecyclePoint.java:11`、注册映射 `DefaultHookRegistry.java:169` 与注释存在；`AgentHookInvoker.java:58` 注释称其直调 `invokeHooks`，与实际不符）——流式输出路径未接线，见 §6.3。
> **L2 注（2026-09-14）**：`IBudgetProvider` 的"无功能实现"仅指 **main scope**；test scope 存在 `InMemoryBudgetProvider`（`nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/budget/InMemoryBudgetProvider.java:37`，`implements IBudgetProvider`），供测试装配使用，不计入生产闭合度。

### 4.3 Layer 3 Reliability Extensions（12 个扩展点）

| 接口 | 包 | NoOp / pass-through 默认 | 功能实现 | 文件/DB 实现 | 主要消费者 | 状态 |
|---|---|---|---|---|---|---|
| `ICheckpointManager` | reliability | `NoOpCheckpoint` | `ToolExecutionCheckpoint` | `FileBackedCheckpointManager` / `DBCheckpointManager` | DAE, RAE | ✅ |
| `IGoalTracker` | reliability | `NoOpGoalTracker` | `SessionGoalTracker` | 无 | DAE, RAE | ✅ |
| `ISustainer` | reliability | `NoOpSustainer` | `SisypheanSustainer` | 无 | DAE, RAE | ✅ |
| `IWaitCoordinator` | reliability | `NoOpWaitCoordinator` | `DefaultWaitCoordinator` | 无 | RAE（WAIT_FOR 挂起原语） | ✅ |
| `IApprovalGate` | security | `AutoApproveGate`（触发 WARN，**未分离 NoOp 命名**） | `DefaultApprovalGate` | 无 | DAE, RAE | ✅ |
| `IDenialLedger` | security | `NoOpDenialLedger`（触发 WARN） | `DefaultDenialLedger` | `DBDenialLedger` | DAE, RAE | ✅ |
| `IPostDenialGuard` | security | `PassThroughPostDenialGuard`（触发 WARN） | `DefaultPostDenialGuard` / `FingerprintPostDenialGuard` | 无 | DAE, RAE | ✅ |
| `IConflictStrategy` | conflict | 无（`FailFastStrategy` 即默认） | `FailFastStrategy` | 无（`CoordinationBusStrategy` 为 successor） | DAE, RAE | ✅ |
| `IWriteIntentRegistry` | conflict | 无（`InMemoryWriteIntentRegistry` 即默认） | `InMemoryWriteIntentRegistry` | 无（DB 为 successor） | DAE, RAE | ✅ |
| `ICompressionStrategy` | compact | 无 | `MicroCompressionCompactor` / `Layer2TurnPruningStrategy` / `Layer3FullSummaryStrategy` | 无 | `PipelineCompactor` | ✅ |
| `ISpillStore` | compact | 无（`InMemorySpillStore` 即默认，session 宿主 lazy 装配，无 null-store 分支） | `InMemorySpillStore` | 无（持久化为 successor） | `AgentToolDispatcher` (写), `ReadSpillExecutor` (读) | ✅ |
| `ICompactionSnapshotArchive` | session | 无（未装配时为 null archive 分支，非失败） | `InSessionCompactionSnapshotArchive` | 无 | `AgentCompactionCoordinator`, `AgentSession` | ✅ |

> **L3 注 1**：`ICircuitBreaker` ↔ `ISustainer` 是**部署层文档约束**（非运行时硬性互斥 guard，plan 212 裁定）。
> **L3 注 2**：L3 有 3 处 **未分离 NoOp 命名**——`AutoApproveGate` / `PassThroughPermissionMatrix`（L2）/ `PassThroughPostDenialGuard`。另 2 处（`AlwaysClosed` / `NoRetryPolicy`）随接口迁至 `nop-ai-core`（`io.nop.ai.core.reliability`），不再计入本矩阵。详见 §6。
> **L3 注 3（2026-09-14）**：`ICircuitBreaker` / `IRetryPolicy` 接口定义已迁至 `nop-ai-core`（`io.nop.ai.core.reliability`，含 `AlwaysClosed` / `ThresholdBreaker`、`NoRetryPolicy` / `StandardRetryPolicy` 实现），本模块仍消费（`LlmCallCoordinator` 等），按 live 口径不再计入本矩阵。

### 4.4 Layer 4 Platform Extensions（23 个扩展点）

> **L4 的契约**：opt-in 扩展点，shipped 默认 NoOp 零回归。

| 接口 | 包 | NoOp 默认 | 功能实现 | DB 实现 | 主要消费者 | 状态 |
|---|---|---|---|---|---|---|
| `IActorRuntime` | runtime | `NoOpActorRuntime` | `InMemoryActorRuntime` | 无 | DAE | ✅ |
| `ISessionTakeoverLock` | runtime/lock | `NoOpSessionTakeoverLock` | 无 | `DbSessionTakeoverLock` | DAE | ✅ |
| `IDaemonCoordinator` | runtime/coordination | `NoOpDaemonCoordinator` | 无 | `DbDaemonCoordinator` | TTSD | ✅ |
| `IRecoveryManager` | runtime/recovery | `NoOpRecoveryManager` | `ScheduledRecoveryManager` | 无 | DAE (held) | ✅ |
| `IOrphanRecoveryHandler` | runtime/recovery | `NoOpOrphanRecoveryHandler` | `DefaultOrphanRecoveryHandler` | 无 | SRM | ✅ |
| `ISessionTimeoutHandler` | runtime/recovery | `NoOpSessionTimeoutHandler` | `DefaultSessionTimeoutHandler` | 无 | SRM | ✅ |
| `ITeamTaskRecoveryHandler` | runtime/recovery | `NoOpTeamTaskRecoveryHandler` | `DefaultTeamTaskRecoveryHandler` | 无 | SRM | ✅ |
| `IFencingTokenService` | fencing | `NoOpFencingTokenService` | `DefaultFencingTokenService` | 无 | **无** | 🔴 **未闭合** |
| `IResourceGuard` | quota | `NoOpResourceGuard` | `DefaultResourceGuard` | 无 | IAR, TeamManager | ✅ |
| `IContributionRegistry` | contribution | `NoOpContributionRegistry` | `InMemoryContributionRegistry` | 无 | DAE (HOOK), RAE (PROMPT) | ✅ |
| `ISandboxBackend` | security | `NoOpSandboxBackend` | `DockerSandboxBackend` | 无 | DAE, RAE | ✅ |
| `IStorageAdapter` | memory | `NoOpStorageAdapter` | `InMemoryStorageAdapter` | 无 | ABMS, ABMSP | ✅ |
| `IEmbeddingAdapter` | memory | `NoOpEmbeddingAdapter` | `InMemoryEmbeddingAdapter` | 无 | ABMS, ABMSP | ✅ |
| `IVectorAdapter` | memory | `NoOpVectorAdapter` | `InMemoryVectorAdapter` | 无 | ABMS, ABMSP | ✅ |
| `IMemberSpawner` | team | `NoOpMemberSpawner` | `DefaultMemberSpawner` | 无 | TTSD, TTFO, tools, steps | ✅ |
| `ITeamAclChecker` | team | `NoOpTeamAclChecker` | `DefaultTeamAclChecker` | 无 | DAE, RAE, ATEC | ✅ |
| `ITeamManager` | team | `NoOpTeamManager` | 无 | `InMemoryTeamManager` / `DbTeamManager` | DAE, RAE, ATEC, TTFO, TTSD | ✅ |
| `ITeamTaskStore` | team | `NoOpTeamTaskStore` | 无 | `InMemoryTeamTaskStore` / `DbTeamTaskStore` | DAE, RAE, ATEC, TTFO, TTSD, steps | ✅ |
| `IReductionStrategy` | team/flow | 无（`AllMustSucceedReduction` 即默认） | `AllMustSucceedReduction` | 无 | MemberFanOutDispatcher, fan-out steps | ✅ |
| `ITaskMemberRouter` | team/flow | `NoOpTaskMemberRouter`（**命名误导**，实为功能默认） | 无 | 无 | TTFO, TTSD | ✅ |
| `ITeamTaskSchedulerDaemon` | team/scheduler | `NoOpTeamTaskSchedulerDaemon` | `TeamTaskSchedulerDaemon` | 无 | DAE (held) | ✅ |
| `IAgentMessenger` | message | `NoOpAgentMessenger` | `LocalAgentMessenger` | （`DBMessageService` 实现 `IMessageService`） | DAE, RAE, ATEC, tools | ✅ |
| `IMailbox` | message | `NoOpMailbox` | `DeferredAckMailbox` | 无 | AgentActor, MailboxMessageHandler, IAR | ✅ |

> **L4 注 1**：`ITeamManager` / `ITeamTaskStore` 的 NoOp 默认对**写操作抛 UnsupportedOperationException**（非 silent no-op，符合 Minimum Rules #24）。
> **L4 注 2**：`IAgentMessenger` 的 DB 路径间接——`DBMessageService` 实现平台 `IMessageService`，被 `LocalAgentMessenger` 包装，非直接实现 `IAgentMessenger`。

### 4.5 消费者缩写表

| 缩写 | 全名 |
|---|---|
| DAE | `DefaultAgentEngine` |
| RAE | `ReActAgentExecutor` |
| ATEC | `AgentToolExecuteContext` |
| SRM | `ScheduledRecoveryManager` |
| IAR | `InMemoryActorRuntime` |
| TTSD | `TeamTaskSchedulerDaemon` |
| TTFO | `TeamTaskFlowOrchestrator` |
| ABMS / ABMSP | `AdapterBackedAiMemoryStore` / `AdapterBackedMemoryStoreProvider` |

> **免责注记（2026-09-14）**：`DAE` / `RAE` 为**粗粒度默认标注**——实际消费点已散入 6+ 个协作者类（`IHookRegistry` / `AgentHookInvoker` / security chain / team 编排 / 工具执行器等），"主要消费者"列以最高频消费面概括，不逐行穷举；精确消费分布见各接口 javadoc 与源码 grep。本列仅对失真最严重的行（`IHookRegistry`、`IToolAccessChecker` / `IPathAccessChecker` / `IPermissionProvider`、`ISessionStore`）改为实际消费类名。

## 五、闭合状态分析

### 5.1 闭合度分布

| 状态 | 计数 | 占比 | 含义 |
|---|---|---|---|
| ✅ 闭合 | 61 | 89.7% | 有消费者 + 有功能实现（含 InMemory/DB/File） |
| 🟡 半闭合 | 2 | 2.9% | 有消费者 + **main scope 唯一实现是 NoOp**（不计 test scope 装配实现，如 `InMemoryBudgetProvider`） |
| 🔴 未闭合 | 1 | 1.5% | **零消费者**（纯原语预留） |
| ⚪ 回调契约 | 4 | 5.9% | 下游/插件实现，不参与闭合分析 |

> 合计 68 = 模块 72 个顶层 public interface − 4 个矩阵外接口（2026-09-14 修订后重算）。

### 5.2 🟡 半闭合清单（vision §4 的主要落实缺口）

| 接口 | Layer | 消费者 | 唯一实现 | 期望 successor |
|---|---|---|---|---|
| `ITalent` | L2 | DAE (List), RAE | `NoOpTalent`（且 DAE 默认空 List） | 业务侧动态工具集准入实现 |
| `IBudgetProvider` | L2 | DAE, RAE | `NoOpBudgetProvider`（main scope；test scope 另有 `InMemoryBudgetProvider`） | `DbBudgetProvider`（基于 cost 数据库的预算闸门） |

**评估**：两者都是 vision §4「更多假定通过外部 XDSL 模型逐步引入」的合法 successor，**不算违反**渐进式原则。但应在 roadmap 显式标记为"半闭合扩展点"，便于审计。`IContentGuardrail` 已于 2026-09-14 移出本清单（`PromptInjectionGuardrail` / `RuleGraphGuardrail` 功能实现已落地，见 §4.2）。

### 5.3 🔴 未闭合清单（设计性主动预留）

| 接口 | Layer | 已落地实现 | 状态 | 设计意图 |
|---|---|---|---|---|
| `IFencingTokenService` | L4 | `NoOpFencingTokenService` + `DefaultFencingTokenService` | 全代码库**零字段消费者**（仅 `IMemberSpawner.java`、`ITaskMemberRouter.java` 的 javadoc 引用） | 接口 Javadoc 明示："reserved for first consumer (scope_claim / Compaction) successor" |

**评估**：这是**设计上主动预留**的原语切片（plan 235），不是缺陷。但它与 §5.4 的 `conflict` 包形成"明日的抽象原语 vs 今日的具象实现"并存——`fencing` 是抽象令牌，`conflict` 是文件写意图注册表。**建议**：要么尽快接入 consumer（如让 `IWriteIntentRegistry` 在写校验时校验 fencing token），要么在 roadmap 显式标记为"reserved primitive, awaiting first consumer"，避免后续误读为遗留废弃。

### 5.4 ⚪ 回调契约清单（不参与闭合分析）

| 接口 | 角色 | 实现来源 |
|---|---|---|
| `IAgentEventSubscriber` | 事件订阅 | 集成商实现；`DefaultAgentEventPublisher.addSubscriber` 暴露 |
| `IAgentLifecycleHook` | Hook 回调 | XDSL `<hooks>` 经 `IEvalFunction` + `EvalFunctionHookAdapter`；或集成商直接 `IHookRegistry.register` |
| `IAgentMessageHandler` | 消息处理 | `@FunctionalInterface`；集成商 lambda 实现 |
| `TaskRunner` | plan 执行 SPI | 集成商实现（2026-09-14 复核：主源码零 `implements TaskRunner`，唯一消费者 `PlanExecutor`） |

## 六、命名与接线一致性反例

按"是否影响闭合度"分两类。**这些反例不影响闭合**（消费者与功能实现都齐全），但破坏"接口 + NoOp 三件套"的命名一致性预期。

### 6.1 未分离 NoOp 命名（3 处，本模块内）

| 接口 | 当前命名 | 应有命名（按 NoOp 范式） | 备注 |
|---|---|---|---|
| `IApprovalGate` | `AutoApproveGate` | `NoOpApprovalGate` | 语义对，命名偏离 |
| `IPermissionMatrix` | `PassThroughPermissionMatrix` | `NoOpPermissionMatrix` | 语义对，命名偏离 |
| `IPostDenialGuard` | `PassThroughPostDenialGuard` | `NoOpPostDenialGuard` | 语义对，命名偏离 |

> **注（2026-09-14）**：原清单另 2 处（`ICircuitBreaker`→`AlwaysClosed`、`IRetryPolicy`→`NoRetryPolicy`）随接口迁至 `nop-ai-core`（`io.nop.ai.core.reliability`），不再计入本模块矩阵。

**评估**：`AutoApproveGate` 在语义上是"NoOp 等价物"——pass-through、不做实际工作。命名为 `NoOp*` 会更清晰地表达"这是 shipped 默认、不是功能实现"。但当前命名也有合理性（更具业务语义）。**建议**：在 glossary 显式登记此 3 处为"NoOp 等价物命名变体"，避免审计时误判。

### 6.2 NoOp 命名误导（2 处）

| 接口 | 命名 | 实际语义 | 问题 |
|---|---|---|---|
| `ITaskMemberRouter` | `NoOpTaskMemberRouter` | **功能性默认**——按 bound > spawn > empty 算法做单成员路由 | NoOp 命名暗示"什么都不做"，但实际是默认算法实现 |
| `ITenantResolver` | `NullTenantResolver` | **空实现**（null tenant = 全部可见） | 横切依赖命名偏离 NoOp 范式 |

**评估**：`NoOpTaskMemberRouter` 是本矩阵最严重的命名误导——读者可能误以为它是 no-op 而跳过算法理解。**建议**：考虑重命名为 `DefaultTaskMemberRouter` 或 `SingleMemberRouter`。

### 6.3 接线缺口（3 处）

| 接口 | 现象 | 后果 |
|---|---|---|
| `ICompletionJudge` | DAE 未暴露 `setCompletionJudge`，`resolveExecutor` Builder 链也未传 `.completionJudge(...)` | **引擎运行时永远走 NoOp**——功能实现（`RuleBasedCompletionJudge` / `LlmCompletionJudge`）只能由调用方绕过 DAE、自行构造 `ReActAgentExecutor.Builder` 注入 |
| `AgentLifecyclePoint.REASONING_CHUNK`（死点） | **全库零触发点**（2026-09-14 复核）：仅枚举 `AgentLifecyclePoint.java:11`、注册映射 `DefaultHookRegistry.java:169` 与注释存在；`AgentHookInvoker.java:58` 注释称其"continue to call invokeHooks directly"，与实际不符 | 读者误以为流式输出路径已接线；挂载于该点的 hook 永不触发（`ReActAgentExecutor.java:1289`、`AgentExecutionResult.java:111` 仅为注释提及） |
| `DefaultHookRegistry.HookToMiddlewareAdapter`（死代码） | private static 类（`DefaultHookRegistry.java:122`）构造器（`:125`）全库零引用——无任何 `new HookToMiddlewareAdapter(...)` 实例化点 | 死代码；`DefaultHookRegistry.java:30-31` 注释声称 register() 已"wraps the hook in a middleware delegate"，与实际行为不符（middleware 链实际由 `AgentHookInvoker.executeWithMiddleware` 经 `IHookRegistry.getMiddlewares` 构建） |

**评估**：`ICompletionJudge` 是 vision §4「扩展通过添加接口实现」的**违反**——接口已就位、功能实现已就位，但缺少装配入口。**建议**：DAE 增加 `setCompletionJudge` setter 并在 `resolveExecutor` Builder 链传递。`REASONING_CHUNK` 与 `HookToMiddlewareAdapter` 属已登记死点/死代码（2026-09-14），**建议**：前者接入流式输出路径或从执行面收窄合同；后者删除或真正接入注册路径。

## 七、与渐进式设计原则的关系

### 7.1 vision §4 的反向审计工具

`00-vision.md` 约束 4「渐进式增强」要求：
- 内部运行时实现最简化
- 更多假定通过外部 XDSL 模型逐步引入
- 扩展通过添加接口实现，不通过阶段切换

**本矩阵把这三条要求转成可审计的指标**：

| vision §4 要求 | 本矩阵对应指标 |
|---|---|
| 内部运行时最简化 | L1 全部扩展点都有 functional 默认（不是 NoOp）→ ✅ |
| 渐进引入 | L2/L3/L4 扩展点都有 NoOp/pass-through shipped 默认 → ✅ |
| 扩展通过添加接口实现 | 半闭合 (🟡) + 未闭合 (🔴) 接口必须有 successor 计划 → ⚠️ 见 §5 |

### 7.2 「接口已就位、消费者未落地」是渐进式设计的合法状态

vision §4 不要求所有扩展点立即闭合——**"接口已就位、消费者未落地"是渐进式设计的合法中间状态**，前提是：

1. **roadmap 显式标记为 successor**（避免被误读为废弃）
2. **NoOp 默认保证零回归**（shipped 默认行为不变）
3. **plan 编号可追溯**（每个扩展点的引入都有 plan 文档）

**当前缺口**：roadmap 只列 Layer 级粗粒度状态，不列 per-扩展点的闭合度。本矩阵 §5 的清单应回写到 roadmap 的 successor 跟踪机制。

### 7.3 三件套范式（NoOp + 功能 + 可选 DB）

本矩阵可视化三件套在不同 Layer 的强度：

| Layer | 三件套强度 | 典型模式 |
|---|---|---|
| L1 Core | 强制 functional 默认 | `Default*` 是 shipped 默认，`AllowAll*` / `NoOp*` 为 opt-out |
| L2 Execution | pass-through 默认 + 可选功能 | `NoOp*` 是 shipped 默认，`Default*` 为 opt-in |
| L3 Reliability | NoOp/pass-through + 功能 + 可选 DB | 同 L2，但 DB 持久化是多活部署的关键 |
| L4 Platform | NoOp shipped + InMemory + DB | 分布式部署的标配；NoOp 保证单进程零回归 |

## 八、维护策略

### 8.1 何时更新本矩阵

| 触发事件 | 更新内容 |
|---|---|
| 新增扩展点接口 | 新增行 + 标注闭合状态 |
| 接口添加新实现 | 更新「功能实现」/「DB 实现」列 |
| 接口获得首个消费者 | 状态 🔴 → ✅（关键里程碑） |
| 接口获得功能实现 | 状态 🟡 → ✅（关键里程碑） |
| 命名一致性调整 | 更新 §6 反例清单 |

### 8.2 与其他文档的同步关系

```
glossary.md (术语层)
   ↓ 概念来源
03-extension-matrix.md (本篇，闭合度层)
   ↓ 状态来源
nop-ai-agent-roadmap.md (Layer 阶段状态层)
   ↓ 实现来源
源码 (NoOp/功能/DB 实现的真实分布)
```

**单向数据流**：本矩阵从源码推导闭合度，**不反向影响源码**。当源码与矩阵冲突时，矩阵错——以源码为准并更新矩阵。

### 8.3 不做的事

- **不复制源码字段列表**——本篇是闭合度索引，不是 API 文档
- **不重复 glossary 术语定义**——只引用术语
- **不重复 roadmap 阶段划分**——只引用 Layer 归属
- **不展开实现细节**——只标注"有无功能实现"

## 九、拒绝了什么

| 被拒绝的方案 | 拒绝理由 |
|---|---|
| **把矩阵拆到每篇子模块文档** | 子模块文档只看到局部，无法识别跨包断点（如 fencing 零消费者） |
| **在 glossary 中加闭合度列** | glossary 是稳定的术语表，闭合度是动态状态，混在一起互相污染 |
| **在 roadmap 中加 per-扩展点状态** | roadmap 自己声明"是编排层，不是 execution plan"，加 per-扩展点状态会让 roadmap 失去粗粒度索引价值 |
| **生成代码图（PlantUML/Mermaid 大图）** | 大图维护成本高、AI 读取困难；表格更易维护且可 grep |
| **加评分（如"健康度 8/10"）** | 评分是结论性的、易过时；矩阵是事实性的，由读者自行判断 |

## 十、与其他文档的关系

- `00-vision.md` §4 渐进式增强约束 — 本篇是该约束的反向审计工具
- `01-architecture-baseline.md` §4 核心对象职责契约 — 本篇是其"闭合度"补充
- `glossary.md` 核心接口表 — 本篇引用其术语和 Layer 归属
- `nop-ai-agent-roadmap.md` Phase Status — 本篇为其提供 per-扩展点细粒度
- `nop-ai-agent-security-audit-readiness-analysis.md` — 唯一同款"渐进式审计"文档，本篇方法论推广自该文档
- 源码 `io.nop.ai.agent.*` — 本矩阵的事实来源
