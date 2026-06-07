# Nop AI Agent 容错与恢复设计

## 1. 目标

Agent runtime 稳定之后，系统会进入第二类问题：

- 模型调用不稳定
- 工具调用可能失败
- 上下文会膨胀
- Agent 可能循环
- 长执行需要超时和恢复

本篇定义 `nop-ai-agent` 的可靠性增强层，目标是让系统在真实环境中更可控，而不是在设计阶段一次性实现全部复杂能力。

### 1.1 恢复模型

Agent 执行过程中的详细历史自动持久化（消息历史、工具调用及结果、Plan 状态）。崩溃后恢复策略：

- 从持久化的消息历史重建上下文，已完成的工具调用不需要重新执行
- 结合 Plan 系统确定当前进度和待执行步骤，从断点继续
- AgentSession 状态持久化到数据库，任何服务实例都可以接管恢复
- 并发接管的锁机制由 actor 调度系统负责

## 2. 故障模型

Agent 运行时面临的故障，与普通业务代码不同，主要有五类：

1. 模型调用故障
   - 限流
   - provider 5xx
   - 网络超时
2. 工具执行故障
   - 参数错误
   - 文件或权限错误
   - shell 超时
3. 上下文故障
   - token 超限
   - 历史过长导致推理退化
4. 行为故障
   - 循环调用同一工具
   - 持续产出无效动作
5. 长流程故障
   - 任务超时
   - 进程崩溃
   - 执行中断后无法恢复

可靠性设计必须围绕这五类故障展开。

## 2.5 Tool-Call Repair 四阶段修复管线

LLM 输出的工具调用 JSON 经常存在参数丢失、JSON 截断、重复调用风暴。来自 Reasonix 的四阶段 Chain of Responsibility 管线：

| 阶段 | 触发条件 | 算法 |
|------|---------|------|
| **flatten** | schema 叶子参数 >10 或深度 >2 | 自动展平为点记法 (`a.b.c`)，dispatch 时重新嵌套 |
| **scavenge** | 每轮 | 正则扫描 `reasoning_content` 寻找遗漏的 tool-call JSON（3 种模式匹配器），MAX_SCAVENGE_INPUT=100KB 防 ReDoS |
| **truncation** | JSON 不完整 | 括号栈状态机：trim → 去尾逗号 → null 填充 → 闭合括号 → fallback `"{}"` |
| **storm** | 每轮 | 滑动窗口(windowSize=6)追踪最近调用，相同(name, args)≥3次 → 抑制 |

**Nop 映射**：`Chain<ToolCall>` 拦截器链，每阶段实现 `BiFunction<ToolCall, ToolCall>` 接口。Storm 用 `LinkedHashMap` + `removeEldestEntry` 实现滑动窗口。

> Storm 阶段是工具级别的去重，熔断器 (§5.1) 是 turn 级别的终止——两层保护互补。

## 3. 分层设计

建议把可靠性能力分成四层：

### 3.1 调用层

负责单次调用层面的故障：

- 错误分类
- 重试
- 超时

### 3.2 运行层

负责单个 Agent loop 的可持续运行：

- 上下文窗口保护
- 历史压缩
- 循环检测
- 工具参数和安全验证

### 3.3 平台层

负责 provider 和模型层面的降级：

- 断路器
- 模型回退

### 3.4 恢复层

负责跨进程和长流程恢复：

- 检查点
- 会话恢复

## 4. Layer 1-2 优先能力

> **Layer 映射**：本篇"优先能力"对应 roadmap Layer 1（核心闭环）中的可靠性子集 + Layer 2（执行扩展）的前半部分；"后续能力"对应 Layer 3（可靠性扩展）。具体分配见各节。

### 4.1 错误分类

这是最值得尽快落地的可靠性能力。

建议最小分类：

- `RETRYABLE`
- `NON_RETRYABLE`
- `RECOVERABLE`

语义：

- `RETRYABLE`
  - 适合程序化重试
  - 如 429、5xx、网络故障、临时超时
- `NON_RETRYABLE`
  - 不适合程序化重试
  - 如参数错误、权限错误、工具名不存在
- `RECOVERABLE`
  - 需要先做额外处理再继续
  - 如上下文溢出、需要压缩后重试

没有错误分类，就很难决定哪些问题该交给程序、哪些问题该交给 LLM 或 Advisor Agent。

### 4.2 分层超时

建议尽早统一时间预算：

- Agent 总超时
- 单次 LLM 调用超时
- 单个工具默认超时

这是运行可控性的基础。

### 4.3 上下文压缩

长会话必然面临上下文膨胀，因此压缩不是可选项。

**Layer 1-2 实现**：Layer 0（Tool Result 预截断）+ Layer 1（零成本微压缩）+ 基础 Layer 3（LLM 摘要）。完整 Layer 2 和 Layer 4 推迟到 Layer 3 可靠性扩展（见 roadmap.md §5.2）。

完整 5 层管道定义见 §7。

### 4.4 工具验证

这部分也应尽快做，并且尽量程序化而不是 prompt 化。

最小验证顺序：

1. 工具名存在
2. 参数可解析
3. 参数符合 schema
4. 参数满足安全约束

## 5. Layer 3 后续能力

### 5.1 断路器

当某个模型或 provider 连续失败时，继续调用它只会浪费时间和 token。

因此可以为模型层引入断路器状态：

- `CLOSED`
- `OPEN`
- `HALF_OPEN`

但这类能力更适合在 runtime 稳定后再引入，因为阈值和冷却时间需要真实运行数据校准。

### 5.1a 弹性策略选择（Sisyphean vs Fast-fail）

熔断器代表"快速熔断"哲学。另一种截然不同的弹性哲学是 oh-my-claudecode 的 Sisyphean 模型——Stop-hook 拦截退出事件，检查 todo 列表，强制继续执行。

| 哲学 | 代表 | 行为 | 适用场景 |
|------|------|------|---------|
| "快速熔断" | PilotDeck (Circuit Breaker) | 3 轮失败即终止，fail-fast | 交互式场景，成本敏感 |
| "永不放弃" | oh-my-claudecode (Sisyphean) | Stop-hook 确保任务完成，at-least-once | 无人值守长时间执行 |

**设计决策**：两种策略作为 `ICircuitBreaker` 和 `ISustainer` 的互斥配置选项。Layer 1 默认 fail-fast（与 Nop 无人值守定位一致），Sisyphean 可选激活。在 biz action 的后置拦截器中实现。

### 5.2 模型回退

当主模型不可用时，系统可以沿着有序回退链切换到备用模型。

这类能力与断路器天然配套，但同样应该后置到第二阶段或第三阶段。

### 5.3 循环检测

Agent 可能会反复调用同一个工具、同一组参数。

建议使用调用签名做检测：

- `toolName + normalizedArgs`

处理方式分两级：

- 软提示
- 硬中断

这比简单总次数限制更有效，但参数阈值仍需要运行经验。

### 5.4 检查点与恢复

检查点的目标是：

- 长任务可恢复
- 崩溃后可恢复到最近安全点
- plan/todo/message/token budget 等状态可继续使用

这类能力需要明确存储模型和触发时机，因此建议等 runtime 稳定后再实现。

## 6. 工具失败处理

### 6.1 两类失败

工具失败建议分成两类处理：

- 物理性失败
  - 超时
  - 网络异常
  - provider 暂时不可用
- 语义性失败
  - 参数错误
  - 工具名错
  - 输出不符合预期

### 6.2 处理原则

- 物理性失败优先走程序化策略
- 语义性失败再交给 LLM 或 Advisor Agent 决策

这样可以避免把所有错误都扔给 Agent 自己推理，导致确定性问题也被 prompt 化。

## 7. 上下文窗口保护

基于 10 框架调研（LangGraph、CrewAI、AutoGen、OpenCode、DeepAgents、PilotDeck、Reasonix、EdgeClaw、Claude Code、Codex CLI）的源码级对比，建议采用**5 层渐进压缩管道**，核心原则是**逐级升级、不可跳级**——先尝试零成本操作，再尝试无 LLM 调用操作，最后才调用 LLM 生成摘要。

### 7.1 五层保护模型

| 层级 | 名称 | LLM 调用 | 触发时机 | 操作 | 参考 |
|------|------|---------|---------|------|------|
| Layer 0 | Tool Result 预截断 | 无 | 每次工具执行后 | 截断 tool result 超过阈值（默认 8000 tokens）的部分，截断算法保留 head + 1KB tail | Reasonix |
| Layer 1 | 零成本微压缩 | 无 | ReAct 循环每轮检查 | 替换旧 tool_result 内容为 placeholder，仅处理可压缩工具（read_file, bash, grep 等），保留最新 N 条 | PilotDeck MicroCompaction |
| Layer 2 | 中间 Turn 裁剪 | 无 | 超过 Layer 1 阈值 | 裁剪中间 turns，保留 head + tail anchors，维护 tool_call/tool_result 跨边界完整性 | PilotDeck SnipEngine |
| Layer 3 | LLM 摘要 | 1 次（便宜模型） | 超过 Layer 2 阈值 | LLM 生成结构化摘要（增量更新前次 summary），完整历史 offload 到持久存储 | OpenCode + DeepAgents |
| Layer 4 | 强制退出 | 0 或 1 次 | context >90% | 生成最终摘要，停止工具调用，发布 AgentEvent.FORCED_STOP | Reasonix |

### 7.2 Token 计数

双层策略：Provider 报告为主，轻量估算为辅。

- **Post-call（精确）**：直接使用 LLM API 响应中的 `usage.prompt_tokens` / `usage.completion_tokens`。这是精确值，无需自行计算
- **Pre-call（估算）**：发送请求前需要估算当前 context 大小以决定是否触发 compaction。使用简单字符比例估算（如 `chars / 4`）即可，不需要引入 BPE tokenizer 依赖
- **校准**：Post-call 的精确值可用于校准 Pre-call 估算的偏差，运行时自动修正比例系数
- **不引入 JTokkit**：LLM API 返回的 token 数是 source of truth，自行 BPE 计数既不准确（各模型 tokenizer 不同）又增加依赖

### 7.3 摘要策略

- **增量更新**：如果前次 summary 存在，用 `<previous-summary>` 传递，要求 LLM 增量更新（非全量重写）（OpenCode 模式）
- **结构化模板**（8 节）：Goal / Constraints & Preferences / Progress (Done/In Progress/Blocked) / Key Decisions / Next Steps / Critical Context / Relevant Files
- **文件追踪**：Compaction 时提取 read/modified 文件列表，附加到摘要末尾（pi-agent 模式）
- **专用模型**：摘要生成使用便宜/快速模型，不与主 Agent 的 LLM 竞争（Reasonix 用 flash，Hermes 用 auxiliary）。Compaction LLM 调用使用独立配额池（见 actor-runtime-vision.md §5.2: 20/min/tenant）
- **PreCompact hook**：压缩前保存关键状态（todo, plan, project memory），压缩后重新注入（oh-my-claudecode 模式）。此 hook 属于 Layer 2 新增的 Hook 生命周期点（`before_compaction` / `after_compaction`），当前 Hook 引擎仅定义 `before_reasoning` / `after_reasoning` / `before_acting` / `after_acting` / `on_error`

### 7.4 最小保真规则

压缩后必须保留：

1. 最早的 system message
2. 最早的用户初始目标
3. 当前 plan 的任务状态摘要
4. 最近 N 条消息
5. Pinned items（活跃 skills、约束条件）
6. 文件追踪列表（read-files, modified-files）

同时禁止递归压缩：执行压缩的流程本身不应再次触发相同压缩逻辑。

## 8. 超时与预算

时间预算与 token 预算本质上都是有限资源控制问题。

建议统一概念：

- token budget
- timeout budget

并在运行时支持父子预算级联，避免子调用无限制消耗整个 Agent 的资源。

## 9. 输出和安全限制

工具层还应具备几个基础保护：

- 输出大小上限
- shell 工作目录限制
- 环境变量白名单
- 路径穿越防护
- 进程组终止策略

这部分越程序化越好，不应依赖 prompt 来守护。

## 10. 推荐实施顺序

可靠性增强建议按下面顺序落地：

1. 错误分类
2. 分层超时
3. 上下文压缩
4. 工具验证和安全限制
5. 循环检测
6. 模型回退和断路器
7. 检查点和恢复

## 11. 明确延期项

下面这些先不要作为 MVP 强依赖：

- 完整断路器策略
- 多模型冷却追踪
- 全量恢复存储模型
- 所有 provider 的统一回退配置

建议保留但后置的失败升级链：

- `retry-advisor` 返回 `repair`
- 在隔离上下文中尝试 AI 修复
- 修复失败后触发 `bug-report` 或等价故障上报工具

这条链路不进入 Layer 1-2，但应作为 Layer 3 设计保留。

## 12. 本篇结论

可靠性设计必须保留，但它应该是建立在稳定 Agent runtime 之上的增强层：

- 先让单 Agent 正常跑通（Layer 1）
- 再让它在真实环境中稳定运行（Layer 2 执行扩展）
- 最后再支持复杂恢复和平台级降级（Layer 3-4）

这样实现风险最低，也最符合当前阶段的成熟度。

---

## 与其他文档的关系

- `nop-ai-agent-roadmap.md` — 分层架构（本篇覆盖 Layer 1-3 的可靠性部分）
- `nop-ai-agent-llm-layer.md` — LLM 层设计（IRetryPolicy、IModelDialect 的详细设计）
