# 2285 WI4 心跳即输入（带进度证据）

> Plan Status: completed
> Last Reviewed: 2026-10-02
> Source: `ai-dev/backlog/nop-ai-agent-refactor-roadmap.md` WI4（Deliverable: 代码变更 + 测试；deps: WI3）
> Baseline Ruling: `ai-dev/analysis/2026-10/2026-10-02-wi1-dedup-ruling.md`（WI4 本 roadmap 吸收）
> Design Contract: `ai-dev/design/nop-ai-agent/nop-ai-agent-async-tool-execution.md`（§三.1/§三.3 含 plan 2285 实现补充裁定 / §三.5 / §三.6 含进度接缝裁定）

## Purpose

落地心跳即输入：工具等待期按 `heartBeatIntervalMs` 节拍发起心跳唤醒轮——瞬时 LLM 请求（占位渲染 + 心跳证据：运行中工具清单 + 已等待时长 + bash 输出尾部），模型三选项裁决（继续等待/放弃/并行独立工作），机械超时降级为兜底。WI5 在同一竞争原语上追加 steering 唤醒源。

## Current Baseline

（执行时 HEAD `26f29cdd8b`，2026-10-02 复核；两轮对抗性审查修正后锚点）

- **等待机制**：`ReActAgentExecutor.runToolFanout:1159-1246`（安全链逐 call 评估 :1172-1196）→ `AgentToolDispatcher.executeAllowedCalls:174-216`（buildToolFutures :224-298 → awaitToolFutures :306-334 → commitToolResults :345-500）。等待阻塞在 `allOf().get()`（:313-315，可中断）。等待完全在 dispatcher 内部；`buildToolFutures`/`awaitToolFutures`/`commitToolResults`/`addToolResultInPlace` 均 private；`reentryCounters` 在 executeAllowedCalls:185 自建；buildToolFutures 无 callId→future 映射。
- **LLM 调用面只在 executor**：`llmCoordinator.doLlmCallWithRetry`（ReActAgentExecutor:704-705）；`prepareRoutedRequest:856-925` 以 `new ChatRequest(new ArrayList<>(ctx.getMessages()))` 快照构造（:921）+ model-switched 审计（:909-919）；`recordLlmTurn:934-979` 语义越界不可复用（LLM_TURN checkpoint/事件/POST_REASONING），其 usage 归账 `recordLlmTurnUsage:985-1009`（ctx.tokensUsed + usageRecorder）可安全复用。**失败语义**：`LlmCallCoordinator.finalizeLlmCallResult:437-445` 失败时置 ctx failed + EXECUTION_FAILED + invokeOnError——唤醒轮 fail-soft 需专用入口绕开终态副作用（设计 §三.3 补充裁定）。可复用 static 构件 `extractAssistantMessage:572-582`/`extractToolCalls:549-566`/`appendToolCallMessages:588-599`。
- **进度证据接缝（审查修正）**：BashExecutor（nop-ai-toolkit tools/BashExecutor.java:85-134）调 `IBashSandbox.execute(BashSandboxRequest)`（io.nop.ai.toolkit.tools.sandbox 包，Host/Docker 实现），**不是** agent 侧 `ISandboxBackend`（后者生产代码零调用方，NoOpSandboxBackend/DockerSandboxBackend 为未接线预留面）；Maven 依赖方向 agent→toolkit。roadmap "capturedRef 已在位"锚点（NoOpSandboxBackend.java:101-113）对生产 bash 流量不成立——已在设计文档 §三.6 修正，Phase 4 同步修 roadmap Current Baseline。进度登记落在 `IBashSandbox` 接缝（设计 §三.6 进度证据接缝裁定）。
- **取消语义**：orTimeout/cancel 均不中断工具线程（team/scheduler/TestSpawnDispatchTimeout.java:69 注释佐证）；进程级取消需 callId 寻址句柄（IBashSandbox 登记提供）。
- **并发安全**：工具执行线程从不写 ctx.messages（commit 全在 ReAct 线程 :345-500）；唤醒轮 LLM 调用保持在 ReAct 主线程即安全。
- **WI3 已就位**：占位渲染构件、`addToolResultInPlace`（anchor 改造见 Goals——按结果自身 callId 锚定，修复多区段历史下的配对缺陷，审查 M1）、dialect 唤醒形态断言。占位插入 helper 需提升为 list 级复用（请求副本与 ctx 共用，审查 M4）。
- **测试骨架（审查 M6 修正）**：心跳测试用**延迟完成 future** 模式（callTool 立即返回未完成 future，测试持句柄 complete）——`createBlockingToolManager`（TestDefaultAgentEngineCancel:137-157）在 callTool 内 await latch 会阻塞 ReAct 线程进不了等待期，不适用；scripted LLM 先例 TestRestorePendingSessions:89；executor 直构先例 ReActAgentExecutor.builder()（TestReActAgentExecutor:98 等）；测试用 Builder 设 heartBeatIntervalMs 小值（50ms 量级）+ 心跳计数 latch；toolTimeoutMs 置 0（Builder 不设即禁用）或显式调大。
- **配置面**：DefaultAgentEngineConfig 字段+setter 模式（toolTimeoutMs :152/:960-965；**注意 heartBeatIntervalMs 语义为 <=0=禁用分片，不照抄既有 setter 的拒绝 <=0 校验**）；engine→executor 链 = AgentExecutorResolver:158-205 → Builder（:577-603）→ executor 构造器（:223-226）→ dispatcher（:305-318）。
- **观察项（预先存在）**：`prepareDispatchContext:128-145` executor 参传 null，BashExecutor:86 经 engine 全链路会 NPE——已登记 Deferred（独立 defect，WI25 前必须复核）。
- **WI2/WI3 裁定锚点**：唤醒轮语义（非迭代/门槛集/guardrail 快速路径/持久化随下一次正常 checkpoint/入 ctx 顺序=心跳先回复后/放弃不写 TOOL_EXECUTION checkpoint/usage 照常累计——设计 §三.3 实现补充裁定）；心跳证据接缝与占位插入位置（设计 §三.6）。

## Goals

- **等待分片原语 + 等待会话契约**（dispatcher）：新等待会话对象（`startToolPhase` 语义）——登记 callId→future 映射与 deadline（开始时刻+cap）、`awaitNextEvent(sliceMs)` 三态（COMPLETED/HEARTBEAT_TICK/INTERRUPTED，分片=min(heartBeatIntervalMs, 最早 cap 剩余)）、pending callIds 快照、`cancelCall(callId)`（future cancel + tracker 进程取消 + cancelled 集合登记）、`commitResults()`（复用 commitToolResults，跳过 cancelled 项的 join，正常/放弃合成 result 均经原位插入）；`executeAllowedCalls` 重构为该会话的顺序组合（向后兼容门面）。
- **原位插入锚定修正**：`addToolResultInPlace` 锚点从批次首个 callId 改为**结果自身 callId** 所在区段（修复并行派生后多区段历史的 tool_result 先于自己 tool_use 的配对破坏）；list 级 helper 提取供请求副本复用；WI3 既有测试保持通过（锚定改动对其全兼容）。
- **进度证据与进程取消通道**（toolkit IBashSandbox 接缝）：`BashSandboxRequest` 增加 callId（BashExecutor 传 `AiToolCall.getId()`）；`IBashSandbox` 增加 default 方法 `progressSnapshot(callId)`（增量输出尾部+bytes；无登记返回显式空快照）与 `cancelExecution(callId)`（进程句柄取消；Host/Docker 实现维护运行中登记，execute 开始登记/finally 注销）；agent 侧新 `IToolProgressTracker`（engine 包）+ toolkit 适配实现；`DefaultAgentEngineConfig` 挂 tracker（默认 no-op），resolver/Builder/executor 全链传递。
- **心跳唤醒轮编排**（executor）：runToolFanout 工具等待期循环——分片等待→心跳唤醒点→前置门槛（cancel/pause/forceStop）→唤醒请求构造（**ctx 快照副本 + 逐占位按自身 callId 区段锚定插入 + 心跳证据 ChatUserMessage**，不污染 ctx）→ `doWakeupCallWithRetry`（专用 fail-soft 入口，设计 §三.3）→ 内容 guardrail 快速路径 → 心跳消息先、assistant 回复后入 ctx → usage 归账（不落 checkpoint）→ 三选项裁决（继续等待/放弃=cancelCall+合成取消 result 原位插入+pending 移除/并行=安全链逐 call 评估→通过者并入 pending，reentryCounters/checkpointSeq 延续会话）。
- **配置面**：`heartBeatIntervalMs`（默认 60_000，<=0=禁用分片退化为一次性等待）+ `toolTimeoutPolicy`（per-tool cap 覆盖 + 全局 cap，默认空=现状 toolTimeoutMs）全链。

## Non-Goals

- steering 唤醒源（WI5 承接——分片等待预留扩展点，本项不接 steering 分支）。
- 工具相位检查点（WI6 承接）。
- engine dispatch 路径 executor=null 的 bash 集成缺口修复（Deferred 登记在案）。
- agent 侧 ISandboxBackend（NoOp/DockerSandboxBackend）的接线或删除（未接线预留面，不动）。
- `runSingleIteration:641` 既有未使用 per-iteration reentryCounters 的清理（独立治理项）。

## Scope

### In Scope

- `AgentToolDispatcher`：等待会话 API（startToolPhase 语义/awaitNextEvent/cancelCall/commitResults）、executeAllowedCalls 门面化、原位插入锚定修正 + list 级 helper。
- toolkit：`BashSandboxRequest.callId`、`IBashSandbox` progressSnapshot/cancelExecution default 方法、Host/Docker 实现运行中登记、BashExecutor 传 callId。
- agent：`IToolProgressTracker` + 默认 no-op + config/resolver/Builder/executor 传递链。
- `LlmCallCoordinator`：`doWakeupCallWithRetry` fail-soft 入口（复用 retry 循环，终态失败跳过 setStatus/EXECUTION_FAILED/invokeOnError，仅 LOG.warn 返回 unsuccessful result）。
- `ReActAgentExecutor`：等待期循环 + 心跳唤醒轮编排 + 三选项裁决 + guardrail 快速路径。
- 配置链：`DefaultAgentEngineConfig`/`AgentExecutorResolver`/`ReActAgentExecutorBuilder`/executor 构造器/dispatcher。
- 测试：dispatcher 等待会话单测；toolkit sandbox 登记/快照/取消单测；executor 心跳轮集成测试（延迟 future + scripted LLM）；多心跳请求形态 dialect 断言。
- owner doc `nop-ai-agent-async-tool-execution.md` 补实现锚点（§三.1/§三.2/§三.3/§三.5/§三.6 已含裁定，增量=构件名）；roadmap Current Baseline capturedRef 锚点纠偏；log 条目（per-phase）。

### Out Of Scope

- nop-ai-api 公共 API 变更。
- nop-ai-shell `ShellCommandExecutor`（WI25）。
- steering 唤醒源（WI5）。

## Execution Plan

### Phase 1 - 等待会话契约、配置链与锚定修正

Status: completed
Targets: `AgentToolDispatcher`、`LlmCallCoordinator`、配置链

- Item Types: `Feature`

- [x] 等待会话 API（callId→future 登记/deadline/awaitCompletionSlice 三态/pending/cancelCall/commitResults 跳过 cancelled）+ executeAllowedCalls 门面化（API 名以实现为准：awaitNextEvent→awaitCompletionSlice）
- [x] addToolResultInPlace 锚定改为结果自身 callId + list 级 helper 提取（WI3 测试保持全绿）
- [x] `doWakeupCallWithRetry` fail-soft 入口（常规路径行为零变更）
- [x] `heartBeatIntervalMs`/`toolTimeoutPolicy` 配置链 + tracker 挂点（默认 no-op）

Exit Criteria:

- [x] 等待会话单测：完成立即返回/片到期 HEARTBEAT_TICK/中断纪律保持/cancelCall 后 commit 跳过该项/多区段历史下按自身 callId 锚定的插入位置断言（含并行派生形态：[call:A1, H1, R1, call:B1] → result:B1 在 call:B1 之后）
- [x] WI3 全部既有测试零回归（TestToolResultCommitOrdering 等 16 项）
- [x] doWakeupCallWithRetry 单测：失败返回 unsuccessful 且 ctx 状态未变、无 EXECUTION_FAILED 事件；常规 doLlmCallWithRetry 失败行为不回归
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 全绿
- [x] owner doc/log：本 Phase 无 owner-doc 语义变更（构件锚点统一 Phase 4），log 已记

### Phase 2 - 进度证据与取消通道（toolkit 接缝）

Status: completed
Targets: `BashSandboxRequest`、`IBashSandbox` 及实现、`BashExecutor`、`IToolProgressTracker`

- Item Types: `Feature`

- [x] BashSandboxRequest.callId + BashExecutor 传入
- [x] IBashSandbox.progressSnapshot/cancelExecution default 方法 + Host/Docker 运行中登记（开始登记/finally 注销）
- [x] agent 侧 IToolProgressTracker + 适配实现（包装 IBashSandbox；装配约束：适配与实际执行的 BashExecutor 必须共享同一 sandbox 实例）+ config 挂点（默认 no-op，显式空快照语义）

Exit Criteria:

- [x] toolkit 单测：执行中查到增量尾部/结束后注销/取消句柄被调/无登记返回空快照
- [x] 接线验证（Rule #23）：同实例转发直测（同一 sandbox 实例上登记 → 适配查询可见）
- [x] `./mvnw test -pl nop-ai/nop-ai-toolkit,nop-ai/nop-ai-agent -am` 全绿
- [x] owner doc/log：本 Phase 无 owner-doc 语义变更（接缝裁定已在设计文档 §三.6），log 已记

### Phase 3 - 心跳唤醒轮编排

Status: completed
Targets: `ReActAgentExecutor`

- Item Types: `Feature`

- [x] 等待期循环：分片等待→心跳唤醒点→前置门槛→唤醒请求构造（快照副本+逐占位自身 callId 锚定+心跳证据消息）→ doWakeupCallWithRetry → guardrail 快速路径 → 心跳消息先/回复后入 ctx → usage 归账 → 三选项裁决
- [x] 三选项处置（继续等待/放弃/并行并入 pending）+ 唤醒轮无 LLM_TURN checkpoint/无 EXECUTION_COMPLETED 断言面
- [x] engine 级 mock-tool 心跳 E2E 一条（TestDefaultAgentEngineCancel 模式 + 小 interval，验证 engine 入口到工具完成的完整路径）

Exit Criteria:

- [x] 集成测试（延迟 future + scripted LLM，heartBeatIntervalMs=50ms 量级 + 心跳计数 latch；toolTimeoutMs=0）：①两次心跳后放行→工具完成→result 原位插入于心跳消息之前（不变量②时序）；②放弃→pending 清空+合成取消 result 原位入 ctx+进程取消句柄被调+无 TOOL_EXECUTION checkpoint；③并行→DENY 被拒/ALLOW 并入并在完成后 commit 且插入位置正确（call:B1→result:B1 相对位置断言）；④唤醒 LLM 失败→回到等待分片、执行状态未污染；⑤BAIL→唤醒回复弃置按继续等待
- [x] 多心跳（≥2 次）请求形态 dialect 断言（占位紧随各自 tool_use，对齐 WI3 testWakeupRequestForm* 先例）
- [x] 迭代预算不消耗 + 无 LLM_TURN checkpoint/EXECUTION_COMPLETED 事件断言
- [x] `./mvnw test -pl nop-ai/nop-ai-agent,nop-ai/nop-ai-toolkit -am` 全绿

### Phase 4 - 文档同步与收口检查

Status: completed
Targets: owner doc、roadmap、log、全量验证

- Item Types: `Proof`

- [x] owner doc 补实现构件名锚点（§三.1/§三.3/§三.6）；roadmap Current Baseline capturedRef 接缝纠偏注
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

Exit Criteria:

- [x] owner doc 锚点增量完成；roadmap 纠偏注在位
- [x] log 条目完整（含 engine bash 集成缺口登记）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `./mvnw test -pl nop-ai/nop-ai-agent,nop-ai/nop-ai-core,nop-ai/nop-ai-toolkit -am` 通过

## Closure Gates

> 收口顺序：独立 closure audit 通过 → 勾选 refactor roadmap WI4 checkbox → git commit → 本 plan 标 completed。

- [x] 心跳唤醒轮端到端连通（分片等待→心跳请求→LLM 裁决→三选项处置→结果原位提交）——executor 层集成测试完整跑通为本项诚实端到端边界（engine 级 mock-tool E2E 增补一条；bash 真实链路受 Deferred 缺口限制不在本项验证面）
- [x] 唤醒轮语义边界与设计 §三.3（含实现补充裁定）一致
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] `./mvnw test -pl nop-ai/nop-ai-agent,nop-ai/nop-ai-core,nop-ai/nop-ai-toolkit -am` 通过
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-agent --severity high` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] 受影响 owner docs 已同步（含 roadmap 锚点纠偏）
- [x] git commit 完成（含全部 In Scope 文件）

## Deferred But Adjudicated

### engine dispatch 路径 bash 集成缺口（executor=null）

- Classification: `watch-only residual`
- Why Not Blocking Closure: 预先存在集成缺口，与心跳机制无依赖；修复涉及工具执行线程模型归属，属独立 defect；WI25 动工前必须复核。
- Successor Required: yes
- Successor Path: 独立 bug fix（`ai-dev/bugs/` 流程）

## Closure

Status Note: 等待会话契约/fail-soft 唤醒入口/心跳唤醒轮编排/进度证据接缝（toolkit IBashSandbox）/原位插入锚定修正全部落地；closure audit 首轮 FAIL（cap 优先级实现与设计相悖）已修复并复核 PASS——WI4 交付全部落地。
Completed: 2026-10-02

Closure Audit Evidence:

- Reviewer / Agent: 独立子代理 agent_4a7c9b00-e436-4b84-966e-05644dd300e7（fresh session，两轮）
- Evidence:
  - 首轮审计 8 验证点 7 PASS + 1 blocker（resolveToolTimeoutMs cap 优先级实现 name→"*"→tag 与设计 §三.5 钉定 name→tag→"*" 相悖，配置全局 cap 时 tag 覆盖静默失效）
  - blocker 修复：检查顺序改正 + TagCarryingToolManager 钉序测试（tag override 胜 "*"，可区分两种顺序）+ log 表述同步——复核 PASS
  - 4 minor 全部处理：guardrail 块路径 usage 提前入账、并行 DENY 断言测试（配对拒绝 result 语义）、Phase 4 L135 勾选、.rels 无关文件排除出 commit
  - surefire 三模块聚合 4108 tests / 0F / 0E（agent 3433 / core 428 / toolkit 247，skip 均为预先存在环境性）
  - scan-hollow --module nop-ai-agent --severity high exit 0；check-doc-links --strict exit 0
  - 设计契约符合性 §三.3 语义表逐条 PASS（不耗预算/无 ISustainer/门槛集/guardrail/无会级钩子/持久化/补充裁定五项/决策线协议/派生安全链/可中断纪律）
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2285-wi4-heartbeat-as-input.md --strict` exit 0（收口勾选后运行）

Follow-up:

- engine dispatch bash 集成缺口（watch-only residual，WI25 前必须复核）
- 观察：engine `setToolTimeoutMs` setter 正数校验（无法经 engine 禁用超时，executor 层 Builder 0 可禁用）——不阻塞，登记备查
