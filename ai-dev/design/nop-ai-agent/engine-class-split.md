# Engine Class Split — nop-ai-agent 三个超大生产文件 design-first 拆分决策

> Status: final design（实施中如行号/成员盘点有出入，以每日日志映射表复核为准）
> Reviewed: 2026-08-01
> Source: audit MA4.2-05（`ai-dev/audits/2026-07-31-0539-arm-MA4.2-nop-ai-style.md`）、plan `2026-08-01-0441-1-arm-ma4-2-05-engine-split.md`
> Review Stamp: 独立子 agent（fresh session，映射表逐条核对，verdict 见 daily log 2026-08-01）——详见每日日志

## 1. 决策摘要

对 audit MA4.2-05 点名的三个超长生产文件做**同包内部结构拆分**（行为零变化，公共契约保持，每个提取类具有独立职责叙述）：

| 文件 | 现状行数 | 目标行数 | 提取类数量 | 提取类 |
|------|---------|---------|-----------|--------|
| `ReActAgentExecutor.java` | 3728 | ~877（<1000） | 9 | `ReActAgentExecutorBuilder`、`LlmCallCoordinator`、`AgentHookInvoker`、`AgentSecurityConsultation`、`AgentCompactionCoordinator`、`AgentToolPlanResolver`、`AgentPromptAssembly`、`AgentToolDispatcher`、`AgentLoopGuard` |
| `DefaultAgentEngine.java` | 3681 | ~845（<1000） | 8 | `DefaultAgentEngineConfig`、`AgentSessionLifecycle`、`AgentCallDelegate`、`SessionLockRenewal`、`AgentExecutorResolver`、`AgentSessionSupport`、`AgentTeamBinder`、`AgentStartupWarnings` |
| `TeamTaskSchedulerDaemon.java` | 1108 | ~923（<1000） | 1 | `TaskDispatchCoordinator` |

三个主类拆分后均 <1000 行；每个提取类均 <1000 行（最大提取类 `DefaultAgentEngineConfig` ~950 行，含全部可选依赖 setter/getter 与 javadoc）。所有提取类落在与主类**同一包**，不新建跨模块 API，不触碰 `IAgentExecutor`/`IAgentEngine` 接口。

## 2. ReActAgentExecutor（3728 → ~877）

### 2.1 提取决策（9 个提取类）

| 提取类 | 职责叙述 | 移入成员（原行号区间，1-based） | 移入字段（原字段） |
|--------|---------|-------------------------------|-------------------|
| `ReActAgentExecutorBuilder` | 执行器构造 DSL：全部可选依赖的链式装配与默认值决策（`build()` 内完成 NoOp 默认选择与校验） | nested `Builder` :768-1344（含全部 40 个 setter 方法 + `build()` + 类 javadoc） | Builder 自身 44 字段（随类整体移动） |
| `LlmCallCoordinator` | 单次 LLM 调用生命周期：电路断路器检查 → 重试循环（RETRY/STOP/FALLBACK）→ 超时包装（`callChatWithTimeout`）→ 模型切换审计键（`buildModelKey`）→ 电路感知 fallback 扫描 | `doLlmCallWithRetry` :637-741、`LlmCallResult` :743-763、`buildModelKey` :2325-2336、`callChatWithTimeout` :2338-2380、`parseToolCallId` :2382-2397、`resolveCircuitAware` :2399-2511、`MAX_FALLBACK_SCAN` :2513-2523、`sleepBackoff` :2526-2544 | `chatService`、`retryPolicy`、`circuitBreaker`、`modelRouter`、`llmTimeoutMs`、`timeoutExecutor`；注入 `AgentHookInvoker`（失败路径 `invokeOnError`/`publishErrorEvent` 委托） |
| `AgentHookInvoker` | 生命周期 hook 观察者循环 + 中间件洋葱链（`executeWithMiddleware`）+ 事件发布（`publishEvent`/`publishErrorEvent`） | `executeWithMiddleware` :2766-2794、`invokeHooks` :2796-2834、`invokeOnError` :2836-2842、`vetoReason` :2844-2849、`publishEvent` :2851-2856、`publishErrorEvent` :2858-2863 | `hookRegistry`、`eventPublisher` |
| `AgentSecurityConsultation` | 分发路径安全咨询链：7 个 deny checkpoint 的链装配（`buildCheckpointChain`）、Layer 1 路径访问检查、Layer 2 分级咨询、Layer 3 审批、Layer 2 写冲突策略、拒绝账本阈值处理（`handleDenialAndCheckThreshold`）与指纹工具 | `buildCheckpointChain` :455-635、`handleDenialAndCheckThreshold` :2259-2323、`extractArguments` :2546-2553、`resolveWorkDirString` :2555-2562、`checkPathAccess` :2865-2898、`checkLayer2Consultation` :2900-2953、`checkLayer3Approval` :2955-2999、`checkWriteConflict` :3001-3116、`resolveAbsolute` :3118-3131、`SecurityConsultationOutcome` :3693-3727 | `postDenialGuard`、`auditLogger`、`toolAccessChecker`、`permissionProvider`、`pathAccessChecker`、`securityLevelResolver`、`permissionMatrix`、`approvalGate`、`denialLedger`、`conflictStrategy`、`writeIntentRegistry`；注入 `AgentHookInvoker`（事件发布委托） |
| `AgentCompactionCoordinator` | 上下文压缩编排：压缩触发判定（token 百分比/消息数）、maxContextTokens 解析、压缩执行 + COMPACTION checkpoint + 会话同步 | `shouldTriggerCompaction` :2676-2682、`resolveMaxContextTokens` :2684-2690、`performCompaction` :2692-2764 | `contextCompactor`、`checkpointManager`、`sessionStore`、`tokenEstimator`；注入 `AgentHookInvoker`（PRE_COMPACT/POST_COMPACT 钩子） |
| `AgentToolPlanResolver` | 工具可见性计划：tag 过滤构建 LLM 可见工具列表（`buildToolDefinitions`）、有效工具集/路径根/路径规则继承计算（parent ∩ 子配置钳制） | `buildToolDefinitions` :3159-3257、`intersects` :3259-3271、`computeEffectiveAllowedTools` :3273-3319、`resolveWorkDir` :3321-3335、`computeEffectivePathRoots` :3337-3399、`computeOwnDeclaredPathRoots` :3401-3417、`computeEffectivePathRules` :3419-3470、`isUnderAnyRoot` :3472-3496、`toToolDefinition` :3498-3504 | `toolManager` |
| `AgentPromptAssembly` | 执行前系统提示装配：talents 咨询、skills 解析装配、PROMPT 贡献合并、输入/输出 guardrail 检查、ChatOptions 构建、系统指令注入 | `checkInputGuardrail` :3133-3147、`extractLastUserContent` :3149-3157、`consultTalents` :3506-3550、`injectSystemInstruction` :3552-3559、`consultSkills` :3561-3613、`consultPromptContributions` :3615-3656、`buildChatOptions` :3658-3681；新方法 `assembleExecutionSetup(ctx, agentModel, agentSession, toolDefs)`（execute() :1374-1378 内联块迁入） | `talents`、`skillProvider`、`contributionRegistry`、`contentGuardrail`、`toolManager`；注入 `AgentToolPlanResolver`（`toToolDefinition` 委托） |
| `AgentToolDispatcher` | 工具扇出执行：toolExecCtx 构建、带超时的并行 `callTool` 扇出、中断语义 join、结果提交 + TOOL_EXECUTION checkpoint + 会话同步、hook 点包装 | 新方法 `prepareDispatchContext(ctx, agentModel, agentName, sessionId)`（execute() :1816-1851 内联块迁入）、新方法 `executeAllowedCalls(ctx, agentName, sessionId, allowedCalls, execStartTime, checkpointSeq)`（execute() :1895-2123 内联块迁入）、`ToolCallOutput` :3683-3691、新方法 `drainSteering`（execute() :2142-2150 内联块迁入） | `toolManager`、`engine`、`messenger`、`teamManager`、`teamTaskStore`、`teamAclChecker`、`memoryStoreProvider`、`toolTimeoutMs`、`sessionStore`、`checkpointManager`；注入 `AgentHookInvoker`；`DEFAULT_MAX_REENTRIES` 常量保留主类（public 常量，提取类以 `ReActAgentExecutor.DEFAULT_MAX_REENTRIES` 引用） |
| `AgentLoopGuard` | 循环治理终止处理器：denial-ledger 暂停、goal-tracker STUCK 升级、forced-stop 硬保护与终态事件 | `handleSessionPaused` :2564-2581、`handleGoalStuck` :2583-2608、`shouldForceStop` :2635-2651、`handleForcedStop` :2653-2674 | `denialLedger`、`tokenEstimator`；注入 `AgentHookInvoker`、`AgentCompactionCoordinator`（forced-stop 最终压缩委托） |

### 2.2 主类保留

- 全部公共 API：`execute()`（:1357-2250 保留，内联块 A/B/C/D 迁出后 ~559 行）、`builder()` 工厂（返回 `ReActAgentExecutorBuilder`）、`getSandboxBackend()`、5 个公共常量（`DEFAULT_MAX_CONTEXT_TOKENS` 等——`PipelineCompactor:158` 引用保持不动）
- 编排逻辑：reactLoop 骨架（迭代开始治理检查顺序、预算快照、路由、模型切换检测、token 记账、completion-judge 分支、dispatch-loop checkpoint 链求值、sustain 咨询、终态变更与 POST_CALL 事件）、`handleCancellation`
- 主类字段：`tokenEstimator`、`completionJudge`、`goalTracker`、`sustainer`、`sessionStore`、`checkpointManager`、`toolCallRepairer`、`checkpointChain` + 9 个提取类实例（helper 注入点）；多类共用的 `sessionStore`/`checkpointManager`/`tokenEstimator` 以构造注入在各提取类重复持有（同一对象引用）

### 2.3 execute() 内联块分解（A/B/C/D）

| 块 | 原行号 | 行数 | 目标 |
|----|--------|------|------|
| A 执行前装配（toolDefs + talents + skills + contributions + options） | :1374-1378 | ~40 | `AgentPromptAssembly.assembleExecutionSetup(...)` |
| B memoryStore + toolExecCtx 构建 | :1816-1851 | ~36 | `AgentToolDispatcher.prepareDispatchContext(...)` |
| C 扇出 + 工具结果处理 | :1895-2123 | ~228 | `AgentToolDispatcher.executeAllowedCalls(...)` |
| D 输出 guardrail 检查 | :1727-1757 | ~31 | `AgentPromptAssembly.checkOutputGuardrail(...)` |

### 2.4 行数预算（以 Phase 1 实测为准）

- 移出成员（方法+嵌套类，含 javadoc）：2292 行
- 移出字段（含 javadoc）：~130 行
- 移出 import：~100 行
- execute() 内联块迁出：335 行
- 主类残留：3728 − 2292 − 130 − 100 − 335 − 894(execute) = **~312 行脚手架** + execute() 残留 **~559 行** + `handleCancellation` 6 行 ≈ **877 行 < 1000**（余量 ~123 行）

## 3. DefaultAgentEngine（3681 → ~814）

### 3.1 提取决策（8 个提取类）

| 提取类 | 职责叙述 | 移入成员 | 移入字段 |
|--------|---------|---------|---------|
| `DefaultAgentEngineConfig` | 引擎可选依赖装配面：全部可选 SPI 依赖字段 + 带完整 javadoc 的 setter/getter（构造器与 Builder 之外唯一合法的装配入口） | 区域 :859-1816 的 setter/getter 群（~30 setter + ~28 getter，含 javadoc；messenger 群与 getMailboxFactory 除外） | 对应可选依赖字段（~35 个，含 `actorRuntime`、`teamManager`、`recoveryManager`、lock 参数等） |
| `AgentSessionLifecycle` | 会话生命周期编排：`resumeSession`/`restoreSession` 完整实现（锁获取、会话恢复、消息注入、执行 dispatch）+ 公共执行上下文构建 + 记忆注入 + 取消事件发布 + 锁安静释放/终态判定 | `resumeSession` :2588-2755、`restoreSession` :2758-2932、`buildBaseExecutionContext` :2485-2519、`buildBudgetedMemorySection` :2529-2551、`formatMemorySection` :2560-2585、`CancelHandle` :2212-2237、`publishCancelRequested` :2150-2156、`publishCancelled` :2158-2163、`releaseLockQuietly` :1843-1851、`isTerminalStatus` :3094-3101 | 依赖注入：`config`、`sessionStore`、`eventPublisher`、`Supplier<ExecutorService>`（getAgentExecutor 保留主类，以 supplier 注入避免环）等（构造参数） |
| `AgentCallDelegate` | call-agent 消息委托：消息处理器注册、envelope 处理、结果提取 | `registerCallAgentHandler` :954-971、`handleCallAgentRequest` :989-1051、`extractFinalAssistantMessage` :1059-1072、`getMessenger` :1078-1080 | `messenger`（构造注入） |
| `SessionLockRenewal` | takeover 锁续期：调度器解析、续期循环、租约丢失处理、安静取消 | `getLockRenewExecutor` :1862-1872、`startLockRenewal` :1908-1917、`renewOnceSafe` :1927-1940、`handleLeaseLost` :1953-1965、`cancelLockRenewalQuietly` :1973-1982 | lock 相关字段（构造注入） |
| `AgentExecutorResolver` | 执行器/检查器解析：有效工具/路径检查器组合（parent 约束钳制）、executor 构建、middleware 解析、hook 贡献注册 | `resolveEffectiveToolAccessChecker` :3120-3136、`resolvePerAgentPathChecker` :3178-3184、`resolveEffectivePathAccessChecker` :3192-3194（单参重载）+ :3217-3238、`resolveExecutor` :3240-3252 + 完整版 :3256-3330、`resolveHookContributions` :3352-3369、`resolveMiddlewares` :3386-3420 | 构造注入：`config` + 核心依赖 + `Supplier<ExecutorService>`（getAgentExecutor，:3319 消费） |
| `AgentSessionSupport` | 会话辅助：mailbox 确保、sessionId 解析、agent 模型加载 | `getMailboxFactory` :1105-1107、`ensureSessionMailbox` :3433-3454、`resolveSessionId` :3456-3469、`loadAgentModel` :3495-3510 | 构造注入 |
| `AgentTeamBinder` | 团队/成员自动绑定：预检、lead 绑定、member 绑定 + Actor 身份解析 | `precheckTeamDeclarations` :3542-3551、`autoBindTeam` :3559-3570、`autoBindLead` :3580-3606、`autoBindMember` :3616-3661、`resolveActorId` :3670-3680 | 构造注入：`config`、`sessionStore` |
| `AgentStartupWarnings` | 启动期安全默认值告警 + 默认压缩器解析（不并入 Config——合并后 Config 将超 1000 行阈值） | `warnIfInsecureDefaults` :737-816、`warnIfNoOpUsageRecorder` :833-841、`defaultPipelineCompactor` :695-701 | 构造注入：`config`（读装配） |

### 3.2 主类保留与 API 兼容策略

- **公共 API 100% 保留（委托模式）**：主类保留全部 30+ setter/getter 的**签名**（`public void setRetryPolicy(...)` 等），方法体改为一行委托 `config.setRetryPolicy(...)`（或 `lifecycle.xxx(...)`）；82 个测试文件的 445 处 `engine.setXxx/getXxx` 调用零改动。完整 javadoc 随实现移入 Config 类，主类委托方法带 `@see` 一行注释。
- **主类保留的编排**：`execute`/`doExecute`（doExecute :2271-2470 保留，内联块分解）、`cancelSession`、`forkSession`、`sendMessage`、`getSessionStatus`、`isClosed`、`getInstanceId`、`getEventPublisher`、`getMemoryInjectionBudgetTokens`、超时访问器、`getSessionTakeoverLock` 等
- **Nested class 裁定**：`Builder`（:472-615，~144 行）**保留**在主类（`TestSessionStoreForkMessageFilter:157/175` 直接 `new DefaultAgentEngine.Builder(...)`，API 兼容优先）；`CancelHandle` 随生命周期组移出
- doExecute 内联块分解：`buildBaseExecutionContext`/`precheckTeamDeclarations`/`releaseLockQuietly`/`isTerminalStatus`/`getAgentExecutor` 调用点改为 `sessionLifecycle.xxx(...)`/`teamBinder.xxx(...)` 委托（成员 relocation，白名单）；`getAgentExecutor` 本体**保留主类**（doExecute 直接消费），以 `Supplier<ExecutorService>` 注入生命周期类

### 3.3 行数预算（以 Phase 1 实测为准）

- 移出成员：accessors 群 ~958 + session 生命周期（resume/restore/上下文构建/记忆/取消/锁释放/终态）~510 + delegation ~95 + lock ~58 + resolver ~180 + session 辅助 ~52 + team 绑定 ~110 + startup 告警 ~96 ≈ **~2059 行**
- 主类残留：3681 − 2059 − 移出字段 ~250 − 移出 import ~100 ≈ **~1272 行**，其中公共委托方法约 120 行、核心入口与编排约 450 行、Builder 144 行、脚手架约 270 行
- 补充移出群（Phase 1 识别，全部为真实关注点）：
  1. `getAgentExecutor` :2007-2017 保留主类（-0，见 §3.2）
  2. doExecute 内联块迁往 `AgentSessionLifecycle`/`AgentTeamBinder` 委托（-80 行，调用点重写）
  3. 8 个公共构造函数收敛：签名保留，构造体委托单一私有构造器（-60 行）
  → 残余 ≈ **~845 行 < 1000**（余量 ~155 行）——预算可达

## 4. TeamTaskSchedulerDaemon（1108 → ~923）

### 4.1 提取决策（1 个提取类）

| 提取类 | 职责叙述 | 移入成员 | 移入字段 |
|--------|---------|---------|---------|
| `TaskDispatchCoordinator`（同包 `team/scheduler` 包） | 单任务 CAS-claim 后的调度编排：member 路由 → 扇出 → 归约 → complete 链（`MemberFanOutDispatcher.dispatch`）→ 同步/异步结果分类（`DispatchTally`）→ 在飞队列管理 | `dispatchClaimedTask` :923-1032、`DispatchTally` :1058-1107、`resolveSpawnExecutor` :510-522、`spawnThreadFactory` :525-538（区域）、`awaitInFlightDispatches` :556-572 实现（主类保留公共委托方法） | `taskMemberRouter`、`agentEngine`、`memberSpawner`、`taskStore`、`daemonSessionId`、`inFlightDispatches`、`ownedSpawnExecutor`、`spawnConcurrencyCap` |

### 4.2 主类保留

- 调度循环与扫描：`scanOnce` :737-865（129 行，保留——含租约协调与团队解析编排）、`scanOnceSafe`、`resolveTeamIdsToScan`、start/stop 生命周期、全部公共 getter/setter（`setMemberSpawner`/`setTaskMemberRouter`/`setDaemonCoordinator` 等）、`getScanIntervalSec` 等
- `dispatchClaimedTask` 移出后 `scanOnce` 内调用点改为 `dispatchCoordinator.dispatchClaimedTask(...)`（委托调用重写，白名单）

### 4.3 行数预算

- 移出：dispatchClaimedTask 135 + DispatchTally 50 + resolveSpawnExecutor 13 + spawnThreadFactory 14 + awaitInFlightDispatches 17 + 相关字段/注释 ~30 ≈ **~260 行**
- 主类残留：1108 − 260 + 委托方法 ~15 ≈ **~863 行 < 1000**（余量 ~137 行）
- 拆分裁定理由：该 daemon 有 9+ 个测试文件（非死代码），audit 阈值统一适用于所有生产文件，>1000 即触发 P3；拆分而非保留

## 5. 注册面与引用面（Phase 1 item 4 盘点结果）

- **注册面**：三个主类均无 beans.xml/yaml/工厂/反射/IoC 注册（全仓 `*.xml`/`*.yaml`/`*.beans.*` grep 0 命中）
- **编译期引用点**：
  - `DefaultAgentEngine.java:3274` `ReActAgentExecutor.builder()` → 返回类型改 `ReActAgentExecutorBuilder`，调用点不变量（链式 `.modelRouter(...).build()`）不变
  - `PipelineCompactor.java:158` `ReActAgentExecutor.DEFAULT_MAX_CONTEXT_TOKENS` → 常量保留在主类，不动
  - `TestSessionStoreForkMessageFilter.java:157/175` `new DefaultAgentEngine.Builder(...)` → Builder 保留嵌套，不动
- **注释/javadoc 引用（需随提取更新）**：
  - `ReActAgentExecutor.Builder` 引用 6 处：`SmartModelRouter.java:50`、`IModelSwitchedMessageWriter.java:17`、`DbModelSwitchedMessageWriter.java:27`、`NoOpModelSwitchedMessageWriter.java:15`、`InMemoryMemoryStoreProvider.java:11`、`DefaultAgentEngine.java:1633`（+ 测试 `TestSmartModelRouterWiring.java:38`）
  - 移出方法引用 3 处：`ToolPathArgKeys.java:8`（checkPathAccess）、`ICircuitBreaker.java:31/46`、`ThresholdBreaker.java:12`（buildModelKey）
  - `CheckpointType.java:44`（performCompaction 提及）→ 更新为提取类名或保持泛化描述
- 其余 80+ 文件对类名的普通 javadoc 提及（如 `{@link DefaultAgentEngine}`）不受影响（类名未变）

## 6. 与既有 SPI/内部抽象的关系（Phase 1 item 5）

- 提取类**只做同包/同模块内部结构提取**：不新建跨模块 API、不修改任何既有接口（`IAiMemoryStore`、`ICheckpointManager`、`IHookRegistry`、`IAgentEngine`、`IAgentExecutor`、`ITeamTaskSchedulerDaemon` 等均不动）
- 提取类相互依赖方向（ReAct）：`ReActAgentExecutor` → 9 个提取类；`LlmCallCoordinator` → `AgentHookInvoker`；`AgentSecurityConsultation` → `AgentHookInvoker` + `AgentToolPlanResolver`（resolveWorkDir）；`AgentCompactionCoordinator` → `AgentHookInvoker`；`AgentPromptAssembly` → `AgentToolPlanResolver`；`AgentLoopGuard` → `AgentHookInvoker` + `AgentCompactionCoordinator`；`AgentToolDispatcher` → `AgentHookInvoker` + `AgentToolPlanResolver`（resolveWorkDir/computeEffective*）+ `AgentSecurityConsultation`（resolveWorkDirString）。无环
- DefaultAgentEngine 提取类依赖方向：主类 → 各提取类；`AgentSessionLifecycle`/`AgentExecutorResolver`/`AgentTeamBinder`/`AgentStartupWarnings` → `DefaultAgentEngineConfig`（读装配）；`AgentSessionLifecycle` → `SessionLockRenewal`（锁续期）/`AgentExecutorResolver`（执行器解析）/`AgentSessionSupport`（会话辅助）/`AgentTeamBinder`（团队绑定）；`Config` → `AgentCallDelegate`（messenger 注册）。无环

## 7. Nested class 裁定（Phase 1 item 3）

| 文件 | 嵌套类 | 裁定 | 理由 |
|------|--------|------|------|
| ReActAgentExecutor | `Builder` :769-1342 | **提取为顶层类** `ReActAgentExecutorBuilder` | 574 行；`builder()` 工厂保留返回新类型，全部链式调用点不变量；javadoc 6 处同步更新；构造器 private→package-private（白名单访问放宽） |
| ReActAgentExecutor | `LlmCallResult` :748-763 | **随 `LlmCallCoordinator` 移出** | LLM 调用结果载体，与调用协调器同主题 |
| ReActAgentExecutor | `ToolCallOutput` :3683-3691 | **随 `AgentToolDispatcher` 移出** | 扇出结果包装，与调度器同主题 |
| ReActAgentExecutor | `SecurityConsultationOutcome` :3699-3727 | **随 `AgentSecurityConsultation` 移出** | Layer 2 咨询结果载体 |
| DefaultAgentEngine | `Builder` :472-615 | **保留** | 测试直接 `new DefaultAgentEngine.Builder(...)`；~144 行在预算内 |
| DefaultAgentEngine | `CancelHandle` :2212-2237 | **随 `AgentSessionLifecycle` 移出** | 会话取消句柄，与生命周期同主题 |
| TeamTaskSchedulerDaemon | `DispatchTally` :1058-1107 | **随 `TaskDispatchCoordinator` 移出** | 调度结果统计，与调度器同主题 |

## 8. 语义对比方法学（Phase 1 item 6）

- **对比域**：原文件**全部成员集合**（含 private 方法、嵌套类、字段、常量）vs 拆分后（新主类 ∪ 全部提取类）成员集合。成员允许跨类迁移（relocation，这是拆分操作本身）
- **对比对象**：全部成员签名 multiset（含 private）+ 全部方法体逐方法 token 归一化 diff
- **归一化白名单**（预先落盘，脚本头部注释同步）：
  1. 访问放宽：`private` → 包级/`public`
  2. 状态注入参数追加：因字段迁入提取类导致的构造参数/方法参数追加
  3. 成员 relocation：方法/字段/嵌套类跨类迁移
  4. 委托调用重写：原调用点 `foo(x)` → `helper.foo(x)` / `new X().foo(x)`；字段引用 `field` → `config.field`/`this.field`
  5. 内联块提取：execute()/doExecute() 内联块迁为提取类新方法（以归一化文本重叠 ≥0.85 匹配）
- **对比基准**：拆分前 commit 的原始文件快照（`ai-dev/audits/evidence/ma4-2-05/baseline/`），首次拆分提交前导出并提交，commit SHA 写入证据文件头部
- **脚本**：`ai-dev/audits/evidence/ma4-2-05/compare.sh`（+ python 归一化器），输出三份 diff 文件（`react-semantic-diff.txt`/`engine-semantic-diff.txt`/`daemon-semantic-diff.txt`）并提交入库
- **0 diff 判定**：每个原成员在拆分后成员集中存在唯一匹配（签名 multiset 相等 + 方法体归一化相等，白名单除外）；每个新成员被白名单覆盖（relocation 或内联块提取或新委托方法）

## 9. 验证策略

- 每 Phase：`./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` BUILD SUCCESS（含新增 focused 测试）
- 端到端：既有 ReAct 循环/引擎 E2E 测试全绿（`TestCompactionInReActLoop`、`TestSkillEngineInReActLoop`、`TestActorRuntimeEndToEnd`、`TestCheckpointTriggersLLMTurnAndCompaction` 等）
- 接线验证：每个提取类 ≥1 个 focused 测试直接实例化并断言核心行为（值级）；主类调用链通过 wiring 断言/代码追踪证据落盘
- 最终全量：`./mvnw clean install -DskipTests -pl nop-ai -am -T 1C` + `./mvnw test -pl nop-ai -am -T 1C`（3519+ tests 0 failures）+ `scan-hollow-implementations.mjs --module nop-ai --severity high` exit 0 + `check-doc-links.mjs --strict` exit 0

## 10. 拒绝的替代方案

| 替代方案 | 拒绝理由 |
|---------|---------|
| 保持现状（不拆分） | MA4.2-05 已 4 次登记 deferred，最新批次显式"另行规划"；三个文件合计 7409 行，是本模块唯一剩余的超 3000 行生产文件 |
| 合并小方法凑行数 | 违反 plan Non-Goal：不为凑行数做人工切割；每个提取类均有独立职责叙述 |
| 拆分为跨模块 API | 违反 plan Non-Goal：不做跨模块 API 变更；提取类全部同包 |
| 修改 `IAgentExecutor`/`IAgentEngine` 接口 | 违反 plan Non-Goal：公共契约保持 |
| DefaultAgentEngine 的 setter 群整体移出（破坏 API） | 82 个测试文件 445 处调用；委托模式 100% 保留签名，零测试改动 |
| DefaultAgentEngine Builder 移出 | 测试直接构造 `new DefaultAgentEngine.Builder(...)`；保留成本仅 144 行 |
