# Exo 递归自改进与不可变事件日志深度分析 & Nop AI Agent 审计/执行恢复

> Status: open
> Date: 2026-08-01
> Scope: `~/ai/exo`（递归自改进 agent harness，Rust+TS，~304 文件）vs `nop-ai-agent`（checkpoint append-only + reliability）
> Conclusion:

## 一、总览

**Exo** 把"agent 可修改自身的一切"作为一等设计目标：agent 在 sandbox 内可读写自身源码并 rebuild/restart。安全网是**不可变 canonical event log**（agent 唯一不能修改的）。

| 维度 | exo | nop-ai-agent |
|------|-----|--------------|
| 自改进 | 一等目标（agent 改自身代码并 rebuild/restart） | 无 |
| 安全网 | 不可变 canonical event log | checkpoint append-only（可写追加） |
| 状态分离 | canonical（sandbox 外，rewind 不回退）/ 可变（sandbox 内） | AgentModel（静态）/ AgentSession（状态） |
| 调度 | durable scheduler + slot-anchored + redeliver_pending_wakes | 无长时调度 |
| 抽象层级 | Harness→HarnessAgent→HarnessConversation 三级 | AgentModel→Agent→AgentSession |

## 二、核心机制详解

### 2.1 Host-side loop（`executor/harness_executor.rs:31-58`）
- 接收消息/事件 → 构建 context → 暴露工具 → 执行工具调用 → 记录结果。
- 循环运行在 **sandbox 外**（host 侧），agent 代码运行在 sandbox 内。

### 2.2 Sandbox 隔离 + 快照回退 + Canonical State
- Ubuntu sandbox：可安装包/运行命令/修改源码；源码挂载于 `/workspace/exo`，agent 可 rebuild/restart 自身。
- **snapshot/rewind**：撤销实验性改动（sandbox 内可回退）。
- **canonical state**（对话/工具/adapter 事件）存 sandbox 外，**rewind 时不回退**——跨实验/重启可重建上下文。

### 2.3 不可变 Canonical Event Log
- agent 唯一**不能修改**的——递归自改进的防死循环安全网。
- 即使 agent 修改了自身代码导致行为变化，历史事件日志保持完整不可篡改。

### 2.4 Durable Scheduler（`scheduler_runtime.rs:46-60,71-80`）
- 持久化状态、**slot-anchored fires**（按槽位锚定的定时触发）。
- **one-shot @at**：一次性定时任务。
- **durable fire→wake handoff**：触发转为唤醒交接。
- 崩溃后 **`redeliver_pending_wakes`**：补投未确认的唤醒——保证"至少一次"投递。

### 2.5 分层 Trait 模型（`harness_types.rs:17-59`）
- **Harness**→**HarnessAgent**→**HarnessConversation** 三级抽象，每级可独立配置。
- 细粒度执行追踪：Turn → LLM round → Tool call 三级 trace trait。

## 三、对 nop-ai-agent 的借鉴要点

1. **不可变 canonical event log**（高价值）——nop 的 checkpoint 当前是 append-only INSERT（可写追加）；对于"递归/自动化"场景（agent 自主修改 plan/工具），需要**追加不可变审计日志**作为防篡改安全网（即使 agent 改了状态，历史不可抵赖）。checkpoint 的 append-only 特性天然适合演进为"不可变日志"。与 grok-build 的 Journal（`2026-08-01-grok-build-deterministic-replay-analysis.md`）方向一致：grok 是重放用，exo 是审计用。
2. **Durable fire→wake handoff + pending-fire redelivery**（高价值）——补强 rivet 的 Actor 唤醒（`2026-08-01-rivet-actor-runtime-analysis.md`）与 hatchet 的 WAIT_FOR（`2026-08-01-hatchet-durable-execution-analysis.md`）：调度器崩溃恢复后补投未确认唤醒，保证"至少一次"投递。`redeliver_pending_wakes` 是崩溃恢复的关键机制。
3. **canonical/mutable 状态分离**（中价值）——对应 nop 的 AgentModel（静态）/AgentSession（状态）分离，但 exo 更进一步：会话历史本身也分"可回退实验态"与"不可回退事实态"。nop 的 checkpoint（append-only）已具备"不可回退"特性，但需要更明确的"实验态 vs 事实态"建模。
4. **分层 trait（Harness→Agent→Conversation）**（中价值）——适合 team 包组织结构（team → agent → session 三级）。
5. **细粒度执行追踪（Turn→LLM round→Tool call）**（中价值）——三级 trace trait 映射到 nop 各层级的生命周期点。

## 三.5 Harness 可靠性（Retry/Replan/Resume）

- **Durable fire→wake handoff + redeliver_pending_wakes**（`scheduler_runtime.rs:46-60,71-80`）：调度器崩溃后补投未确认唤醒——**至少一次投递**（重试语义）。
- **sandbox snapshot/rewind**：实验性改动可撤销重来——**最粗粒度重试**（整个实验回退）。
- **canonical state 不回退**：rewind 时对话/工具事件保留——重试后上下文可重建。
- **不可变 event log**：agent 无法篡改历史——重试的判定依据不可变。
- **对 nop 的启示**：redeliver_pending_wakes 是 nop 长时唤醒的"至少一次"保证；canonical/mutable 分离是重试的上下文边界。

## 四、优缺点

### 优点
1. 递归自改进作为一等设计目标——独特定位。
2. 不可变 canonical event log 是递归安全网——简洁有力。
3. Durable scheduler 的 redeliver_pending_wakes 保证至少一次唤醒。
4. canonical/mutable 分离让实验安全（sandbox 可 rewind，事实不可变）。

### 缺点
1. 早期阶段，生态/工具有限。
2. Rust/TS 双语言，无 JVM。
3. 聚焦单 agent 自改进（非多 agent team）。
4. sandbox-only 执行模型（不适合服务端无 sandbox 场景）。

## 五、结论

exo 对 nop 的核心启示是"递归/自动化场景下的不可变审计"与"调度崩溃恢复补投"。不可变 canonical event log 是 checkpoint append-only 的自然演进方向。

## 六.5 Harness 机制维度覆盖（对照参考框架 D1-D12）

> 参考：`2026-08-01-harness-mechanism-reference-framework.md`（Agent Harness 十二大机制维度）

覆盖维度：**D10**（递归自改进+canonical state 转交）、**D4**（不可变 event log+durable scheduler）、**D2**（sandbox snapshot/rewind）、**D12**（redeliver_pending_wakes 至少一次）。缺失/薄弱：D6、D9。

## 对比结论：nop-ai-agent 全面超越性分析

**nop-ai-agent 已超越的部分**：
- **checkpoint 持久化**：nop `DBCheckpointManager` append-only INSERT + Journal 双写——exo 的 canonical event log nop 已有等价（append-only 天然不可变），且 nop 有会话级恢复。
- **状态分离**：nop AgentModel（静态）/AgentSession（状态）分离与 exo 的 canonical/mutable 分离同构，nop 更成熟（已落地）。
- **可靠性**：nop reliability 包（熔断/重试/checkpoint）比 exo 的 durable scheduler 更系统化。

**必要参考的增量（以超越方式吸收）**：
- **redeliver_pending_wakes**（调度器崩溃后补投未确认唤醒，至少一次）：nop 长时唤醒可增加"补投"语义——与 hatchet WAIT_FOR/rivet wake 统一为 nop 唤醒原语。

**总评**：nop-ai-agent **全面超越** exo（append-only checkpoint 已实现不可变日志思想、状态分离更成熟）；redeliver 补投一个增量值得吸收（与 WAIT_FOR 统一实现）。

## References
- `~/ai/exo/executor/harness_executor.rs:31-58`、`scheduler_runtime.rs:46-60,71-80`、`harness_types.rs:17-59`
- `ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md`、`nop-ai-agent-actor-runtime-vision.md`
- `ai-dev/analysis/agent-survey/2026-08-01-grok-build-deterministic-replay-analysis.md`、`2026-08-01-rivet-actor-runtime-analysis.md`、`2026-08-01-hatchet-durable-execution-analysis.md`
