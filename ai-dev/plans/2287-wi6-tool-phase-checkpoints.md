# 2287 WI6 长时工具相位检查点 + 保守恢复纪律（refactor roadmap M1）

> Plan Status: completed
> Last Reviewed: 2026-10-02
> Source: `ai-dev/backlog/nop-ai-agent-refactor-roadmap.md` WI6（Deliverable: 代码变更 + 测试；deps: WI2）
> Baseline Ruling: `ai-dev/analysis/2026-10/2026-10-02-wi1-dedup-ruling.md`（WI6 本 roadmap 吸收；与 autonomous WI10 互补分层——该项=恢复时孤儿工具合成"结果未知"响应，本项=相位级检查点使恢复粒度更细）
> Design Contract: `ai-dev/design/nop-ai-agent/nop-ai-agent-async-tool-execution.md`；owner docs `ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md` + `nop-ai-agent-session-and-storage.md`

## Purpose

为 bash/文件写类高副作用工具引入相位检查点（启动前/执行中/完成三相位），崩溃恢复时按相位显式裁决——"结果不可判定即失败"，不盲目重跑。为 autonomous WI10（孤儿合成收尾）提供相位信息基础。

## Current Baseline

（执行时 HEAD `6ee0143e69`，2026-10-02）

- 检查点现状：`AgentToolDispatcher.commitToolResults` 在每工具 commit 后保存唯一 TOOL_EXECUTION checkpoint（callId 幂等键，resultText=工具输出）——只有"完成"一个相位；dispatch 意图与执行中相位无记录。
- `CheckpointType` 4 值（TOOL_EXECUTION:26/LLM_TURN:37/COMPACTION:46/WAIT_FOR:58）；`Checkpoint.of(sessionId, watermark, seq, ts, type, toolName, callId, argumentsText, resultText, messageCount, tokensUsed)`；idempotencyKey 为 divergence 检测指纹（非唯一性约束；唯一性靠 watermark 主键，见下）。
- 读取面（WI3 已提升）：`ICheckpointManager.getCheckpoints(sessionId)` default 方法，三功能实现 override；`OpenToolCallResolver.filterNeverCommitted/filterCommittedButUnsynced`（restore 告警接线 `logOpenToolCallDiff`）。
- 工具执行相位可用信号：WI4 的 `BashExecutionTracker`（callId → 进程 + capturedRef，execute 开始登记/finally 注销）即"执行中"相位的天然登记点；dispatcher `startToolPhase`（future 创建）即"启动前（dispatch intent）"。
- 恢复面：restoreSession → verifyLatestCheckpoint（一致性告警）→ logOpenToolCallDiff（三态）；无相位裁决——开放 callId 的处置（合成收尾）归 autonomous WI10，本项交付其消费的相位信息。
- 水位纪律（审查修正）：checkpointSeq 是 **per-ExecutionState（per-execute()）** 而非 per-session——restore 后新 execute() 从 0 重计；现有 watermark 不碰撞靠 execStartTime 段（`{s}:tool:{c}:{execStartTime}:{seq}`，sessionId==null 时 `anon:` 前缀）；checkpoint 表以 watermark 为主键（DB append-only INSERT，重复主键上抛）。相位水印**必须保留 execStartTime 段**。

## Goals

- **相位检查点**：新增 `ToolExecutionPhase` 两值（DISPATCHED/EXECUTING；COMPLETED 即既有终态 = **无相位段**的 TOOL_EXECUTION checkpoint）。**watermark 相位段是唯一权威载体**（resultText 永不参与解析——bash 输出可能恰好包含标记串；DISPATCHED 的 resultText 用固定常量 `"[phase:DISPATCHED]"`），格式 `{s}:tool:{c}:{PHASE}:{execStartTime}:{seq}`（anon 分支保留），不新增 CheckpointType、不改 Checkpoint 模型。落点：DISPATCHED=startToolPhase/addCallsToPhase future 创建后（**所有工具**——非 heartbeat 模式与只读工具同样受益于保守裁决；journal 膨胀裁定：DB append-only 永不删除，工具 checkpoint 量 2-3 倍为已接受成本）；EXECUTING=executor 轮询（见下）；终态=既有 commit 路径不变。
- **EXECUTING 触发 = executor 侧轮询（审查 M1 修正：放弃 push 设计）**：`IToolProgressTracker.onPhase` push 跨模块不闭环（BashExecutionTracker.register 在 toolkit sandbox 内部 tool 线程，agent 侧 pull 接缝感知不到）且 tool 线程写 checkpoint 与 executor 线程 checkpointSeq 竞态。改为：wait 循环每片边界（awaitWakeup 返回后）对 pending callId 查既有 `progressSnapshot(callId).isInFlight()`，首见 in-flight 即在 executor 线程写 EXECUTING checkpoint（每 callId 至多一次，startedExec 集合去重）；快工具跳过 EXECUTING（DISPATCHED 已保守覆盖，裁决语义不变）。零 toolkit 变更、零接口变更。NoOp tracker 下 EXECUTING 永不出现（裁决不受影响，两相位同为"不可判定"）。
- **保守恢复裁决（含审查 B1 收窄裁定）**：`OpenToolCallResolver.collectCheckpointedCallIds` **收窄为仅终态 checkpoint（无相位段）计为 committed**——否则 DISPATCHED checkpoint 会被 WI3 三态判定误认为已 commit，静默消灭孤儿告警（不变量③状态 a 消失）。新增 `resolveExecutionPhase(openCallId, checkpoints)`（实现命名以本名为准）：COMPLETED 终态 > EXECUTING > DISPATCHED > NEVER_STARTED。restore `logOpenToolCallDiff` 相位分级（EXECUTING 优先于 DISPATCHED 行）。**回归测试**：仅有 DISPATCHED checkpoint 的开放 call 仍必须走 never-committed/orphan WARN。
- **divergence 检查相位感知重分类（审查 M2 修正版，TestRestoreSessionIdempotencyDivergence 暴露）**：相位 latest checkpoint 且 liveKey==null 时"未持久化"与"截断历史"在快照侧不可区分——保守纪律保留 divergence 标记，仅重分类告警文案（inconclusive cause，不宣称 state corruption）。
- **续跑分支裁定（审查 M3 显式登记）**：roadmap WI6"按相位续跑**或**显式失败"取"显式失败"分支——bash 进程在 sandbox register（进程启动）之后才可观测，"无 EXECUTING"≠"无副作用"，DISPATCHED 后的续跑=重跑，违反"结果不可判定即失败不盲目重跑"纪律；孤儿处置动作（合成失败响应）归 autonomous WI10。closure 时在 roadmap WI6 行加勘注登记此取舍。

## Non-Goals

- 孤儿工具的合成收尾响应（autonomous WI10 承接；本项交付相位信息）。
- 按相位续跑（resume mid-execution）——bash 进程不可复位，相位续跑对高副作用工具意味着重跑，违反保守纪律；恢复裁决只区分"可判定终态"与"显式失败"。
- Checkpoint 模型/CheckpointType 枚举变更。
- 非 bash/文件写类工具（无副作用的只读工具无相位价值，DISPATCHED 相位对所有工具写——EXECUTING 仅 bash 类经 tracker 触发）。

## Scope

### In Scope

- `Checkpoint` 水位约定扩展（相位段水印，纯字符串约定 + 常量），`OpenToolCallResolver` 相位判定方法。
- dispatcher startToolPhase/addCallsToPhase 写 DISPATCHED 相位 checkpoint。
- `IToolProgressTracker.onPhase` 回调 + toolkit 适配（register→EXECUTING）+ executor 侧桥接写 EXECUTING checkpoint。
- restore `logOpenToolCallDiff` 相位分级告警。
- 测试：三相位 checkpoint 写入/读取；相位判定（EXECUTING/DISPATCHED/无）；restore 告警分级；既有测试零回归。
- owner docs（reliability.md 相位裁决节 + session-and-storage.md 水位约定 + **async-tool-execution.md §三.2 不变量③修订**——committed 判定收窄为终态 checkpoint，审查 M4）+ log。

### Out Of Scope

- nop-ai-api 变更；checkpoint journal 存储格式变更（水位字符串约定层）。

## Execution Plan

### Phase 1 - 相位检查点与保守裁决

Status: completed
Targets: dispatcher、resolver、executor 轮询、restore 告警

- Item Types: `Feature`

- [x] `ToolExecutionPhase` 枚举 + 相位水位约定常量 + dispatcher DISPATCHED 写入（startToolPhase/addCallsToPhase，checkpointSeq 延续）
- [x] executor 轮询写 EXECUTING（wait 循环片边界，startedExec 去重；facade 路径不轮询）
- [x] `OpenToolCallResolver` 相位判定（resolveExecutionPhase：COMPLETED 终态 > EXECUTING > DISPATCHED > NEVER_STARTED）+ restore `logOpenToolCallDiff` 相位分级

Exit Criteria:

- [x] 单测：DISPATCHED/EXECUTING 写入与读取往返；判定三态（含 COMPLETED 兼容既有无标记终态）；水位唯一性（同 callId 不同相位+同 execStartTime 跨执行重现不碰撞——execStartTime 段生效）
- [x] **B1 回归**：仅有 DISPATCHED checkpoint 的开放 call 仍走 never-committed/orphan WARN（committed 集合收窄生效）
- [x] **M2 重分类**：相位 latest checkpoint + liveKey==null → divergence 标记保留、告警文案重分类为 inconclusive（TestRestoreSessionIdempotencyDivergence 暴露原豁免设计与截断场景冲突，plan 已同步修订）
- [x] addCallsToPhase（唤醒派生）DISPATCHED 写入；abandon/取消路径残留 DISPATCHED checkpoint 时消息已闭合、恢复判定保持静默（终态闭合优先）
- [x] restore 告警分级断言（EXECUTING 与 DISPATCHED 分行）
- [x] WI4/WI5 全部既有测试零回归；`./mvnw test -pl nop-ai/nop-ai-agent,nop-ai/nop-ai-toolkit -am` 全绿
- [x] log 条目

### Phase 2 - owner docs 与收口检查

Status: completed
Targets: reliability.md、session-and-storage.md、log、验证

- Item Types: `Proof`

- [x] reliability.md 补相位裁决节；session-and-storage.md 补水位约定；async-tool-execution.md §三.2 不变量③修订
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

Exit Criteria:

- [x] owner docs 更新在位
- [x] `./mvnw test -pl nop-ai/nop-ai-agent,nop-ai/nop-ai-toolkit -am` 通过
- [x] check-doc-links --strict 退出码 0

## Closure Gates

> 收口顺序：独立 closure audit 通过 → 勾选 refactor roadmap WI6 checkbox → git commit → 本 plan 标 completed。

- [x] 三相位检查点端到端连通（dispatch→EXECUTING→commit 的写入链 + restore 相位裁决读取链）
- [x] 保守纪律可观测（"结果不可判定即失败"的裁决语义在 restore 告警中显式表达）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] `./mvnw test -pl nop-ai/nop-ai-agent,nop-ai/nop-ai-toolkit -am` 通过
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-agent --severity high` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] 受影响 owner docs 已同步
- [x] git commit 完成（含全部 In Scope 文件）

## Closure

Status Note: 三相位检查点（DISPATCHED/EXECUTING/终态）+ committed 集合收窄（B1）+ divergence 重分类（M2 修订版）+ EXECUTING 轮询（M1 修正版）+ 续跑分支裁定（M3 勘注）全部落地；closure audit 两轮——首轮 FAIL 补齐 3 处 focused tests，复核 2 处一行项（测试改名/checkbox）已落地——WI6 交付全部落地。
Completed: 2026-10-02

Closure Audit Evidence:

- Reviewer / Agent: 独立子代理 agent_d8abe595-5a85-4ba6-beca-44e78d069eeb（fresh session，两轮）
- Evidence:
  - 首轮 8 验证点：机制本体全 PASS（相位枚举/水位/dispatcher 写入/B1 收窄/M2 重分类/M1 轮询实现/owner docs/勘注），2 blocker = 3 处声称的 focused tests 不存在（restore 分级断言/EXECUTING 生产写入/addCallsToPhase 派生 DISPATCHED）
  - 补齐：gradeOrphanPhases 提取（行为等价）+ 3 个生产路径测试（分级分行/EXECUTING 恰一次去重生效/派生 DISPATCHED 恰一次）——复核"断言具备正误区分力"PASS
  - 复核轮 2 处一行项：test 5 改名 phaseTaggedWatermarkIsDivergenceReclassificationTrigger（否决设计遗留清除）+ Phase 2 :85 checkbox——已落地
  - surefire agent+toolkit 3693 / 0F / 0E；hollow scan exit 0；doc-links --strict exit 0
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2287-wi6-tool-phase-checkpoints.md --strict` exit 0（completed 后 0 failed）

Follow-up:

- autonomous WI10 消费相位信息合成收尾（跨 roadmap 协同条款，非本 plan 遗留）
