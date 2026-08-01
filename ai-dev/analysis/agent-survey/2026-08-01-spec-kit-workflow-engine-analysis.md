# Spec-Kit SDD 工作流引擎深度分析 & Nop AI Agent 计划运行时

> Status: open
> Date: 2026-08-01
> Scope: `~/ai/spec-kit`（obra/spec-kit，Spec-Driven Development 工作流引擎，TypeScript）vs `nop-ai-agent`（plan 包：静态 DSL 模型 + 无运行时执行器）
> Conclusion:

## 一、总览

**Spec-Kit** 是 Spec-Driven Development（SDD）的工程化实现：把软件开发流程固化为四阶段工作流（**specify → plan → execute → review**），每阶段有明确产物（spec、plan、code、review），阶段间有 **gate 步骤**（人工或自动校验门），整个工作流状态由 `RunState` 持久化到磁盘。核心：**过程即状态机，产物即文件，gate 即检查点**。

| 维度 | Spec-Kit | Nop AI Agent |
|------|----------|--------------|
| 工作流 | specify→plan→execute→review（4 阶段） | 无（ReAct 循环无阶段） |
| 状态 | RunState JSON（持久化） | AgentSession + checkpoint |
| Gate | 阶段间人工/自动校验门 | CompletionJudge（循环出口） |
| 宪法 | rules（宪法级约束） | guardrail/AgentModel 约束 |
| 产物 | spec/plan/code/review 文件 | 工具结果（无产物概念） |

**核心结论先行**：spec-kit 是"把开发流程结构化"的完整参考——它的 **RunState 持久化 + gate 步骤 + 四阶段状态机** 正好构成 nop plan 包运行时执行器的骨架（nop 现状：AgentPlan 只有静态模型，agent 不真正分阶段执行）。与 planning-with-files（文件三件套）对比：spec-kit 是**工程化的计划执行器**（有状态机、有持久化、有 gate），planning-with-files 是**轻量行为准则**（markdown+grep）。nop 的 plan 运行时应当以 spec-kit 的 RunState 状态机为骨架，结合 planning-with-files 的注入机制。

## 二、Context（调研背景）

- **为什么需要这个分析**：7 月博客《Spec-Kit 深度解析：Spec-Driven Development 工作流引擎》介绍其工作流；nop plan 包（AgentPlan 静态模型）与 spec-kit 的四阶段执行器是同一问题的两端。
- **要回答的问题**：四阶段状态机 + RunState 持久化如何映射到 nop plan 包运行时？
- **约束**：nop 是 Java DSL-first；spec-kit 是 TS 工具（git+files 驱动）。

## 三、核心机制详解

### 3.1 四阶段工作流状态机

```
specify（规格）→ plan（计划）→ execute（执行）→ review（评审）
    ↑                                              │
    └────────────── 重规划（gate 未过回到 earlier）  ←┘
```

- **specify**：需求 → 规格文档（spec 文件，人机可读）。
- **plan**：规格 → 实施计划（plan 文件：步骤 + 依赖）。
- **execute**：按计划实现（每步可 gate）。
- **review**：产物评审（自动/人工），未过则回到对应阶段。
- **gate 步骤**：阶段边界的安全检查（人工批准或自动校验如编译/测试）。

### 3.2 RunState 持久化

- 整个工作流的执行状态（当前阶段、已完成步骤、产物引用）写入 RunState JSON 文件。
- 崩溃/中断后读取 RunState 恢复——**状态在磁盘，不在上下文**（与 planning-with-files 同哲学，但结构化）。

### 3.3 rules（宪法级约束）

- 项目级 rules 文件定义"铁律"（如"每步必须可回滚""变更必须带测试"），阶段 gate 强制执行。
- 类比：nop 的 guardrail 与 AgentModel 约束是配置级，spec-kit 的 rules 是流程级（gate 强制）。

## 三.5 Harness 可靠性（Retry/Replan/Resume）

- **RunState 持久化恢复**：四阶段工作流状态写入 RunState JSON——**崩溃后读取恢复**（磁盘即断点）。
- **Gate 未过 → 回到 earlier 阶段**（重规划）：specify→plan→execute→review 循环，review 未过回到 plan——**阶段级 replan**。
- **gate 步骤双重校验**：人工批准/自动校验（编译/测试）——失败则重试该阶段。
- **rules 宪法级约束**：铁律进流程（gate 强制）——重试时规则不可绕过。
- **对 nop 的启示**：RunState 恢复 + 阶段回退是 nop plan 运行时 replan 的参考；与 jcode DAG + planning-with-files 注入三合一。

## 四、优缺点

### 优点

1. 过程工程化：阶段/产物/gate 都有明确状态，可审计、可恢复。
2. RunState 结构化持久化：恢复精确（不同于 markdown+grep 的脆弱匹配）。
3. gate 强制校验：人工/自动检查点防"跳阶段"。
4. rules 宪法化：铁律进流程而非提示词。

### 缺点

1. 流程刚性：四阶段对简单任务（改一行配置）是过度流程。
2. 面向编码场景：spec/plan/review 产物语义偏软件开发，通用 agent 场景需裁剪。
3. TS 工具链，Java 生态无直接复用（参考设计）。

## 五、对 nop-ai-agent 的借鉴要点（核心价值）

### 5.1 PlanRun 状态机（最高优先）

nop 现状：AgentPlan 静态模型（phases/tasks 无状态）。借鉴 spec-kit：

```
PlanRun 状态机（nop 运行时计划器骨架）：
  STAGE 枚举：SPECIFY → PLAN → EXECUTE → REVIEW（回退：REVIEW→PLAN）
  每阶段：state{pending,in_progress,complete,failed} + 产物引用
  Gate 步骤：阶段边界校验（自动：测试/编译；人工：审批）
  RunState：持久化到 AgentSession（复用 checkpoint 体系）
```

- 与 jcode 的 DAG 任务图（`2026-08-01-jcode-dag-first-agent-analysis.md`）协同：四阶段是**流程骨架**（宏观阶段），DAG 是**任务结构**（阶段内依赖）——两者正交，可组合。

### 5.2 产物概念（中优先）

- nop 工具结果无"产物"身份；spec-kit 的产物（文件）让阶段可校验（review 针对产物而非对话）。
- 落地：AgentSession 增加 `artifacts`（工具产出的文件/文档引用），gate 校验针对产物。

### 5.3 rules → 流程约束（中优先）

- nop 的 guardrail 是运行期约束；spec-kit 的 rules 是**阶段门约束**（何时执行受 rules 控制）。
- 落地：Gate 步骤读取 AgentModel 的 guardrail 配置作为阶段校验规则。

## 六、结论

- spec-kit 提供 nop plan 运行时缺失的**状态机骨架**：四阶段 + gate + RunState 持久化。
- nop 落地：PlanRun 状态机（含回退边）+ 阶段 gate + 产物引用；与 jcode DAG（任务结构）和 planning-with-files（注入机制）三合一构成完整计划运行时。
- 后续工作：指向 `ai-dev/design/nop-ai-agent/nop-ai-agent-plan-dsl.md` 的运行时扩展（与 `2026-08-01-planning-with-files-persistent-plan-analysis.md` 合并评估）。

## Open Questions

- [ ] 四阶段对所有任务都强制还是按任务复杂度跳过（轻量任务直接 execute）？
- [ ] gate 的人工审批与 AGT 审批流（`2026-08-01-agent-governance-toolkit-analysis.md`）是同一机制还是两层？
- [ ] PlanRun 持久化复用 checkpoint（消息级）还是独立状态表？

## 六.5 Harness 机制维度覆盖（对照参考框架 D1-D12）

> 参考：`2026-08-01-harness-mechanism-reference-framework.md`（Agent Harness 十二大机制维度）

覆盖维度：**D5**（四阶段+gate+RunState）、**D9**（gate 双重校验+rules 宪法）、**D4**（RunState JSON 持久化）、**D12**（阶段级回退 replan）。缺失/薄弱：D2、D6。

## 对比结论：nop-ai-agent 全面超越性分析

**nop-ai-agent 已超越的部分**：
- **流程引擎**：nop-task（ChooseTaskStep/GraphTaskStep/Retry/Timeout/saveState）比 spec-kit 的 RunState 状态机更成熟、更通用（非专用编码流程）。
- **持久化**：nop `DBCheckpointManager` append-only 比 spec-kit 的 RunState JSON 整写更健壮。
- **质量门**：nop `CompletionJudge` + 12 hook 点 + guardrail 比 spec-kit 的 gate 更系统化。

**必要参考的增量（以超越方式吸收）**：
- **四阶段状态机**（specify→plan→execute→review + 回退边）：nop plan 运行时骨架可参考其阶段划分——但以 nop-task 的 ChooseTaskStep/GraphTaskStep 实现（已在 mission-driver 移植设计中确认复用 nop-task）。

**总评**：nop-ai-agent **全面超越** spec-kit（nop-task 复用使流程引擎能力远超其 RunState 状态机）；四阶段划分作为 plan 运行时的参考语义吸收，实现完全 nop 原生。

## References

- `~/ai/spec-kit/`（src/、specs/、README.md）
- `nop-ai-agent/src/main/java/io/nop/ai/agent/plan/`（AgentPlan 静态模型）
- `ai-dev/design/nop-ai-agent/nop-ai-agent-plan-dsl.md`
- `ai-dev/analysis/agent-survey/2026-08-01-planning-with-files-persistent-plan-analysis.md`、`2026-08-01-jcode-dag-first-agent-analysis.md`
