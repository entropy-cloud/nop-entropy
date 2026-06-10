# Agent Memory / Compaction / Session 深度对比分析

> Status: open
> Date: 2026-06-06
> Scope: 10 个 Agent 框架的 memory 管理、context 压缩、session 状态管理机制的代码级深度对比
> Conclusion: (pending)

## 1. Context / Background

前期 19 篇分析文档覆盖了 12+ 个框架的广度调研，但在 memory/compaction/session 三个维度上**只有 3 个框架有算法级细节**（Reasonix, PilotDeck, DeepAgents），其余 10 个框架缺乏具体机制描述。

本文通过**源码级深度调研**补充缺失内容，回答：

1. 每个框架的 compaction 触发条件、算法、保留策略分别是什么？
2. Session 状态如何持久化和恢复？
3. Memory 管理有哪些独特机制？
4. 对 Nop Agent 设计的具体建议是什么？

**调研方法**：直接阅读各框架源码，追踪 compaction trigger → algorithm → result 的完整数据流。

---

## 2. Compaction 策略对比

### 2.1 触发机制全景

| 框架 | 触发方式 | 触发阈值 | Token 计数方式 |
|------|---------|---------|---------------|
| **Reasonix** | 多层触发 | Turn-start >90%、Post-response >75%、Post-response >78%(aggressive)、Force >80%、手动 /compact | 自研 DeepSeek V4 BPE tokenizer (精确), 带 8192-entry LRU 缓存 |
| **PilotDeck** | 每轮预算检查 | 80% warning、95% blocking | js-tiktoken o200k_base (精确), 单例懒加载 |
| **DeepAgents** | 中间件 before_model | 默认 85% context window (或 170K tokens) | count_tokens_approximately (估算) |
| **OpenCode** | 后置溢出检测 + Provider 错误 | tokens.total >= usable(context - reserved(20K)) | Provider 报告 + char/4 估算 |
| **pi-agent** | 前置阈值 + 溢出恢复 | contextTokens > contextWindow - 16384 | API usage (精确) + char/4 估算 (混合) |
| **oh-my-pi** | 同 pi-agent (继承) | 同 pi-agent | Rust tiktoken-rs BPE (精确), o200k_base + cl100k_base |
| **oh-my-opencode** | 抢先式 | 主动窗口管理，不等触发 | 继承 OpenCode |
| **oh-my-claudecode** | Host 管理 + PreCompact hook | Claude Code 自身压缩 | N/A (宿主管理) |
| **Hermes** | 阈值检测 [^2] | prompt_tokens >= 50% context [^2] | Provider 报告 |
| **VoltAgent** | Agent 级 summarize | 170K tokens (默认) | AI SDK 管理 (估算) |
| **AgentScope Java** | maxIters 边界 | 迭代次数 >= 10 (默认) | 不基于 token |

**关键发现**：只有 5 个框架使用精确 token 计数（Reasonix, PilotDeck, oh-my-pi, pi-agent 混合, OpenCode 部分精确）。其余依赖 char/4 估算或完全依赖 Provider 报告。

> **补充调研（2026-06-10）**：对 Codex、Claude Code、OpenCode、SolonCode、Reasonix 五个框架的 token 估算方式进行了更深入的源码级追踪，发现：
> - Codex 使用 `ceil(bytes/4)` + API `response.completed` usage 混合，图片固定 ~7373 字节估算
> - Claude Code 使用 API `usage` 精确值 + `chars/4` 回退，图片固定 1334 tokens
> - OpenCode 纯 `chars/4`，无任何精确计数
> - SolonCode 使用 jtokkit `o200k_base` 编码（对 DeepSeek 偏差 <5%），逐消息计数 + metadata 缓存
> - Reasonix 自研完整 V4 BPE tokenizer + bounded 估算优化（头尾采样外推）+ 双层 LRU 缓存
>
> 详见 `ai-dev/analysis/agent-survey/2026-06-10-token-estimation-and-context-compression-survey.md` §1。

### 2.2 压缩算法分类

#### Tier A: 多级渐进压缩（最精细）

**PilotDeck** — 3-tier progressive compression（业界最精细）：

```
触发 → Tier 1: MicroCompaction (零成本)
       替换旧 tool_result >1536 bytes 为 "[Old tool result content cleared]"
       仅处理 COMPACTABLE_TOOL_NAMES 中的工具 (read_file, bash, grep, glob 等)
       保留最新 1 条 eligible message
       → 检查: ok? 返回 compacted("micro")

     → Tier 2: SnipEngine (无 LLM 调用)
       裁剪中间 turn，保留 head(2) + tail(4) turn
       工具对完整性: 跨边界 tool_call/tool_result 互删
       插入 <snip-boundary> 标记
       → 检查: ok? 返回 compacted("snip")

     → Tier 3: CompactionEngine (LLM 摘要)
       保留尾部 35% 消息
       LLM 摘要头部 (专用 summarizer prompt)
       插入 <compact-boundary> 标记
       返回 compacted("full")
```

**逐级升级**：Tier 1 解决问题则跳过 2/3，不可跳级（不能直接到 Tier 3）。

**Reasonix** — 多层 tool result 管理 + History Fold：

```
1. Tool Result 截断 (dispatch 时): 每条结果 8000 tokens [^1]
2. Post-turn oversized shrink: 扫描所有 tool_role 消息，截断超 8000 tokens 的 [^1]
3. Tool call args shrink: JSON 字符串 >300 chars 替换为 marker
4. History Fold (aggregate):
   - Normal: context >75% → 摘要头部，保留 20% tail
   - Aggressive: context >78% → 保留 10% tail
   - Force summary: context >80% → 退出，生成最终摘要
   - Turn-start pre-emptive: context >90% → 开新 turn 前强制 fold
5. Reasoning content pruning: 丢弃旧 turn 的 reasoning_content (保留有 tool_calls 的)
```

**截断算法特色**：head + 1KB tail 保留（保留错误信息），UTF-16 安全（处理 emoji/补充字符），迭代二分法找最大 fit (最多 6 轮)。

#### Tier B: 中间件管道（最可配置）

**DeepAgents** — SummarizationMiddleware 管道：

```
before_model() 每轮调用:
  Step 1: _truncate_args() — 截断旧 write_file/edit_file tool 参数 >2000 chars
  Step 2: _should_summarize() — 检查 3 种触发器
  Step 3a: 仅截断 → RemoveMessage(REMOVE_ALL) + truncated_messages
  Step 3b: 不需要 → None
  Step 4: 完整摘要:
    4a: _offload_to_backend() — 完整消息持久化到 Backend (文件系统/Store)
    4b: _create_summary() — LLM 生成摘要
    4c: _build_new_messages_with_path() — 摘要消息 + 保留消息
```

**独特之处**：
- 三种触发器类型: `("messages", N)`, `("tokens", N)`, `("fraction", F)`
- 模型自适应: 有 `profile.max_input_tokens` 时自动切换到 fraction 模式
- 参数截断独立于摘要（更轻量，可单独生效）
- 摘要消息含后端文件路径，可回溯完整历史

#### Tier C: 专用 Agent 摘要（最创新）

**OpenCode** — Compaction Agent：

```
触发: isOverflow() 或 Provider 返回 context_overflow 错误
  → select() head/tail split (估计 token，preserveRecentBudget = min(8K, 25% usable))
  → 获取 compaction agent (hidden, "*": "deny" — 无工具权限)
  → 构建 prompt (前次 summary + SUMMARY_TEMPLATE)
  → 流式执行 compaction agent (专用 model)
  → filterCompacted() 重建可见消息: summary + tail

SUMMARY_TEMPLATE (8 个必填节):
  Goal / Constraints & Preferences / Progress (Done/In Progress/Blocked)
  Key Decisions / Next Steps / Critical Context / Relevant Files
```

**独特之处**：
- 使用独立 Agent（无工具）执行摘要，而非函数调用
- 如果前次 summary 存在，用 `<previous-summary>` 传递，要求增量更新
- 摘要后自动注入 "Continue if you have next steps..." 合成消息
- 背景 prune: 循环结束后异步裁剪旧 tool output (保留最近 40K tokens)

#### Tier D: 基础摘要

**pi-agent / oh-my-pi** — 标准 LLM 摘要 + split turn 处理：

```
触发: contextTokens > contextWindow - 16384
  → 向后 walk 找到 cut point (保留 20K tokens)
  → snap 到有效 cut point (user/assistant/bash/custom message)
  → 序列化消息 (tool result 截断到 2000 chars)
  → LLM 摘要 (如前次存在则增量更新)
  → 附加文件追踪 (<read-files>, <modified-files>)
  → 追加 CompactionEntry 到 session
```

**pi-agent split turn 处理**：如果 cut point 在 turn 中间（assistant message），并行生成两个摘要再合并。

### 2.3 压缩后保留策略对比

| 框架 | 始终保留 | 可能丢失 | 特殊保留 |
|------|---------|---------|---------|
| **Reasonix** | ImmutablePrefix (system + tools + fewshots), pinned skills | 旧 tool results, reasoning_content | 截断的完整结果保存到 `.reasonix/truncated-results/` |
| **PilotDeck** | System message, head turns (2), tail turns (4) | 中间 turns | `<snip-boundary>` 标记, FileHistoryStore 可 undo |
| **DeepAgents** | System prompt, 最近 messages, summary | 旧消息细节, write_file/edit_file 参数 | 完整历史持久化到 Backend 文件 |
| **OpenCode** | System prompt, tail messages, summary | 旧 tool output (prune >40K) | 前次 summary 增量更新 |
| **pi-agent** | 摘要 + 保留 20K tokens 的最近消息 | 被摘要覆盖的旧消息 | CompactionEntry 永不删除 (JSONL 保留) |
| **oh-my-pi** | 同 pi-agent + TTSR 注入记录 | 同 pi-agent | TTSR rules 跨 compaction 存活 (全路径扫描) |
| **oh-my-claudecode** | PreCompact checkpoint (mode state, todo, project memory) | 完整对话 transcript | Notepad (Priority/Working/MANUAL) 磁盘持久 |
| **Hermes** | System prompt, protected head, protected tail (~20K tokens) | 中间 turns, 旧 tool results | Previous summary 增量积累 |
| **VoltAgent** | 最近 6 messages, summary | 旧消息 | Working Memory (跨 context limit) |
| **AgentScope Java** | 摘要消息 | 所有旧消息 | 无特殊保留 |

### 2.4 Compaction 成本对比

| 框架 | LLM 调用次数 | 使用模型 | 估算额外 Token 开销 |
|------|------------|---------|-------------------|
| **Reasonix** | 1 次 fold + 1 次 summary | deepseek-v4-flash (最便宜) | ~2000 tokens input + summary output |
| **PilotDeck** | 0-1 次 (Tier 1/2 零成本, Tier 3 一次) | 当前模型或专用 summarizer | Tier 3: ~20K tokens output max |
| **DeepAgents** | 1 次 summary | 当前模型 | trim_tokens_to_summarize=4000 input + output |
| **OpenCode** | 1 次 compaction agent | 专用 compaction model | 整个 head 作为 input |
| **pi-agent** | 1-2 次 (split turn 时 2 次并行) | 当前模型 | 完整被摘要消息序列化 |
| **Hermes** | 1 次 summarization | auxiliary model (便宜) | 2000-12000 tokens output (弹性) |

---

## 3. Session 状态管理对比

### 3.1 存储机制

| 框架 | 存储方式 | 格式 | 恢复方式 |
|------|---------|------|---------|
| **Reasonix** | JSONL + Event Sourcing | `.jsonl` 消息 + `.events.jsonl` 事件 + `.meta.json` 元数据 | Event replay via 7 个纯函数 Reducer |
| **PilotDeck** | JSONL Transcript | `AgentTranscriptEntry` (sessionId, turnId, sequence, entryId, parentEntryId) | Transcript replay (从最后 compact boundary 重建) |
| **DeepAgents** | LangGraph Checkpoint | SQLite `sessions.db` (thread_id → checkpoint blob) | Thread resume (LangGraph 自动从 checkpoint 恢复) |
| **OpenCode** | SQLite (Drizzle ORM) | SessionTable + MessageTable + PartTable + SessionInputTable + ContextEpochTable | Message clone + filterCompacted() 重建 |
| **pi-agent** | JSONL 树 | 9 种 entry type (message, compaction, branch_summary, model_change 等) | buildSessionContext() 从 root-to-leaf walk |
| **oh-my-pi** | JSONL 树 (继承 pi-agent) | 同 pi-agent + ttsr_injection entry | 同 pi-agent + TTSR 全路径扫描 |
| **oh-my-claudecode** | better-sqlite3 + JSONL | `.omc/state/` 目录下多个 JSON 状态文件 + `.claude/todos.json` | Checkpoint 文件恢复 + mode state JSON 重读 |
| **Hermes** | SQLite (FTS5) | `SessionDB` 含 messages, sessions, messages_fts, messages_fts_trigram | Session ID 查询 + message 列表重建 |
| **VoltAgent** | StorageAdapter (可插拔) | 6 种后端 (InMemory, LibSQL, PostgreSQL, Supabase, D1, VoltOps) | Workflow suspend/resume + Time Travel |
| **AgentScope Java** | Session 接口 (可插拔) | 4 种实现 (InMemory, Json, Redis, MySQL) | Session.save()/load() + Hash-based 增量 |

### 3.2 Session 拓扑

| 框架 | 拓扑类型 | 分支/分叉 | 特色 |
|------|---------|---------|------|
| **Reasonix** | 线性 + Event Log | 无分支 | Event Sourcing 支持完整重放 |
| **PilotDeck** | 线性 + Subagent 侧链 | 主线 + `<stem>/subagents/<id>.jsonl` | FileHistoryStore 支持 undo/rewind |
| **pi-agent** | **树** (非线性) | `/tree` 导航, `/fork` 复制, `/clone` 提取活跃分支 | BranchSummaryEntry 记录被放弃分支 |
| **OpenCode** | 线性 + parentID 子 session | Session.fork() 消息克隆 + ID 重映射 | V2 Context Epoch (不可变 baseline) |
| **DeepAgents** | 线性 + Thread 分支 | LangGraph thread branching | Sub-agent 状态过滤 (5 种 excluded keys) |
| **VoltAgent** | 线性 + Workflow Time Travel | workflow.timeTravel() 从任意历史 step 重执行 | 新 executionId，源不被修改 |

### 3.3 崩溃恢复

| 框架 | 恢复粒度 | 机制 | 持久化时机 |
|------|---------|------|-----------|
| **Reasonix** | 最近 ReAct 步骤 | `.jsonl.bak` backup + Event replay | 每次 fold 后 crash-safe rewrite |
| **PilotDeck** | 最近 compact boundary | TranscriptReplay 从最后 compact boundary | 每条 entry 即时写入 JSONL |
| **DeepAgents** | 最近 LangGraph checkpoint | CheckpointSaver 自动恢复 | 每个 agent step 后 |
| **OpenCode** | 最近消息 | SQLite (Drizzle ORM) 事务 | 实时持久 |
| **pi-agent** | 最近 CompactionEntry | buildSessionContext() 从 compact boundary | Append-only (appendFileSync) |
| **AgentScope Java** | 最近 AgentState | GracefulShutdown: SIGTERM → saveState → interrupt | JVM shutdown hook |

---

## 4. Memory 管理机制对比

### 4.1 短期记忆 (Context Window)

所有框架的短期记忆本质相同：消息历史在 context window 内线性增长，达到阈值后压缩。

**独特机制**：

| 框架 | 独特短期机制 |
|------|------------|
| **Reasonix** | 3-zone cache-first: ImmutablePrefix (SHA-256 hash, 99.82% cache hit) + AppendOnlyLog + VolatileScratch |
| **oh-my-pi** | TTSR: regex 触发的流中断，规则不占 context 但实时有效 |
| **oh-my-claudecode** | Notepad 3 级 (Priority <500 chars / Working <4K / MANUAL 永久)，磁盘持久 |
| **VoltAgent** | Working Memory: per-session/user KV store，支持 Zod schema 验证或 Markdown 模板 |

### 4.2 长期记忆

| 框架 | 长期记忆机制 | 存储 | 检索 |
|------|------------|------|------|
| **Reasonix** | 无专门长期记忆 | — | — |
| **PilotDeck** | EdgeClaw (white-box) | SQLite + Markdown files | MemoryResolver.retrieve() + captureTurn() |
| **DeepAgents** | AGENTS.md + Backend store | Filesystem / Store / State | Backend.read() |
| **PilotDeck** | EdgeClaw Dream Mode | SQLite + staged snapshot | LLM 聚类合并 + rollback |
| **oh-my-pi** | 3 backend: local / mnemopi (SQLite) / hindsight (remote) | SQLite 或 HTTP | retain/recall/reflect 3 工具 |
| **oh-my-pi** | Mental Models (Hindsight 专属) | Hindsight server | 16K char 预算，自动刷新 |
| **Hermes** | 8 个可插拔 Provider (honcho, mem0, supermemory 等) + 内置 SQLite = 9 种总计 | 可插拔 | FTS5 + 向量搜索 + 方言式推理 |
| **VoltAgent** | StorageAdapter + EmbeddingAdapter + VectorAdapter | 6 后端 (InMemory, LibSQL, Postgres 等) | 语义搜索 (embedding + cosine similarity) |
| **AgentScope Java** | LongTermMemory (deprecated 2.0) + Extensions | Mem0, Bailian | record() / retrieve() |

### 4.3 长期记忆的独特机制

**Hermes — 方言式用户建模 (Honcho)**：
- 3 档推理深度 (minimal → base → max)
- 多轮迭代: "Who is this person?" → "What gaps remain?" → "Reconcile contradictions"
- Peer Cards 积累关键事实，Conclusions 显式写入
- 成本感知: empty-streak backoff (连续空结果放宽调用频率)

**Hermes — Curator 自我改进**：
- 后台技能维护: active → stale (30天) → archived (90天)
- LLM 审查: 识别前缀聚类 → 合并为 umbrella skills → 窄 skills 降级为子文件
- 从不删除，只归档

**PilotDeck — EdgeClaw Dream Mode**：
- 空闲时内存整理: 合并重复条目 → LLM 聚类合并 → 项目元数据审查 → 用户画像重写
- 分阶段快照: 所有变更在 stage 上进行，成功后 atomically swap
- 一键回滚: `rollbackLastDream()` 恢复快照

**oh-my-pi — TTSR 跨 Compaction 存活**：
- `ttsr_injection` entry 追加到 session tree
- `buildSessionContext()` 扫描**全路径** (不只是 compact boundary 内)
- TTSR 注入状态在 session reload 时恢复到 TtsrManager
- 效果: regex 规则在不占 context 的前提下，经过多次 compaction 仍然有效

---

## 5. Nop Agent 设计建议

### 5.1 Compaction: 推荐混合策略

基于调研，Nop Agent 应采用**分层渐进压缩**（参考 PilotDeck 3-tier），同时结合 Reasonix 的精确计数：

```
Nop Agent Compaction Pipeline:

Layer 0 (预防性 - 每次 Tool 执行后):
  → Tool result 截断 (配置阈值, 默认 8000 tokens)
  → Tool call args 大字符串替换 (Reasonix 风格)

Layer 1 (零成本 - ReAct 循环每轮检查):
  → 旧 tool_result 内容清除 (PilotDeck MicroCompaction)
  → 仅处理可压缩工具 (read_file, bash, grep 等)
  → 保留最新 N 条

Layer 2 (无 LLM 调用 - 超过 Layer 1 阈值):
  → 中间 turn 裁剪 (PilotDeck SnipEngine)
  → 保留 head + tail anchors
  → 工具对完整性检查

Layer 3 (LLM 摘要 - 超过 Layer 2 阈值):
  → LLM 生成摘要 (使用便宜模型)
  → 前次 summary 增量更新 (OpenCode 风格)
  → 完整历史 offload 到 DB (DeepAgents 风格)

Layer 4 (强制退出 - context >90%):
  → 生成最终摘要，停止工具调用
  → 发布 AgentEvent.FORCED_STOP
```

### 5.2 Token 计数

**推荐**：精确计数 + LRU 缓存 (Reasonix 模式)。

Nop 的 Java 生态有 JTokkit (tiktoken Java port)，可直接使用。建议：
- `o200k_base` 作为默认编码 (GPT-4o/GPT-5/Claude 近似)
- LRU 缓存 (4096 entries) 避免重复计数
- Provider 报告的 exact tokens 用于校准本地估算

### 5.3 Session 状态

**推荐**：DB 持久化 + Event Sourcing 混合 (Reasonix + OpenCode)。

```
Session State:
  → IOrmSession 持久化 Actor 状态变更 (即时写入)
  → AppendOnly event log (消息事件, 工具调用, compaction)
  → CompactionEntry 记录压缩边界
  → Session rebuild: 从最近 CompactionEntry + 保留消息
```

### 5.4 Memory

**推荐**：3 层记忆 (短期 + Working Memory + 长期)。

```
短期: ReAct 循环内的消息历史 (context window 管理)
Working Memory: per-session KV store (VoltAgent 风格)
  → 支持 JSON schema 验证
  → Session 启动时注入到 system prompt
  → Agent 通过工具读写

长期: Phase 3+ (可选)
  → IMessageService + 向量存储
  → retain/recall/reflect 工具
  → EdgeClaw 风格的 captureTurn + retrieve
```

### 5.5 关键设计决策

| 决策 | 推荐 | 参考 |
|------|------|------|
| Compaction 触发 | 前+后置混合 (turn-start 预检 + post-response 决策) | Reasonix |
| 压缩后保留 | System prompt + summary + 最近 N 条 + pinned items | OpenCode SUMMARY_TEMPLATE |
| 摘要模型 | 使用便宜/快速模型，不与主模型竞争 | Reasonix (flash), Hermes (auxiliary) |
| 文件追踪 | Compaction 时提取 read/modified 文件列表 | pi-agent (<read-files>, <modified-files>) |
| 子 Agent 隔离 | 状态过滤 (excluded keys) | DeepAgents (_EXCLUDED_STATE_KEYS) |
| 摘要模板 | 结构化 8 节 (Goal, Constraints, Progress, Decisions, Next Steps, Context, Files) | OpenCode SUMMARY_TEMPLATE |
| PreCompact hook | 压缩前保存关键状态 (todo, plan, project memory) | oh-my-claudecode |

---

## 6. Open Questions

- [ ] Nop Agent 的 Compaction Layer 1/2 的具体阈值如何配置？是否通过 `agent.xdef` 的 constraints 段？
- [ ] Working Memory 的 KV store 用什么实现？直接用 Session 的扩展属性还是独立实体？
- [ ] 长期记忆的向量存储用哪个实现？nop-stream CEP 还是独立集成？
- [ ] 摘要生成的 system prompt 如何与 agent 的 system prompt 协调？避免摘要中的 "do not mention summarizing" 类指令与主 prompt 冲突。
- [ ] 多租户场景下，Compaction 的 LLM 调用成本如何分摊？是否需要租户级配额？

## References

- `ai-dev/analysis/agent-survey/2026-06-05-agent-design-key-elements.md` — 12+ 框架综合分析
- `ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md` — Nop Agent 可靠性设计 (5 层渐进压缩管道)
- `ai-dev/design/nop-ai-agent/nop-ai-agent-session-and-storage.md` — Nop Agent Session 设计
- `ai-dev/design/nop-ai-agent/nop-ai-agent-actor-runtime-vision.md` — Nop Actor Runtime 构想

## Footnotes

[^1]: 前期分析文档记录 Reasonix tool result 阈值为 3000 tokens。源码级调研确认实际值为 `DEFAULT_MAX_RESULT_TOKENS = 8_000`（`mcp/registry.ts:46`），3000 为前期分析的不准确值。

[^2]: 前期分析文档标记 Hermes 无上下文压缩引擎。源码级调研发现 `agent/context_compressor.py`（2078 行）实现了完整压缩：阈值 50% context、tool result prune (3 pass)、LLM 摘要（弹性 2K-12K token 预算）、前次 summary 增量积累。前期分析遗漏了此模块。

[^3]: 本文中的具体数值阈值（如 PilotDeck 的 1536 bytes / head(2) tail(4) / 35%、DeepAgents 的 85% / 170K / 2000 chars、OpenCode 的 SUMMARY_TEMPLATE 8 节结构、pi-agent 的 16384 / 20000 等）均来自源码级调研，非前期分析文档已有内容。前期分析文档仅确认了概念和高层架构。
