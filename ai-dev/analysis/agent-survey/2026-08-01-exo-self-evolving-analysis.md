# Exo 递归自改进与不可变事件日志深度分析 & Nop AI Agent 审计/执行恢复

> Status: open
> Date: 2026-08-01
> Scope: `~/ai/exo`（递归自改进 agent harness，Rust+TS）vs `nop-ai-agent`（checkpoint 审计 + 可靠性）
> Conclusion:

## 一、总览

**Exo** 把"agent 可修改自身的一切"作为一等设计目标：agent 在 sandbox 内可读写自身源码并 rebuild/restart。安全网是**不可变 canonical event log**（agent 唯一不能修改的）。核心：sandbox 隔离 + 快照回退、canonical state 与可变 sandbox 分离、durable scheduler（slot-anchored fires + pending-fire redelivery）、分层 trait（Harness→Agent→Conversation）。

| 维度 | Exo | nop-ai-agent |
|------|-----|--------------|
| 自改进 | 一等目标（agent 改自身代码） | 无 |
| 安全网 | 不可变 canonical event log | checkpoint（可写） |
| 状态分离 | canonical（sandbox 外）/ 可变（sandbox 内） | AgentModel（静态）/ AgentSession（状态） |
| 调度 | durable scheduler + slot-anchored + redeliver_pending_wakes | 无 |

## 二、核心机制

### 2.1 Host-side loop（`executor/harness_executor.rs:31-58`）
- 接收消息/事件→构建 context→暴露工具→执行→记录；循环在 sandbox 外。

### 2.2 Sandbox + 快照回退 + canonical state
- Ubuntu sandbox 可装包/跑命令；snapshot/rewind 撤销实验；源码挂载于 `/workspace/exo`。
- **canonical state**（对话/工具/adapter 事件）存 sandbox 外，rewind 不回退——跨实验/重启重建上下文。

### 2.3 不可变 canonical event log
- agent 唯一不能修改的——递归自改进的防死循环安全网。

### 2.4 Durable scheduler（`scheduler_runtime.rs:46-60,71-80`）
- 持久化状态、slot-anchored fires、one-shot @at、durable fire→wake handoff；崩溃后 `redeliver_pending_wakes` 补投未确认唤醒。

## 三、对 nop-ai-agent 的借鉴要点

1. **不可变 canonical event log**（高价值）——nop 的 checkpoint 当前可写可覆盖；对于"递归/自动化"场景（agent 自主修改 plan/工具），需要**追加不可变审计日志**作为防篡改安全网（即使 agent 改了状态，历史不可抵赖）。与 grok-build 的 Journal（`2026-08-01-grok-build-deterministic-replay-analysis.md`）方向一致：grok 是重放用，exo 是审计用。
2. **Durable fire→wake handoff + pending-fire redelivery**（高价值）——补强 rivet 的 Actor 唤醒（`2026-08-01-rivet-actor-runtime-analysis.md`）与 hatchet 的 WAIT_FOR（`2026-08-01-hatchet-durable-execution-analysis.md`）：调度器崩溃恢复后补投未确认唤醒，保证"至少一次"投递。
3. **canonical/mutable 状态分离**（中价值）——对应 nop 的 AgentModel（静态）/AgentSession（状态）分离，但 exo 更进一步：会话历史本身也分"可回退实验态"与"不可回退事实态"。
4. **分层 trait（Harness→Agent→Conversation）**（中价值）——适合 team 包组织结构。

## 四、结论

exo 对 nop 的核心启示是"递归/自动化场景下的不可变审计"与"调度崩溃恢复补投"。局限：早期阶段、Rust/TS、聚焦单 agent 自改进、sandbox-only 执行模型。

## References
- `~/ai/exo/executor/harness_executor.rs`、`scheduler_runtime.rs`、`harness_types.rs`
- `ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md`、`nop-ai-agent-actor-runtime-vision.md`
- `ai-dev/analysis/agent-survey/2026-08-01-grok-build-deterministic-replay-analysis.md`、`2026-08-01-rivet-actor-runtime-analysis.md`、`2026-08-01-hatchet-durable-execution-analysis.md`
