# Token 估算与上下文窗口压缩策略：五框架源码级对比

> Status: analysis
> Date: 2026-06-10
> Scope: Codex, Claude Code, OpenCode, SolonCode, DeepSeek-Reasonix — Token 估算方式、压缩触发阈值、压缩策略
> 方法: 直接阅读 ~/ai/ 下各框架源码，追踪 token 计数 → 阈值判断 → 压缩执行的完整数据流

---

## 1. Token 估算方式

### 1.1 总览

| 框架 | 估算方式 | 精度 | 依赖 | 源码位置 |
|------|---------|------|------|---------|
| **Codex** | `ceil(bytes/4)` 启发式 + API `usage` 混合 | 低 | 无外部依赖 | `codex-rs/utils/string/src/truncate.rs:71-74` |
| **Claude Code** | API `usage` 精确值回退 `text.length/4` | 中高 | 无外部依赖 | `cli.js` ~L2066 `pDA()` |
| **OpenCode** | `Math.round(text.length / 4)` | 低 | 无外部依赖 | `packages/core/src/util/token.ts:3-5` |
| **SolonCode** | jtokkit (Java tiktoken port), `o200k_base` 编码 | 高 | jtokkit 1.1.0 | `ContextCompressionInterceptor.java:77-79, 410-448` |
| **Reasonix** | 自研 DeepSeek V4 BPE tokenizer (纯 TS) | 最高 | `deepseek-tokenizer.json.gz` | `src/tokenizer.ts:107-294` |

### 1.2 逐框架细节

#### Codex — bytes/4 启发式 + API 校准

```rust
// codex-rs/utils/string/src/truncate.rs:4,71-74
const APPROX_BYTES_PER_TOKEN: usize = 4;

pub fn approx_token_count(text: &str) -> usize {
    let len = text.len();
    len.saturating_add(APPROX_BYTES_PER_TOKEN.saturating_sub(1)) / APPROX_BYTES_PER_TOKEN
}
```

**混合策略**：本地启发式作为基线，每次 API 响应后用 `response.completed` 事件的 `token_usage` 校准。后续估算 = 服务端报告的精确值 + 本地对新增消息的启发式估算。

- 精确值获取：`context_manager/history.rs:296-314` `get_total_token_usage()`
- 单消息估算：`context_manager/history.rs:479-482` 序列化为 JSON → 字节长度 → bytes/4
- 图片特殊处理：base64 数据替换为固定 ~7373 字节估计值
- 压缩后重新计算：`session/mod.rs:3089-3124` `recompute_token_usage()` 从零重新估算

#### Claude Code — API usage + chars/4 回退

```javascript
// cli.js ~L1206 (deobfuscated)
function estimateTokens(text) { return Math.round(text.length / 4); }
```

**混合策略**：从最新 assistant 消息向前扫描，找到最近一条含 `usage` 的消息，用精确值 + 后续消息的 chars/4 估算。

- 精确值来源：API 响应的 `usage` 对象（含 `input_tokens`, `cache_creation_input_tokens`, `cache_read_input_tokens`, `output_tokens`）
- 精确值聚合函数 `$xA(usage)` = `input_tokens + cache_creation + cache_read + output_tokens`
- 内容块级别：text=chars/4, image=固定 1334 tokens, tool_result=递归估算内容
- Rust 重实现确认：`claude-code/rust/crates/runtime/src/compact.rs:458-474`

#### OpenCode — 纯 chars/4，无 API 追踪

```typescript
// packages/core/src/util/token.ts:3-5
const CHARS_PER_TOKEN = 4
export const estimate = (input: string) => Math.max(0, Math.round(input.length / CHARS_PER_TOKEN))
```

**无任何精确计数机制**。估算前先 `JSON.stringify()` 序列化（`compaction.ts:79`）。

#### SolonCode — jtokkit 精确计数 + LRU 缓存

```java
// ContextCompressionInterceptor.java:77-79
private static final EncodingRegistry registry = Encodings.newDefaultEncodingRegistry();
private static final Encoding encoding = registry.getEncodingForModel(ModelType.GPT_4O);
```

**逐消息精确计数**（`estimateTokens()`, lines 410-448）：
- 每条消息内容调用 `encoding.countTokens(m.getContent())`
- AssistantMessage 的 tool_calls：`countTokens(name + args) + 10` 每个
- 结果缓存在消息 metadata（`META_TOKEN_SIZE`）避免重复计算
- 开销：每条消息 +4，system prompt +4，总计 +3

使用 `o200k_base` 编码。代码注释指出对 DeepSeek 和其他 cl100k_base 模型偏差 <5%。

#### Reasonix — 自研 V4 BPE + 边界估算优化

```typescript
// src/tokenizer.ts:261-294
function countTokens(text: string): number
// src/tokenizer.ts:296-315
function countTokensBounded(text: string, maxChars?: number): number
```

**最精细的实现**：
- 加载 `data/deepseek-tokenizer.json.gz`（V4 词汇表 + merges）
- 完整 BPE：split regex → byte-level encoding → merge rank table → added-token patterns
- **边界估算优化** `countTokensBounded()`：只 tokenize 头尾各 1KiB，然后按 char-to-token 比率外推全长。避免对超长 tool output 完整 tokenize
- 8192-entry BPE LRU 缓存（`tokenizer.ts:194`）
- 4096-entry 内容缓存（`tokenizer.ts:535`，最大 10KiB 字符串）
- 完整请求估算：`estimateRequestTokens()` = conversation tokens + tool spec tokens（`tokenizer.ts:611-628`）
- 完整 chat template 渲染：`formatDeepSeekPrompt()`（`tokenizer.ts:484-530`）

---

## 2. 压缩触发阈值

### 2.1 总览

| 框架 | 触发阈值 | 触发时机 | 可配置性 |
|------|---------|---------|---------|
| **Codex** | `context_window * 90%` (auto) 或 `95%` (hard) | Turn 前、Turn 中每次 response 后、model downshift | `auto_compact_token_limit` 可配置，scope 可选 `Total` 或 `BodyAfterPrefix` |
| **Claude Code** | `context_window - max_output - 13,000` | 每 turn 开始 | `CLAUDE_AUTOCOMPACT_PCT_OVERRIDE` 环境变量 |
| **OpenCode** | `context_window - max(output, buffer_20K)` | 每 turn 前 + Provider overflow 错误恢复 | `compaction.buffer` (default 20K), `keep.tokens` (default 8K) |
| **SolonCode** | `max_tokens * 80%` **OR** `max_messages > 30` (双维度) | 每 ReAct reason 循环开始前 | `summaryWindowSize`, `summaryWindowToken` 可在 settings.json 配置 |
| **Reasonix** | 4 级梯度: 75% / 78% / 80% / 90% | Turn 前 + 每次 API response 后 | 阈值为代码常量，不可配置 |

### 2.2 逐框架细节

#### Codex — 双阈值 + 双 scope

| 阈值 | 计算公式 | 作用 |
|------|---------|------|
| Auto-compact | `min(configured_limit, context_window * 9/10)` | 触发压缩 |
| Hard limit | `context_window * 95%` (effective_context_window_percent) | 绝对上限 |

**双 scope 模式**（`config_types.rs:26-37`）：
- `Total`（默认）：所有活跃 context tokens vs auto_compact 阈值
- `BodyAfterPrefix`：只看上次压缩后新增部分 vs 阈值，同时检查 95% 硬上限

三个检查点（`session/turn.rs`）：
1. Pre-turn (L759-778): `run_pre_sampling_compact()`
2. Post-sampling (L247-291): mid-turn 每次 model response 后
3. Model downshift (L785-835): 切换到更小 context 模型时

#### Claude Code — 固定 margin 阈值

Sonnet-4 (200K context, 64K output) 的具体数值：

| 阈值 | 计算 | Token 值 |
|------|------|---------|
| Effective max | 200000 - 64000 | 136,000 |
| Auto-compact | 136000 - 13000 | **123,000** (~90.4%) |
| Warning | 136000 - 20000 | 116,000 |
| Blocking | 136000 - 3000 | **133,000** |

常量定义（`cli.js` ~L2497）：`NC0=13000`, `Ai5=20000`, `Qi5=20000`, `wC0=3000`

#### OpenCode — 头部空间检查

```typescript
// compaction.ts:230-241
if (estimate(request) <= context - Math.max(output, config.buffer)) return false;
// buffer 默认 20,000 (line 12)
```

**不是百分比阈值**，而是固定头部空间：request tokens 超过 `context - headroom` 即触发。

第二个触发路径：Provider 返回 context overflow 错误 → `isContextOverflowFailure()` 匹配 15+ 种正则 → 压缩后重试一次。

#### SolonCode — 双维度 OR 门

```java
// ContextCompressionInterceptor.java:146-160
if (messageSize <= maxMessages && currentTokens <= (maxTokens * 0.8)) {
    return; // 不压缩
}
```

**任一条件满足即触发**：
- 消息数量：非 FIRST 消息 > `maxMessages`（CLI 默认 30，no-arg 默认 15）
- Token 数量：> `maxTokens * 80%`（CLI 默认 30,000）

可配置：`~/.soloncode/settings.json` 的 `summaryWindowSize` 和 `summaryWindowToken`。

#### Reasonix — 4 级梯度 + 双检查点

```typescript
// context-manager.ts:23-39
HISTORY_FOLD_THRESHOLD = 0.75          // 触发 fold
HISTORY_FOLD_AGGRESSIVE_THRESHOLD = 0.78 // 激进 fold (更小 tail)
FORCE_SUMMARY_THRESHOLD = 0.80         // 强制退出
TURN_START_FOLD_THRESHOLD = 0.90       // Turn 前预折叠
```

Post-response 决策（`context-manager.ts:138-169`）：
| ratio 范围 | 决策 |
|-----------|------|
| > 0.80 | `exit-with-summary` |
| 0.78 ~ 0.80 (已 fold 过) | `none` |
| 0.75 ~ 0.78 | `fold` aggressive (10% tail) |
| ≤ 0.75 | `none` |

Turn-start（`loop.ts:752-786`）：本地估算 ratio > 0.90 → 立即 fold。

---

## 3. 压缩策略

### 3.1 策略分类

| 策略类型 | 使用者 | 核心机制 |
|---------|--------|---------|
| LLM 摘要 | Codex, Claude Code, OpenCode, SolonCode, Reasonix | 发送旧消息给 LLM 生成摘要 |
| 零成本裁剪 | Claude Code (micro), SolonCode (null fallback) | 无 LLM 调用，直接替换/截断 |
| 远端压缩 | Codex (OpenAI only) | 服务端加密压缩 |
| 多策略管道 | SolonCode | 5 种可插拔策略，Composite 组合 |
| 前缀缓存感知 | Reasonix | 三区域架构 + fold 操作共享 prefix bytes |

### 3.2 逐框架策略

#### Codex — 本地摘要 + 远端压缩双路径

```
触发 → 选择实现:
  OpenAI/Azure:
    Remote V2: 发送 CompactionTrigger → 服务端加密压缩 → 保留 64K tokens 的 user/developer/system
    Remote V1: 调用 /responses/compact API (legacy)
  其他 Provider:
    Local: 发送完整历史给模型 → 生成 handoff summary → 保留 20K tokens 的 user 消息
```

选择逻辑：`session/turn.rs:837-882` `run_auto_compact()` → `supports_remote_compaction()` 判断是否 OpenAI/Azure。

Local 摘要的 user 消息保留策略：从最新向前取 20K token 预算，超出部分从**中间**截断（保留头尾）。

摘要 prompt (`prompts/templates/compact/prompt.md`)：创建 handoff summary，包含当前进度、关键决策、待办事项、关键数据。

#### Claude Code — 三层压缩

**Layer 1: Micro-compact（每 turn 静默执行）**
- 保留最近 3 条 tool result（`Ji5=3`）
- 旧的 tool result 替换为文件引用：`"[Previous: tool result saved to /path]"`
- 仅在 savings > 20K tokens 且超过 warning 阈值时触发（`Zi5=20000`）
- 涉及工具：Read, Write, Bash, Edit, MultiEdit, NotebookEdit, Glob, Grep
- 完整内容保存到临时文件，可恢复

**Layer 2: Auto-compact（阈值触发）**
1. 先尝试 session memory compaction（无 API 调用）
2. 失败则 LLM 生成结构化摘要（9 个必填节）
3. 摘要替换全部旧消息，插入 `compact_boundary` 标记
4. 附件和 hook 结果保留

摘要 prompt 要求 9 节：Primary Request / Key Technical Concepts / Files and Code Sections / Errors and fixes / Problem Solving / All user messages / Pending Tasks / Current Work / Next Step

**Layer 3: Manual compact（用户 `/compact`）**
同 Layer 2，trigger 标记为 "manual"。

#### OpenCode — 分割保留 + 增量摘要

```
触发 → select(): 从尾部向前 walk，保留 8K tokens 的 recent 消息
     → head 部分发送给 LLM 生成结构化摘要 (8 节模板)
     → 如有前次 summary，用 <previous-summary> 传递，要求增量更新
     → 结果存储为 <conversation-checkpoint> 消息
     → Provider overflow → 再触发一次恢复（仅一次重试）
```

8 节模板：Goal / Constraints & Preferences / Progress (Done/In Progress/Blocked) / Key Decisions / Next Steps / Critical Context / Relevant Files

安全检查：摘要 prompt 自身也要在 context 内，否则放弃压缩。

#### SolonCode — 可插拔策略管道

```
触发 → 提取 "First Chain" (META_FIRST 消息永久保护)
     → 计算截断点 (双维度: 消息数 + token 预算)
     → 原子对齐 (tool_call/tool_result 不可分割)
     → 语义连续性 (截断点在 assistant thought 后则包含)
     → Protected segment summary (溢出段额外摘要)
     → 策略执行 → 重建消息列表
```

**5 种可插拔策略**：

| 策略 | 机制 | LLM 调用 | 输出 |
|------|------|---------|------|
| `null` (fallback) | 滑动窗口 + 原子对齐 | 无 | 直接截断 |
| `LLMCompression` | LLM 摘要执行进度 | 1 次 | 300 chars 摘要 |
| `KeyInfoExtraction` | LLM 提取业务参数/确认事实/失败路径 | 1 次 | 结构化 Markdown |
| `HierarchicalRolling` | 合并旧摘要 + 新过期消息 | 1 次 | max 500 chars, hard cap 800 |
| `VectorStore` | RAG 归档 + recall_history 工具 | 0 次 (向量检索) | 紧凑指针 |

**默认组合**：`Composite(KeyInfoExtraction + HierarchicalRolling)` — 先提取事实，再滚动合并。

压缩模型可与主模型不同：`compressionModel` 配置，未设置时回退到主模型。

#### Reasonix — 三区域 Cache-First + 多层 Fold

**三区域架构**（`src/memory/runtime.ts`）：
```
ImmutablePrefix (L15-132)  |  system + tools + fewShots, SHA-256 锁定
AppendOnlyLog (L136-251)   |  单调追加, 滑动窗口 200 条
VolatileScratch (L253-263) |  每轮重置, 永不发送到 API
```

**Fold 操作**（`context-manager.ts:183-259`）：
1. 分区 log 为 head（旧）和 tail（新），tail budget 由阈值梯度决定（20% 正常 / 10% 激进）
2. 跳过无效 fold：head 不节省至少 30% tokens 则放弃
3. 提取 pinned skill memos（`<skill-pin>` 块全文保留）
4. 用 `deepseek-v4-flash`（最便宜模型）生成语义摘要，15s 超时
5. 摘要调用**复用主 agent 的 cached prefix bytes**（共享已付费的缓存）
6. 组装：摘要 + pinned skills + pinned constraints + tail
7. 持久化：内存 `compactInPlace()` + 磁盘 `rewriteSession()`

**每轮自动 healing**（`loop.ts:550-585`）：
- shrink oversized tool results（8000 tokens/条，head+tail 截断 + 迭代二分法）
- shrink oversized tool call args（>300 chars → marker）
- strip droppable reasoning_content（保留有 tool_calls 的）
- fix tool call pairing（移除未配对的）

**缓存效果**：实测 435M input tokens, 99.82% cache hit, ~80% 成本节省。

---

## 4. 保留策略对比

| 框架 | 始终保留 | 可能丢失 | 特殊保留 |
|------|---------|---------|---------|
| **Codex** | User 消息 (20K budget) | 旧 tool output | User 消息中间截断（保留头尾） |
| **Claude Code** | 最近 3 条 tool result, attachments, hook results | 被摘要覆盖的旧消息 | compact_boundary 标记 |
| **OpenCode** | System prompt, tail (8K tokens), summary | 旧 tool output | 前次 summary 增量更新 |
| **SolonCode** | First Chain (META_FIRST), 工具对完整性 | 中间消息 | 5 种策略各有不同保留 |
| **Reasonix** | ImmutablePrefix, pinned skills, tail (10-20%) | 旧 tool results, reasoning_content | 截断结果保存到 .reasonix/truncated-results/ |

### 原子对齐

只有 SolonCode 和 Codex 明确实现了 tool_call/tool_result 的原子对齐（不拆分配对）。Claude Code 通过 micro-compact 隐式保护最近 3 条。Reasonix 通过 healing pipeline 的 `fixToolCallPairing()` 事后修复。

---

## 5. 压缩成本对比

| 框架 | LLM 调用 | 使用模型 | 估算额外 Token |
|------|---------|---------|---------------|
| **Codex** (local) | 1 次 | 当前模型 | 整个历史 + summary output |
| **Codex** (remote) | 0 次 (服务端) | N/A | 服务端处理，客户端无额外消耗 |
| **Claude Code** | 0-1 次 | 当前模型 | 0 (session memory) 或整个历史 + summary |
| **OpenCode** | 1 次 | 可配置 | head 部分 + 4096 max output |
| **SolonCode** | 0-2 次 | 可配置 compressionModel | KeyInfo + Hierarchical 各一次 |
| **Reasonix** | 1 次 | deepseek-v4-flash (最便宜) | ~2000 tokens input + summary output，**复用 cached prefix** |

Reasonix 成本最优：使用最便宜模型 + 复用已缓存的 prefix bytes。

---

## 6. 关键设计差异总结

| 维度 | 最精细 | 最简洁 | 最独特 |
|------|--------|--------|--------|
| Token 估算 | Reasonix (自研 V4 BPE + LRU + bounded) | Codex/OpenCode (chars/4) | SolonCode (jtokkit, Java 唯一精确) |
| 触发机制 | Reasonix (4 级梯度 + 双检查点) | OpenCode (单阈值) | SolonCode (token + message count 双维度 OR) |
| 压缩策略 | Claude Code (3 层) | OpenCode (单次摘要) | SolonCode (5 种可插拔策略管道) |
| 缓存感知 | Reasonix (cache-first 三区域 + fold 共享 prefix) | 其他均无缓存感知 | — |
| 原子性保护 | SolonCode (tool_call/result 对齐 + 语义连续) | — | — |
| 增量摘要 | OpenCode (`<previous-summary>`) | — | SolonCode (HierarchicalRolling max 500 chars) |

### 普遍模式

1. **所有成熟框架都实现了某种 compaction** — 简单截断已不够用
2. **"保留 recent tail" 模式是标准做法** — 预算从 8K (OpenCode) 到 20K (Claude Code/Codex) 不等
3. **工具输出是 context 膨胀的主要来源** — 所有框架都有某种 tool result 截断
4. **Token 估算都是近似的** — 即使 SolonCode 用了精确 tokenizer，也只能近似 o200k_base 对非 OpenAI 模型
5. **overflow recovery 是通用 fallback** — Provider 返回 context overflow 错误后压缩重试

---

## 7. 对 Nop Agent 的建议

### 7.1 Token 估算

推荐 **jtokkit 精确计数 + Provider usage 校准**（SolonCode 模式 + Codex 混合）：

- `o200k_base` 编码作为默认（对 Claude/DeepSeek 偏差 <5%）
- LRU 缓存（4096 entries）避免重复计数
- Provider 报告的 exact tokens 用于校准本地估算
- 可选：Reasonix 的 bounded 估算（头尾采样外推）用于超长 tool output

### 7.2 触发机制

推荐 **Reasonix 的多级梯度 + SolonCode 的双维度**：

```
ReAct reason 循环开始前:
  双维度 OR 门: token > 80% maxTokens OR message count > threshold
  ↓ 触发
  4 级梯度决策:
    ≤ 80%: 不压缩
    80-85%: 零成本裁剪 (micro-compact)
    85-90%: LLM 摘要 (保留 15% tail)
    > 90%: 强制摘要 + 停止工具调用
```

### 7.3 压缩策略

推荐 **分层渐进**（PilotDeck 3-tier 思路，参见 `2026-06-06-agent-memory-compaction-session-deep-dive.md` §5.1）：

- Layer 0: Tool result 截断 (每条 8000 tokens)
- Layer 1: 零成本裁剪 (旧 tool result 清除)
- Layer 2: 中间 turn 裁剪 (无 LLM)
- Layer 3: LLM 摘要 (使用便宜模型)
- Layer 4: 强制退出

### 7.4 前缀缓存

参见 `ai-dev/analysis/2026-06-10-nop-prefix-cache-design-analysis.md`。已完成的工作项：
- [x] ChatUsage 加 cacheMissTokens + getCacheHitRate()
- [x] ChatMessage 加 providerHints
- [x] ILlmDialect.convertMessage() 移除 isLast 参数
- [x] AnthropicDialect 从 providerHints 读取并注入 cache_control
- [ ] ReAct 循环：prefixLength + prefixHash 约定

---

## References

- `ai-dev/analysis/agent-survey/2026-06-06-agent-memory-compaction-session-deep-dive.md` — 10 框架 memory/compaction/session 深度对比
- `ai-dev/analysis/agent-survey/2026-06-10-reasonix-prefix-cache-immutability.md` — Reasonix 前缀缓存不变性源码级分析
- `ai-dev/analysis/2026-06-10-nop-prefix-cache-design-analysis.md` — Nop 平台前缀缓存设计构想
- `ai-dev/design/nop-ai-agent/nop-ai-agent-llm-layer.md` — Nop Agent LLM 层设计
- `ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md` — Nop Agent 可靠性设计

## Source Repos

| 框架 | 路径 |
|------|------|
| Codex | ~/ai/codex/codex-rs/ |
| Claude Code | /opt/homebrew/lib/node_modules/@anthropic-ai/claude-code/cli.js + ~/ai/claude-code/rust/ |
| OpenCode | ~/ai/opencode/packages/ |
| SolonCode | ~/ai/solon-ai/ + ~/ai/soloncode/ |
| Reasonix | ~/ai/DeepSeek-Reasonix/ |
