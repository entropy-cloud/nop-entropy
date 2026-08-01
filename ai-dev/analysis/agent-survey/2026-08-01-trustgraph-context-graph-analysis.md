# TrustGraph Holonic Context Graph 深度分析 & Nop AI Agent 上下文工程

> Status: open
> Date: 2026-08-01
> Scope: `~/ai/trustgraph`（auspices-ai/trustgraph，基于 RDF 的上下文知识图）vs `nop-ai-agent`（memory/prompt/context 体系）
> Conclusion:

## 一、总览

**TrustGraph** 是一个"上下文工程"平台：把 agent 的上下文从"剪贴板拼贴"升级为 **Holonic Context Graph（HCG）**——以 RDF 1.2（RDF-star）三元组为事实基元，四阶段管线（capture → interpret → compose → consume）统一管理上下文摄取、解释、组装、消费，并以 provenance（来源溯源）保证上下文可信。

| 维度 | TrustGraph | Nop AI Agent |
|------|-----------|--------------|
| 上下文基元 | RDF 三元组（subject-predicate-object，可嵌套声明） | 消息（Message 对象）+ prompt 组装 |
| 管线 | capture→interpret→compose→consume 四阶段 | compact 3 层管线（压缩为主，非摄取） |
| 存储 | Cassandra（quads_by_entity/quads_by_collection，属性图视图） | AgentSession 存储（消息流） |
| 溯源 | provenance 四元组（来源实体+时间+置信度） | 无（消息无来源元数据） |
| 记忆 | 上下文图即长期记忆（图查询） | memory 包（向量/存储适配器） |

**核心结论先行**：TrustGraph 对 nop 的借鉴不在 RDF 本体（引入成本高），而在**两点方法论**：**①上下文即证据链**——每个上下文事实带 provenance（来源、时间、置信度），这让"上下文可信度"成为可计算属性（agent 区分事实/猜测/过期信息）；**②摄取与组装分离**——先统一捕获（capture），再按需组装（compose），而非 nop 当前的"消息流 + 提示词模板"单轨。nop 可以在保留消息模型的前提下，给消息增加来源元数据 + 引入上下文组装层。

## 二、Context（调研背景）

- **为什么需要这个分析**：7 月博客《TrustGraph 深度解析：Holonic Context Graph 上下文工程》介绍了其 RDF 上下文图与四阶段管线；nop 的 compact 管线解决"上下文太大"，但**"上下文是什么、从哪来、可信吗"**尚无模型。
- **要回答的问题**：不引入 RDF 的前提下，nop 能从 TrustGraph 拿走什么？
- **约束**：nop 是 Java DSL-first，已有消息流模型与向量记忆；RDF 生态在 Java 中有实现但引入成本高。

## 三、核心机制详解

### 3.1 RDF 三元组基元

- 事实 = `(subject, predicate, object)`，RDF-star 支持"关于事实的事实"（嵌套声明，如"张三说：股票会涨"）。
- 上下文图 = 三元组集合 + 实体网络——agent 的问题映射为图查询（subgraph extraction）。

### 3.2 四阶段管线（架构核心）

```
capture（摄取）：从工具/文档/对话提取事实 → 三元组
interpret（解释）：把三元组组装为实体/关系/含义（子图）
compose（组装）：针对当前任务选择相关子图（按实体/关系相关性）
consume（消费）：子图注入 agent 上下文（prompt）
```

- 与 nop 对照：nop 只有 consume（prompt 组装）与"压缩"（compact，处理已消费内容）；capture/interpret 缺——**知识从工具结果进入消息流后即"原样存在"，没有结构化**。

### 3.3 Provenance 溯源

- 每个三元组携带来源：`(source_entity, timestamp, confidence)`。
- 意义：agent 可评估上下文事实的时效性与可信度（过期的数据、低置信度的推断）。

### 3.4 存储

- Cassandra 属性图视图（quads_by_entity / quads_by_collection）+ 向量/全文索引；nop 可参考其"图视图 = 实体为中心的查询加速"思路。

## 四、优缺点

### 优点

1. 上下文有结构、有来源、可追溯——可信度可计算。
2. 摄取/组装分离 → 上下文选择是显式决策（查询），不是隐式拼接。
3. 图模型天然支持多跳推理与实体关联（跨文档连接事实）。

### 缺点

1. RDF 栈成本高（本体管理/查询语言 SPARQL/学习曲线）。
2. 结构化摄取依赖高质量提取（本身需要 LLM，精度不稳）。
3. 图查询对"聊天记录式"agent 是过度设计（简单任务线性消息足够）。

## 五、对 nop-ai-agent 的借鉴要点（核心价值）

### 5.1 消息来源元数据（高优先，低成本）

nop 现状：Message 有 role/content/meta，但**无"来源"概念**。建议：

```
Message 扩展（不影响现有模型，meta 已可承载）：
  - sourceEntity（来源：工具名/文档ID/用户/LLM）
  - timestamp（已有）
  - confidence（置信度：事实/推断/猜测）
  - validity（时效：过期时间）
```

- 收益：prompt 组装时可标注"来源 + 置信度"，让 LLM 区分事实与猜测；compaction 时按来源保留高置信事实。

### 5.2 上下文组装层（中优先）

nop 现状：PromptAssembly 组装 system/历史/工具定义，无"上下文选择"步骤。借鉴 compose：

```
AgentContextAssembler（可选层，默认行为 = 现有组装）：
  - 按当前任务（goal + 当前 Phase）从 session 消息 + memory 检索相关子集
  - 相关性子图（信任图）→ 注入 prompt
```

- 与 compact 管线的关系：compact 处理"输出窗口超限"（长度问题）；assemble 处理"注入什么"（内容问题）——正交。

### 5.3 不引入的

- RDF/SPARQL/Cassandra 栈：nop 的消息流 + 向量记忆已够用；图能力留给未来（如 team 包多 agent 共享知识时）。

## 六、结论

- TrustGraph 的方法论（上下文即证据链、摄取/组装分离）比技术（RDF）更值得借鉴。
- nop 落地：先加**消息来源元数据**（低成本高收益），再评估**上下文组装层**。
- 后续工作：指向 `ai-dev/design/nop-ai-agent/nop-ai-agent-memory.md` 与 `-prompt-assembly.md` 的扩展。

## Open Questions

- [ ] 来源元数据由谁写（工具执行器/消息中间件/LLM 判定）？
- [ ] 置信度是人工标注还是 LLM 自动评估（成本考虑）？
- [ ] 上下文组装层与现有 AgentPromptAssembly 的融合边界？

## References

- `~/ai/trustgraph/`（context-engine/、store/、doc/）
- `nop-ai-agent/src/main/java/io/nop/ai/agent/`（compact/PipelineCompactor、memory、prompt 组装）
- `ai-dev/design/nop-ai-agent/nop-ai-agent-memory.md`
- `ai-dev/analysis/agent-survey/2026-06-06-agent-memory-compaction-session-deep-dive.md`
