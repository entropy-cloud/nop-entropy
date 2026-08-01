# Orca 多 Agent 编排原语与联邦化分析 & Nop AI Agent 编排/分布式

> Status: open
> Date: 2026-08-01
> Scope: `~/ai/orca`（StablyAI，Electron+TS，桌面多 agent 编排器）vs `nop-ai-agent`（team 包多 agent）
> Conclusion:

## 一、总览

**Orca** 把多个异构 CLI agent（Codex/ClaudeCode/OpenCode/Pi）当作可编排"工人"，并行多 git worktree 跑。核心是 **Run/mailbox/lifecycle/幂等**编排原语 + **federation 跨服务器**协作 + **git worktree 隔离**。

| 维度 | orca | nop-ai-agent |
|------|------|--------------|
| 编排原语 | Run + mailbox + lifecycle + 幂等 | team 包静态配置 |
| 隔离 | git worktree（每 agent 独立） | AgentSession 独立 |
| 分布式 | federation（权威 Run home + worker 服务器） | 单进程 |
| agent 类型 | 异构 CLI（Codex/Claude/OpenCode/Pi） | 同构 nop agent |
| 落地进度 | Phase 0-4 渐进（本地→同服务器→跨服务器→结构化输出） | — |

## 二、核心机制详解

### 2.1 编排原语（`ORCHESTRATION_IMPLEMENTATION_CHECKLIST.md` Phase 0-4）
- **Run**：一次运行实例（含 ID/状态/输入/输出/归属 agent）。
- **mailbox**：agent 间消息投递（每 agent 有独立邮箱，消息幂等投递）。
- **lifecycle**：Run 的生命周期状态机（created→dispatched→running→completed/failed/cancelled）。
- **幂等**：同一 Run 多次投递只执行一次（靠 Run ID 去重）。
- 渐进式落地 4 阶段：Phase 0 本地 Run → Phase 1 同服务器 worker → Phase 2 跨服务器 federation → Phase 3 结构化 worker 输出 → Phase 4 移动端 companion 配对。

### 2.2 作用域不变量（Scope invariants）
- **agent 自行决定**分解/拓扑/放置/恢复策略——编排层不做调度/公平/自动重试。
- **"静默不证明 worker 死亡"**——编排层不能因 worker 长时间无响应就判定死亡（可能在做长任务）；需显式心跳/超时确认。

### 2.3 Git Worktree 隔离
- 每 agent 一个独立 git worktree（共享仓库但独立工作目录/分支）。
- SSH 远程 worktree：支持远程服务器上的 agent。
- 移动端 companion 配对（E2EE 加密）：手机端配对桌面端 agent。

### 2.4 跨 agent 适配层（`src/shared/`）
- `agent-detection`：自动识别当前环境可用哪些 CLI agent。
- `orchestration-rpc-contract`：编排 RPC 契约（Run 创建/状态查询/结果收集）。
- `pty-session`：伪终端会话管理（与 CLI agent 的标准输入输出交互）。
- `runtime-client`：运行时客户端（连接各 agent 的运行时）。

## 三、对 nop-ai-agent 的借鉴要点

1. **Run/mailbox/lifecycle/幂等原语**（中价值，team 包）——作为 nop checkpoint 的协调模型（team 包多 agent 间消息投递）。mailbox 幂等投递对应 nop 的消息去重（Run ID → nop 的 callId/watermark）。
2. **federation 权威 Run home + worker 服务器**（高价值）——对 nop team 包分布式 plan 执行（跨实例）有参考：一个权威节点管理 Run 状态，worker 节点执行并汇报。对应 conductor 的 Decider 模式（`2026-08-01-conductor-decider-replay-analysis.md`）但更分散化。
3. **"静默不证明 worker 死亡"作用域不变量**（中价值）——谨慎的生命周期判定原则，避免误判重试导致重复执行（对应 hive 的 stall 检测 `2026-08-01-hive-dual-middleware-analysis.md` 的对立面：宁可等待不可误杀）。nop team 包的任务超时/回收（`nop-ai-agent-team-task-reclaim.md`）应参考此原则。
4. **git worktree 隔离**（低价值）——适合编码场景；nop 通用 agent 场景用 AgentSession 隔离即可。

## 三.5 Harness 可靠性（Retry/Replan/Resume）

- **幂等原语**（`ORCHESTRATION_IMPLEMENTATION_CHECKLIST.md`）：Run ID 去重——**重试安全**（同 Run 多次投递只执行一次）。
- **"静默不证明 worker 死亡"**：显式心跳/超时确认——**避免误判重试**（宁可等待不可误杀）。
- **lifecycle 状态机**：created→dispatched→running→completed/failed/cancelled——失败任务可重新派发。
- **对 nop 的启示**：幂等 Run 是 nop team 包重试的前提；"静默不证明死亡"是 nop team-task-reclaim 的反面约束。

## 四、优缺点

### 优点
1. 编排原语（Run/mailbox/lifecycle/幂等）概念清晰，可独立于具体 agent 引擎复用。
2. federation 渐进式落地（Phase 0-4）是务实的分布式演进路径。
3. "静默不证明死亡"体现了分布式系统的心跳设计智慧。

### 缺点
1. GUI/Electron 产品，核心逻辑与渲染/终端/PTY 强耦合。
2. 编排原语依赖外部 CLI agent（不自建推理循环）。
3. Java 借鉴需剥离大量 UI/终端代码。
4. Phase 0-4 部分阶段仍在实现中（未完全落地）。

## 五、结论

Orca 的联邦化多 agent 编排原语（Run/mailbox/lifecycle/幂等 + federation）对 nop team 包分布式场景有参考。"静默不证明死亡"原则值得 team-task-reclaim 借鉴。局限：Electron 产品、依赖外部 CLI、部分阶段未落地。

## 六.5 Harness 机制维度覆盖（对照参考框架 D1-D12）

> 参考：`2026-08-01-harness-mechanism-reference-framework.md`（Agent Harness 十二大机制维度）

覆盖维度：**D10**（Run/mailbox/lifecycle/幂等+federation）、**D2**（git worktree 隔离）、**D12**（幂等 Run 重试安全）。缺失/薄弱：D1（编排外部 CLI agent）、D5。

## 对比结论：nop-ai-agent 全面超越性分析

**nop-ai-agent 已超越的部分**：
- **编排**：nop team 包 + nop-task GraphTaskStep（DAG 调度已落地）比 orca 的 Run/mailbox 原语更成熟（nop-task 是 Java 原生）。
- **隔离**：nop AgentSession 独立 + checkpoint 比 orca 的 git worktree 更通用（非编码专用）。

**必要参考的增量（以超越方式吸收）**：
- **"静默不证明 worker 死亡"**：nop team-task-reclaim 可参考此原则（显式心跳确认，避免误判重试）——真正增量（反误杀）。

**总评**：nop-ai-agent **全面超越** orca（nop-task + team 包更成熟）；"静默不证明死亡"原则一个增量吸收（nop team-task-reclaim 参考）。

## References
- `~/ai/orca/ORCHESTRATION_IMPLEMENTATION_CHECKLIST.md`、`src/shared/`（agent-detection、orchestration-rpc-contract、pty-session、runtime-client）
- `ai-dev/design/nop-ai-agent/nop-ai-agent-multi-agent.md`、`nop-ai-agent-team-task-reclaim.md`、`nop-ai-agent-reliability.md`
- `ai-dev/analysis/agent-survey/2026-08-01-hive-dual-middleware-analysis.md`、`2026-08-01-conductor-decider-replay-analysis.md`
