# 1 引用式压缩双轨（Reference Compaction Dual-Track）

> Plan Status: completed
> Last Reviewed: 2026-08-02
> Draft Consensus: 3 轮独立子 agent 对抗性审查通过（r1 修 Blocker+4Major+Minor；r2 修归档实例宿主 Major；r3 CONSENSUS yes）
> Source: `ai-dev/backlog/nop-ai-agent-harness-evolution-roadmap.md` W4-1；`ai-dev/analysis/agent-survey/2026-08-01-context-mode-compaction-analysis.md` §5.1；`ai-dev/design/nop-ai-agent/nop-ai-agent-context-model.md` §8.2
> Mission: nop-ai-agent-harness-evolution
> Work Item: W4-1
> Related: `2026-08-02-0900-2-nop-ai-agent-compaction-snapshot-archive.md`（successor，压缩管线安全/可观测）

## Purpose

把 nop-ai-agent 的上下文压缩管线从"单一摘要式（有损）"升级为"双轨"——新增**引用式压缩**路径（无损指针）：把必须保真的内容（文件内容/配置/长文档原文）替换为 `shortRef{type, path, range, hash}` 引用，需要时通过 `read-ref` 工具按 hash 校验后读回原文。本计划交付这条新增路径 + 内容类型路由 + 读回工具，使长任务的精确内容（代码/配置）不再因压缩累积失真。

## Current Baseline

基于 live repo（`nop-ai/nop-ai-agent/`、`nop-ai/nop-ai-toolkit/`）核对：

- **压缩管线（已有，摘要式/有损）**：`io.nop.ai.agent.compact` 包已落地三层管线：
  - `IContextCompactor`（`PipelineCompactor`，compact 包）+ `ICompressionStrategy`（`name()` + `compact(CompactionContext)`）。
  - 策略实现：`MicroCompressionCompactor`、`Layer2TurnPruningStrategy`、`Layer3FullSummaryStrategy`（LLM 摘要，失败降级为 Layer2）、`ToolResultTruncator`（offloading）。
  - `PipelineCompactor` 按 escalation 顺序运行策略，token/消息数双维度 OR-gate 触发，relieved 即停。
- **调用入口（已有）**：`engine/AgentCompactionCoordinator.performCompaction` 是管线唯一调用点（`PRE_COMPACT` middleware → `compact()` → `POST_COMPACT` hook → 真实压缩时替换消息 + 发射 `COMPACTION` checkpoint）。
- **结果类型（已有）**：`session/CompactionResult(sessionId, tokensBefore, tokensAfter, retainedMessageCount, snapshotId, compactedMessages)`。本计划**不扩展** `CompactionResult`（见 Phase 1 裁定）。
- **来源元数据（已有枚举，但未附着到消息）**：`security/ContentOrigin` 枚举（`CHANNEL_INPUT / WEB_FETCH / FILE_READ / AGENT_GENERATED`）存在，**但仅用于 `IContentTrustEvaluator` 内部评估，从未绑定到 `ChatMessage`**。核对 `nop-ai-api/.../messages/ChatMessage.java`：基类只有 `messageId` / `providerHints` / 抽象 `role`/`content`，**没有任何 origin/content-type 字段**。即"内容类型路由信号源"**当前不存在**——这是 Phase 1 必须裁定的关键 Decision（见 Phase 1 裁定 A）。
- **工具形态（已有）**：`nop-ai-toolkit` 工具实现 `IToolExecutor`（`getToolName()` + `executeAsync(AiToolCall, IToolExecuteContext) → CompletionStage<AiToolCallResult>`），注册到 toolkit manager；`ReadFileExecutor`（`read-file`，tools 包）是按行范围读取的参照实现。
- **模块依赖方向（关键约束）**：`nop-ai-toolkit` 仅依赖 `nop-ai-api`，**不依赖 `nop-ai-agent`**；反向 `nop-ai-agent` 依赖 `nop-ai-toolkit`。引用归档接口若放在 `nop-ai-agent`，toolkit 的 `read-ref` 工具无法 import（循环依赖）——归档接口归属须在 Phase 1 裁定（见 Phase 1 裁定 B）。
- **`IToolExecuteContext` 爆炸半径**：该接口在仓库有 **22 处实现**（生产 + 测试 mock）。若经它向工具暴露归档只读视图，加方法须同步这 22 处——Phase 1 裁定 + Phase 3 Exit Criteria 必须覆盖（见 Phase 1 裁定 C）。
- **真正剩余的 gap**：
  - 没有"引用式"压缩策略——`shortRef{type,path,range,hash}` 结构不存在，没有按内容类型分流的策略。
  - 没有 `read-ref` 读回工具，没有按 hash 校验失效的读回路径。
  - 压缩策略选择不依赖内容类型（全部走摘要/裁剪）。
  - **内容类型路由信号源不存在**（`ChatMessage` 不携带 origin/content-type）。

## Goals

- 新增引用式压缩策略：把可定位的保真内容替换为 `shortRef` 指针，原文归档供读回。
- 按内容类型分流：摘要式处理可概括内容（对话轮次/中间推理），引用式处理保真内容（文件/配置/长文档原文）。
- 新增 `read-ref` 工具：按引用读回原文，读回时做 content hash 校验，不一致 fail-loud 提示"内容已变更"。
- 引用式压缩作为 `PipelineCompactor` 的一条策略接入（与现有摘要策略共存，不替换）。
- 零回归：未配置引用式策略 / 无可定位内容时，行为与今日一致。

## Non-Goals

- **不做**压缩前整段消息历史的 snapshot 归档与压缩比度量（属 W4-2，successor plan `2026-08-02-0900-2`）。本计划的"引用归档"（hash 寻址 per-content）与 W4-2 的"snapshot 归档"（snapshotId 寻址 per-compaction-event 整段历史）是**两个不同抽象**，不复用基础设施。
- **不做**跨 session / 跨 agent 的引用共享与继承（首版 per-session 归档）。
- **不改** `ChatMessage` / `nop-ai-api` 公共契约（内容类型路由信号由策略侧推断，见 Phase 1 裁定 A；`nop-ai-api` 是跨模块公共 API，属 plan-first Protected Area，本计划不动）。
- **不扩展** `CompactionResult`（引用归档句柄不进结果对象，`read-ref` 直接按 hash 从归档读，见 Phase 1 裁定 D）。
- **不做**引用的版本感知/多版本快照（首版 hash 失效即提示重读，不维护版本谱）。
- **不做**引用式压缩的自动触发阈值生产调参（设计留可配置项，调参 non-blocking）。
- **不改** `AgentLifecyclePoint` 枚举、`HookResult` 密封层级、checkpoint 存储格式。
- **不改**现有三层摘要策略的行为（引用式是新增策略，非替换）。

## Scope

### In Scope

- `shortRef` 数据结构定义（type / path / range / hash）+ 序列化形态（引用作为消息内容嵌入）。
- 引用式压缩策略（新 `ICompressionStrategy` 实现）+ 内容类型路由判定。
- 原文归档存储抽象 + 首版 in-session / 内存实现（per-session，按 hash 寻址）。
- `read-ref` 工具（toolkit）：按引用读回 + hash 校验 + 失效 fail-loud。
- 管线集成（`PipelineCompactor` 可装配引用式策略）+ 端到端压缩→读回路径。
- 设计文档 `nop-ai-agent-context-model.md` §8.2 由方向性描述回写为最终架构决策。

### Out Of Scope

- 引用归档的持久化后端（DB / 文件）——首版内存实现，持久化是 successor。
- 引用式压缩的 LLM 智能内容分类（首版用内容类型元数据 + 启发式，非额外 LLM 调用）。
- 引用压缩比的度量上报/可观测面板（W4-2 范畴）。

## Execution Plan

### Phase 1 - 设计裁定与契约定义

Status: completed
Targets: `ai-dev/design/nop-ai-agent/nop-ai-agent-context-model.md` §8.2；引用式压缩的接口/结构/归属契约（design 层，非代码签名）

- Item Types: `Decision`

- [x] **裁定 A — 内容类型路由信号源**（解决"ChatMessage 不携带 origin"现状）：**采纳策略侧推断，不改 `ChatMessage`/`nop-ai-api`**。信号来源：(1) `ChatToolResponseMessage` 携带的工具调用标识——由文件类工具（`read-file`/`search-*` 等）产出的 tool result 视为可引用保真内容；(2) 内容长度阈值（超阈值的长 tool result 才值得引用化）；(3) 角色（仅 tool-response 类消息进入引用候选，对话/推理消息仍走摘要）。**拒绝**给 `ChatMessage` 加 origin/content-type 字段（`nop-ai-api` 公共契约扩展，scope 超本计划）。裁定如何在策略内识别 tool-response 的来源工具（核对 `ChatToolResponseMessage` 是否携带 tool name / tool_call_id 关联）
- [x] **裁定 B — 引用归档接口的模块归属**（解决 toolkit→agent 反向依赖）：**采纳归档接口放 `nop-ai-toolkit`**（接口消费方 = `read-ref` 工具所在模块），实现在 `nop-ai-agent/compact`（agent 依赖 toolkit，可 import 接口并实现）。`nop-ai-agent` 的引用策略 put，toolkit 的 `read-ref` get，两者经 toolkit 拥有的接口对齐。**拒绝**接口放 `nop-ai-agent`（循环依赖）或 `nop-ai-api`（归档非 chat API 核心概念）
- [x] **裁定 C — `read-ref` 如何到达归档**（爆炸半径声明）：经 `IToolExecuteContext`（toolkit api，**22 处实现**）新增只读访问器返回归档视图。Exit Criteria 必须验证全部 22 处实现（含测试 mock）已更新并通过——否则用 default 方法（抛 UOE）+ 仅在 agent 提供的实现里覆写为非空（核对 nop 是否接受 default UOE 桥模式，参照 `ISessionStore.save`/`listAllSessions` 的 default UOE 先例）。**注**：`ISessionStore` default 抛 `NopAiAgentException`（agent 模块类型），但 `IToolExecuteContext` 在 **toolkit** 不能 import agent 异常——toolkit 侧 default 须用 `UnsupportedOperationException` 或 toolkit 自有错误类型。**裁定二选一**：default UOE 桥（最小爆炸半径）vs 全量加方法（22 处）
- [x] **裁定 D — `CompactionResult` 不扩展**：引用归档句柄**不进** `CompactionResult`。`shortRef` 自带 hash（即读回键），`read-ref` 按 hash 直读归档，不需要结果对象转交归档句柄。保持 `CompactionResult` 形态不变（与 W4-2 successor 的扩展正交、无冲突）
- [x] **裁定 G — 归档实例的物理宿主与双侧访问路径**（解决中枢接线，避免空壳）：裁定 per-session 归档**实例**（非接口）宿主——建议挂 `AgentSession`（per-session 生命周期天然匹配，会话结束释放；`AgentSession` 已有非 final 可变字段 + setter 先例，可加 archive 字段）。**写侧**：引用式策略需拿到归档实例 PUT 原文——注意 `AgentExecutionContext` 仅暴露 `getSessionId()`、**不直接暴露 AgentSession**；裁定的访问路径参照 `AgentCompactionCoordinator.performCompaction:140` 已演示的 `sessionStore.get(sessionId)→AgentSession` 现成模式（coordinator 持 `ISessionStore`），由 coordinator 在构造 `CompactionContext` 时把 archive 实例（或可达它的句柄）注入 context（Phase 1 design 补全精确注入点）。**读侧**：`read-ref`（toolkit）经 `IToolExecuteContext` 访问器（裁定 C）拿到归档；agent 的 `AgentToolExecuteContext`（已持 `getSession():AgentSession` @ `:470`）覆写该访问器从 session 取归档实例。双侧经同一 session 宿主共享同一实例——这是 compact 写入 → 归档 → read-ref 读回的完整 wiring
- [x] **裁定 E — `shortRef` 结构与序列化 + `read-ref` 输入契约**：字段（`type`/`path`/`range`/`hash`）；作为 ChatMessage content 的**精确可解析序列化格式**（LLM 据此生成 `read-ref` 工具调用，非仅供系统 `startsWith` 检测，与 `SUMMARY_MARKER` 用途不同）；`read-ref` 工具 input schema 与 `shortRef` 字段的一一映射；`type` 取值与可引用工具的映射
- [x] **裁定 F — 共存/escalation 顺序**：引用式策略插入位置（建议摘要式之前：先剥离可保真内容→再摘要剩余），以及"引用剥离后是否仍需摘要"的判定；与现有三层摘要策略的 escalation 顺序

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `nop-ai-agent-context-model.md` §8.2 已重写为最终架构决策（含上述 A–G 七条裁定结论 + 拒绝的替代方案及原因），无 "Proposed vs Current" 残留；**删除 §8.2 现存"与 ContentOrigin（已有）组合"句**（裁定 A 推翻该假设）
- [x] design §8.2 明确**不扩展 ChatMessage/nop-ai-api**、**归档接口归属 toolkit**、**CompactionResult 不扩展**、**归档实例宿主 AgentSession + 双侧 wiring**（裁定 G）四条边界
- [x] `shortRef` 序列化格式 + `read-ref` input schema 映射在 design 中足够精确，Phase 2/3 可直接落地（无臆想空间，含归档实例写/读双侧路径）
- [x] **无静默跳过**：design 明确读回 hash 不一致抛/返回显式错误，不静默返回（见 Minimum Rules #24）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 引用式压缩策略与归档实现

Status: completed
Targets: `nop-ai/nop-ai-toolkit/`（归档接口，按 Phase 1 裁定 B）；`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/compact/`（引用式策略 + 归档实现）

- Item Types: `Proof`

- [x] 新增 `shortRef` 结构（type/path/range/hash）+ 作为消息内容的精确可解析序列化标记（按 Phase 1 裁定 E 格式）
- [x] 新增引用归档接口（toolkit，按裁定 B）+ 内存实现（agent/compact，按裁定 B）：按 hash 寻址 put/get，per-session 生命周期
- [x] **归档实例宿主接线**（按裁定 G）：归档实例挂 `AgentSession`；引用式策略经 `CompactionContext`→`AgentExecutionContext`→session 拿到实例 PUT；`AgentToolExecuteContext` 覆写 `IToolExecuteContext` 访问器从 session 取实例。双侧共享同一 session 宿主实例
- [x] 新增引用式 `ICompressionStrategy` 实现：按 Phase 1 裁定 A 的信号源识别可保真内容 → 原文入归档 → 替换为 `shortRef`；无可定位内容时返回 unchanged 结果（显式，不抛、不静默吞）
- [x] **不扩展 `CompactionResult`**（按裁定 D）：归档句柄不进结果对象，`shortRef` 自带 hash 作读回键
- [x] 引用式策略可被 `PipelineCompactor` 装配（构造器/Builder 接受，escalation 顺序按裁定 F）

Exit Criteria:

- [x] 引用式策略对"含可保真内容"的输入产出 `shortRef` 引用 + 原文已入归档（hash 寻址可读回）
- [x] 引用式策略对"无可定位内容"返回显式 unchanged 结果（`tokensAfter==tokensBefore` + `compactedMessages` 按既有约定），不抛异常、不静默丢消息（PipelineCompactor 既有 skip-layer 路径兼容）
- [x] 归档按 hash 寻址：相同内容单份存储；读回键=内容 hash
- [x] **接线验证**：引用式策略确被 `PipelineCompactor.compact` 在 escalation 顺序中调用（单测断言策略被调用 + 产出被消费，见 Minimum Rules #23）
- [x] **无静默跳过**：归档 put/get 失败、hash 计算失败时显式异常或显式 unchanged，不 `continue`/空体/吞异常（见 Minimum Rules #24）
- [x] 新增引用式策略有对应单测覆盖：有可保真内容 / 无可保真内容 / 归档 hash 寻址 三个路径（见 Minimum Rules #25）
- [x] design §8.2 与落地一致（无新 drift）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - read-ref 工具与端到端读回

Status: completed
Targets: `nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/`（新增 `ReadRefExecutor`）；`nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/api/IToolExecuteContext.java`（按 Phase 1 裁定 C 暴露归档只读视图）；端到端压缩→读回集成

- Item Types: `Proof`

- [x] 新增 `read-ref` 工具（`IToolExecutor` 实现）：输入 `shortRef`（或其字段，按裁定 E schema）→ 从归档按 hash 读回原文 → hash 校验
- [x] hash 一致：返回原文（`AiToolCallResult` 成功）
- [x] hash 不一致 / 引用不存在：返回显式错误结果（"内容已变更/引用失效，请重新读取"），fail-loud，不返回空或旧文
- [x] 工具注册到 toolkit manager（与 `read-file` 同级注册路径，工具名 `read-ref` 与 design/analysis 一致）
- [x] 归档只读视图经 `IToolExecuteContext` 暴露（按裁定 C 选定的 default-UOE 桥 或 全量加方法）；Tool 只读不写、不访问完整消息历史/Plan（符合 context-model §4.1）

Exit Criteria:

- [x] **`IToolExecuteContext` 爆炸半径已处理**（裁定 C）：按选定方案（default UOE 桥 或 全量 22 处更新）落地，全部相关实现 + 测试 mock 编译/测试通过；`./mvnw test -pl nop-ai/nop-ai-toolkit -am` 通过
- [x] **端到端验证**（见 Minimum Rules #22）：从 `AgentCompactionCoordinator.performCompaction`（压缩触发）→ 引用式策略产出 `shortRef` + 归档原文 → LLM 看到 `shortRef` → 调用 `read-ref` → 读回原文与原始内容一致，完整路径有一条集成测试
- [x] **接线验证**：`read-ref` 工具确被 toolkit 注册并在 agent 工具集中可见（断言工具名存在 + 可调用）；归档视图确经 `IToolExecuteContext` 被工具读到（断言 put 的内容能被 get，见 Minimum Rules #23）
- [x] hash 校验路径有测试：一致（读回成功）+ 不一致（显式错误）两条（见 Minimum Rules #24, #25）
- [x] `read-ref` 工具不绕过 Tool 可见性约束（只访问归档只读视图，不访问完整消息历史/Plan）
- [x] 新增工具有对应单测（见 Minimum Rules #25）
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 与 `-pl nop-ai/nop-ai-toolkit -am` 通过
- [x] design §8.2 / `nop-ai-agent-context-model.md` 与落地一致
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：本 section 所有条目 + 每个 Phase Exit Criteria 全部 `[x]` 后才能 `completed`。本计划涉及代码变更，构建验证条目必填。

- [x] 引用式压缩双轨已落地：摘要式 + 引用式按内容类型分流共存
- [x] `read-ref` 读回工具可用且 hash 校验 fail-loud
- [x] 端到端"压缩→读回"路径已验证（Anti-Hollow：调用链运行时连通，非仅类型存在）
- [x] 零回归：未配置引用式策略 / 无可定位内容时行为与今日一致（既有 9 个 compact 测试 + 管线测试全过）
- [x] design §8.2 已回写为最终架构决策，无方向性/Proposed 残留
- [x] 受影响 owner docs 已同步（`nop-ai-agent-context-model.md` §8.2）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证引用式策略被 PipelineCompactor 运行时调用 + read-ref 被注册可达 + 端到端读回连通
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent,nop-ai/nop-ai-toolkit -am`
- [x] `./mvnw test -pl nop-ai/nop-ai-agent,nop-ai/nop-ai-toolkit -am`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### 引用归档的持久化后端（DB / 文件 / 跨进程）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 首版 in-session 内存归档即可使引用式压缩 + 读回端到端可用；持久化/跨进程归档是独立增强（长 session 跨重启读回），与本计划"双轨压缩能力"正交。
- Successor Required: yes
- Successor Path: 引用归档持久化 successor（与 session store / checkpoint 持久化同向）

### 引用式压缩的自动触发阈值生产调参

- Classification: `optimization candidate`
- Why Not Blocking Closure: 触发阈值（内容大小/类型/重要性）已在 design 留可配置项；生产调参不影响双轨压缩能力成立。
- Successor Required: no

## Non-Blocking Follow-ups

- 引用式压缩的可观测性指标（引用命中率、读回频率、hash 失效频率）——与 W4-2 压缩比度量同向，可合并到 W4-2。
- 跨 agent / 跨 session 引用共享——首版 per-session，共享是独立增强。

## Closure

Status Note: W4-1 引用式压缩双轨全部落地。新增引用式（无损指针）压缩路径与既有摘要式（有损）按内容类型分流共存：toolkit 侧交付 `ICompactionArchive`/`ICompactionArchiveReader` 接口 + `ShortRef`/`ShortRefHasher` + `ReadRefExecutor` 工具（fail-loud hash 校验）；agent 侧交付 `InSessionCompactionArchive`（per-session hash 寻址）+ `ReferenceCompactionStrategy`，经 `AgentSession` 宿主双侧 wiring（coordinator 写 / AgentToolExecuteContext 读）。`CompactionResult` 未扩展、`ChatMessage`/`nop-ai-api` 未动（裁定 A/D）。22 处 `IToolExecuteContext` 实现经 default UOE 桥零改动（裁定 C）。端到端 Anti-Hollow 验证通过。独立 closure audit（fresh subagent）CAN CLOSE，无 hollow、无回归、无静默跳过。
Completed: 2026-08-02

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure-audit 子 agent（fresh session, task `ses_04180cc27ffeQGBNHFewzX8VbE`，explore 类型，read-only 对抗性审计）
- Evidence:
  - **Phase 2 Exit Criteria 7/7 PASS**：(1) shortRef 产出 + 原文归档 hash 寻址读回——`TestReferenceCompactionStrategy.hasReferenceableContentProducesShortRefAndArchivesOriginal` 断言 `archive.contains(hash)` + `getByHash==original` + `tokensAfter<tokensBefore`；(2) 无可保真内容显式 unchanged（null compactedMessages，PipelineCompactor skip-layer 兼容）；(3) hash 寻址去重 `InSessionCompactionArchive.put:putIfAbsent` + `TestInSessionCompactionArchive.deduplicationSameContentYieldsSameHashSingleCopy`；(4) 接线验证 `wiringStrategyInvokedByPipelineAndOutputConsumed` AtomicInteger 断言 invoked==1 + 产出被消费；(5) 无静默跳过（null/空 fail-fast，put 异常上抛由 PipelineCompactor 优雅降级）；(6) 10+8 单测覆盖三路径；(7) design §8.2 一致。
  - **Phase 3 Exit Criteria 9/9 PASS**：(1) 爆炸半径 default UOE 桥，3179 测试通过证明 22 处实现/mock 零破坏 + `defaultUoeBridgeFailsLoudOutsideAgentEngine` 验证；(2) 端到端 `TestReferenceCompactionEndToEnd.compactProducesShortRefAndReadRefReadsItBack` 驱动真实 `AgentCompactionCoordinator.performCompaction` → shortRef → read-ref 读回 `originalLong==body`；(3) `readRefToolRegisteredAndCallableByName` 断言工具名可见 + 可调用 + bean 注册 `ai-tools-defaults.beans.xml:7`；(4) hash 一致 + 不一致（corrupting archive）+ 缺失三失败路径测试；(5) ReadRefExecutor 只访问归档只读视图；(6) 8 单测；(7)(8) 测试通过 + design 一致；(9) log 已更新。
  - **Closure Gates 12/12 PASS**：见上 + design 无 Proposed 残留（grep 验证）+ deferred 两项（持久化后端/阈值调参）明确 out-of-scope。
  - **Anti-Hollow Check PASS**：(a) 策略运行时被 PipelineCompactor 调用（AtomicInteger 计数器断言）；(b) read-ref 运行时注册可达（provider 名解析 + executeAsync 实调）；(c) 端到端 performCompaction→shortRef→read-ref 读回连通（驱动真实 coordinator + InMemorySessionStore）。新增文件无空方法体/continue/吞异常/TODO-as-implemented。
  - **`node ai-dev/tools/check-plan-checklist.mjs <plan> --strict`** 退出码 0（所有 checklist 已勾选 + Closure Evidence 已写入）。
  - **`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai/nop-ai-toolkit --severity high`** 退出码 0（0 findings）。`--module nop-ai/nop-ai-agent` 仅 2 findings，均为 pre-existing（`PlanReplanner.java`/`NoOpProviderFailoverQueue.java` 注释，非本次 scope）。
  - **Build**：`./mvnw test -pl nop-ai/nop-ai-agent,nop-ai/nop-ai-toolkit -am -T 1C` → BUILD SUCCESS，3179 tests, 0 failures, 0 errors。29 新测试（TestInSessionCompactionArchive 8 + TestReferenceCompactionStrategy 10 + ReadRefExecutorTest 8 + TestReferenceCompactionEndToEnd 3）全过。
  - **Deferred 项分类检查**：两项（持久化后端/阈值调参）为 `out-of-scope improvement`/`optimization candidate`，附 non-blocking 理由，无 in-scope live defect 降级。

Follow-up:

- 引用归档持久化后端（DB/文件/跨进程）—— successor（与 session store / checkpoint 持久化同向），归 W4-2 范畴。
- 引用式压缩可观测性指标（命中率/读回频率/hash 失效频率）+ 自动触发阈值生产调参—— non-blocking，可与 W4-2 压缩比度量合并。
