# 维度 18：文档-代码一致性

## 检查范围

docs-for-ai/（平台使用文档）+ ai-dev/design/nop-ai-agent/（设计过程文档）vs live code。原则：ai-dev/design 是过程文档允许"计划 vs 已完成"差异，只报"文档声称已完成但代码不符"或"引用不存在代码锚点会误导后续开发"。

## 第 1 轮（初审）发现

### [维度18-01] Hook 生命周期点数量与代码不一致（文档 10 vs 代码 12），漏 2 个已激活 re-entrant 点

- **文件**: 文档 `ai-dev/design/nop-ai-agent/02-execution-model.md:12,86-108`、`glossary.md:97-119`；代码 `hook/AgentLifecyclePoint.java:3-16`、`hook/DefaultHookRegistry.java:62-73`、`engine/ReActAgentExecutor.java:1958,2021`
- **证据片段**:
  - 文档 02-execution-model.md:12「10 个生命周期点」；glossary 列 Layer1 核心 5 点 + Layer2 扩展 5 点 = 10。
  - 代码 AgentLifecyclePoint.java:3-16 枚举共 12 值，额外含 `BEFORE_TOOL_RESULT_PROCESSED`(:14)、`AFTER_TOOL_RESULT_PROCESSED`(:15)。
  - ReActAgentExecutor.java:1958,2021 实际 invoke 这两点；DefaultHookRegistry:72-73 注册 DSL 映射；ReActAgentExecutor:2683-2686 为这两点定义 re-entrant 语义。
- **严重程度**: P1
- **现状**: 两个已落地、有独立 DSL 名称、有专属 re-entrant 语义的 hook 点完全没出现在设计文档。文档明确写"10 个"。
- **风险**: 后续开发 Hook/Skill 引擎或为集成方写 Hook 配置时，基于"10 个"契约无法发现这两个支持 re-entrant（可改写 tool result）的高价值扩展点；DSL 用户不知 before/after_tool_result_processed 是合法 event 值。
- **建议**: 02-execution-model.md §5.1 与 glossary §Hook 新增 Layer2 两个 re-entrant 点及触发时机，"10 个"更正为"12 个"，标注 re-entrant 仅适用这两点。
- **信心水平**: 高（4 独立代码锚点交叉验证）
- **误报排除**: 两枚举值非 dead code——既被 invokeHooks 调用又在 DefaultHookRegistry 暴露 DSL 事件名；是"已实现未文档化"。
- **复核状态**: 未复核

### [维度18-02] glossary.md 事件类型表用代码不存在的事件名，命名约定（PascalCase）与实际代码（UPPER_SNAKE_CASE）矛盾

- **文件**: 文档 `glossary.md:71-95,145`；代码 `engine/AgentEventType.java:3-80`
- **证据片段**:
  - 文档 glossary:75-80 列核心事件 TextChunk/ThinkingChunk/ToolCallStart/ToolCallComplete/AgentResult/AgentError/AgentInterrupted；:87-95 扩展事件 HookExecuted/SteeringInjected/CompactionCompleted 等；:145 命名约定「事件类型 PascalCase」。
  - 代码 AgentEventType 实际全 UPPER_SNAKE_CASE：EXECUTION_STARTED/ITERATION_STARTED/LLM_RESPONSE_RECEIVED/TOOL_CALL_STARTED/TOOL_CALL_COMPLETED/TOOL_CALL_DENIED/PATH_ACCESS_DENIED/EXECUTION_COMPLETED/EXECUTION_FAILED/SESSION_CREATED/SESSION_LOADED/SESSION_CANCEL_REQUESTED/SESSION_CANCELLED/SESSION_FORKED/FORCED_STOP/SESSION_PAUSED/SESSION_RESUMED/SESSION_RESTORED。
  - grep 全模块：TextChunk/ThinkingChunk/AgentResult/AgentError/AgentInterrupted/HookExecuted/SteeringInjected/CompactionCompleted/SecurityDecision 等代码零出现。
- **严重程度**: P1
- **现状**: 术语表所列 16 事件名几乎全不在代码，命名规则与实际枚举风格完全相反。TOOL_CALL_DENIED/PATH_ACCESS_DENIED/SESSION_PAUSED/RESUMED/RESTORED/FORCED_STOP 等承载关键语义（治理暂停/crash 恢复）的事件未进术语表。
- **风险**: (a)后续开发者事件代码 grep/LSP 按术语表搜不到；(b)文档读者会以 PascalCase 命名新事件违反实际约定；(c)SESSION_PAUSED/RESUMED 治理语义事件未进权威术语表易被忽略。
- **建议**: 用 AgentEventType 实际枚举重写 glossary 事件表，分 Layer 标注，修正命名约定为 UPPER_SNAKE_CASE。
- **信心水平**: 高
- **误报排除**: 文档放"核心事件 Layer1"且无 ✅/计划标记，措辞是当前事实陈述，非"计划事件"。
- **复核状态**: 未复核

### [维度18-03] glossary.md 与 01-architecture-baseline.md 将 IAgentMemory 列为 Layer1 核心接口，代码不存在

- **文件**: 文档 `glossary.md:38`、`01-architecture-baseline.md:64`；代码 memory 包无 IAgentMemory.java
- **证据片段**:
  - 文档 glossary:38「IAgentMemory | Layer1 | 三层记忆管理」；01-arch:64 核心对象职责表同。
  - 代码 memory 包含 IAiMemoryStore/IMemoryStoreProvider/IStorageAdapter/IEmbeddingAdapter/IVectorAdapter/InMemoryAiMemoryStore/AdapterBackedAiMemoryStore，**无 IAgentMemory**。grep "IAgentMemory" nop-ai/ 全模块零命中。
- **严重程度**: P2
- **现状**: 文档把 IAgentMemory 当 unifying Layer1 接口呈现，但三层记忆被拆散：短期 compaction 由 ReActAgentExecutor 直接驱动、Working Memory 由 IAiMemoryStore+IMemoryStoreProvider、长期由三适配器承载，无 IAgentMemory 聚合。
- **风险**: 按"LSP/锚点 lookup"工作流的开发者查找 IAgentMemory 失败浪费排障时间；术语表/架构基线作为权威命名源在此条失效。
- **建议**: 删除条目或标注"概念聚合名，由 IAiMemoryStore+IMemoryStoreProvider+IMemoryAdapter 共同实现，无单一接口"，指向真实锚点。
- **信心水平**: 高
- **误报排除**: 设计文档对 Working Memory 有 ✅ 标记会标完成度；IAgentMemory 一行无 ✅ 但被放进核心契约表呈现为"当前契约"。
- **复核状态**: 未复核

### [维度18-04] glossary.md 将 ISessionManager 列为 Layer1 核心接口，代码不存在

- **文件**: 文档 `glossary.md:35`；代码 session 包仅 ISessionStore，无 ISessionManager
- **证据片段**:
  - 文档 glossary:35「ISessionManager | Layer1 | Session 生命周期管理」。
  - 代码 session 包接口仅 ISessionStore（持久化原语）；grep "ISessionManager" nop-ai/ 零命中。fork/compact/snapshot 实际由 DefaultAgentEngine.forkSession(:2041)/restoreSession(:2638)/ReAct compaction pipeline 承载。
- **严重程度**: P2
- **现状**: 术语表把不存在的 ISessionManager 标 Layer1，与实际架构（ISessionStore 只管持久化、生命周期在 engine 内）不符。
- **风险**: 开发者按术语表找 ISessionManager 扩展点落空；掩盖"session 生命周期无独立可替换抽象、与 DefaultAgentEngine 强耦合"的真实架构事实。
- **建议**: 替换为实际存在的 ISessionStore（Layer1 持久化原语），说明 fork/compact/snapshot 当前内聚在 DefaultAgentEngine 无独立 manager。
- **信心水平**: 高
- **误报排除**: grep 零命中；ISessionStore 名字相近但职责（持久化）显著窄于术语表描述（生命周期）。
- **复核状态**: 未复核

### [维度18-05] glossary.md 把 Todo 描述为 constraints.todos，但 agent.xdef 无 todos 属性，代码无任何 Todo 实现

- **文件**: 文档 `glossary.md:133`、`01-architecture-baseline.md:243`；代码 `agent.xdef:29-31`、全模块 grep todos/setTodos/getTodos
- **证据片段**:
  - 文档 glossary:133「Todo | 轻量任务补充（constraints.todos）」。
  - 代码 agent.xdef:29-31 `<constraints ... maxIterations/maxTimeoutSeconds/maxParallelTools/tokenCompactionThreshold/>` 无 todos 属性；全模块 grep constraints.todos|setTodos|getTodos 零命中。
- **严重程度**: P2
- **现状**: 术语表用具体 DSL 路径形式描述 Todo，但该路径不存在，代码层完全无 Todo 子系统。
- **风险**: 术语表形式（constraints.todos）强烈暗示已存在 DSL 字段；按此写 .agent.xml 会得 xdef 校验错误；误导读者以为 Todo 已交付。
- **建议**: 删除条目或标注"规划中能力，当前未实现，无 DSL 字段或代码"，移除 constraints.todos 路径形式。
- **信心水平**: 高
- **误报排除**: agent.xdef 是 schema 权威源；条目无 ✅/计划标记呈现为当前事实。
- **复核状态**: 未复核

### [维度18-06] docs-for-ai/03-modules/nop-ai.md 写 execute，与同模块 source-anchors.md AIREL-001 的 doExecute 不一致

- **文件**: 文档 `docs-for-ai/03-modules/nop-ai.md:64` vs `docs-for-ai/04-reference/source-anchors.md:111`；代码 `DefaultAgentEngine.java:2128`(execute)/`:2147`(doExecute)
- **证据片段**:
  - nop-ai.md:64「三个入口点（execute / resumeSession / restoreSession）的 supplyAsync executor」。
  - source-anchors.md:111 AIREL-001「三个入口点（doExecute / resumeSession / restoreSession）使用专用 executor」。
  - 代码 :2128 execute 只是 sessionId 解析+委派（不含 supplyAsync）；:2147 doExecute 才含 supplyAsync。
- **严重程度**: P3
- **现状**: nop-ai.md 说 supplyAsync 在 execute，实际在 private doExecute；同份 docs-for-ai 下 source-anchors.md 已正确写 doExecute。两份 docs-for-ai 文档对同一锚点描述自相矛盾。
- **风险**: 低（execute 与 doExecute 一行委派父子关系，行为等价）；主要是 docs-for-ai 内部不一致影响锚点准确性。
- **建议**: nop-ai.md:64 改 doExecute，与 source-anchors AIREL-001 一致。
- **信心水平**: 高
- **误报排除**: 文档原文描述 supplyAsync 发生位置，execute 不含 supplyAsync；source-anchors 证明项目精确口径是 doExecute。
- **复核状态**: 未复核

## 维度复核结论

6 项均以文档引用+代码实际对照核验成立。source-anchors.md（AISEC/AIREL）锚点高度准确；不一致集中在 glossary.md（事件名/IAgentMemory/ISessionManager/constraints.todos）与 02-execution-model.md"10 个生命周期点"——这两份应是权威契约源却恰是不一致最集中处，建议优先修复。

## 最终保留项

| 编号 | 严重程度 | 文件路径 | 一句话摘要 |
|------|---------|---------|-----------|
| 18-01 | P1 | ai-dev/design/.../02-execution-model.md | Hook 生命周期点文档10 vs 代码12（漏2 re-entrant） |
| 18-02 | P1 | ai-dev/design/.../glossary.md | 事件类型表16名几乎全不存在于代码，命名约定反向 |
| 18-03 | P2 | ai-dev/design/.../glossary.md | IAgentMemory 列为 Layer1 接口，代码零存在 |
| 18-04 | P2 | ai-dev/design/.../glossary.md | ISessionManager 列为 Layer1 接口，代码零存在 |
| 18-05 | P2 | ai-dev/design/.../glossary.md | constraints.todos 在 agent.xdef 不存在，Todo 未实现 |
| 18-06 | P3 | docs-for-ai/03-modules/nop-ai.md | execute vs source-anchors doExecute 不一致 |
