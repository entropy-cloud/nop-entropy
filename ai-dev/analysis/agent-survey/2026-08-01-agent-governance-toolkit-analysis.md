# Agent Governance Toolkit 策略内核与失败恢复深度分析 & Nop AI Agent 治理体系

> Status: open
> Date: 2026-08-01
> Scope: `~/ai/agent-governance-toolkit`（microsoft/agent-governance-toolkit，ACS 策略引擎 + 失败恢复运行时）vs `nop-ai-agent`（security/guardrail/reliability/repair 包）
> Conclusion:

## 一、总览

**Agent Governance Toolkit（AGT）** 是微软开源的 Agent 治理工具包：把**策略决策**从 agent 内部代码中彻底剥离——策略由独立的 **ACS 策略引擎**（Rego/Cedar）评估，通过结构性不可绕过的拦截点（"structurally impossible"）强制生效；失败恢复通过 **Circuit Breaker + Saga Handoff** 组合实现。

| 维度 | AGT | Nop AI Agent |
|------|-----|--------------|
| 策略引擎 | ACS（Rego/Cedar 策略语言） | 无独立策略引擎（security 是 Java 硬编码检查链） |
| 拦截方式 | 结构性不可绕过（stub 服务 + 中间层强制） | middleware 洋葱链（可配置但代码级） |
| 策略内容 | access-control / circuit-breaker / rate-limit / endpoint-guard 四种 | security 6 层 + PermissionMatrix |
| 失败恢复 | Circuit Breaker + Saga Handoff（agent-hypervisor） | reliability 重试/回退 + repair 自修复 |
| 例子 | bank_agent（操作审批流 + 资金转移检查） | guardrail 包（可观测性/护栏） |

**核心结论先行**：AGT 的治理哲学与 nop 的安全体系**互为镜像**——nop 把策略写死在 Java 安全检查链里（灵活度差但确定性好），AGT 把策略外置为声明式规则（灵活但引入策略引擎复杂度）。对 nop 最有价值的借鉴是：**①策略决策与执行分离的接口设计**（把"检查"抽象为可注入的策略服务，为未来接 Rego/Cedar 留口）；**②Circuit Breaker 在 agent 工具调用链上的落位**；**③bank_agent 的审批流模式**（人工审批作为工具调用的中间层）。

## 二、Context（调研背景）

- **为什么需要这个分析**：7 月博客《Agent Governance Toolkit 深度解析：从策略到失败恢复》拆解了 ACS 策略引擎与失败恢复两层；nop 的 security/guardrail 已实现 6 层安全检查但无声明式策略引擎。
- **要回答的问题**：策略外置对 nop 意味着什么？Circuit Breaker 与 nop reliability 的关系？
- **约束**：nop 是 Java DSL-first，追求配置化而非引入新的策略语言生态（除非成本可接受）。

## 三、核心机制详解

### 3.1 ACS 策略引擎（policy-engine/）

- 策略语言：Rego（OPA 生态）与 Cedar（AWS 生态）两种，统一在 ACS 引擎内评估。
- 示例策略（`policy-engine/examples/bank_agent/`）：银行 agent 的**资金转移审批策略**——transfer 动作需人工审批、金额上限、双重签名等规则。
- 策略包：`access-control`（操作/资源/条件）、`circuit-breaker`（失败阈值熔断）、`rate-limit`、`endpoint-guard`。
- 拦截方式：**结构性不可绕过**（README:55 行原话 "structurally impossible to bypass"）——策略检查被编译进服务调用路径的中间层（stub），agent 无法跳过该层直接调用后端。

### 3.2 失败恢复（agent-hypervisor/src/hypervisor/）

- **Circuit Breaker**：工具调用连续失败达到阈值 → 熔断（快速失败，避免雪崩）→ 冷却期后半开探活。
- **Saga Handoff**（`saga/orchestrator.py`）：跨多个步骤的分布式事务补偿——第一步成功、第二步失败 → 按逆序执行补偿动作。
- 两者组合：熔断保护资源，Saga 保证多步一致性。

### 3.3 bank_agent 案例

- 完整示例：agent 想转移资金 → 触发 ACS 策略评估 → 策略要求人工审批 → agent 生成审批请求 → 审批通过后继续 → 失败走 saga 补偿。

## 三.5 Harness 可靠性（Retry/Replan/Resume）

- **Circuit Breaker + Saga Handoff**（`agent-hypervisor/src/hypervisor/`）：熔断（连续失败阈值）→ 冷却 → 半开探活；Saga 逆序补偿多步失败——**重试前先熔断降级**。
- **ACS 策略引擎的重试判定**：Rego/Cedar 策略决定 allow/deny/requireApproval——被拒任务按策略重试或升级。
- **bank_agent 审批流重试**：transfer 需人工审批 → 被拒后 agent 调整方案重试。
- **对 nop 的启示**：熔断+补偿组合是 nop reliability 的参考（nop 已有 ThresholdBreaker，补 Saga 补偿）；审批流被拒→调整重试是 nop 审批的 replan 路径。

## 四、优缺点

### 优点

1. 策略外置后：可热更新（不动 agent 代码）、可审计（策略即文档）、可跨 agent 复用。
2. 结构性不可绕过 > 提示词约束（"你不应…"）——这是治理与建议的本质区别。
3. Circuit Breaker + Saga 是生产级失败处理的组合拳。

### 缺点

1. 引入策略引擎（Rego/Cedar）技术栈，学习与运维成本高。
2. 策略评估本身成为新的故障点（引擎挂 = 服务全挂，需高可用）。
3. Java 生态接 Rego 需 CGO/子进程，复杂度高。

## 五、对 nop-ai-agent 的借鉴要点（核心价值）

nop 现状：`security` 包 6 层检查（AuthenticationInterceptor/PermissionMatrix/…）+ `guardrail`（可观测性）+ `reliability`（重试/回退）+ `repair`（自修复）。

### 5.1 策略服务接口抽象（高优先）

```
nop 现有：CheckAgentTool（Java 检查链，硬编码规则）
建议：AgentPolicyService 接口（评估返回 Decision{allow/deny/requireApproval}）
  - 默认实现 = 现有 security 链（无行为变化）
  - 可选实现：外部策略引擎适配（Rego/Cedar，后续扩展点）
```

- 收益：治理规则从代码走向配置（`policy.xdef`），与 nop DSL-first 契合。
- 不强行引入 Rego 生态；先留接口，默认用 nop 配置表达式。

### 5.2 熔断器在工具链上的落位（nop 已有，增强错误率维度）

- nop `reliability` 包**已实现熔断器**（`ICircuitBreaker`/`ThresholdBreaker`/`CircuitState` CLOSED/OPEN/HALF_OPEN，连续失败计数阈值，默认 3 次）。借鉴 AGT 的增量：
  - **错误率阈值**（vs nop 的计数阈值，更平滑）；
  - **工具 + Agent 双维度**（nop 当前熔断粒度可扩展到 per-tool/per-agent）；
  - 与 hatchet 借鉴的退避重试组合：先熔断降级，冷却后重试。
- 接入点：AgentToolDispatcher 的工具调用拦截处（ToolExecutor 之前）。

### 5.3 审批流模式（中优先，场景价值大）

- bank_agent 的"人工审批作为工具调用中间层"模式直接对应 nop 企业场景：
  - `AgentToolInterceptor` 检查工具元数据 `approvalRequired` → 生成审批任务（挂起执行）→ 审批通过后放行（复用 checkpoint WAIT_FOR 语义挂起）。
- nop 已具备工具元数据模型，补审批状态机即可。

### 5.4 Saga 补偿（低优先）

- nop 单 agent 场景跨步骤事务少见；team 包多 agent 协作时再考虑补偿编排。

## 六、结论

- AGT 的最大价值是把"治理策略"从建议升级为**结构性强制**——nop 的 Java 检查链已具备强制力，nop reliability 包也**已实现熔断器**（`ThresholdBreaker`/`CircuitState`）；真正缺的是**策略外置接口**与**审批流**（熔断的增量仅在错误率阈值/工具+Agent 双维度）。
- 落地建议按序：AgentPolicyService 接口抽象 → 审批流（人工审批中间层）→ 熔断增强（错误率阈值/双维度）→ 后续按需接外部策略引擎。
- 后续工作：指向 `ai-dev/design/nop-ai-agent/nop-ai-agent-security-and-permissions.md` 与 `guardrail-contract.md` 的扩展。

## Open Questions

- [ ] 策略外置走 XDEF 配置（推荐）还是接外部策略引擎（Rego/Cedar）？
- [ ] 审批任务的超时与取消语义（审批挂起期间 agent 可做什么）？
- [ ] 熔断的粒度（per-tool / per-agent / per-session）？

## 六.5 Harness 机制维度覆盖（对照参考框架 D1-D12）

> 参考：`2026-08-01-harness-mechanism-reference-framework.md`（Agent Harness 十二大机制维度）

覆盖维度：**D6**（ACS 策略引擎+结构性不可绕过+审批流）、**D7**（失败恢复 Circuit Breaker+Saga）、**D9**（bank_agent 审批质量门）、**D12**（熔断+补偿）。缺失/薄弱：D2、D5（治理层，非引擎）。

## 对比结论：nop-ai-agent 全面超越性分析

**nop-ai-agent 已超越的部分**：
- **熔断器**：nop `reliability` 包已有 `ICircuitBreaker`/`ThresholdBreaker`/`CircuitState`（CLOSED/OPEN/HALF_OPEN）——AGT 的熔断 nop 已具备。
- **安全检查**：nop security 6 层（ContentOrigin/PermissionMatrix/ToolAccessChecker/AutoApproveGate/...）比 AGT 的单一 ACS 策略引擎更系统化、更贴合 Java DSL。
- **审计**：nop `AuditEvent`/`AuditDecision` + checkpoint append-only 比 AGT 的 Saga 补偿更完整。

**必要参考的增量（以超越方式吸收）**：
- **策略外置接口**（`AgentPolicyService` 评估返回 allow/deny/requireApproval）：nop 检查链是代码级——策略判定与执行分离是真正增量（默认实现 = 现有链，可选接外部策略引擎）。
- **审批流中间层**（工具调用挂起等审批）：nop `ApprovalGate` 已有雏形，可完善为"审批任务 + 挂起恢复"完整流程。
- **Saga 补偿**：team 包多 agent 跨步骤事务可吸收（低优先）。

**总评**：nop-ai-agent 在熔断/安全/审计上**全面超越**；策略外置接口 + 审批流两个增量值得吸收（以 nop DSL 声明式实现，超越 AGT 的 Rego/Cedar 引入成本）。

## References

- `~/ai/agent-governance-toolkit/policy-engine/examples/bank_agent/`、`agent-hypervisor/src/hypervisor/{circuit_breaker,saga}/`、README.md
- `nop-ai-agent/src/main/java/io/nop/ai/agent/security/`、`guardrail/`、`reliability/`
- `ai-dev/design/nop-ai-agent/nop-ai-agent-security-and-permissions.md`、`guardrail-contract.md`
- `ai-dev/analysis/agent-survey/2026-08-01-hatchet-durable-execution-analysis.md`
