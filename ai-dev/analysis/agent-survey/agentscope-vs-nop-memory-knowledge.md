# AgentScope vs Nop AI Agent: Memory / Knowledge 对比

## 1. Memory 架构

### AgentScope Java

| 维度 | 详情 |
|---|------|
| **设计哲学** | v2 架构**已删除** v1 的 `Memory`/`LongTermMemory` 接口（deprecated for removal）。上下文是简单的 `List<Msg>` on `AgentState.context`。长期记忆是**文件系统上的 markdown 文件**。 |
| **短期记忆** | `AgentState.context` — 内存 `ArrayList<Msg>`, scoped to (userId, sessionId)。另有 `summary: String` 字段用于滚动摘要。 |
| **长期记忆** | **文件-based 两层模型**：<br>1. **Daily ledgers** (`memory/YYYY-MM-DD.md`) — append-only 日志。由 `MemoryFlushManager` 在每轮 agent 调用后通过 LLM 提取写入。<br>2. **MEMORY.md** — 跨 session 的事实源头。由 `MemoryConsolidator` 定期读取 daily ledgers, 通过 LLM 去重合并后覆写。 |
| **存储位置** | Agent workspace 文件系统。MEMORY.md 在根目录, daily ledgers 在 memory/, archive/ 下。Session JSONL logs 在 `agents/<agentId>/sessions/<sessionId>.log.jsonl`。 |
| **RAG** | v2 已废弃 (`Knowledge`/`KnowledgeRetrievalTools` 已移除)。存活的只有 markdown 文件的 keyword search (MemorySearchTool)。 |

### Nop AI Agent

| 维度 | 详情 |
|---|------|
| **设计哲学** | **双层分离**：对话消息 (`AgentSession`) 与工作记忆 (`IAiMemoryStore`) 完全分开。工作记忆是有结构的 per-session key-value store。 |
| **短期记忆** | `AgentSession` 持有 `List<ChatMessage>` + token/iteration 计数 + status + metadata。通过 `ISessionStore` 持久化。 |
| **工作记忆** | `IAiMemoryStore` 接口：`getAll`/`getLastN`/`search`/`add`/`update`/`remove`/`batchAdd`/`readBudgeted`。Item 是 `AiMemoryItem` 有：key/type/content/priority/pinned/tokenEstimate/checksum/createTime/lastAccessTime/accessCount。**Per-session 隔离**。 |
| **长期记忆** | 两个实现：<br>1. `InMemoryAiMemoryStore` (ConcurrentHashMap, 不持久化)<br>2. `AdapterBackedAiMemoryStore` (opt-in, 代理到三个可插拔 adapter: IStorageAdapter + IEmbeddingAdapter + IVectorAdapter) |
| **RAG** | **内建在工作记忆中**：`AdapterBackedAiMemoryStore.search()` 支持**双路径检索**：semantic search (embedding top-k) + keyword fallback。另有独立的 `NopAiKnowledge` ORM 实体。 |

---

## 2. 记忆写入（Ingestion）

### AgentScope

**两条并行路径**：

**A. 自动提取（背景）** — LLM-driven
- `MemoryFlushMiddleware` 在每轮 agent 调用后触发。
- 将当前对话 (跳过 SYSTEM 和 session-context 消息) 序列化，读取已有 MEMORY.md 和今日 daily ledger 作为去重上下文。
- 调 LLM 用**专用提取 prompt**，产出 markdown bullet list。
- 追加到今日 daily ledger；同时保存 raw messages 到 session JSONL。

**B. 工具触发 — 显式调用**
- `MemorySaveTool` (`memory_save`) — agent 可显式调用此工具持久化事实。
- 同时写入 MEMORY.md 和今日 daily ledger。

**C. Consolidation（定期合并）**
- `MemoryMaintenanceMiddleware` 每 30 分钟触发。
- 读取从上次 watermark 以来修改过的 daily ledgers + 当前 MEMORY.md。
- 调 LLM 合并去重 → 覆写 MEMORY.md → 更新 watermark。

**D. 存档生命周期**
- Daily files 超过 `dailyFileRetentionDays` (default 90) 移至 archive/。
- Session JSONL 超过 `sessionRetentionDays` (default 180) 删除。

### Nop

**单条路径（Agent 主动写入）**：

- `WriteMemoryExecutor` (`write-memory` tool)：agent 调用 `action=add` (default)/`update`/`remove`/`clear`。
- 存储到 per-session `IAiMemoryStore`。
- 使用 `AdapterBackedAiMemoryStore` 且有 embedding 时，每次 add 同时构建向量索引。

**没有自动提取**。Nop 认为记忆是 agent 控制的能力，不是框架管理的 pipeline。

---

## 3. 记忆读取（Retrieval）

### AgentScope

**A. 工具读取**
- `MemorySearchTool`：在 memory 目录的 markdown 文件上做 keyword search (regex line matching)。
- `MemoryGetTool`：读取指定 memory 文件全文。

**B. 无自动上下文注入**
- 框架不自动将长期记忆注入 LLM 调用。记忆按需通过工具访问。

### Nop

**A. 工具读取**
- `ReadMemoryExecutor` (`read-memory`)：四种模式 — `list` (getAll)/`last` (getLastN)/`budgeted` (readBudgeted 按 token 预算)/`key` (单键查找)。
- `SearchMemoryExecutor` (`search-memory`)：semantic + keyword search。

**B. Budgeted 上下文注入（future）**
- `readBudgeted(maxTokens, context)` 是专门设计用于按 token 预算注入 LLM 上下文的。
- 算法：pinned items 优先 → 按 priority 排序 → 填满 maxTokens。

**C. Semantic search 路径**
- `AdapterBackedAiMemoryStore.search()`：如果 `IEmbeddingAdapter.isAvailable()` → embed query → `IVectorAdapter.search()` (topK=10) → 从 `IStorageAdapter.loadByKey()` 解析 items。否则退化为 keyword substring match。

---

## 4. 上下文窗口 / 记忆大小管理

### AgentScope

**CompactionPipeline** (`CompactionMiddleware`)：
- `onReasoning` 触发。
- Token 数超过 `triggerTokens` 时：
  1. Memory flush: 提取长期记忆
  2. Offload: 完整对话 → session JSONL
  3. Summarize: `ConversationCompactor` 调 LLM 将前缀摘要为一条结构化消息
  4. Replace: `AgentState.context` → `[summaryMsg] + preservedTail`
- `keepTokens` 保留尾部消息。
- 专用 compaction LLM prompt。

### Nop

**CompactionPipeline** (`PipelineCompactor`)：
- ReAct loop 内 `shouldTriggerCompaction(ctx)` 触发。
- Token 估算 >= `maxContextTokens * triggerTokenPercent` (default 0.8) **或** 消息数 >= `triggerMaxMessages` (default 30)。
- **多层压缩**：`PipelineCompactor` 按 escalation 顺序执行 `ICompressionStrategy` 实例。每层后重新估算 token，低于阈值则停止。
- 三个策略：`MicroCompressionCompactor` (L1 tool-result 截断)、LLM summary (L2)、aggressive truncation (L3)。

---

## 5. 关键差异

| 维度 | AgentScope | Nop |
|------|-----------|-----|
| **记忆模型** | 无结构 markdown + raw Msg list | 结构化的 AiMemoryItem 带 type/priority/pinning/tokenEstimate |
| **写入范式** | **框架管理**：自动 LLM 提取 + 合并 | **Agent 控制**：agent 显式 call `write-memory` |
| **持久化** | 文件-based (markdown in workspace) + JSONL logs | Adapter 三元组 (storage + embedding + vector) 可插拔 |
| **搜索** | Keyword-only (regex on markdown) | Dual: semantic (embedding + vector) + keyword fallback |
| **Consolidation** | 内建：定期 LLM merge daily ledgers → MEMORY.md | 无（deferred） |
| **Session 状态** | AgentState.context + summary | AgentSession.messages + 独立 IAiMemoryStore |
| **RAG** | 独立 legacy Knowledge 接口 (v2 deprecated) | 内建在工作记忆 search 中；独立 NopAiKnowledge entity |
| **保留生命周期** | 内建：daily file archival (90d), session log pruning (180d) | 无（deferred） |
| **上下文压缩** | 单体 CompactionMiddleware (LLM summary + tail) | 多层 PipelineCompactor (escalation 顺序) |

---

## 6. 各自独特之处

### AgentScope

1. **记忆 as markdown**：长期记忆是 agent workspace 中人类可读的 markdown 文件。版本可控、LLM 直接读写、格式熟悉。
2. **Two-layer + LLM consolidation**：flush → consolidate 模式。提取 LLM 看到已有 MEMORY.md + 今日 ledger 以避免重复。Consolidation 30 分钟 throttle + watermark。
3. **Dedicated model for memory**：`MemoryConfig.model()` 允许为 memory 操作配置更便宜的模型，独立于主推理模型。
4. **Message offloading to JSONL**：Session JSONL tree 用稳定 Msg ID 等幂追加，支持跨机器交接。

### Nop

1. **结构化的 AiMemoryItem + Budgeted selection**：priority/pinning/tokenEstimate/accessCount/checksum。`readBudgeted` 算法 (pinned-first → priority-desc within budget) 专为 LLM context 注入设计。
2. **Adapter 三元组架构**：IStorageAdapter + IEmbeddingAdapter + IVectorAdapter。每个独立可插拔 + capability-queried (isAvailable())。`AdapterBackedAiMemoryStore` 优雅降级：没有 embedding 就退化 keyword search。
3. **没有自动提取**：刻意选择——memory 是 agent-controlled。代码明确备注 "Phase 1 of plan 189 is just the store CRUD; automatic injection is deferred." AgentScope 的自动提取是相反的哲学选择。
4. **Separation of concerns**：对话历史 (AgentSession) 与工作记忆 (IAiMemoryStore) 完全分离。Agent 可以有长时间对话的同时维护结构化、可搜索的记忆。
5. **Token estimation**：`AiMemoryItem.getTokenEstimate()` 默认 `content.length() / 4`。结合 `readBudgeted` 可以在 token 预算内选择记忆注入 prompt。
