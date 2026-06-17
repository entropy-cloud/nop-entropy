# 192 nop-ai-agent A1 Budgeted Injection — System-Prompt 自动消费

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: A1

> Last Reviewed: 2026-06-15
> Source: Carry-over from plan 189 (`ai-dev/plans/189-nop-ai-agent-working-memory-tools.md`) — `Deferred But Adjudicated` "Memory 自动注入 system prompt" (`Successor Required: yes, Successor Path: A1（Budgeted Injection 功能化消费）独立 successor`) + `Non-Blocking Follow-ups` / `Follow-up` "A1 Budgeted Injection auto-consumption (system-prompt injection) — successor plan"。Roadmap `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §4 row A1（`:145`，描述当前过时——仅列接口/字段工作，未覆盖 functional consumption）。Design owner doc: `01-architecture-baseline.md` §四 line 75（"system-prompt 自动注入 deferred 至 A1（Budgeted Injection 功能化消费）"）+ `nop-ai-agent-session-engine.md` §6.3（Budgeted Injection 算法 + `readBudgeted` 已功能化，但 "session 启动时将 memory 内容拼入 system prompt 前缀" 未落地）。
> Related: Plan 189 (L2-15 Working Memory 工具 + InMemoryAiMemoryStore + IMemoryStoreProvider + dispatch-loop wiring — 消费对象)，L1-16 (IAiMemoryStore 接口 + AiMemoryItem 字段 priority/tokenEstimate/pinned — 已落地，本计划消费)

## Purpose

将 Working Memory 的最后一个预留缺口收口到"Agent 无需显式调用 `read-memory` 工具即可在每轮执行启动时自动获得 budgeted memory 注入 system prompt"状态。

当前 `InMemoryAiMemoryStore.readBudgeted(maxTokens, context)` 已功能化（plan 189 Phase 1：pinned 始终包含 → 非 pinned 按 priority 降序 → 累计 tokenEstimate 至 maxTokens 截断）。`DefaultAgentEngine.memoryStoreProvider`（`:103`，默认 `InMemoryMemoryStoreProvider`）已就绪。但 **`readBudgeted()` 在 main src 中仅被两处消费**：(1) `InMemoryAiMemoryStore` 自身实现；(2) `ReadMemoryExecutor` 的 `action=budgeted`（Agent 显式调用工具）。**dispatch loop / system-prompt 构建路径从不调用 `readBudgeted()`**——`buildBaseExecutionContext`（`DefaultAgentEngine.java:708-725`）只读取 `agentModel.getPrompt().getSource()` 作为 system prompt，不感知 memory store。

结果：Agent 在 turn N 通过 `write-memory` 写入的记忆，在 turn N+1 必须依赖 Agent 自主决定调用 `read-memory` 才能被 LLM 感知。若 Agent 未调用（常见于新 session 或 LLM 未识别到记忆需求），budgeted memory 永远不进入 LLM 的上下文。本计划交付**自动注入**：每轮执行启动时（`doExecute` / `resumeSession` 共用的 `buildBaseExecutionContext`），将当前 session 的 budgeted memory 格式化后追加到 system prompt，无需 Agent 显式调用 `read-memory`。

## Current Baseline

- **`InMemoryAiMemoryStore.readBudgeted` ✅ 功能化（plan 189）**：`InMemoryAiMemoryStore.java:83-114`，算法 = pinned 始终包含（累计 tokenEstimate）→ 非 pinned 按 priority 降序（同 priority 按 createTime 升序）→ 累计至 maxTokens 截断。maxTokens ≤ 0 返回空列表。`synchronized(this)` 保护复合排序迭代
- **`IAiMemoryStore.readBudgeted` default UOE ✅ 保留**：`IAiMemoryStore.java:15-17`，`throw new UnsupportedOperationException("readBudgeted requires Phase 2")`。`InMemoryAiMemoryStore` 覆盖为功能化；其他未来实现可继承 UOE 作为 fail-fast 选择（设计 §6.3 line 109）
- **`IMemoryStoreProvider` + `InMemoryMemoryStoreProvider` ✅（plan 189）**：per-session 隔离，`getOrCreate(sessionId)` 同 sessionId 返回同实例
- **`DefaultAgentEngine.memoryStoreProvider` ✅（plan 189）**：`:103`，`private IMemoryStoreProvider memoryStoreProvider = new InMemoryMemoryStoreProvider();`，`setMemoryStoreProvider` / `getMemoryStoreProvider`（`:478-483`），`resolveExecutor` 透传到 `ReActAgentExecutor.Builder`（`:1179`）
- **System-prompt 构建点 ✅ 已知**：`DefaultAgentEngine.buildBaseExecutionContext(agentModel, session)`（`:708-725`）。读取 `agentModel.getPrompt().getSource()`（`:711-714`），非空时 `ctx.addMessage(new ChatSystemMessage(systemPrompt))`（`:716-718`），随后 replay session 历史（`:720-722`）。**此方法被 `doExecute`（`:641`）与 `resumeSession`（`:764`）共用**——单点注入覆盖两路径（新 turn + 恢复）
- **`readBudgeted` 自动消费 ❌ 零接线**：`rg "readBudgeted" nop-ai/nop-ai-agent/src/main` 仅命中 `InMemoryAiMemoryStore`（实现）+ `ReadMemoryExecutor`（显式工具 action）。`buildBaseExecutionContext` / `doExecute` / `resumeSession` / `ReActAgentExecutor` reactLoop 均不调用 `readBudgeted`
- **`AgentModel` 无 memory 配置字段**：`AgentModel.getPrompt()` 返回 `IPromptSyntaxNode`（`_gen/_AgentModel.java:376`）。无 budget / memory-enable 配置字段。预算配置载体不能走 AgentModel（需 xdef 变更，protected area）→ 本计划采用 engine 级配置
- **Roadmap A1 行 ❌ 过时**：`nop-ai-agent-roadmap.md:145` 描述为 "AiMemoryItem 补充 priority/tokenEstimate/pinned 等字段 + IAiMemoryStore.readBudgeted() default 方法"，状态 `❌`。但字段（L1-16 ✅）+ `readBudgeted` default（L1-16 ✅）+ `InMemoryAiMemoryStore` 功能化实现（plan 189 ✅）均已落地。真正未落地的是 **functional consumption（system-prompt 自动注入）**——roadmap 行需更正
- **设计契约断言 ⏳ deferred**：`01-architecture-baseline.md` §四 line 75（"system-prompt 自动注入 deferred 至 A1"）+ `nop-ai-agent-session-engine.md` §6.3（"session 启动时将 memory 内容拼入 system prompt" 未标注 ✅）。本计划落地后须更新为 ✅
- **审计观察（非阻塞）**：`ai-dev/audits/.../09-error-handling.md:83-99` 建议 `readBudgeted` UOE 消息改为面向调用方表述（"readBudgeted is not supported by this memory store implementation"）。属消息措辞优化，不影响本计划功能契约——`InMemoryAiMemoryStore` 已功能化不触发 UOE；default UOE 仅在其他实现未覆盖时触发。本计划不改 UOE 消息（out-of-scope），但记录此观察

## Goals

- **每轮执行启动时自动注入 budgeted memory**：`doExecute` 与 `resumeSession` 共用的 `buildBaseExecutionContext` 在构建 system prompt 时，经 `memoryStoreProvider.getOrCreate(sessionId)` 解析当前 session 的 store，调用 `readBudgeted(budget, context)`，将返回的非空 memory 条目格式化为 memory 段落追加到 system prompt。Agent 无需显式调用 `read-memory` 工具即可让 LLM 感知 budgeted memory
- **预算可配 + shipped 默认**：注入的 maxTokens 预算在 engine 级可配（shipped 默认值，可通过 engine setter 覆盖），控制注入的 memory 总 token 上限，避免记忆过多撑爆上下文。预算为 0 或负值时禁用注入（向后兼容的显式 opt-out）
- **空 memory 无副作用**：当 store 为空（新 session / 无任何 memory 条目）或 `readBudgeted` 返回空列表时，**不注入任何 system 消息**（不产生空的 memory 段落 system message）。保证现有未使用 memory 的 session 行为完全不变（向后兼容）
- **pinned + priority 语义透传**：注入的 memory 列表直接采用 `readBudgeted` 的排序结果（pinned 始终包含 → 非 pinned 按 priority 降序），本计划不重新排序或过滤
- **null provider 显式跳过**：`memoryStoreProvider` 为 null（仅测试场景；engine shipped 默认非 null）时跳过注入，不抛异常——memory 是增强而非必需

## Non-Goals

- **Per-agent budget 配置（AgentModel 字段 / xdef）**：`AgentModel` 新增 memory 配置字段需要 `agent.xdef` 变更（ORM/模型 protected area，plan-first）。本计划采用 engine 级 budget 配置（setter + shipped 默认）。per-agent budget override 是独立 successor
- **Memory 持久化 / L4-3 IMemoryAdapter**：`InMemoryAiMemoryStore` 数据不随 `AgentSession` 序列化，进程重启后丢失。持久化（DB / 文件 / 向量）是 L4-3 successor（同 plan 189 deferred）。本计划注入的 memory 来自 in-memory store
- **注入内容反馈到 token 计账 / compaction 触发**：注入的 memory 段落是 system prompt 的一部分，经 LLM 调用的 prompt tokens 自然计入（`ReActAgentExecutor` token 计账在 LLM 响应后）。memory 注入增加的 token 会参与 `shouldTriggerCompaction` 判定——这是合法行为（memory 是上下文的一部分）。本计划不特殊处理此交互
- **readBudgeted UOE 消息措辞优化**：审计 09 建议 UOE 消息改为面向调用方表述。本计划不改 `IAiMemoryStore.java:15-17` 的 default UOE 消息（`InMemoryAiMemoryStore` 已功能化覆盖，default UOE 仅其他实现触发）。措辞优化是独立 advisory 改进
- **memory 段落本地化 / 多语言**：memory 段落的格式（标题、分隔符）采用固定英文格式（如 `## Working Memory`），不做 i18n。格式细节是实现选择，plan 只约束"LLM 可读、可辨识为 memory 段落"
- **注入频率 / 去重 / 变更检测**：每轮 `buildBaseExecutionContext` 都重新注入当前 budgeted memory（反映上一轮的 write）。不缓存注入结果、不做 checksum 变更检测（`AiMemoryItem.checksum` 字段已就绪，但去重/变更检测是 L4-3 optimization）
- **Memory 注入到 system prompt 之外的载体**（如独立 user message / tool result）：本计划只注入到 system prompt（设计 §四 line 75 + §6.3 明确"拼入 system prompt 前缀"）。其他载体是独立设计变更

## Scope

### In Scope

- `DefaultAgentEngine.buildBaseExecutionContext` 新增 budgeted memory 注入逻辑（解析 store → readBudgeted → 格式化 → 追加到 system prompt）
- engine 级 budget 配置（字段 + setter + shipped 默认值；预算 ≤ 0 禁用注入）
- 注入语义：空 memory / 空 readBudgeted 结果 → 不注入（向后兼容）；null provider → 跳过
- 单元测试：注入内容正确性 + 预算截断 + pinned 优先 + 空 memory 不注入 + budget ≤ 0 禁用 + null provider 跳过 + budget 可配
- 端到端测试：Agent 经 `write-memory` 工具写入记忆 → 下一轮 `execute` 的 system prompt 含 memory 段落（**无需 Agent 调用 read-memory**）→ LLM 收到的 messages 含 memory 内容
- 端到端测试：`resumeSession` 路径同样注入 budgeted memory
- 向后兼容测试：无 memory 的 session（空 store）行为不变（system prompt 不含 memory 段落、无多余 system 消息）
- 设计文档更新：`01-architecture-baseline.md` §四 line 75（system-prompt 自动注入 ✅）+ `nop-ai-agent-session-engine.md` §6.3（自动注入标注 ✅）
- roadmap A1 行更正 + 状态 ✅

### Out Of Scope

- Per-agent budget 配置（AgentModel / xdef 变更）
- L4-3 IMemoryAdapter（DB / Vector / Embedding 后端 + memory 持久化）
- readBudgeted UOE 消息措辞优化
- memory 段落 i18n
- 注入缓存 / checksum 变更检测 / 去重
- 注入到 system prompt 之外的载体

## Execution Plan

### Phase 1 - Budgeted Injection 核心：engine 注入逻辑 + 预算配置 + 单元测试

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/DefaultAgentEngine.java`（`buildBaseExecutionContext` `:708-725` + 新增 budget 字段 + setter/getter）

- Item Types: `Decision | Proof`

- [x] **Decision（注入点 = `buildBaseExecutionContext`，doExecute + resumeSession 共用）**：budgeted memory 在 `DefaultAgentEngine.buildBaseExecutionContext`（`:708`）中注入，而非 `ReActAgentExecutor` reactLoop。理由：(1) 此方法是 system prompt 的唯一构建点（`doExecute:641` + `resumeSession:764` 共用），单点注入覆盖新 turn + 恢复两路径；(2) memory 是 session 级持久上下文（同 system prompt 定位），不是 per-LLM-turn 临时数据——在 reactLoop 每次 LLM 调用前注入会重复注入同 session 内相同 memory；(3) `buildBaseExecutionContext` 已持有 `session`（可取 sessionId）且 engine 持有 `memoryStoreProvider`（`:103`），无需新增参数透传链。注入发生在 system prompt 构建之后、session 历史 replay（`:720-722`）之前——memory 段落作为 system prompt 的尾部 section
- [x] **Decision（注入语义 = 追加 memory 段落到 system prompt 文本，单条 system message）**：将格式化后的 memory 条目作为一段结构化文本（如 `## Working Memory` 标题 + 每条 item 的 key/type/content 摘要）追加到 `agentModel.getPrompt().getSource()` 之后，合并为单条 `ChatSystemMessage`。理由：(1) 保持单条 system message（部分 LLM dialect 对多条 system message 支持不一致）；(2) base prompt 在前（保持 prefix 缓存友好——base prompt 是稳定前缀，memory 是可变后缀），memory 段落在后；(3) 无 base prompt 但有 memory 时，memory 段落单独构成 system message（仍只注入当 memory 非空）。具体段落标题/分隔符格式是实现选择，plan 只约束"LLM 可读、可辨识为 memory section、base prompt + memory 在同一条 system message"
- [x] **Decision（空 memory 不注入——向后兼容硬约束）**：仅当 `readBudgeted` 返回**非空列表**时注入。空 store / 空结果 / budget ≤ 0 / null provider 时 `buildBaseExecutionContext` 的 system prompt 行为与当前完全一致（无 memory 段落、无多余 system 消息）。理由：(1) 新 session 或未使用 memory 的 session 占多数——注入空段落会污染所有现有 session 的上下文，破坏向后兼容；(2) 空注入会破坏 prefix 缓存（每轮都多一条空 system message）；(3) 这是 honest 行为——无 memory 时不假装有 memory 段落
- [x] **Decision（预算配置 = engine 级，shipped 默认 + setter，预算 ≤ 0 禁用）**：budgeted injection 的 maxTokens 在 engine 级配置（新增字段 + setter，同 `memoryStoreProvider` / `contextCompactor` 等 engine 级组件配置模式）。shipped 默认值（具体数值在实现时裁定，需在合理范围——既给 memory 足够空间又不主导上下文）。预算 ≤ 0（含 0 和负值）= 显式 opt-out 禁用注入（向后兼容的 escape hatch，测试可注入 0 禁用）。理由：(1) `AgentModel` 无 memory 配置字段且 xdef 变更属 protected area——engine 级配置避免触碰模型层；(2) setter 模式与 engine 现有 20+ 组件配置一致；(3) per-agent budget override 是独立 successor（需 xdef/AgentModel 变更）。`readBudgeted` 的 `context` Map 参数：本计划传入描述性 context（如 sessionId 用于可观测性），不依赖 context 控制预算（预算由 maxTokens 参数控制）
- [x] **Decision（memory 段落格式——可观测 + 可辨识）**：注入的 memory 段落必须满足：(1) 有可辨识的 section 标识（让 LLM 和人类审查能区分"这是注入的 memory"vs"agent 原始 system prompt"）；(2) 每条 item 至少含 content（核心信息），可选含 key/type/priority 用于上下文；(3) tokenEstimate 已由 `readBudgeted` 控制总量，段落内不再二次截断。具体 key/type 是否展示、是否含 priority 标注是实现选择，plan 只约束"content 必含 + section 可辨识 + 总量受 budget 控制"
- [x] 在 `buildBaseExecutionContext` 中实现 budgeted memory 注入：当 `memoryStoreProvider != null` 且 `budget > 0` 时，`memoryStoreProvider.getOrCreate(session.getSessionId())` → `store.readBudgeted(budget, context)` → 若结果非空，格式化为 memory 段落并追加到 system prompt（base prompt 之后），构造单条 `ChatSystemMessage`。无 base prompt 时 memory 段落单独成 system message（仅 memory 非空时）
- [x] engine 新增 budget 字段 + setter/getter（shipped 默认 > 0；setter 允许调用方注入 0/负值禁用）
- [x] 单元测试（注入内容正确性）：构造 engine 注入预填充的 `InMemoryMemoryStoreProvider`（某 sessionId 的 store 含若干 AiMemoryItem）→ 调用 `execute`/`buildBaseExecutionContext` 等价路径 → 断言 ctx 的第一条消息为 `ChatSystemMessage`，其文本同时含 base prompt（若有）+ memory 段落标识 + 各 item 的 content
- [x] 单元测试（预算截断）：store 含多条高 tokenEstimate item（总 token > budget）→ 注入的 memory 段落仅含 budget 内的 item（pinned 始终在 + 非 pinned 按 priority 降序至 budget 截断）→ 断言注入 item 集合与 `readBudgeted(budget)` 直接返回的一致（透传 `readBudgeted` 排序，不重新过滤）
- [x] 单元测试（pinned 优先）：store 含 pinned item（高 token）+ 多条高 priority 非 pinned item → pinned item 始终出现在注入段落中（即使总 token 接近 budget）
- [x] 单元测试（空 memory 不注入——向后兼容）：store 为空（无 item）→ 注入结果为空 → system prompt 仅含 base prompt（若有），**无 memory 段落、无多余 system 消息**。断言 messages 列表与不启用 budgeted injection 时完全一致
- [x] 单元测试（budget ≤ 0 禁用注入）：engine budget 设为 0 / 负值 → 即使 store 含 memory 也不注入 → system prompt 仅含 base prompt。这是显式 opt-out
- [x] 单元测试（null provider 跳过）：engine `memoryStoreProvider` 设为 null → 不抛异常、不注入、system prompt 仅含 base prompt
- [x] 单元测试（budget 可配）：engine budget 设为不同值（小/大）→ 注入的 memory item 数量随 budget 变化（小 budget 少 item，大 budget 多 item）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `buildBaseExecutionContext` 在 memory 非空 + budget > 0 + provider 非 null 时，将 budgeted memory 追加到 system prompt（单条 `ChatSystemMessage`，base prompt 在前 memory 段落在后）
- [x] 注入的 memory item 集合 = `store.readBudgeted(budget, context)` 直接返回的集合（透传排序，不重新过滤/排序）
- [x] 空 store / 空 readBudgeted 结果时**不注入**（无 memory 段落、无多余 system 消息）——向后兼容
- [x] budget ≤ 0 时禁用注入（显式 opt-out）；null provider 时跳过注入不抛异常
- [x] engine budget 字段 + setter/getter 存在，shipped 默认 > 0
- [x] **新增功能测试**（Minimum Rules #25）：注入内容正确性 + 预算截断 + pinned 优先 + 空 memory 不注入 + budget ≤ 0 禁用 + null provider 跳过 + budget 可配——各有对应通过的测试
- [x] **接线验证**（Minimum Rules #23）：`buildBaseExecutionContext` 确实在运行时调用 `memoryStoreProvider.getOrCreate(sessionId)` + `store.readBudgeted(...)`（通过注入功能化 provider + 断言 system message 含 memory content 验证，非仅方法被调用）
- [x] **无静默跳过**（Minimum Rules #24）：空 memory 不注入是设计行为（无 memory 可注入），非静默跳过——有明确"非空才注入"语义；null provider 跳过不抛异常是测试场景 opt-out（engine shipped 默认非 null），非吞异常
- [x] No owner-doc update required（设计文档更新在 Phase 2）
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` Phase 1 新增测试通过
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 端到端验证 + resume 路径 + 向后兼容 + 设计文档 + roadmap

Status: completed
Targets: `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/engine/`（或 `memory/`——端到端测试）; `ai-dev/design/nop-ai-agent/01-architecture-baseline.md`（§四 line 75）; `ai-dev/design/nop-ai-agent/nop-ai-agent-session-engine.md`（§6.3）; `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md`（§4 A1 row `:145`）

- Item Types: `Proof | Follow-up`

- [x] 端到端测试（**write 后自动注入——Anti-Hollow**）：构造 `DefaultAgentEngine`（功能化 chatService mock + `InMemoryMemoryStoreProvider`）→ 第一轮 `execute` 触发 Agent 调用 `write-memory` 工具写入一条记忆（如 "User prefers concise answers"）→ 第一轮完成 → 第二轮 `execute`（同 sessionId）→ **断言第二轮的 system prompt（ctx 第一条 ChatSystemMessage）含该 memory content**，且第二轮的 Agent **未调用 read-memory 工具**（证明自动注入，非显式读取）。证明从 write-memory → store → readBudgeted → buildBaseExecutionContext → system prompt 的完整路径连通
- [x] 端到端测试（**resumeSession 路径注入**）：session 写入 memory 后 pause → `resumeSession` → 断言 resume 重建的 ctx system prompt 含 budgeted memory（`buildBaseExecutionContext` 经 `:764` 在 resume 路径同样注入）
- [x] 端到端测试（**向后兼容——无 memory 行为不变**）：构造 engine（budget > 0 默认）但 session 从不写 memory → 多轮 execute → 断言每轮 system prompt 仅含 base prompt，**无 memory 段落、无多余 system 消息**、现有所有无 memory 测试通过（无回归）
- [x] 端到端测试（**多 memory item + budget 截断端到端**）：session 写入多条 memory（含 pinned + 高/低 priority）→ 下一轮 system prompt 的 memory 段落含 pinned + 高 priority item（受 budget 截断）→ 断言段落内容与 `readBudgeted(budget)` 一致
- [x] 更新 `01-architecture-baseline.md` §四 line 75（Working Memory 行）：将 "system-prompt 自动注入 deferred 至 A1（Budgeted Injection 功能化消费）" / "⏳ system-prompt 自动注入 (A1 successor)" 更新为 ✅ 已落地（plan 192）。记录架构决策：注入点 = `buildBaseExecutionContext`（engine 级，doExecute + resumeSession 共用）、单条 system message（base prompt + memory 段落）、空 memory 不注入（向后兼容）、budget engine 级配置（per-agent budget = deferred successor）
- [x] 更新 `nop-ai-agent-session-engine.md` §6.3：标注 budgeted injection 的 system-prompt 自动消费已落地（plan 192）；记录注入语义（追加到 system prompt、空 memory 不注入、budget engine 配置）。readBudgeted 算法描述（line 114-123）维持（算法未变，仅新增消费点）
- [x] 更新 `nop-ai-agent-roadmap.md` §4 A1 行（`:145`）：更正描述（原描述仅列接口/字段工作——已由 L1-16 ✅ 完成；补充 functional consumption = system-prompt 自动注入，plan 192 落地）；状态 `❌` → `✅`

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] **端到端验证**（Minimum Rules #22 Anti-Hollow）：Agent 经 `write-memory` 写入记忆 → 下一轮 system prompt 含 memory content **且未调用 read-memory**——从 write-memory → store → readBudgeted → buildBaseExecutionContext → system prompt 完整路径已验证
- [x] **端到端验证**（resume 路径）：`resumeSession` 重建的 system prompt 同样含 budgeted memory（`buildBaseExecutionContext` 两路径共用）
- [x] **端到端验证**（向后兼容）：无 memory 的 session 行为完全不变（system prompt 无 memory 段落、无多余 system 消息、现有测试无回归）
- [x] **接线验证**（Minimum Rules #23）：budgeted memory 经 `memoryStoreProvider.getOrCreate` → `store.readBudgeted` → 追加到 system prompt → LLM 收到的 messages 含 memory（端到端测试断言 system message content，非仅方法被调用）
- [x] **Anti-Hollow Check**：端到端测试证明 write-memory → store → readBudgeted → buildBaseExecutionContext → ChatSystemMessage 完整调用链连通（不只是 readBudgeted 存在）
- [x] **新增功能测试**：write 后自动注入 + resume 路径 + 向后兼容 + 多 item budget 截断——各有对应通过的测试
- [x] `01-architecture-baseline.md` §四 line 75 已更新（system-prompt 自动注入 ✅ + 架构决策记录）
- [x] `nop-ai-agent-session-engine.md` §6.3 已更新（自动消费 ✅ + 注入语义记录）
- [x] `nop-ai-agent-roadmap.md` §4 A1 行已更正（描述 + 状态 ✅）
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` 全部通过（含新增 + 现有测试无回归）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见 plan guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [x] 每轮执行启动时（`doExecute` + `resumeSession` 共用的 `buildBaseExecutionContext`）自动注入 budgeted memory 到 system prompt
- [x] 注入无需 Agent 显式调用 `read-memory`（write-memory 后下一轮自动可见）
- [x] 空 memory / budget ≤ 0 / null provider 时不注入（向后兼容，无副作用）
- [x] 注入的 memory 集合 = `readBudgeted(budget)` 返回值（透传 pinned/priority 排序）
- [x] engine budget 可配（shipped 默认 + setter + ≤ 0 opt-out）
- [x] 不存在被静默降级到 deferred 的 in-scope live defect 或 contract drift
- [x] 受影响 owner docs（architecture-baseline §四 line 75、session-engine §6.3、roadmap A1）已同步到 live baseline
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证 (a) `buildBaseExecutionContext` → `memoryStoreProvider.getOrCreate` → `store.readBudgeted` → system prompt 追加的调用链在运行时连通（经端到端测试断言 system message content），(b) 无空方法体/静默跳过（空 memory 不注入是有条件的显式行为，非吞逻辑）
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am`
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C`
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/192-nop-ai-agent-budgeted-injection-system-prompt.md --strict` 退出码为 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-agent --severity high` 退出码为 0
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### Per-agent budget 配置（AgentModel 字段 / xdef）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: `AgentModel` 新增 memory budget 字段需要 `agent.xdef` 变更（ORM/模型结构 protected area，plan-first）。本计划采用 engine 级 budget 配置（setter + shipped 默认），功能完整——per-session budget 差异化（不同 agent 不同预算）是配置灵活性增强，不影响 budgeted injection 的功能契约成立
- Successor Required: yes
- Successor Path: 独立 enhancement plan（AgentModel / xdef 加 memory budget 字段 + engine 读取 per-agent budget）

### readBudgeted UOE 消息措辞优化

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 审计 09（`09-error-handling.md:83-99`）建议 `IAiMemoryStore.java:15-17` 的 default UOE 消息改为面向调用方表述。`InMemoryAiMemoryStore` 已功能化覆盖（不触发 UOE）；default UOE 仅在其他未来实现未覆盖 `readBudgeted` 时触发。本计划不改 UOE 消息——措辞优化是 advisory 改进，不影响 budgeted injection 功能
- Successor Required: no

### Memory 持久化 / L4-3 IMemoryAdapter

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 同 plan 189 deferred。`InMemoryAiMemoryStore` 数据不随 `AgentSession` 序列化，进程重启后丢失。本计划注入的 memory 来自 in-memory store——单进程内每轮注入完全可用；持久化是生产部署增强（DB / 文件 / 向量后端）
- Successor Required: yes
- Successor Path: L4-3 独立 plan（deps L2-15 ✅ 后可规划）

### 注入缓存 / checksum 变更检测 / 去重

- Classification: `optimization candidate`
- Why Not Blocking Closure: 每轮 `buildBaseExecutionContext` 都重新 `readBudgeted`（反映上一轮 write）。`AiMemoryItem.checksum` 字段已就绪，可用于变更检测（跳过未变化的注入以保护 prefix 缓存）。但当前 `readBudgeted` 是 in-memory 排序（廉价），重新注入开销可忽略。缓存/去重是 prefix-cache 优化，非功能契约
- Successor Required: no

## Non-Blocking Follow-ups

- Per-agent budget 配置（AgentModel / xdef 字段 + engine 读取）
- readBudgeted UOE 消息措辞优化（面向调用方表述）
- L4-3 IMemoryAdapter（DB / Vector / Embedding 后端 + memory 持久化）
- 注入缓存 / checksum 变更检测 / 去重（prefix-cache 优化）
- memory 段落 i18n（当前固定英文格式）

## Closure

Status Note: Plan 192 delivered the final piece of working-memory integration — automatic budgeted memory injection into the system prompt. `DefaultAgentEngine.buildBaseExecutionContext` (shared by `doExecute` + `resumeSession`) now resolves the per-session store via `memoryStoreProvider.getOrCreate(sessionId)`, calls `store.readBudgeted(budget, context)`, and appends a non-empty budgeted memory section to the system prompt as a single `ChatSystemMessage` (base prompt before, `## Working Memory` section after). Empty memory / budget ≤ 0 / null provider → no injection (backward compatible). Budget is engine-level configurable (default 1024, ≤ 0 opt-out). Both Phase 1 (unit tests) and Phase 2 (end-to-end + design docs + roadmap) are complete. All 1518 tests pass (1514 original + 4 new e2e, no regression). All deferred items are out-of-scope improvements with explicit non-blocking rationale.
Completed: 2026-06-15

Closure Audit Evidence:

- Reviewer / Agent: Independent explore subagent (task ses_136009ff7ffexJE1MAG4gb6mzu)
- Audit Session: ses_136009ff7ffexJE1MAG4gb6mzu
- Evidence:
  - Exit Criterion P1 (injection in buildBaseExecutionContext): PASS — `DefaultAgentEngine.java:782` calls `buildBudgetedMemorySection`; lines 784-792 append to systemPrompt as single ChatSystemMessage
  - Exit Criterion P1 (readBudgeted passthrough): PASS — lines 820-821 call `getOrCreate` → `readBudgeted`; no re-sort/filter
  - Exit Criterion P1 (empty/no injection): PASS — lines 811-813 (null/budget≤0), 827-829 (empty result) return null; documented conditionals
  - Exit Criterion P1 (budget config): PASS — field line 110 (default 1024), setter/getter lines 537-543
  - Exit Criterion P1 (new tests): PASS — 12 unit tests in `TestBudgetedMemoryInjection.java`
  - Exit Criterion P2 (write-then-auto-inject e2e): PASS — `TestBudgetedMemoryInjectionEndToEnd.writeMemoryThenAutoInjectedInNextTurn` asserts turn-2 system prompt contains memory without read-memory
  - Exit Criterion P2 (resume e2e): PASS — `resumeSessionInjectsBudgetedMemory` asserts resume system prompt contains memory
  - Exit Criterion P2 (backward compat e2e): PASS — `noMemorySessionUnchangedBehavior` asserts system prompt = base prompt
  - Exit Criterion P2 (multi-item e2e): PASS — `multiItemBudgetTruncationEndToEnd` asserts pinned+high injected, low truncated
  - Exit Criterion P2 (design docs): PASS — all 3 docs updated (architecture-baseline:75, session-engine:113, roadmap:145)
  - `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C`: 1518 tests, 0 failures, 0 errors
  - `node ai-dev/tools/check-plan-checklist.mjs --strict` exit code: 0
  - Anti-Hollow check: PASS — full call chain `buildBaseExecutionContext` → `buildBudgetedMemorySection` → `getOrCreate` → `readBudgeted` → `formatMemorySection` → ChatSystemMessage verified wired at runtime via e2e tests; no stubs/empty bodies
  - `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-agent --severity high` exit code: 0 (0 findings)
  - Deferred items classification check: all 4 deferred items are `out-of-scope improvement` or `optimization candidate` with explicit non-blocking rationale; no in-scope live defect downgraded

Follow-up:

- Per-agent budget 配置（AgentModel / xdef 字段 — successor plan required）
- readBudgeted UOE 消息措辞优化（advisory, no successor）
- L4-3 IMemoryAdapter（DB/Vector/Embedding backend — successor plan required）
- 注入缓存 / checksum 变更检测 / 去重（prefix-cache optimization, no successor）
