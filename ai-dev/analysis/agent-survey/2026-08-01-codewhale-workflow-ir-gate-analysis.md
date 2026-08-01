# CodeWhale Workflow IR Gate 与有界 Review-Repair 深度分析 & Nop AI Agent 计划门控

> Status: open
> Date: 2026-08-01
> Scope: `~/ai/codewhale`（社区驱动 Rust 终端 coding agent，v0.9.3，~1384 文件）vs `nop-ai-agent`（plan 包静态模型 + repair 包）
> Conclusion:

## 一、总览

**CodeWhale** 的核心是 **Workflow IR（类型化工作流计划）+ Gate 节点（角色交接门控）+ Lane（运行实例与 runtime 后端）+ 有界 review→repair 循环**，把"结构化、有界、可重放"作为执行框架的一等目标。

| 维度 | codewhale | nop-ai-agent |
|------|-----------|--------------|
| 计划模型 | WorkflowConfig/WorkflowPlan（goal+phases+max_concurrent） | AgentPlan 线性静态模型（AgentExecStatus 9 值） |
| 门控 | Gate：block/approve/escalate + on_fail(Retry/Block/Escalate) | CompletionJudge（出口判定） |
| 运行实例 | Lane（runtime 后端 tmux/inline/vm/ci） | AgentSession |
| 修复 | 有界 review→repair（显式 ceiling + stale-input digest） | repair 包（ChainRepairer/IToolCallRepairer） |
| Hook | HookEvent ~7 类（流式/工具/审批生命周期） | 12 生命周期点（AgentLifecyclePoint） |

## 二、核心机制详解

### 2.1 Workflow IR（`crates/workflow/src/lib.rs:102`）
- `WorkflowConfig`：goal + phases + max_concurrent。
- `WorkflowPlan`：编译后的执行计划（phases 展开 + 依赖解析）。
- JS/TS 可编译进 IR（`js_authoring.rs` 的 `compile_javascript_workflow`）——允许用脚本语言编写工作流，编译为类型化 IR。

### 2.2 Gate 节点（`crates/workflow/src/gates.rs:1-80`）
- 角色到角色的交接，语义 **block**（阻塞等待）/ **approve**（审批通过）/ **escalate**（升级到上级）。
- 失败策略 **Retry / Block / Escalate**，带 `max_retries`（最大重试次数）与 `require_explicit_verdict`（必须显式判定，不可自动通过）。
- Gate 触发时机：`on(RoleStart)` / `on(RoleComplete)`——在角色开始/完成时触发门控检查。

### 2.3 Lane（`crates/lane/src/lib.rs`、`lane/runtime.rs`）
- Lane = 运行中的工作流实例（含状态/中间结果/当前阶段）。
- Runtime 后端决定"在哪跑"：**tmux**（终端会话）/ **inline**（进程内）/ **vm**（虚拟机隔离）/ **ci**（CI 环境）。
- Fleet 只供角色（角色池，不含执行逻辑）——角色与执行环境分离。

### 2.4 有界 review→repair（`crates/workflow/src/review_repair.rs:1-32`）
- 显式 **ceiling**（上限）：迭代次数 / 墙钟时间 / 工具调用次数——达到任一上限即停止修复循环。
- **stale-input 按 digest 失败即停**：每次修复时计算输入 digest（内容哈希），若 digest 变化（输入已过期）则立即停止当前修复——防止基于过期输入继续修复。
- 这是"有界"的核心：不是无限修复，而是在明确边界内修复。

### 2.5 HookEvent（`crates/hooks/src/lib.rs:21-78`）
- ~7 类事件：ResponseStart/ResponseDelta/ResponseEnd（流式响应）、ToolLifecycle（工具调用生命周期）、JobLifecycle（Job 生命周期）、ApprovalLifecycle（审批生命周期）。
- 偏流式/观察，未达 nop 的 12 点细粒度。

## 三、对 nop-ai-agent 的借鉴要点

1. **Gate 门控语义**（最高价值）——给 nop plan 静态模型的阶段边界增加 `Gate{on RoleStart/RoleComplete, on_fail(Retry/Block/Escalate), max_retries, require_explicit_verdict}`。直接映射 spec-kit 的 gate（`2026-08-01-spec-kit-workflow-engine-analysis.md`）和 jcode 的 is_gate（`2026-08-01-jcode-dag-first-agent-analysis.md`），三者构成 plan 门控的完整语义。`require_explicit_verdict` 防止"自动通过"（nop 的 AgentExecStatus 已有 escalated 状态，可对接 Gate 的 escalate 语义）。
2. **有界 review→repair 的 ceiling + stale-input digest**（高价值）——nop 的 repair 包（ChainRepairer/IToolCallRepairer）需要：① 显式上限（防无限修复，对应 ceiling 三维度）；② 输入 digest 校验（输入变更后停止旧的修复循环，避免基于过期上下文）。stale-input digest 与 grok-build 的 req_hash（`2026-08-01-grok-build-deterministic-replay-analysis.md`）异曲同工。
3. **Lane runtime 后端抽象**（中价值）——把"执行宿主"（inline/vm/ci）抽象为可插拔后端，对应 nop middleware 洋葱链之外的执行宿主分层。
4. **JS/TS 编译进 IR**（低价值）——nop DSL-first 用 XDEF，不需要脚本编译；但"脚本→类型化 IR"的思路可参考。

## 三.5 Harness 可靠性（Retry/Replan/Resume）

- **Gate 失败策略**（`crates/workflow/src/gates.rs:1-80`）：`on_fail` 三态 Retry / Block / Escalate + `max_retries` + `require_explicit_verdict`——重试上限显式、不可自动通过。
- **有界 review→repair**（`review_repair.rs:1-32`）：显式 ceiling（迭代/墙钟/工具调用三维度上限）+ **stale-input digest 校验**（输入变更即停止旧修复）——修复循环有界且防过期输入。
- **恢复语义**：Lane 运行实例从 checkpoint 恢复（runtime 后端 tmux/inline/vm/ci）。
- **对 nop 的启示**：`require_explicit_verdict` 防自动通过；stale-input digest 是 nop repair 包的"输入过期即停"参考（与 grok-build req_hash 互补）。

## 四、优缺点

### 优点
1. Workflow IR 类型化 + Gate 门控：阶段交接有结构化检查，不是自由 prompt。
2. 有界 review→repair 的 ceiling + stale-input digest：防止无限修复和过期输入修复。
3. Lane runtime 后端抽象：执行环境可插拔。
4. `require_explicit_verdict`：防止自动通过（企业合规场景关键）。

### 缺点
1. HookEvent 仅 ~7 类且偏流式/观察，无 middleware 洋葱链概念。
2. Workflow IR 与运行时部分未接线（lib.rs 注释自述停在 IR 边界）。
3. 强绑 Rust 终端场景，泛化到通用 agent 需抽象。

## 五、结论

codewhale 补足了 grok-build 较弱的部分：结构化 Workflow IR 的 Gate 门控 + 有界修复。Gate 的 `on(RoleStart/RoleComplete)` + `on_fail(Retry/Block/Escalate)` + `max_retries` + `require_explicit_verdict` 与 spec-kit/jcode 三者合一构成 nop plan 门控、阶段验收、修复循环的完整范式。有界 review→repair 的 ceiling + stale-input digest 直接增强 nop repair 包。

## Open Questions
- [ ] Gate 的审批（approve）由谁执行（人工/LLM 判定/规则）？
- [ ] stale-input digest 在 nop 中如何计算（工具参数 hash vs 完整上下文 hash）？
- [ ] Lane 的多 runtime 后端（inline/vm/ci）在 nop Java 单进程场景是否需要？

## 六.5 Harness 机制维度覆盖（对照参考框架 D1-D12）

> 参考：`2026-08-01-harness-mechanism-reference-framework.md`（Agent Harness 十二大机制维度）

覆盖维度：**D5**（Workflow IR+Gate 节点 on_fail Retry/Block/Escalate）、**D9**（require_explicit_verdict 质量门）、**D12**（有界 review→repair+ceiling+stale-input digest）。缺失/薄弱：D2、D6。

## 对比结论：nop-ai-agent 全面超越性分析

**nop-ai-agent 已超越的部分**：
- **Hook 体系**：nop 12 个 AgentLifecyclePoint + middleware 洋葱链，codewhale 仅 ~7 类流式观察事件——nop 更细粒度。
- **计划模型**：nop AgentPlan 21 个静态模型类 + AgentExecStatus 9 态（含 escalated/forced_stopped），比 codewhale 的 WorkflowConfig 更丰富。
- **修复**：nop repair 包（ChainRepairer/IToolCallRepairer）+ reliability 比 codewhale 的 review_repair 更系统化。

**必要参考的增量（以超越方式吸收）**：
- **Gate 门控语义**（on RoleStart/Complete + on_fail Retry/Block/Escalate + require_explicit_verdict）：nop plan 静态模型的阶段边界可增加 Gate 定义——以 XDEF 声明式实现（与 spec-kit gate/jcode is_gate 三合一）。
- **stale-input digest**：nop repair 包增加"输入变更即停"校验（与 grok req_hash 同源）。

**总评**：nop-ai-agent 在 hook/计划模型/修复上**全面超越**；Gate 门控 + stale-input digest 两个增量值得吸收（与 spec-kit/jcode/grok 的同类借鉴统一为 nop plan 门控体系）。

## References
- `~/ai/codewhale/crates/workflow/src/{lib.rs:102,gates.rs:1-80,review_repair.rs:1-32}`、`crates/lane/src/lib.rs`、`crates/hooks/src/lib.rs:21-78`
- `ai-dev/design/nop-ai-agent/nop-ai-agent-plan-dsl.md`、compact 包源码（PipelineCompactor 等）
- `ai-dev/analysis/agent-survey/2026-08-01-spec-kit-workflow-engine-analysis.md`、`2026-08-01-jcode-dag-first-agent-analysis.md`、`2026-08-01-grok-build-deterministic-replay-analysis.md`
