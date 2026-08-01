# Orca 多 Agent 编排原语与联邦化分析 & Nop AI Agent 编排/分布式

> Status: open
> Date: 2026-08-01
> Scope: `~/ai/orca`（StablyAI，Electron+TS，桌面多 agent 编排器）vs `nop-ai-agent`（team 包）
> Conclusion:

## 一、总览与机制
Orca 把多个异构 CLI agent（Codex/ClaudeCode/OpenCode/Pi）当作可编排"工人"，并行多 worktree 跑。核心：**编排原语 Run + mailbox + lifecycle + 幂等**（`ORCHESTRATION_IMPLEMENTATION_CHECKLIST.md` Phase 0-4：本地 Run→同服务器 worker→跨服务器 federation→结构化 worker 输出）；**作用域不变量**（agent 自行决定分解/拓扑/放置/恢复，编排层不做调度/公平/自动重试）；**git worktree 隔离**（每 agent 独立 worktree）+ SSH 远程 worktree + 移动端 companion 配对（E2EE）。

## 二、对 nop-ai-agent 的借鉴要点
1. **Run/mailbox/lifecycle/幂等原语**（中价值）——作为 nop checkpoint 的协调模型（team 包多 agent 间）。
2. **federation 权威 Run home + worker 服务器**（高价值）——对 nop 分布式 plan 执行（team 跨实例）有参考。
3. **"静默不证明 worker 死亡"作用域不变量**（中价值）——谨慎的生命周期判定原则，避免误判重试导致重复执行（对应 hive 的 stall 检测 `2026-08-01-hive-dual-middleware-analysis.md` 的对立面：宁可等待不可误杀）。

## 三、结论
Orca 的联邦化多 agent 编排原语对 nop team 包分布式场景有参考。局限：GUI/Electron 产品、核心逻辑与渲染/终端/PTY 强耦合、编排原语依赖外部 CLI。

## References
- `~/ai/orca/ORCHESTRATION_IMPLEMENTATION_CHECKLIST.md`、`src/shared/`
- `ai-dev/design/nop-ai-agent/nop-ai-agent-team.md`、`nop-ai-agent-reliability.md`
- `ai-dev/analysis/agent-survey/2026-08-01-hive-dual-middleware-analysis.md`、`2026-08-01-conductor-decider-replay-analysis.md`
