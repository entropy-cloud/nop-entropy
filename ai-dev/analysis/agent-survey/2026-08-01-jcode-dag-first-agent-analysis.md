# JCode DAG-First 编码 Agent 深度分析 & Nop AI Agent 任务编排/压缩/记忆

> Status: open
> Date: 2026-08-01
> Scope: `~/ai/jcode`（1jehuang/jcode，Rust 编写、DAG-first 的本地编码 Agent，v0.64.2）vs `nop-ai-agent`（plan/team/compact/memory 包）
> Conclusion:

## 一、总览

**JCode** 是一个用 Rust 编写的本地优先编码 Agent（90+ crates、MIT），核心差异化：**DAG 任务图（不是线性的 todo 列表）**、**会话级压缩**（上下文精简，不是切段丢历史）、**本地 ONNX 嵌入记忆**、**命令风险评估**、**Deep/Light 双模式**、**软中断**（可恢复的运行时中断，而非强杀）。

| 维度 | JCode v0.64.2 | Nop AI Agent |
|------|--------------|--------------|
| 任务模型 | DAG（TaskNode{id,kind,status,owner,parent,depends_on,is_gate} + 4 个调度器文件） | AgentPlan 线性 phases/tasks 静态模型 |
| 压缩 | jcode-compaction-core（纯算法，无 I/O） | PipelineCompactor 3 层管线（有 I/O 依赖） |
| 记忆 | jcode-embedding：本地 ONNX all-MiniLM-L6-v2 + memory graph | memory 包（存储/向量/嵌入适配器） |
| 安全 | jcode-command-risk：bash 风险评估 | security 6 层 + PermissionMatrix |
| 运行模式 | Deep（自主多轮）/ Light（单轮快速）双模式 | DefaultAgentEngine/ReActAgentExecutor 单模式 |
| 中断 | InterruptSignal + SoftInterruptQueue（可恢复） | 无（仅 cancel/failure） |

**核心结论先行**：jcode 的 **DAG 任务图**和**会话压缩算法**是两个最值得深挖的点——前者正好是 nop plan 包从静态线性模型升级为可调度运行时图的最完整参考实现（scheduler 在 `jcode-plan/src/dag/` 下：mod.rs + ops.rs + schedule.rs + sim.rs）；后者（jcode-compaction-core 纯算法实现，无 I/O 耦合）与 nop PipelineCompactor 的"压缩即管线"形成互补，可对照评估 nop 压缩管线中每一层。

## 二、Context（调研背景）

- **为什么需要这个分析**：7 月博客《JCode：DAG-first 本地编码 Agent》重点拆解其 DAG 任务图、压缩与记忆设计；nop 的 plan 包缺运行时调度、compact 包已实现但缺少与 DAG 结构的关联。
- **要回答的问题**：DAG 任务图如何在 nop 的 DSL-first 模型下落地？jcode 的压缩管线与 nop PipelineCompactor 的差异与借鉴？
- **约束**：nop 是 Java + DSL-first（AgentModel 静态配置）；jcode 是 Rust + 本地文件驱动。

## 三、核心机制详解

### 3.1 DAG 任务图（jcode-plan/src/dag/）

- **TaskNode**：`{ id, kind, status, owner, parent, depends_on[], is_gate, payload, input, output }`。
  - `depends_on`：显式依赖（任务 DAG 边）。
  - `is_gate`：门节点——后续任务必须等该任务成功后（或按策略）才能继续。
- **调度器四文件分工**（schedule.rs 为关键）：
  - `ops.rs`：对任务的原子操作（创建/更新状态/绑定 owner/完成）。
  - `schedule.rs`：**可执行任务发现 + 决策**——拓扑序 + 就绪条件（依赖全 complete + owner 可用）→ 生成调度建议。
  - `sim.rs`：调度模拟（无副作用预估任务耗时/依赖链，供 planner 决策用）。
  - `mod.rs`：图模型本体。
- **状态机**：`pending → in_progress → complete / failed`（加上 cancelled）；门节点控制串行/并行切换。
- **DAG 与 LLM 的关系**：planner 产出任务图 → 执行器按图调度 → 任务完成后更新图 → 必要时重规划（新分支节点）。

### 3.2 会话压缩（jcode-compaction-core）

- 定位：**纯算法 crate**，不依赖任何 I/O/网络——输入是 `CompactionRequest{ messages, ... }`，输出 `CompactionResult`，调用方（jcode 主程序）负责存取。
- 压缩策略：按 token 预算目标，将早期消息压缩为结构化摘要（保留角色/工具调用边界/关键数据），与 nop 的"3 层压缩管线"目标一致但实现独立。
- 与 nop 的差异：nop 是 `Compactor → LLMCompactor → CompactTools` 三层管线、可配置压缩点；jcode 是单一算法 + 无 I/O 的纯函数。

### 3.3 本地嵌入记忆（jcode-embedding）

- 本地 ONNX Runtime 加载 all-MiniLM-L6-v2（~80MB），无外部 API 依赖。
- 记忆图 + 向量检索：语义查询历史任务/对话。
- 与 nop memory 包对照：nop 有存储/向量/嵌入适配器抽象，可插拔 OpenAI/本地嵌入；jcode 是固定本地模型。

### 3.4 软中断（InterruptSignal / SoftInterruptQueue）

- 设计动机：长时间 LLM 生成无法硬杀（丢失进度）；软中断让执行器在**安全点**（工具调用之间/生成循环边界）检查中断信号队列，优雅暂停，状态可恢复。
- 与 nop 对照：nop 的取消是硬中断（Future cancel）；jcode 的软中断是可恢复暂停——这是 nop 可靠性体系可借鉴的（对应 Hatchet 的 eviction 语义）。

## 四、优缺点

### 优点

1. **DAG 表达真实依赖**：门节点 + depends_on 能表达"先重构接口再实现"等线性 todo 表达不了的依赖；调度器可静态验证死锁/环。
2. **压缩纯算法化**：无 I/O 耦合 → 可单测、可替换策略、可基准。
3. **本地优先**：ONNX 嵌入、无外部服务，数据不出机器。
4. **Rust 性能**：90 crates 的精细拆分，各能力可独立复用。

### 缺点

1. 生态/成熟度弱于 Claude Code/opencode；插件、MCP 集成不深。
2. DAG 的调度复杂度换不来明显收益时（简单任务），线性 todo 更直接。
3. Deep/Light 双模式切换的触发策略模糊（没有明确的规则说明何时用哪种）。

## 五、对 nop-ai-agent 的借鉴要点（核心价值）

### 5.1 DAG 运行时调度（最高优先）

nop 现状：plan 包 AgentPlan 是**静态线性** phases/tasks；agent 不真正执行计划（纯 ReAct）。借鉴 jcode：

```
PlanRun 升级：
  TaskNode{ id, kind, status, owner, parentId, dependsOn[], isGate, input, output }
  PlanScheduler（对应 schedule.rs）：
    - 拓扑序遍历，发现「依赖全 complete + 未分配 owner」的就绪任务
    - 单 agent 串行：每次只取一个就绪任务（门节点天然串行化）
    - 多 agent（team 包）：按 team 能力匹配 owner 并行分发
  PlanSimulator（对应 sim.rs）：预估依赖链长度 → planner 重规划决策
```

- `isGate` 直接映射 nop 场景：**每个阶段结束的验收点**（测试通过才进下一阶段）。
- 状态机扩展：`pending/in_progress/complete/failed/cancelled/blocked`（planning-with-files 只有三态，jcode 的失败/取消/门是更完整的表达）。
- 与 DSL-first 的兼容：AgentPlan（静态 XDEF 配置）作为**初始图**，运行时 PlanRun 在其上增删节点（重规划产生新节点）——定义与状态分离的既有范式完美契合。

### 5.2 压缩管线的对照评估（次优先）

- jcode-compaction-core 无 I/O 纯算法 vs nop PipelineCompactor（压缩点注入 LLM 调用、需要 I/O）——**把压缩策略算法与 I/O 分离**是 nop 可改进点：压缩策略（保留哪些消息、如何摘要）应为纯函数，LLM 调用只是策略的一个参数化实例。
- 建议新增 `CompactionPolicy` 接口，默认实现 `JCodeLikePolicy`（按 token 预算 + 角色保留）。

### 5.3 软中断（可选）

- 实现 `AgentInterruptSignal`（检查点安全点暂停），与 checkpoint 的 WAIT_FOR 语义（hatchet 借鉴）结合，形成"可暂停长任务"能力。

## 六、结论

- jcode 的最大价值是 **DAG 任务图 + 门节点**，这是 nop plan 包从静态模型到运行时计划器的关键参照；其次是**压缩纯算法化**的思路。
- nop 落地建议：`TaskNode` 模型 + `PlanScheduler` + `isGate` 阶段验收 + `PlanSimulator`，接入现有 AgentPlan 静态模型作为初始图。
- 后续工作：指向 `ai-dev/design/nop-ai-agent-plan-dsl.md` 的运行时图扩展设计。

## Open Questions

- [ ] nop 的任务图是否需要跨 agent（team）的依赖感知（owner 匹配）？
- [ ] 门节点的验收标准由谁判定（工具结果 / LLM 判定 / 测试命令）？
- [ ] 重规划触发的阈值（任务失败率 / 依赖链长度 / LLM 建议）？

## References

- `~/ai/jcode/crates/jcode-plan/src/dag/`（mod.rs / ops.rs / schedule.rs / sim.rs）
- `~/ai/jcode/crates/jcode-compaction-core/`、`~/ai/jcode/crates/jcode-embedding/`、`~/ai/jcode/crates/jcode-command-risk/`
- `ai-dev/design/nop-ai-agent/nop-ai-agent-plan-dsl.md`
- `ai-dev/analysis/agent-survey/2026-08-01-planning-with-files-persistent-plan-analysis.md`
