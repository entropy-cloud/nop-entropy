# CodeWhale Workflow IR Gate 与有界 Review-Repair 深度分析 & Nop AI Agent 计划门控

> Status: open
> Date: 2026-08-01
> Scope: `~/ai/codewhale`（社区驱动 Rust 终端 coding agent，v0.9.3）vs `nop-ai-agent`（plan 包静态模型 + repair 包）
> Conclusion:

## 一、总览

**CodeWhale** 的核心是 **Workflow IR（类型化工作流计划）+ Gate 节点（角色交接门控）+ Lane（运行实例与 runtime 后端）+ 有界 review→repair 循环**，把"结构化、有界、可重放"作为执行框架的一等目标。

| 维度 | codewhale | nop-ai-agent |
|------|-----------|--------------|
| 计划模型 | WorkflowConfig/WorkflowPlan（goal+phases+max_concurrent） | AgentPlan 线性静态模型 |
| 门控 | Gate：block/approve/escalate + on_fail(Retry/Block/Escalate) | CompletionJudge（出口判定） |
| 运行实例 | Lane（runtime 后端 tmux/inline/vm/ci） | AgentSession |
| 修复 | 有界 review→repair（显式 ceiling + stale-input digest） | repair 包自修复 |

## 二、核心机制

### 2.1 Workflow IR（`crates/workflow/src/lib.rs:102`）
- `WorkflowConfig`：goal + phases + max_concurrent；JS/TS 可编译进 IR（`js_authoring.rs`）。

### 2.2 Gate 节点（`crates/workflow/src/gates.rs:1-80`）
- 角色到角色的交接，语义 **block/approve/escalate**。
- 失败策略 **Retry/Block/Escalate**，带 `max_retries` 与 `require_explicit_verdict`。

### 2.3 Lane（`crates/lane/src/lib.rs`、`lane/runtime.rs`）
- 运行中的工作流实例；Runtime 后端（tmux/inline/vm/ci）决定"在哪跑"；Fleet 只供角色。

### 2.4 有界 review→repair（`crates/workflow/src/review_repair.rs:1-32`）
- 显式 ceiling（迭代次数/墙钟/工具调用）；**stale-input 按 digest 失败即停**（防止基于过期输入继续修复）。

## 三、对 nop-ai-agent 的借鉴要点

1. **Gate 门控语义**（最高价值）——给 nop plan 静态模型的阶段边界增加 `Gate{on RoleStart/RoleComplete, on_fail(Retry/Block/Escalate), max_retries}`。直接映射 spec-kit 的 gate（`2026-08-01-spec-kit-workflow-engine-analysis.md`）和 jcode 的 is_gate（`2026-08-01-jcode-dag-first-agent-analysis.md`），三者构成 plan 门控的完整语义。`require_explicit_verdict` 防止"自动通过"。
2. **有界 review→repair 的 ceiling + stale-input digest**（高价值）——nop 的 repair 包需要：① 显式上限（防无限修复）；② 输入 digest 校验（输入变更后停止旧的修复循环，避免基于过期上下文）。stale-input digest 与 grok-build 的 req_hash（`2026-08-01-grok-build-deterministic-replay-analysis.md`）异曲同工。
3. **Lane runtime 后端抽象**（中价值）——把"执行宿主"（inline/vm/ci）抽象为可插拔后端，对应 nop middleware 洋葱链之外的执行宿主分层。

## 四、结论

codewhale 补足了 grok-build 较弱的部分：结构化 Workflow IR 的 Gate 门控 + 有界修复。与 spec-kit/jcode 三者合一构成 nop plan 门控、阶段验收、修复循环的完整范式。局限：HookEvent 仅 ~7 类偏流式观察，无 middleware 洋葱链概念；workflow IR 与运行时部分未接线。

## References
- `~/ai/codewhale/crates/workflow/src/{lib.rs,gates.rs,review_repair.rs}`、`crates/lane/src/lib.rs`
- `ai-dev/design/nop-ai-agent/nop-ai-agent-plan-dsl.md`、`nop-ai-agent-repair.md`
- `ai-dev/analysis/agent-survey/2026-08-01-spec-kit-workflow-engine-analysis.md`、`2026-08-01-jcode-dag-first-agent-analysis.md`、`2026-08-01-grok-build-deterministic-replay-analysis.md`
