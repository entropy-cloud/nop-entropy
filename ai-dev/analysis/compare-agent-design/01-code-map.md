# 三方代码地图：nop-ai-agent / deepseek-harness / pi

> Status: resolved
> Date: 2026-09-12
> Scope: nop-ai/nop-ai-agent（本仓库）、~/ai/deepseek-harness、~/ai/pi 三方 agent harness 的包结构、关键类与 load-bearing 文件清单；为 WI2 维度矩阵与后续逐维对比提供可解析的代码锚点基础
> Conclusion: 三方 HEAD 与 2026-09-12 探查基线一致（nop=c585459f83、dsh=141eb6fef8、pi=c49906ec7）；nop 侧 536 个 main Java 文件分布在 io.nop.ai.agent 下 27 个功能包 + nop-ai-core 可靠性层；dsh 侧 core/llm/compaction 三大包共 108 个 src .ts 文件、2.5 万+ 行；pi 侧自 2026-06 调研后演进剧烈（4→10 工作区、pi-ai 的 ApiProvider 架构降级为 compat 层、扩展事件 22→25 联合成员），2026-06 两份调研锚点大部分仍可解析但路径/API 已漂移，必须按 HEAD 重核。

## Context

- 为什么需要：`nop-ai-agent-design-comparison` roadmap（WI1）需要一份三方代码地图作为全部 20+ 份对比报告的锚点基础；既有调研（dsh 2026-08-13 快照、pi 2026-06-05 两份）的锚点须按各仓库当前 HEAD 复核。
- 涉及模块：nop-ai/nop-ai-agent + nop-ai/nop-ai-core + nop-ai/nop-ai-api（本仓库）；packages/core|llm|compaction 等（dsh）；packages/agent|coding-agent|ai（pi）。
- 约束：纯分析任务，无代码变更；结论必须落到代码锚点（仓库相对路径 + 类/函数名）；对方仓库设计文档只作导航。

## 三方 HEAD 基线（分析当日实测）

| 仓库 | HEAD commit | 日期 | 分支 | 备注 |
|------|-------------|------|------|------|
| nop-entropy（本仓库，含 nop-ai-agent） | c585459f83dd883b89d933d706e58b5ca1e71da7 | 2026-09-12 09:03 +0800 | master | docs(ai-dev): 新增专项深挖里程碑 |
| deepseek-harness | 141eb6fef83422698aef7a981029e843e8161534 | 2026-08-19 23:11 +0800 | master | Merge PR #2783 release/dsh-0.1.0-rc.8 |
| pi | c49906ec77788625aacbdc53ebca6fbe65bd20f5 | 2026-08-22 01:19 +0200 | main | fix(coding-agent): preserve managed state file permissions |

## 一、nop-ai-agent（本仓库）

**模块根**：`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/`，main 源码 **536 个 Java 文件**（含 39 个 `_gen` 生成文件），包树如下（仅列功能包）：

```
io.nop.ai.agent（NopAiAgentErrors.java + NopAiAgentException）
├── budget(3)        配额/预算快照（IBudgetProvider/NoOpBudgetProvider）
├── compact(13)      上下文压缩（PipelineCompactor + 4 层策略）
├── completion(7)    终止判定（ICompletionJudge/RuleBased/Llm 两种）
├── conflict(7)      跨 session 写冲突（IWriteIntentRegistry/InMemoryWriteIntentRegistry/FailFastStrategy）
├── contribution(6)  插件贡献注册表（IContributionRegistry/ContributionType 7 类）
├── engine(42)       执行层核心（DefaultAgentEngine/ReActAgentExecutor/LlmCallCoordinator/...）
├── fencing(5)       防并发回退（IFencingTokenService 单调计数器）
├── guardrail(6+8+24) 内容护栏（PromptInjectionGuardrail/RuleGraphGuardrail + 测试脚手架）
├── hook(7)          lifecycle hook（IAgentLifecycleHook/IHookRegistry/AgentLifecyclePoint）
├── memory(17)       工作记忆（IAiMemoryStore + 三适配器 IStorageAdapter/IEmbeddingAdapter/IVectorAdapter）
├── message(18)      messenger/mailbox（IAgentMessenger/IMailbox/DeferredAckMailbox/DBMessageService）
├── middleware(7)    执行级中间件（IAgentMiddleware/MiddlewareChain/ExecutionPoint）
├── model(16)        Agent DSL 模型（AgentModel 等，XDef 投影）
├── plan(0+24+20)    计划状态机（plan.model AgentPlanModel 系 + plan.runtime PlanExecutor 系）
├── quota(6)         资源配额（IResourceGuard/QuotaConfig）
├── recipe(1)        recipe 模型解析
├── reliability(30)  checkpoint journal/goal-tracker/sustainer/wait（本包不含 LLM 重试，见 nop-ai-core）
├── repair(8)        工具调用修复链（ChainRepairer 4 阶段）
├── router(5)        模型路由（SmartModelRouter/Complexity 分级）
├── runtime(7+4+4+19) actor（AgentActor/IActorRuntime）+ 跨实例协调 + 接管锁 + 恢复守护
├── security(74)     7-checkpoint 安全链（SecurityCheckpointChain/DenialLedger/ApprovalGate/Sandbox 等）
├── session(18)      AgentSession/ISessionStore 三实现 + 快照 I/O
├── skill(14)        skill 发现与策展（ISkillProvider/LLMCurator）
├── talent(2)        动态准入（ITalent）
├── team(28+20+7)    team（TeamManager/ACL/FanOut）+ team.flow（TeamTaskFlowOrchestrator）+ team.scheduler
├── tool(13)         agent 工具（memory 三件套/call-agent/send-message/team 五件套）
└── usage(6)         usage 记录（IUsageRecorder/DbUsageRecorder）
```

### 关键类与 load-bearing 文件清单

| 包 | 关键类 | 角色（一行） |
|---|---|---|
| engine | `ReActAgentExecutor` | 多步 ReAct 主循环（双层 while + 工具 fan-out + 生命周期 hook 接线） |
| engine | `DefaultAgentEngine` | 引擎门面，装配全部扩展点（store/executor/security/router/messenger/mailbox/actor 等） |
| engine | `LlmCallCoordinator` | LLM 调用生命周期：熔断检查 → 重试循环 → 时钟超时 → 模型身份键 |
| engine | `AgentSecurityConsultation` | 组装并执行 7-checkpoint `SecurityCheckpointChain` |
| engine | `AgentCompactionCoordinator` | 压缩编排（何时压缩、跑 Pipeline、记 COMPACTION checkpoint） |
| engine | `AgentExecutionContext` / `AgentExecutionResult` | 单次执行内存态容器 / 结果值对象 |
| engine | `IAgentEngine` / `IAgentExecutor` / `IAgentEventPublisher` | 引擎契约 / 执行模式策略 / 事件发布 |
| engine | `ITokenEstimator` / `TokenEstimators` / `CalibratedTokenEstimator` / `ChatOptionsHelper` | token 估算（EMA 校准）/ ChatOptionsModel→ChatOptions 转换 |
| plan.runtime | `PlanExecutor` / `PlanRunner` / `PlanScheduler` / `PlanReplanner` / `StagnationDetector` | 计划状态机：phase 门控 / 任务编排 / DAG ready 计算 / 重规划 / 停滞检测 |
| plan.model | `AgentPlanModel` / `AgentPlanPhase` / `AgentPlanTaskModel` / `TriggerRule` / `GateOnFail` | 计划 DSL 模型 |
| security | `SecurityCheckpointChain` + `SecurityCheckpoint` | 7-checkpoint 链抽象（ALLOW/DENY/DENY_AND_BREAK） |
| security | `IDenialLedger`/`DefaultDenialLedger`/`DBDenialLedger` | 拒绝账本（阈值 3 → session paused） |
| security | `IPostDenialGuard`/`FingerprintPostDenialGuard` | post-denial 指纹预拦 |
| security | `IApprovalGate`/`DefaultApprovalGate`/`AutoApproveGate` | 审批门 |
| security | `ISandboxBackend`/`DockerSandboxBackend`/`NoOpSandboxBackend` | 沙箱（fail-closed） |
| security | `IPathAccessChecker`/`DefaultPathAccessChecker`/`RuleBasedPathAccessChecker` | 路径访问检查 |
| security | `IWriteIntentRegistry`（conflict 包） | 跨 session 写冲突原子注册 |
| reliability | `CheckpointJournalWriter`/`CheckpointJournalReader` | append-only journal.md 序列化（LLM_TURN/TOOL_EXECUTION/COMPACTION/WAIT_FOR） |
| reliability | `ICheckpointManager`/`DBCheckpointManager`/`FileBackedCheckpointManager` | checkpoint 持久化三实现 |
| reliability | `IGoalTracker`/`SessionGoalTracker` | 卡死检测（滑动窗口 5，STUCK→escalated） |
| reliability | `ISustainer`/`SisypheanSustainer` | 续跑（at-least-once，truncated 终态） |
| reliability | `IWaitCoordinator`/`DefaultWaitCoordinator` | WAIT_FOR 挂起原语 |
| compact | `PipelineCompactor` | 压缩编排（Layer 1→2→3 升级） |
| compact | `Layer2TurnPruningStrategy` / `Layer3FullSummaryStrategy` / `ReferenceCompactionStrategy` | 层 2 turn 剪枝 / 层 3 LLM 总结（7 段 prompt）/ 内容寻址引用式压缩 |
| compact | `ToolResultTruncator` / `MicroCompressionCompactor` | 层 1 截断 / 微压缩占位 |
| compact | `ISpillStore`/`InMemorySpillStore` | spill 溢出存储 |
| repair | `ChainRepairer` + 4 Stage | 工具调用修复：名称规范化/参数结构/类型强转/schema 清理 |
| router | `SmartModelRouter` | 复杂度分级路由 + 预算降级 + fallback 链 |
| guardrail | `PromptInjectionGuardrail` | OpenSquilla 4 正则注入检测（OFF/REPORT/ENFORCE） |
| guardrail.rule | `RuleGraphGuardrail` | 规则关系图护栏（BLOCK/MODIFY 链式） |
| memory | `IAiMemoryStore` / `InMemoryAiMemoryStore` / `AdapterBackedAiMemoryStore` | 工作记忆契约与两实现 |
| memory | `IMemoryStoreProvider`/`InMemoryMemoryStoreProvider`/`AdapterBackedMemoryStoreProvider` | per-session store 解析 |
| memory | `IStorageAdapter`/`IEmbeddingAdapter`/`IVectorAdapter` | L4-3 三适配器契约 |
| session | `AgentSession` / `ISessionStore` / `InMemorySessionStore` / `FileBackedSessionStore` / `DBSessionStore` | 会话状态与三存储实现 |
| message | `IAgentMessenger` / `LocalAgentMessenger` / `NoOpAgentMessenger` | 跨 Agent 消息（fire-and-forget / request-response） |
| message | `IMailbox` / `DeferredAckMailbox` / `NoOpMailbox` | deferred-ack 邮箱（3-phase reservation） |
| message | `DBMessageService` | DB-backed 平台消息桥 |
| runtime | `AgentActor` / `AgentActorStatus` / `IActorRuntime` / `InMemoryActorRuntime` | Actor 容器（opt-in） |
| runtime.lock | `ISessionTakeoverLock` / `DbSessionTakeoverLock` / `NoOpSessionTakeoverLock` | 跨进程接管锁（CAS + lease） |
| runtime.recovery | `IRecoveryManager` / `ScheduledRecoveryManager` / `NoOpRecoveryManager` | 60s 恢复扫描守护 |
| runtime.recovery | `DefaultOrphanRecoveryHandler` / `DefaultSessionTimeoutHandler` / `DefaultTeamTaskRecoveryHandler` | 三类恢复 handler |
| contribution | `IContributionRegistry` / `InMemoryContributionRegistry` / `NoOpContributionRegistry` | 插件贡献注册表（7 类 ContributionType） |
| tool | `ReadMemoryExecutor` / `WriteMemoryExecutor` / `SearchMemoryExecutor` | memory 三工具（read-memory/write-memory/search-memory） |
| tool | `CallAgentExecutor` / `SendMessageExecutor` / `ReadSpillExecutor` | call-agent / send-message / read-spill |
| tool | `TeamExecuteFlowExecutor` / `TeamSendMessageExecutor` / `TeamStatusExecutor` / `TeamTaskCreateExecutor` / `TeamTaskUpdateExecutor` | team 五工具 |
| team | `ITeamManager`/`InMemoryTeamManager`/`DbTeamManager` | 团队生命周期管理 |
| team.flow | `TeamTaskFlowOrchestrator` / `MemberFanOutDispatcher` / `AllMustSucceedReduction` | 团队任务流编排 / fan-out / 归约 |
| model | `AgentModel` / `AgentExecStatus` / `AgentConstraintsModel` | agent DSL 配置模型（XDef 投影） |

### LLM 可靠性层（nop-ai-core，不在 agent 模块）

`nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/reliability/`（19 文件）：

| 类 | 角色 |
|---|---|
| `LlmErrorClassifier` | chatService 失败 → `ErrorClassification`（TRANSIENT/RATE_LIMITED/NON_TRANSIENT/QUOTA_EXCEEDED/AUTH_INVALID/CACHE_STATE_LOST，cause 链解包） |
| `StandardRetryPolicy` | 指数退避全抖动 + Retry-After floor |
| `ThresholdBreaker` | 熔断（CLOSED/OPEN/HALF_OPEN，阈值 3/60s 冷却，懒探针） |
| `IAccountChainResolver`/`AccountChain` | 账号链（QUOTA/AUTH 同模型换 key） |
| `IProviderFailoverChainResolver`/`ProviderFailoverChain`/`ProviderFailoverQueue` | 跨 provider 故障转移 |
| `ModelKeys` | provider:model 复合键 |

其余支撑位置：`ILlmDialect`/`AbstractLlmDialect`/`OpenAiDialect` 等在 `io.nop.ai.core.dialect`；`ChatOptionsModel` 在 `io.nop.ai.core.model`；`IChatService`/`ChatOptions`/`ChatMessage` 等在 `nop-ai/nop-ai-api` 的 `io.nop.ai.api.chat`。

### 与 owner doc `ai-dev/design/nop-ai-agent/01-architecture-baseline.md` 交叉核对结果

**一致项（复核通过）**：

1. 五层架构、配置与执行分离（`AgentModel` 纯配置 / Engine 执行）——一致。
2. 核心五对象（AgentModel、IAgentEngine、AgentExecutionContext、IAgentExecutor、事件发布）——除命名外全部可解析（见不一致 1、2）。
3. LLM 层在 nop-ai-core 的 `ILlmDialect`，`ChatOptionsModel`→`ChatOptions` 经 `ChatOptionsHelper`——一致（`ChatOptionsHelper` 在 agent engine 包，`ChatOptionsModel` 在 core model 包，owner doc 表述吻合）。
4. Working Memory 三工具在 `io.nop.ai.agent.tool`（Read/Write/SearchMemoryExecutor）——一致。
5. 7-checkpoint 安全链、熔断/三通道故障转移（core reliability）、checkpoint journal、恢复守护、接管锁、actor 基础层、messenger/mailbox/contribution 注册表——全部可解析到上述清单。
6. 压缩为 4 层升级（ToolResultTruncator→MicroCompression→Layer2TurnPruning→Layer3FullSummary）+ EMA 校准计价——一致。

**不一致 / 需修正项（差异清单）**：

1. `AgentEventPublisher`（owner doc 核心对象）→ 实际为接口 `IAgentEventPublisher` + 实现 `DefaultAgentEventPublisher`（engine 包），无同名类。
2. `IAgentMemory`（owner doc）→ 无此类；记忆契约实为 `IAiMemoryStore`（memory 包），三层记忆的表述须以此为准。
3. owner doc 称 "Layer 1 只实现 ReAct" → 实际另有 `SingleTurnExecutor`（单轮策略），"只实现 ReAct"不成立。
4. owner doc 称 `LocalMessageService` 为 Agent 域实现 → 实为平台类 `io.nop.message.core.local.LocalMessageService`（nop-message-core）；Agent 域对应物是 `LocalAgentMessenger`。
5. owner doc 无 `ISessionTakeoverLock`/`IRecoveryManager` 之外的新增运行时面：`runtime.coordination.IDaemonCoordinator`（多实例守护协调）、`fencing`、`quota`、`usage`、`budget`、`talent`、`skill` 包均为 owner doc 未覆盖但已落地（owner doc 以 01-architecture-baseline 命名，属基线而非全量索引，差异不构成错误，记录供 WI2 维度矩阵取锚点）。
6. 规模数字：2026-08-13 调研记 551 个 Java 文件 → 现为 536（main，含 39 个 `_gen`）；包数 27 个功能包（roadmap 基线一致）。

**结论**：owner doc 的设计结论全部成立，命名/实现细节差异 6 项如上，后续 WI 引用时以本代码地图为准。

## 二、deepseek-harness（~/ai/deepseek-harness）

**仓库根**：`/Users/abc/ai/deepseek-harness`，pnpm monorepo，`packages/` 下 51 个包；核心三包 src .ts 共 **108 个文件 / 25,366 行**（core 44/13,497 + llm 46/8,989 + compaction 18/2,880）。

### packages/core（8 子包，44 文件 / 13,497 行）

| 子包 | 文件/行 | 关键类与 load-bearing 文件 |
|---|---|---|
| `agent` | 8 / 1,636 | `Agent` 接口（src/runtime-types.ts:64）、`AgentRegistry`（src/index.ts:256）、`agent/inbox/spliced` 事件（src/types.ts:19 声明合并，src/inbox.ts:186 应用） |
| `agent-loop` | 6 / 1,662 | `AgentLoop extends Service implements AgentFactory`（src/index.ts:296）、`ReactLoopAgent implements Agent`（src/agent.ts:64）、tool-call 派发（src/tool-calls.ts）、runtime-context.ts |
| `session` | 10 / 3,164 | 事件溯源 `Session`（src/index.ts:425）、`SurfaceManager`（src/surface.ts:398）、`SessionEventMap`（src/types.ts:236，声明合并扩展）、崩溃尾部修复 `interruptedTurnClosers`（src/repair.ts:27） |
| `tools` | 10 / 5,628 | `ToolDefinition`/`ToolNotFoundError`/`ToolOutputError`（src/index.ts:494/513）、presentation、JSON-schema/py/ts-types |
| `system-prompt` | 2 / 605 | `renderPrompt`/`renderContextSections`/`PromptAssembly`（src/index.ts:212/115） |
| `scope` | 4 / 561 | `createScope`/`scopeOf`/`ScopedLayers`（src/index.ts:137, src/store.ts:159） |
| `agent-default-model` | 2 / 137 | （新增）无显式 model 时的默认模型选择 |
| `agent-tool-presentation` | 2 / 104 | （新增）presentation 行（native vs code mode） |

### packages/llm（5 子包，46 文件 / 8,989 行）

| 子包 | 文件/行 | 关键类与 load-bearing 文件 |
|---|---|---|
| `llm` | 14 / 2,771 | 抽象 `LlmAdapter`（src/index.ts:180，唯一必需方法 `abstract stream(options): AsyncIterable<StreamChunk>`）、`LlmRuntime`（src/index.ts:284）、`StreamChunk`（src/types.ts:312）、retry-policy.ts / api-key.ts / message.ts / content.ts |
| `llm-retry` | 5 / 494 | Cordis 插件（src/index.ts:20，apply :99）；`agent/request-error` waterfall 重试监听（:210）+ 退避 + provider retry-after + history.ts |
| `token-meter` | 10 / 999 | `TokenMeter extends Service`（src/index.ts:74）；estimate.ts / projection.ts / usage-projection.ts / surface-fold.ts / breakdown-projection.ts / surface-projection.ts |
| `llm-deepseek` | 7 / 1,491 | （新增）`DeepSeekAdapter extends LlmAdapter`（src/adapter.ts:171）、sse.ts |
| `llm-pi-ai` | 10 / 3,234 | （新增）`PiAiAdapter extends LlmAdapter`（src/adapter.ts:191）、discovery/catalog |

### packages/compaction（4 子包，18 文件 / 2,880 行）

| 子包 | 文件/行 | 关键类与 load-bearing 文件 |
|---|---|---|
| `compaction` | 6 / 792 | 抽象 `CompactionEngine extends Service`（src/index.ts:96，compactIfNeeded/compactNow）、checkpoint.ts（compactCheckpointSource）、tool-pairing.ts、brand.ts（CompactionId） |
| `compaction-basic` | 6 / 1,621 | `BasicCompactionEngine extends CompactionEngine`（src/index.ts:103）、region.ts（区间选择）、summarizer.ts、config.ts |
| `compaction-tool-result-pruner` | 4 / 331 | `ToolResultPruner extends Service`（src/index.ts:44） |
| `command-compact` | 2 / 136 | 手动压缩 slash 命令 |

### 其他 load-bearing 子系统（锚点速查）

| 子系统 | 包 | 锚点 |
|---|---|---|
| spill | packages/spill/{spill,spill-local,spill-policy} | 抽象 `SpillStore`（spill/src/index.ts:45，唯一方法 saveText）、`LocalSpillStore`（:37）、spill-policy 挂 `tools/post-execute`（:110） |
| goal | packages/goal/{goal,goal-round-driver,command-goal,tool-goal} | `GoalService`（goal/src/index.ts:183）、`foldGoal`（fold.ts）、goal-round-driver apply（src/index.ts:76）+ renderGoalRoundPrompt（prompt.ts） |
| plan | packages/plan/plan-mode | `PlanModeController`（src/index.ts:188）、foldPlanMode（:130） |
| guard | packages/guard/{repeat-tool-reminder,timeout-policy} | repeat-tool-reminder apply（src/index.ts:162）、timeout-policy（dsh-tool-call-timeout-policy，src/index.ts:55） |
| sandbox | packages/sandbox/{sandbox,sandbox-local,sandbox-policy,sandbox-windows-acl} | 抽象 `SandboxProvider.confine(argv, policy)`（sandbox/src/index.ts:158）、`LocalSandboxProvider`（:250） |
| skill | packages/skill/{skill,skill-badge,skill-filesystem,tool-skill} | `SkillDefinition`/`SkillCandidate`（skill/src/index.ts，868 行） |
| storage | packages/storage/{storage,storage-domain,storage-json,storage-sqlite} | `BackendRegistry`（storage/src/registry.ts:14）、`StorageBackend`（src/backend.ts:17） |
| subagent | packages/subagent/*（12 包） | `SubagentRuntime`（subagent/src/index.ts:171）、`SubagentContinuationManager`（src/continuation.ts:355）；6 provider：spawn/fork（in-process）、acp、claude-code、codex、dsh-sdk |
| workflow | packages/workflow/{workflow,workflow-worker-thread,tool-workflow,tool-ralph} | 抽象 `WorkflowEngine`（workflow/src/index.ts:157）、`WorkflowError`（:130） |
| session-persistence | packages/session/session-persistence | 崩溃恢复接线 `interruptedTurnClosers`（src/coordinator.ts:903） |
| Cordis vendor | vendor/cordis/src/ | context.ts / events.ts / fiber.ts / registry.ts / service.ts（9 文件） |
| 一手文档 | docs/architecture.md、docs/subsystems/*.md（94 个 .md） | 只作导航，结论须落代码锚点 |

### 2026-08-13 调研快照锚点复核结论

| 快照锚点 | 复核结果 | 说明 |
|---|---|---|
| Session ~3156 行（core/session） | ✅ 仍有效 | 整包 src 3,164 行；类在 src/index.ts:425（非 types.ts） |
| types.ts:173 `interrupted` 事件 | ⚠️ 部分有效 | :173 是事件类型锚点（TurnEndReasonMap）；合成实现在 repair.ts:27 + session-persistence coordinator.ts:903 |
| SurfaceManager | ✅ 仍有效 | src/surface.ts:398 |
| CompactionEngine + BasicCompactionEngine | ✅ 仍有效 | compaction/src/index.ts:96 + compaction-basic/src/index.ts:103 |
| compaction-tool-result-pruner | ✅ 仍有效 | src/index.ts:44 |
| SpillStore | ✅ 仍有效 | spill/src/index.ts:45 |
| goal 5786 行 | ✅ 基本有效 | 全树含测试 5,909 行；GoalService src/index.ts:183 |
| plan mode 2028 行 | ⚠️ 口径修正 | src-only 584 行；含测试 2,131 行——原数字应为含测试口径 |
| guard repeat-tool / timeout | ✅ 仍有效 | repeat-tool-reminder:162、timeout-policy:55 |
| sandbox confine(argv) | ✅ 仍有效 | sandbox/src/index.ts:158 |
| LlmAdapter.stream() + StreamChunk | ✅ 仍有效 | llm/src/index.ts:180 + types.ts:312 |
| llm-retry request-error waterfall | ✅ 仍有效 | llm-retry/src/index.ts:210 |
| token-meter | ✅ 仍有效 | token-meter/src/index.ts:74 |
| 6 subagent provider | ✅ 仍有效 | subagent-* 各包 run.ts |
| SubagentContinuationManager | ✅ 仍有效 | continuation.ts:355 |
| skills / storage / Cordis vendor | ✅ 仍有效 | 路径不变 |
| SessionEventMap / inbox/spliced | ✅ 仍有效 | session/src/types.ts:236、agent/src/types.ts:19 |

**快照以来变化**：core 增加 `agent-default-model`、`agent-tool-presentation` 两个子包；llm 增加 `llm-deepseek`、`llm-pi-ai` 两个适配器包（快照仅深析了 llm 抽象）。其余锚点全部按当前 HEAD（141eb6fef8）可解析。

## 三、pi（~/ai/pi）

**仓库根**：`/Users/abc/ai/pi`，npm workspaces monorepo，lockstep 版本 **0.84.2**（2026-06-05 调研基线为 0.78.1，10 周 30 次发布）。工作区从 4 个扩到 **10 个发布包 + 1 私有**：ai / agent / coding-agent / tui / telemetry（08-05 抽出）/ protocol（07-30）/ client（07-31）/ server（07-21 更名）/ session-backends/sqlite-node（08-05 更名）/ evals（07-25，私有）。

### packages/agent（pi-agent-core，50 src .ts / 12,635 行）

```
src/
├── agent.ts           Agent 类（592 行）：subscribe/steer/followUp（PendingMessageQueue
│                      QueueMode all|one-at-a-time）/prompt/continue/abort/waitForIdle
├── agent-loop.ts      底层循环（796 行）：agentLoop()/agentLoopContinue() EventStream 工厂
│                      + runAgentLoop()/runAgentLoopContinue()（AgentMessage 版）
├── types.ts           AgentTool<TParameters>(:386)/AgentEvent(:428)/AgentContext(:412)/
│                      AgentLoopConfig/StreamFn/QueueMode
├── stream-fn.ts       setDefaultStreamFn()/getDefaultStreamFn() 注入点
├── proxy.ts           ProxyMessageEventStream（经 server 路由 LLM 调用）
├── harness/           AgentHarness 高层封装（508 行）：lanes/runs/compaction/navigation
│   ├── compaction/    compaction.ts（848 行：CompactionSettings/summarization/file-ops）
│   ├── session/       session.ts + state.ts + context.ts（buildSessionContext）+ memory.ts
│   │   └── jsonl/     JSONL 持久化：codec.ts/storage.ts（原子重命名写）/repo.ts（JsonlSessionRepo）
│   ├── tools/         read/bash/edit/edit-diff/write/image + file-mutation-queue
│   └── events.ts      HarnessEvent（run_start/run_end）
└── search/            会话搜索（createScanningSessionSearch）
```

### packages/coding-agent（pi-coding-agent，203 src .ts / 59,983 行）

| 目录/文件 | 关键类与角色 |
|---|---|
| `core/agent-session.ts` | `AgentSession`（3,469 行）：interactive/print/rpc 三模式共享；持久化/压缩/bash/分支 |
| `core/session-manager.ts` | JSONL 会话文件管理（CURRENT_SESSION_VERSION=3）、fork/switch/tree |
| `core/settings-manager.ts` + `config.ts` | `Settings` 接口（:91：compaction/branchSummary/retry/steeringMode/followUpMode/theme/extensions…）；发现路径 CONFIG_DIR_NAME=".pi" |
| `core/extensions/` | 扩展系统：types.ts（1,751 行：ExtensionAPI/Extension/ExtensionFactory/ExtensionEvent 25 联合成员/34 type 标签/48 具体事件）、loader.ts（745 行，jiti TS 加载）、runner.ts（1,236 行，生命周期/事件派发）、wrapper.ts |
| `core/tools/` | **7 内置工具不变**：read/bash/edit/write/grep/find/ls（ToolName union + allToolNames）+ edit-diff + file-mutation-queue |
| `core/compaction/` | compaction.ts + branch-summarization.ts |
| `modes/` | interactive/（TUI）/print-mode.ts/rpc/（rpc-mode + jsonl）/json-event.ts |
| `client/` + `server/` | remote-session.ts / transcript.ts（pi-client 集成）/ create-harness.ts |
| `cli/experimental/` | 新 CLI 框架（command.ts + commands/{client,pi,server}.ts） |
| `bun/` | bun 二进制入口 |

### packages/ai（pi-ai，177 src .ts / 23,555 行）

| 文件 | 关键类与角色 |
|---|---|
| `types.ts` | `Api`(:29)/`Context`(:521)/`Model<TApi>`(:821)/`StreamFunction`(:332)/`ProviderStreams`/`SimpleStreamOptions` |
| `models.ts` | `Models` 接口（stream/streamSimple/complete/completeSimple/getAuth/getModel/…）、`Provider<TApi>`(:97)、`createModels` |
| `models.generated.ts` | 自动生成模型目录聚合（scripts/generate-models.ts） |
| `compat.ts` | **遗留兼容层**：`ApiProvider`(:83)/`apiProviderRegistry`(:100)——2026-06 调研的主 API 已降级为 deprecated |
| `api/` | 12 个 API 适配器 + .lazy.ts 孪生：openai-responses/openai-completions/anthropic-messages/google-generative-ai/google-vertex/bedrock-converse-stream/mistral-conversations/openai-codex-responses/pi-messages/azure-openai-responses/cloudflare/openrouter-images |
| `providers/` | ~44 个 provider（openai/anthropic/google/deepseek/groq/xai/…），每个 `<name>.ts` + `<name>.models.ts` |
| `auth/oauth/` | 11 个 OAuth 流程（anthropic/github-copilot/kimi-coding/openai-codex/openrouter/radius/xai/device-code/pkce/oauth-page/load） |
| `utils/event-stream.ts` | `EventStream` 类 + `AssistantMessageEventStream` |
| `images.ts` | Images API（generateImages/ImagesApi registry） |

### 2026-06-05 两份调研锚点复核结论（pi 演进快，全部按 HEAD 重核）

| 调研锚点 | 复核结果 | 说明 |
|---|---|---|
| 4 包架构（ai/agent/coding-agent/tui） | ⚠️ 已过期 | 现 10 发布包 + 1 私有；新增 telemetry/protocol/client/server/sqlite-node/evals |
| `ApiProvider`/`apiProviderRegistry`（pi-ai 主 API） | ❌ 已失效（降级） | 降级为 deprecated `compat.ts`；主 API 为 `Provider`/`Models`（models.ts） |
| 扩展事件 22 类 | ⚠️ 已更新 | 25 联合成员 / 34 type 标签 / 48 具体事件；新增 ProjectTrustEvent/BeforeProviderHeadersEvent/AgentSettledEvent 等 |
| AgentTool 在 tool.ts | ⚠️ 路径漂移 | 移至 types.ts:386（无 tool.ts） |
| AgentEvent 在 event.ts | ⚠️ 路径漂移 | 在 types.ts:428；harness/events.ts 只含 HarnessEvent |
| AgentContext 在 context.ts | ⚠️ 路径漂移 | 在 types.ts:412；另有 harness/session/context.ts（buildSessionContext） |
| compaction 在 agent 包 | ⚠️ 路径漂移 | 在 harness/compaction/（compaction.ts 848 行） |
| session JSONL 持久化 | ✅ 仍有效 | 在 harness/session/jsonl/（重写为原子写 + 会话树 repo） |
| coding-agent settings.ts | ⚠️ 路径漂移 | 拆为 config.ts + core/settings-manager.ts |
| pi-ai model.ts/stream.ts/provider.ts/context.ts | ⚠️ 全部路径漂移 | Model/StreamFunction/Context 并入 types.ts；Provider 在 models.ts |
| 7 内置工具（read/bash/edit/write/grep/find/ls） | ✅ 仍有效 | 不变（+edit-diff/file-mutation-queue） |
| `Agent` 类 + agent-loop | ✅ 仍有效 | agent.ts（592 行）+ agent-loop.ts（796 行） |
| models.generated.ts 自动生成 | ✅ 仍有效 | 路径不变 |
| 供应链安全实践 / 配置层级 / TUI 四模式 | ✅ 仍有效 | 未发生结构性变化 |

**结论**：pi 的两份 2026-06 调研**概念层仍有效**（分层架构、扩展系统、事件驱动、JSONL 会话、provider 注册），但**文件级锚点大多漂移**（AgentTool/AgentEvent/Context/settings/pi-ai 四文件均搬迁或改名），且 pi-ai 的 Provider 架构经历了一次核心 API 换代（ApiProvider→Provider/Models）。后续 WI 引用 pi 时必须使用本代码地图的 HEAD 锚点，禁止直接复用 2026-06 调研的文件路径。

## 四、锚点可解析性汇总（供 WI2 及后续维度矩阵复用）

| 仓库 | 可解析锚点数（本地图） | 代表锚点示例 |
|---|---|---|
| nop-ai-agent | 50+ | nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/ReActAgentExecutor.java、engine/LlmCallCoordinator.java、engine/AgentSecurityConsultation.java、plan/runtime/PlanExecutor.java、compact/PipelineCompactor.java、repair/ChainRepairer.java、router/SmartModelRouter.java、reliability/CheckpointJournalWriter.java、runtime/recovery/ScheduledRecoveryManager.java、session/AgentSession.java |
| deepseek-harness | 30+ | packages/core/session/src/index.ts（Session:425, SurfaceManager:398）、packages/core/agent-loop/src/agent.ts（ReactLoopAgent:64）、packages/llm/llm/src/index.ts（LlmAdapter:180）、packages/llm/llm-retry/src/index.ts（:210）、packages/compaction/compaction/src/index.ts（CompactionEngine:96）、packages/spill/spill/src/index.ts（SpillStore:45）、packages/goal/goal/src/index.ts（GoalService:183）、packages/sandbox/sandbox/src/index.ts（confine:158）、packages/subagent/subagent/src/continuation.ts（:355） |
| pi | 25+ | packages/agent/src/agent-loop.ts（agentLoop）、packages/agent/src/agent.ts（Agent）、packages/agent/src/types.ts（AgentTool:386, AgentEvent:428）、packages/agent/src/harness/compaction/compaction.ts、packages/agent/src/harness/session/jsonl/repo.ts、packages/coding-agent/src/core/agent-session.ts（AgentSession）、packages/coding-agent/src/core/extensions/types.ts（ExtensionEvent）、packages/ai/src/models.ts（Provider:97）、packages/ai/src/types.ts（Model:821, StreamFunction:332）、packages/ai/src/compat.ts（ApiProvider:83） |

每个仓库锚点均 ≥5 个，且全部在对应仓库分析当日 HEAD（上表）下实测解析通过。

## Conclusion

- 三方 HEAD 与计划基线一致，代码地图全部锚点在各自 HEAD 下实测可解析。
- nop 侧与 owner doc `01-architecture-baseline.md` 交叉核对：设计结论全部成立，命名/实现差异 6 项已记录（IAgentEventPublisher vs AgentEventPublisher、IAiMemoryStore vs IAgentMemory、SingleTurnExecutor 存在、LocalMessageService 属平台层、运行时新增面清单、551→536 文件数）。
- dsh 侧 2026-08 调研快照锚点：除 types.ts:173 需修正为"类型锚点 + repair.ts:27 合成实现"、plan-mode 行数为含测试口径外，全部仍有效；新增 4 个子包。
- pi 侧 2026-06 两份调研：概念层有效、文件锚点大部分漂移、pi-ai 核心 API 换代（ApiProvider→Provider/Models）、4→10 工作区——必须按 HEAD 重核，后续 WI 以本地图锚点为准。
- 后续工作：本地图为 WI2 维度矩阵（D1–D10 子机制拆解 + 锚点复用）提供输入；逐维对比（WI8 起）引用本地图锚点无需重新测绘。

## References

- `ai-dev/design/nop-ai-agent/01-architecture-baseline.md`（nop 侧 owner doc，交叉核对对象）
- `ai-dev/analysis/agent-survey/2026-08-13-deepseek-harness-analysis.md`（dsh 2026-08 快照，锚点复核对象）
- `ai-dev/analysis/agent-survey/2026-06-05-pi-agent-analysis.md`、`2026-06-05-pi-ecosystem-comparison.md`（pi 2026-06 过期调研，锚点重核对象）
- `~/ai/deepseek-harness`（HEAD 141eb6fef8；docs/architecture.md、docs/subsystems/*.md 只作导航）
- `~/ai/pi`（HEAD c49906ec7；packages/coding-agent/docs/*.md 只作导航）
- `ai-dev/backlog/nop-ai-agent-design-comparison-roadmap.md`（WI1 编排）