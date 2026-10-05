# 2286 WI5 steering 双相位唤醒 Phase A（refactor roadmap M1）

> Plan Status: completed
> Last Reviewed: 2026-10-02
> Source: `ai-dev/backlog/nop-ai-agent-refactor-roadmap.md` WI5（Deliverable: Phase A 代码变更 + 测试 + Phase B 立项裁定记录；deps: WI3）
> Baseline Ruling: `ai-dev/analysis/2026-10/2026-10-02-wi1-dedup-ruling.md`（WI5 本 roadmap 吸收；autonomous roadmap 定位排除 steering 交互场景）
> Design Contract: `ai-dev/design/nop-ai-agent/nop-ai-agent-async-tool-execution.md` §三.1/§三.3/§三.6

## Purpose

落地 steering 等待期唤醒（Phase A）：工具等待期 steering 到达时模型空闲（无在飞请求可打断），以含运行中工具占位的瞬时请求处理新输入，处理后回到等待，迟到结果按 WI3 不变量原位插入。同步产出 Phase B（在飞 LLM 请求打断-重建）立项裁定记录（本项只裁定不实施）。

## Current Baseline

（执行时 HEAD `0674500c31`，2026-10-02）

- WI4 等待基础设施在位：`ToolPhaseSession.awaitCompletionSlice(sliceMs)`（三态 boolean；内部单次 allOf.get(sliceMs)）；`runHeartbeatToolPhase` 循环（门槛集/心跳唤醒轮/三选项裁决）；fail-soft `doWakeupCallWithRetry`；`insertToolResultInList` 占位锚定。
- steering 现状：`AgentExecutionContext.steeringQueue`（ConcurrentLinkedQueue，:84）+ `enqueueSteering:298`（Actor 消费线程写）+ `drainSteering:319`（ReAct 线程 round 边界消费，`runToolFanout` 边界 drain 块 `ReActAgentExecutor:1506-1514`）；等待期 steering 不可见（阻塞在 slice 上，最长等至 heartBeatIntervalMs）。
- round 边界 drain 语义（react-engine §5.2）：heartbeat 模式关闭时 steering 仍只在 round 边界消费——Phase A 不改变该路径。
- 决策线协议（设计 §三.3）：WAIT/ABANDON[callId]/tool_calls=并行——steering 唤醒轮复用同一裁决。
- WI1 裁定表协同条款：WI9×autonomous WI3（事件 payload 契约面）——本项不触碰事件面。

## Goals

- **steering 唤醒源**（dispatcher）：`awaitCompletionSlice` 保留（facade 路径继续使用——**facade 不得接入 steering probe**，否则空循环体在 steering pending 期间 busy-spin），新增 `awaitWakeup(sliceMs, steeringProbe)` 三态事件 COMPLETED/SLICE_EXPIRED/STEERING_PENDING——内部 200ms 量子分片（**量子钳至剩余 sliceMs；sliceMs<=0 时先做完成判定+probe 再返回**，不破坏 nextWaitSlice 的 cap 契约；感知延迟上界 ~200ms，不引入 watcher 线程）；同拍 tie-break：**probe 先于 expiry 判定**（新输入优先于心跳证据）；COMPLETED 优先于 STEERING_PENDING（全部完成时 steering 留队列由边界 drain 消费，不丢失）；中断纪律保持。
- **steering 唤醒轮**（executor）：STEERING_PENDING → 瞬时请求构造（**队列非破坏性快照**——CLQ toArray 弱一致读取构造请求副本 + 逐占位自身 callId 锚定；**不先 drain**）→ doWakeupCallWithRetry（fail-soft）→ **仅 LLM 成功后执行 drain-commit**：steering 消息先/回复后入 ctx → usage 归账 → 同一三选项裁决（steering 轮回复可 WAIT/ABANDON/并行派生）。失败路径（fail-soft/BAIL 前置 return）steering 留队列**不回灌**（下轮 probe 重试消费，无重排风险）；guardrail BAIL 特例：弃置回复但 steering 消息必须入史（真实用户输入），BAIL 分支内执行 drain-commit（仅消息，无裁决）。
- **Phase B 立项裁定记录**：设计文档登记"不立项"裁定（Phase A 覆盖等待期 steering；在飞打断-重建无场景驱动，成本覆盖流式语义）+ successor 条件。
- react-engine.md §5.2 steering 语义更新（round 边界 drain 与等待期唤醒的共存关系）+ Open Question ②（steering 过渡策略）收口。

## Non-Goals

- Phase B 在飞打断-重建（只裁定不实施）。
- steering 持久化/语义三分（WI8 承接）。
- heartbeat 关闭模式（heartBeatIntervalMs<=0）的行为变更——该模式 round 边界 drain 语义保持。
- mailbox/DeferredAckMailbox 扩展（WI7 承接）。

## Scope

### In Scope

- `AgentToolDispatcher.ToolPhaseSession`：awaitWakeup 三态事件（200ms 量子内部分片 + steeringProbe）。
- `ReActAgentExecutor.runHeartbeatToolPhase`：循环改用 awaitWakeup；steering 唤醒轮方法（复用心跳轮构件）。
- owner doc：§三.3 补 steering 唤醒轮语义 + Phase B 裁定记录；react-engine.md §5.2 更新。
- 测试：steering 等待期唤醒集成测试（消费/裁决/ABANDON/迟到结果原位）+ 非 heartbeat 模式回归。
- log 条目。

### Out Of Scope

- nop-ai-api 变更；WI7/WI8 的输入通道面。

## Execution Plan

### Phase 1 - steering 唤醒源与唤醒轮

Status: completed
Targets: `AgentToolDispatcher`、`ReActAgentExecutor`

- Item Types: `Feature`

- [x] awaitWakeup 三态（200ms 量子 + steeringProbe；COMPLETED 优先；中断纪律保持；awaitCompletionSlice 保留或由 awaitWakeup 取代——以实现整洁为准，既有调用零回归）
- [x] runHeartbeatToolPhase 循环改 awaitWakeup + runSteeringWakeupTurn（构件复用心跳轮：请求副本构造/fail-soft/guardrail/入 ctx 顺序/usage/三选项裁决）
- [x] Phase B 裁定记录落设计文档

Exit Criteria:

- [x] 集成测试：①steering 等待期到达→唤醒轮消费（steering 消息+回复入史，先 steering 后回复）→迟到结果原位插入于 steering 消息之前（不变量②）；②steering 轮 ABANDON 裁决生效；③steering 唤醒轮 LLM 失败→fail-soft 回等待（steering 消息不入史，下轮重试消费）；④guardrail BAIL→回复弃置、steering 消息入史、按 WAIT
- [x] 非 heartbeat 模式（heartBeatIntervalMs<=0）steering round 边界 drain 回归（既有测试全绿）
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 全绿
- [x] log 条目

### Phase 2 - 文档同步与收口检查

Status: completed
Targets: owner doc、react-engine、log、全量验证

- Item Types: `Proof`

- [x] owner doc §三.3 steering 唤醒轮 + Phase B 裁定；react-engine.md §5.2 更新（Open Question ② 收口）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

Exit Criteria:

- [x] owner doc/react-engine 更新在位且不改既有语义（§5.2 增量）
- [x] log 条目在位
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 通过
- [x] check-doc-links --strict 退出码 0

## Closure Gates

> 收口顺序：独立 closure audit 通过 → 勾选 refactor roadmap WI5 checkbox → git commit → 本 plan 标 completed。

- [x] steering 等待期唤醒端到端连通（等待→steering 到达→唤醒轮消费→裁决→结果原位提交）
- [x] Phase B 裁定记录在位（不立项 + successor 条件）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 通过
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-agent --severity high` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] 受影响 owner docs 已同步
- [x] git commit 完成（含全部 In Scope 文件）

## Closure

Status Note: steering 等待期唤醒全链落地（awaitWakeup 三态/非破坏性快照/drain-commit 时序/BAIL 特例/三选项裁决复用/失败节流），Phase B 不立项裁定与 successor 条件登记设计 §三.3，Open Question ② 收口——WI5 Phase A 交付全部落地。
Completed: 2026-10-02

Closure Audit Evidence:

- Reviewer / Agent: 独立子代理 agent_8b04f793-b720-4dea-932d-f77a2eafdcdc（fresh session）
- Evidence:
  - 7 验证点全 PASS（awaitWakeup 三态/probe 先于 expiry/COMPLETED 优先/facade 封 P2/runSteeringWakeupTurn 快照+drain-commit 时序/循环分流/Phase B 裁定三要素/非 heartbeat 回归）
  - 审计 3 个非阻塞发现已全部修复并复测：①test ③ 空转断言改直接内容断言+注释修正（重试轮消费语义）；②失败 steering 轮后单 slice 节流（circuit-open 热循环封死）；③§5.2→§5.5 节号 drift 修正
  - surefire agent 3437 / 0F / 0E（clean 后）；hollow scan exit 0；doc-links exit 0
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2286-wi5-steering-phase-a-wakeup.md --strict` exit 0（收口勾选后运行）

Follow-up:

- no remaining plan-owned work（Phase B 不立项裁定见设计文档；若流式 UI 桥接场景出现再按 successor 条件立项）
