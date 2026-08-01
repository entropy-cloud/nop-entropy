# Go-Micro 工具中间件栈与 Checkpoint 状态机分析 & Nop AI Agent 安全/恢复

> Status: open
> Date: 2026-08-01
> Scope: `~/ai/go-micro`（Go，agent harness runtime + MCP/A2A gateway，643 .go 文件）vs `nop-ai-agent`（security 6 层 + reliability + checkpoint append-only）
> Conclusion:

## 一、总览

**Go-Micro** 把 harness 定义为"agent 周围的运行时——工具/记忆/guardrail/工作流/服务发现/协议"。核心：**12 层工具中间件栈**、**Checkpoint/Resume 状态机**、**内置工具三件套**、**A2A 网关**、**组合执行边界**。

| 维度 | go-micro | nop-ai-agent |
|------|----------|--------------|
| 工具中间件 | 12 层洋葱栈（context→...→base） | middleware 洋葱链 |
| Checkpoint | flow.Run 三态（done/paused/input-required） | append-only INSERT（按 watermark） |
| 内置工具 | plan/delegate/request_input | ToolRegistry |
| 网关 | A2A（AgentCard + JSON-RPC task 生命周期） | MCPRegistry |
| 执行边界 | MaxSteps/LoopLimit/MaxSpend/Timeout | — |

## 二、核心机制详解

### 2.1 工具 handler 中间件栈（`agent/builtin.go:124-148`）
- guardrail 以洋葱模型层层包裹，从外到内执行顺序：
  `context → trace → plan → step → loop → spend → approve → checkpoint → retry → x402Pay → timeout → base`
- 每一层可拦截/拒绝/修改工具调用。
- 开发者 wrapper 挂在最外层，能观测到所有 guardrail 拒绝结果。

### 2.2 Checkpoint/Resume 状态机（`agent/checkpoint.go:70-159`）
- 每次 Ask 保存为 `flow.Run`。
- 三态：
  - **done**：完成后返回缓存响应（**不重调模型**——幂等）。
  - **paused**：断点续跑（paused→running 恢复，保留已完成 tool history）。
  - **input-required**：等待人工输入（human-in-the-loop 暂停）。

### 2.3 内置工具三件套
- **plan**：记忆中的计划跟踪（agent 维护并更新计划）。
- **delegate**：子 agent 委托（隔离上下文——子 agent 看不到父 agent 全部历史）。
- **request_input**：human-in-the-loop 暂停等待输入。

### 2.4 A2A 网关（`gateway/a2a/a2a.go:85-202`）
- **AgentCard 发现**：agent 发布能力卡片（name/description/skills）。
- **JSON-RPC task 生命周期**：working → completed / failed / input-required。
- 支持流式和 push-notification 安全回调。

### 2.5 组合执行边界（`agent/options.go:226-275`）
- `MaxSteps`：最大步数。
- `LoopLimit`：重复调用检测（防死循环）。
- `MaxSpend`：x402 协议预算控制。
- `ToolCallTimeout`：单次工具调用超时。
- `ApproveTool` hook（:233-237）：工具执行前的 human-in-the-loop 审批点。

## 三、对 nop-ai-agent 的借鉴要点

1. **工具 handler 中间件栈**（高价值）——多层 guardrail 洋葱组合范式，直接映射 nop security（每层独立、可拒绝、洋葱包裹）；与 plano 声明式 filter chain（`2026-08-01-plano-declarative-filter-chain-analysis.md`）互补：go-micro 是代码级、plano 是声明式。nop 可借鉴其 12 层的**分工**（context/trace/plan/step/loop/spend/approve/checkpoint/retry/timeout 各司其职）。
2. **Checkpoint 状态机**（高价值）——done 返回缓存（不重调模型）、paused→running 恢复、input-required 分支，是 checkpoint 实现的参考范本（对应 hatchet WAIT_FOR `2026-08-01-hatchet-durable-execution-analysis.md`、rivet sleep/wake `2026-08-01-rivet-actor-runtime-analysis.md`）。nop checkpoint append-only 已具备保存能力，但缺少 input-required（等待人工输入）分支。
3. **组合执行边界**（中价值）——MaxSteps/LoopLimit（重复检测）/MaxSpend/Timeout 组合式安全护栏。nop 可在 AgentModel 中声明这些边界。
4. **ApproveTool hook**（中价值）——工具执行前的 human-in-loop 审批点（对应 AGT 审批流 `2026-08-01-agent-governance-toolkit-analysis.md`）。

## 三.5 Harness 可靠性（Retry/Replan/Resume）

- **工具重试包装**（`agent/builtin.go:135,238-258`）：`toolRetryWrap` 对 **transient 失败**做有界退避重试——`ToolRetryBackoff`（指数退避）、`maxAttempts` 上限、`ctx.Err()` 取消感知；非 transient 错误（x402 等）不重试直接失败。
- **Checkpoint/Resume 三态**（`checkpoint.go:70-159`）：`done`（完成缓存，不重调模型）/ `paused`（断点续跑）/ `input-required`（等待人工输入恢复）。
- **组合执行边界**（`options.go:226-275`）：`MaxSteps`（步数上限）、`LoopLimit`（重复调用检测）、`ToolCallTimeout`（超时）——防无限循环与卡死。
- **对 nop 的启示**：`toolRetryWrap` 的"仅 transient 重试 + 指数退避 + 取消感知"是 nop reliability 重试的参考范本（nop 已有 `ThresholdBreaker` 熔断，二者组合：先重试后熔断）。

## 四、优缺点

### 优点
1. 12 层中间件栈分工明确，覆盖 context/trace/plan/budget/approve/checkpoint/retry/timeout。
2. Checkpoint 三态（done/paused/input-required）简洁实用。
3. Agent + Service + Flow 共享同一 runtime——agent 本身是分布式服务。

### 缺点
1. 纯 Go，与 go-micro service registry 耦合。
2. A2A/MCP 与框架强绑定，不易移植 Java。

## 五、结论

Go-Micro 的 12 层工具中间件栈 + checkpoint 三态是 nop security/reliability 的直接参考。

## 六.5 Harness 机制维度覆盖（对照参考框架 D1-D12）

> 参考：`2026-08-01-harness-mechanism-reference-framework.md`（Agent Harness 十二大机制维度）

覆盖维度：**D1**（12 层工具中间件栈）、**D4**（checkpoint 三态 done/paused/input-required）、**D6**（ApproveTool hook+x402 预算）、**D12**（toolRetryWrap 退避重试）。缺失/薄弱：D5、D9。

## 对比结论：nop-ai-agent 全面超越性分析

**nop-ai-agent 已超越的部分**：
- **middleware**：nop middleware 洋葱链 + 12 hook 点（可拦截/仅通知）——go-micro 的 12 层栈是代码级顺序，nop 是类型化 + 可配置。
- **checkpoint**：nop `DBCheckpointManager` append-only + restore 已落地——go-micro 的 flow.Run 三态 nop 有对应（paused/input-required 可对接 WAIT_FOR）。

**必要参考的增量（以超越方式吸收）**：
- **input-required 状态**（等待人工输入恢复）：nop checkpoint 可增加"等待输入"分支——真正增量（与 hatchet WAIT_FOR/rivet wake 统一）。
- **组合执行边界**（MaxSteps/LoopLimit/MaxSpend/Timeout）：nop AgentModel 可声明化这些边界——增强。

**总评**：nop-ai-agent **全面超越** go-micro（middleware 更类型化、checkpoint 更健壮）；input-required + 执行边界声明两个增量吸收。

## References
- `~/ai/go-micro/agent/`（builtin.go:124-148、checkpoint.go:70-159、options.go:226-275）、`gateway/a2a/a2a.go:85-202`
- `ai-dev/design/nop-ai-agent/nop-ai-agent-security-and-permissions.md`、`nop-ai-agent-reliability.md`
- `ai-dev/analysis/agent-survey/2026-08-01-plano-declarative-filter-chain-analysis.md`、`2026-08-01-hatchet-durable-execution-analysis.md`、`2026-08-01-rivet-actor-runtime-analysis.md`、`2026-08-01-agent-governance-toolkit-analysis.md`
