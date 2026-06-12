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

这类能力与断路器天然配套，但同样属于 Layer 3 可靠性扩展，应在 Layer 1-2 稳定后实施。

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

### 5.4a Checkpoint Journal 格式（MiMoCode 吸收）

参考 MiMoCode 的 `checkpoint.ts`（1478 行），检查点日志采用 `journal.md` + `snapshot.json` 双文件格式：

**journal.md**（追加写入，source of truth）：
```markdown
# Checkpoint Journal - sess-001

## CP-001
type: tool_execution
seq: 1
timestamp: 2026-06-12T10:00:00Z
entries:
  - tool: file_write
    callId: call_abc
    input: { path: "src/main.java", content: "..." }
    output: { status: "ok" }
watermark: cp_001

## CP-002
type: llm_turn
seq: 2
timestamp: 2026-06-12T10:01:00Z
entries:
  - turn: 3
    promptTokens: 4500
    completionTokens: 1200
    toolCalls: [call_def, call_ghi]
watermark: cp_002
```

**snapshot.json**（派生缓存，可重建）：
```json
{
  "snapshotId": "snap-003",
  "sessionId": "sess-001",
  "lastWatermark": "cp_002",
  "messageCount": 14,
  "tokenEstimate": 8500,
  "planStatus": { "phase": "implementation", "progress": "0.6" },
  "toolResults": [
    { "callId": "call_def", "tool": "file_write", "status": "success" }
  ],
  "createdAt": "2026-06-12T10:01:00Z"
}
```

**恢复流程**：
1. 定位最近的 `snapshot.json`
2. 从 `lastWatermark` 之后的 journal entries 重建增量状态
3. 加载 `firstKeptEntryId` 之后的消息（与 session-and-storage.md §5.3 一致）

**触发时机**：
- 每个 LLM turn 完成后自动写入 journal entry
- 压缩时生成完整 snapshot
- 工具执行前后（仅 long-running tool）写入 tool-level checkpoint

**与 Nop Event Log 的关系**：
- Journal 是 Event Log 的运行时加速结构，不是替代
- Event Log（`events.jsonl`）保持 source of truth 地位
- Journal 提供按 watermark 快速定位和恢复的能力
- Phase 1 用 Event Log 重建即可；Phase 2 可选启用 journal 加速恢复

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

基于 5 框架（Codex, Claude Code, OpenCode, SolonCode, Reasonix）源码级深度调研（详见 `ai-dev/analysis/agent-survey/2026-06-10-token-estimation-and-context-compression-survey.md`），结合 Nop 框架的可插拔定位，确定以下策略。

### 7.1 设计原则

1. **逐级升级，不可跳级**——先尝试零成本操作，再尝试无 LLM 调用操作，最后才调用 LLM 生成摘要
2. **5 层管道为默认实现**——清晰、可操作、每个层级做什么和什么时候触发一目了然
3. **双维度触发**——token 占比和消息数量两个维度独立检查，任一越线即触发（来自 SolonCode 的实践）
4. **可插拔策略作为 Layer 3 扩展点**——默认 5 层管道不需要任何配置即可运行，高级用户通过 `ICompressionStrategy` 接口替换或扩展
5. **前缀缓存感知**——压缩操作不影响 `prefixLength` 前缀区（见 `nop-ai-agent-llm-layer.md` §八）

### 7.2 五层保护模型（默认实现）

| 层级 | 名称 | LLM 调用 | 触发时机 | 操作 | 参考 |
|------|------|---------|---------|------|------|
| Layer 0 | Tool Result 预截断 | 无 | 每次工具执行后 | 截断 tool result 超过阈值（默认 8000 tokens）的部分，保留 head + 1KB tail | Reasonix |
| Layer 1 | 零成本微压缩 | 无 | ReAct 循环每轮检查 | 替换旧 tool_result 内容为 placeholder，仅处理可压缩工具（read_file, bash, grep 等），保留最近 N 条 | PilotDeck MicroCompaction |
| Layer 2 | 中间 Turn 裁剪 | 无 | 超过 Layer 1 阈值 | 裁剪中间 turns，保留 head + tail anchors，维护 tool_call/tool_result 跨边界完整性 | PilotDeck SnipEngine |
| Layer 3 | LLM 摘要 | 1 次（便宜模型） | 超过 Layer 2 阈值 | LLM 生成结构化摘要（增量更新前次 summary），完整历史 offload 到持久存储 | OpenCode + DeepAgents |
| Layer 4 | 强制退出 | 0 或 1 次 | context >90% | 生成最终摘要，停止工具调用，发布 AgentEvent.FORCED_STOP | Reasonix |

**逐级升级规则**：Layer 0 是独立预截断（每次工具执行后自动应用），Layer 1→2→3 逐级尝试——本级解决问题则停止，不跳级。Layer 4 是硬上限保护。

**各层 NoOp/fallback 行为**：

| 层级 | 前提条件不满足时 | 触发条件独立？ |
|------|---------------|-------------|
| Layer 0 | `enabled=false` 时不截断 | ✅ 独立（每次工具执行后） |
| Layer 1 | 无可压缩工具（非 read_file/bash/grep 等）→跳过 | ✅ 独立（ReAct 每轮检查 token 占比） |
| Layer 2 | head + tail 窗口重叠（消息过少）→跳过 | ⚠️ 依赖 Layer 1 已执行。**设计修正**：Layer 2 改为独立检查自己的触发条件（`messageCount > layer2Threshold`），而非"超过 Layer 1 阈值" |
| Layer 3 | LLM 不可用 → 降级为 Layer 2 效果（保留更多原始消息），记录 fallback 日志 | ✅ 独立（有自己的触发阈值） |
| Layer 4 | 不适用（硬保护始终生效） | ✅ 独立 |

### 7.3 触发机制

**双维度 OR 门**（参考 SolonCode）：

```
ReAct reason 循环开始前（每轮检查）:
  tokenEstimate > maxTokens * triggerTokenPercent    // 默认 0.8
  OR
  messageCount > triggerMaxMessages                   // 默认 30

  任一条件满足 → 从 Layer 1 开始逐级尝试压缩
```

**两个触发点**：

1. **Pre-iteration**（每轮 ReAct reason 前检查）：`ILlmDialect.estimateTokens()` 本地估算 vs 阈值
2. **Post-response**（每次 LLM 响应后检查）：Provider 报告的精确 `usage.prompt_tokens` vs 阈值。Post-response 使用精确值，可校准 Pre-iteration 的估算偏差

### 7.4 Token 计数

双层策略：Provider 报告为主，轻量估算为辅。

- **Post-call（精确）**：直接使用 LLM API 响应中的 `usage.prompt_tokens` / `usage.completion_tokens`。这是精确值，无需自行计算
- **Pre-call（估算）**：通过 `ILlmDialect.estimateTokens()` 估算，缺省 chars/4（见 `nop-ai-agent-llm-layer.md` §4.0）。对触发阈值（80%）来说精度够用——chars/4 的 ±10% 偏差不会导致错误决策
- **校准**：Post-call 精确值与 Pre-call 估算值的偏差记录在 Dialect 内部，用于修正后续估算

### 7.5 摘要策略

- **增量更新**：如果前次 summary 存在，用 `<previous-summary>` 传递，要求 LLM 增量更新（非全量重写）（OpenCode 模式）
- **结构化模板**（8 节）：Goal / Constraints & Preferences / Progress (Done/In Progress/Blocked) / Key Decisions / Next Steps / Critical Context / Relevant Files
- **文件追踪**：Compaction 时提取 read/modified 文件列表，附加到摘要末尾（pi-agent 模式）
- **专用模型**：摘要生成使用便宜/快速模型，不与主 Agent 的 LLM 竞争（Reasonix 用 flash，Hermes 用 auxiliary）。通过 `compressionModel` 配置
- **PreCompact hook**：压缩前保存关键状态（todo, plan, project memory），压缩后重新注入（oh-my-claudecode 模式）。使用 `PRE_COMPACT` / `POST_COMPACT` 生命周期点（`02-execution-model.md` §5.1 Layer 2 扩展）

### 7.6 压缩后保留规则

必须保留：

1. 最早的 system message
2. 最早的用户初始目标
3. 压缩生成的摘要消息
4. 最近 N 条消息（`keepTailPercent` 决定大小）
5. Pinned items（活跃 skills、约束条件——Reasonix 的 `<skill-pin>` 模式）
6. 文件追踪列表（read-files, modified-files——pi-agent 模式）

禁止递归压缩：执行压缩的流程本身不再触发压缩。

### 7.7 配置模型

通过 `agent.xdef` 的 `<compaction>` 元素配置。所有参数都有合理默认值，零配置即可运行。

```xml
<agent name="my-agent">
  <compaction enabled="true"
              triggerTokenPercent="0.8"
              triggerMaxMessages="30"
              forcedStopPercent="0.9"
              keepTailPercent="0.15"
              compressionModel=""/>
</agent>
```

| 参数 | 默认值 | 含义 |
|------|--------|------|
| `enabled` | `true` | 是否启用自动压缩 |
| `triggerTokenPercent` | `0.8` | token 占比超过此值触发（Post-call 用精确值，Pre-call 用估算值） |
| `triggerMaxMessages` | `30` | 非首链消息数超过此值触发 |
| `forcedStopPercent` | `0.9` | 超过此值触发 Layer 4 强制退出 |
| `keepTailPercent` | `0.15` | Layer 3 压缩时保留尾部消息的比例 |
| `compressionModel` | 空（=主模型） | Layer 3 摘要使用的模型（建议用便宜模型） |

### 7.8 可插拔策略（Layer 3 扩展点）

默认 5 层管道覆盖绝大多数场景。如果需要定制，实现 `ICompressionStrategy` 接口并通过 Delta 替换 Layer 3 的默认摘要逻辑：

```
ICompressionStrategy:
  String name()
  CompactionResult compact(CompactionContext ctx)
```

可用策略实现（Layer 3 可选）：

| 策略 | LLM 调用 | 机制 | 参考 |
|------|---------|------|------|
| `FullSummary`（默认 Layer 3） | 1 次 | 结构化摘要（8 节模板），增量更新 | OpenCode |
| `KeyInfoExtraction` | 1 次 | 提取业务参数、确认事实、失败路径 | SolonCode |
| `HierarchicalRolling` | 1 次 | 合并旧摘要 + 新过期消息，输出有界（max 500 chars） | SolonCode |
| `VectorArchive` | 0 次 | 过期消息存入向量存储，提供 recall 工具 | SolonCode |

Delta 定制示例：

```xml
<!-- delta: /delta/default/my-agent.agent.xml -->
<compaction>
  <strategy class="com.mycompany.MyCustomSummaryStrategy"/>
</compaction>
```

### 7.9 与前缀缓存的协同

压缩操作只修改 `messages[prefixLength..]` 的 Log Zone，不触及前缀区。引擎层在 `AgentExecutionContext` 中维护 `prefixLength` 和 `prefixHash`（见 `nop-ai-agent-llm-layer.md` §八），压缩前后校验前缀完整性。

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

## 11a. 拒绝了什么

### 拒绝：所有错误都交给 LLM 自行推理

**方案**：不做错误分类，所有工具调用失败都由 LLM 判断是否重试。

**拒绝理由**：确定性错误（参数错误、权限错误、工具名不存在）不需要 LLM 推理，程序化处理更快更准。混合处理会导致确定性问题被 prompt 化，浪费 token 且结果不稳定。

### 拒绝：统一重试策略（不区分 Provider 级别 vs 工具级别）

**方案**：用一个通用重试策略覆盖 LLM 调用失败和工具调用失败。

**拒绝理由**：LLM 调用失败需要 429 语义分类、Retry-After 解析、流式保护（已流出内容不 failover）、图片 fallback——这些是 Provider 特有逻辑。工具调用失败需要参数修复、schema 验证——逻辑完全不同。强行统一会导致策略既不适合 LLM 也不适合工具。

### 拒绝：压缩时全量重写历史

**方案**：每次触发压缩时，LLM 重写整个消息历史为摘要。

**拒绝理由**：全量重写成本高（需要传入完整历史），增量更新更高效（传入前次 summary + 新消息）。OpenCode 和 Reasonix 的实践证明增量摘要效果足够好。

### 拒绝：固定四级梯度触发

**方案**：采用 Reasonix 的 75%/78%/80%/90% 四级梯度。

**拒绝理由**：四级是为 DeepSeek 1M 超大窗口优化的 Provider 特定设计。Nop 作为通用框架面向 128K-200K 的常见窗口，5 层管道的逐级升级更清晰、更通用。需要更精细控制的用户可通过 `ICompressionStrategy` 扩展点定制。

### 拒绝：Layer 1 就暴露 7 种可配置策略

**方案**：默认实现就提供 7 种可组合策略，通过 `<strategy>` 元素配置管道。

**拒绝理由**：对 Layer 1-2 的实现者来说，7 种策略是认知负担。5 层管道每一层做什么和什么时候触发一目了然，实施者读完就知道要实现什么。可插拔策略作为 Layer 3 扩展点保留，需要时才引入。

### 拒绝：断路器和 Sisyphean 同时启用

**方案**：断路器和 Sisyphean 作为可叠加的策略同时工作。

**拒绝理由**：两者代表对立的弹性哲学——"快速熔断" vs "永不放弃"。同时启用语义矛盾（断路器要终止，Sisyphean 要继续）。设计为互斥配置选项，由部署场景决定。

## 12. 本篇结论

可靠性设计必须保留，但它应该是建立在稳定 Agent runtime 之上的增强层：

- 先让单 Agent 正常跑通（Layer 1）
- 再让它在真实环境中稳定运行（Layer 2 执行扩展）
- 最后再支持复杂恢复和平台级降级（Layer 3-4）

这样实现风险最低，也最符合当前阶段的成熟度。

---

## 与其他文档的关系

- `nop-ai-agent-roadmap.md` — 分层架构（本篇覆盖 Layer 1-3 的可靠性部分）
- `nop-ai-agent-llm-layer.md` — LLM 层设计（IRetryPolicy、ILlmDialect 的详细设计）
