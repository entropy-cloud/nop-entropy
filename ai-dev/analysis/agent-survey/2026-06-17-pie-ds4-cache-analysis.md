# pie 项目深度分析：架构与 DS4 KV 前缀缓存优化

> Status: open
> Date: 2026-06-17
> Scope: ~/ai/pie — Rust AI coding agent，重点为 DS4（DeepSeek V4 Flash 本地服务器）KV 前缀缓存优化经验
> Conclusion: pie 的 DS4 三项优化（reasoning 回放、409 重试、cache_write 记账）揭示了 nop-ai-agent 前缀缓存设计的三个缺口；详见 §6 与沉淀到 `nop-ai-agent-llm-layer.md` §八的改进。

## Context

`nop-ai-agent` 的前缀缓存设计（`nop-ai-agent-llm-layer.md` §八）目前仅覆盖"序列化确定性"这一原则性约束，缺少运行时层面保证缓存命中的可操作机制。本调研通过源码级分析 pie（一个为驱动本地 DS4 模型而生的 Rust 编码 agent），提取其"让客户端请求流对字节精确 KV 前缀缓存 cache-exact"的工程经验，对照 nop-ai-agent 现状识别缺口并给出改进建议。

所有 pie 源码引用基于 `~/ai/pie` 仓库（单一 initial commit），行号对应 `crates/ai/src/` 下的实际实现。

---

## 1. 项目定位与架构

### 1.1 一句话定位

`pie` 是 `pi`（pi-coding-agent）的 Rust 重写，一个**终端内运行的 AI 编码 agent**。其诞生动机明确写在 README 里：作者需要在一个**本地 DS v4 模型**上跑长时间主动式自动化任务，因此需要一个可定制的 agent 运行时来支撑触发器、cron 等简单自动化。这直接解释了为什么 DS4 缓存优化是 pie 的一等公民功能。

> 模型返回什么、怎么解析、怎么存储，都不影响缓存——应用层全权控制发送内容。这与 nop-ai-agent §8.1 的核心原则完全一致，pie 是这一原则在"本地字节精确缓存服务器"极端场景下的工程兑现。

### 1.2 四 Crate 分层

Rust 2024 Cargo workspace，严格单向依赖：

```
coding-agent (pie CLI/TUI)  →  agent (harness feature on)  →  ai (LLM client)
                                  ↑
                          mcp (MCP client，被 coding-agent 包装)
```

| Crate | 职责 | nop-ai-agent 对应 |
|-------|------|-------------------|
| `pie-ai` | 统一流式 LLM 客户端，Provider 实现、model/image catalog（生成式）、OAuth、SigV4、SSE、retry/overflow | `nop-ai-core`（ChatServiceImpl + ILlmDialect）+ Provider 实现 |
| `pie-agent-core` | agent 运行时。bare `Agent`（`src/agent.rs`）**IO-free**，拥有对话状态/listeners/queues/cancellation；`agent_loop.rs` 是循环；`harness` 模块（feature gate）组装 session 存储(JSONL)、cost tracker、compaction、permission、skills、trigger runtime | `nop-ai-agent` ReAct 引擎 + reliability 扩展 |
| `pie-coding-agent` | `pie` CLI 二进制、REPL/TUI、slash 命令、hooks、LSP supervisor、tools、trigger 适配器 | nop-ai-shell / 集成层 |
| `pie-mcp` | 最小 MCP 客户端（stdio transport、JSON-RPC、tools/list+call） | MCP 集成 |

**关键架构纪律**（CLAUDE.md 明确强调）：**保持 bare `Agent` IO-free**。任何触碰文件系统、环境变量、网络适配器的代码必须放进 `harness/` 或 `coding-agent`，不能污染 IO-free 的核心 Agent。这与 nop-ai-agent"Layer 1 Core 接口纯净，IO 通过 store/provider 注入"的理念一致。

---

## 2. 核心特性概览（与 nop-ai-agent 的映射）

| 特性 | pie 实现 | nop-ai-agent 现状 |
|------|---------|-------------------|
| **Session 持久化** | JSONL per-project（`~/.pie/sessions/<cwd-hash>/<uuidv7>.jsonl`），`--resume` | FileBacked/DB SessionStore（plan 183/185） |
| **Memory** | `~/.pie/memory/*.md`，跨 session 注入 | Working Memory 工具 + system-prompt 注入（plan 192） |
| **Compaction** | `/compact` slash 命令 + harness 内 compaction 模块 | IContextCompactor（plan 187 COMPACTION checkpoint） |
| **Triggers** | 自然语言触发器（"when X exists, do Y"），session-scoped，sub-agent 执行，三种 delivery（SubAgent/InjectSummary/InjectAndRun） | Hook 生命周期 + 事件驱动（设计层） |
| **Cron / Loops** | `/cron add` + `--stateful` 有状态循环：job 自带 memory 文件，findings 写入 triage inbox（`/inbox claim/dismiss`） | 无直接对应（nop-ai-agent 定位为引擎，调度由 nop-job 承接） |
| **MCP 通知** | MCP server-push 流 → NotificationHook → trigger envelope，统一去重/审计/prompt/队列 | Channel Connector 抽象 |
| **Skills** | harness skills loading | Skill 系统三层表示 |
| **模型多 Provider** | OpenAI/Anthropic/OpenRouter/Groq/Mistral/Gemini + 本地 OpenAI-compatible | ILlmDialect + 多 Provider |

**Loops（有状态循环 + inbox）是 pie 最具特色的设计**：把 cron job 升级为"有记忆的循环"——每次运行结束时 agent 写 `<loop-state>` 笔记，下次运行读回；findings 以 `<inbox>` 标签提取到全局 triage inbox，人像处理邮件一样 `/inbox claim N` 把发现转成真实 agent turn。这是 Addy Osmani "Loop Engineering"（停止 prompt agent，构建会 prompt 你的 loop）理念的落地。nop-ai-agent 引擎层不直接需要此特性（定位不同），但"触发器输出异步落库、人不被中断、人异步 triage"的模式对 nop-ai-agent 的 trigger/notification 设计有参考价值。

---

## 3. DS4 KV 前缀缓存优化：背景（为什么这是最重要的杠杆）

### 3.1 两种缓存模型的本质差异

| 维度 | 托管 Provider（Anthropic/OpenAI） | 本地 DS4（DeepSeek V4 Flash） |
|------|-----------------------------------|-------------------------------|
| 缓存控制 | 显式 `cache_control` + 模糊记账 | 客户端无控制，服务器自管 |
| 命中判定 | 服务端 fuzzy bookkeeping | **字节精确**：新请求渲染出的前缀必须与上次采样的 token 流**逐字节相同**才复用 KV checkpoint |
| 持久化 | 服务端内存，会话级 | **KV checkpoint 持久化到磁盘**（`--kv-disk-dir`），命中可跨驱逐/重启存活 |
| 失效代价 | 重新 prefill cached 段 | 重新 prefill 整段（本地硬件上 prefill 是瓶颈） |

### 3.2 核心矛盾

本地 DS4 把请求历史渲染成 token 流，**仅当新请求的渲染前缀与上次采样的逐字节相同**时才复用 KV checkpoint。反面：**模型产出与客户端回放之间任何分歧**——丢弃的 reasoning block、重排的 item——都会从前缀该点起**静默使缓存失效**。

> 在 100k-token 的 agent session 上，这是"每轮只 prefill 几百新 token"与"每轮重新 prefill 整个对话"的差别。本地硬件上 prefill 是瓶颈，这是感知延迟最大的单一杠杆。

pie 作为客户端的职责因此被浓缩成三句话：**精确回放历史、按服务器期望的方式重试、诚实报告缓存流量**。这正是下面三项修复。

### 3.3 nop-ai-agent §八 现状对照

`nop-ai-agent-llm-layer.md` §八当前只有 4 个子节：8.1 核心原则、8.2 序列化确定性、8.3 不引入新数据结构、8.4 实测效果。这些都是**原则层约束**（convertMessage 纯函数、引擎自律不修改前缀区），**完全没有覆盖**保证缓存命中的运行时机制（reasoning 回放策略、缓存丢失恢复、缓存流量记账）。pie 的三项修复恰好填补这三个空白。

---

## 4. DS4 三项修复（源码级）

### 4.1 修复一：把 assistant thinking 作为 `reasoning` input item 回放

**问题**：DeepSeek V4 是 reasoning 模型，每个 assistant turn 以 thinking 开头。DS4 把 thinking 渲染进采样的 token 流，因此**它是 KV checkpoint 的一部分**。但标准 OpenAI Responses 客户端行为是回放历史时**丢弃** thinking → 客户端发回的渲染前缀永远不匹配服务器采样的 → 一旦 live continuation state 被驱逐，磁盘 KV checkpoint 立即变 stale。

**实现**：model descriptor 声明 `requiresReasoningContentOnAssistantMessages: true`，`openai-responses` provider 现在读取此 flag。源码 `crates/ai/src/providers/openai_responses.rs`：

```rust
// openai_responses.rs:37-41, 56
/// Replay assistant thinking content as `{"type":"reasoning"}` input items.
/// Needed by servers that do byte-exact KV prefix matching on the rendered
/// history (e.g. ds4 / DeepSeek V4 local): omitting the reasoning changes
/// the rendered prefix and invalidates their cache checkpoints.
pub replay_reasoning_content: bool,
// ...
replay_reasoning_content: read_bool("requiresReasoningContentOnAssistantMessages", false),
```

`convert_messages` 在 `replay_reasoning` 为 true 且 thinking 非空时，把 thinking 作为独立的 `{"type":"reasoning"}` input item 发射，**且必须在该 assistant message 之前**（`openai_responses.rs:689-699`）：

```rust
// openai_responses.rs:689-699
// Servers that consume reasoning items merge them into
// the *following* assistant message, so this must be
// emitted before the message / function_call items.
ContentBlock::Thinking(th)
    if replay_reasoning && !th.thinking.is_empty() =>
{
    out.push(json!({
        "type": "reasoning",
        "summary": [{ "type": "summary_text", "text": th.thinking }],
    }));
}
ContentBlock::Thinking(_) => {}  // 不回放（非缓存精确场景）
```

**关键细节**：ordering is load-bearing。DS4 把 reasoning item **合并进它后面的 message**，所以 reasoning item 必须在前。单元测试 `thinking_replayed_as_reasoning_item_when_compat_requires`（`:957-990`）显式断言 `reasoning_idx < assistant_idx`。

**效果**：渲染历史跨 turn 字节相同，DS4 的 checkpoint 跨驱逐和服务器重启保持有效。

### 4.2 修复二：把 HTTP 409 视为可重试

**问题**：当 DS4 丢失某 session 的 live continuation state（被驱逐/重启），返回 `409 Conflict`，含义是"回放完整历史，我从磁盘 checkpoint 重建"。pie 本来就总是发送完整历史——所以对 pie 而言，**原样重试同一请求本身就是服务器要求的回放**。

**实现**：retry 层把 409 加入可重试状态集合。源码 `crates/ai/src/utils/retry.rs:139-144`：

```rust
fn is_retryable_status(status: reqwest::StatusCode) -> bool {
    let c = status.as_u16();
    // 409: local inference servers (ds4) ask the client to replay the full
    // history; pie always sends the full history, so a plain retry is that replay.
    c == 408 || c == 409 || c == 425 || c == 429 || (500..600).contains(&c)
}
```

单元测试 `conflict_is_retryable`（`:188-193`）固化此行为。原来会作为硬错误中断 session 的情况，现在在一个 round-trip 内透明自愈。

**前提条件**：409-retry-as-replay 正确性的前提是**客户端总是发送完整历史**（pie 的唯一模式）。如果客户端依赖服务端 continuation state（只发增量），409 重试不能直接复用，必须先补全历史。

### 4.3 修复三：在 `/cost` 报告 cache write

**问题**：DS4 报告非标准 usage 字段 `input_tokens_details.cache_write_tokens`——本次请求**新写入** prompt cache 的 token 数。此前 pie 只读 cache read，看不到缓存账本的写入侧。

**实现**：`update_usage` 把 `cache_write_tokens` 折进 `Usage.cache_write`，且 `total_tokens` 包含读写两侧（`openai_responses.rs:542-564`）：

```rust
// openai_responses.rs:549-563
if let Some(n) = val.pointer("/input_tokens_details/cached_tokens").and_then(|v| v.as_u64()) {
    usage.cache_read += n;
}
// Non-standard but reported by local inference servers (ds4): tokens newly
// written into the prompt cache this request.
if let Some(n) = val.pointer("/input_tokens_details/cache_write_tokens").and_then(|v| v.as_u64()) {
    usage.cache_write += n;
}
usage.total_tokens = usage.input + usage.output + usage.cache_read + usage.cache_write;
```

`Usage` 结构体显式有 `cache_read` / `cache_write` 双字段（`crates/ai/src/types.rs:312-318`）。

**这同时是验证工具**：健康 session 上每轮应看到大 `cache read` + 小 `cache write`。若 cache read 中途坍塌为零，说明前缀已分歧——修复一之后不应再发生。

### 4.4 范围与门控（不硬编码到 DS4）

三项改动都有门控，其他 backend 不受影响（`docs/ds4.md` Scope 节）：

- reasoning 回放仅在 model descriptor 设置 `requiresReasoningContentOnAssistantMessages` 时激活；
- `cache_write_tokens` 仅当服务器发送时才读；
- 409-retry 是通用的，对任何"客户端总是发完整历史"的 API 都正确。

> 任何做字节精确前缀缓存且消费 reasoning input item 的本地服务器，在 `models.json` 里设同样 compat flag 即可获得相同行为——没有一处硬编码到 ds4。

---

## 5. 其他缓存相关细节

### 5.1 prompt_cache_key / session 路由

`build_request_body`（`openai_responses.rs:588-596`）支持 OpenAI Responses 的 `prompt_cache_key`（设为 session_id）和 `prompt_cache_retention`（Long 模式 + provider 支持 24h）。这是托管 Provider 侧的缓存亲和（让同一 session 路由到同一缓存分区），与 DS4 的字节精确客户端侧缓存是互补的两层。

```rust
// openai_responses.rs:588-596
let retention = options.cache_retention.unwrap_or(CacheRetention::Short);
if !matches!(retention, CacheRetention::None) {
    if let Some(sid) = &options.session_id {
        body["prompt_cache_key"] = json!(sid);
    }
    if matches!(retention, CacheRetention::Long) && compat.supports_long_cache_retention {
        body["prompt_cache_retention"] = json!("24h");
    }
}
```

注意 `crates/ai/src/providers/openai_prompt_cache.rs` 目前是 TODO placeholder（仅 `pub fn placeholder() {}`），真正的逻辑在 `openai_responses.rs` 内联实现。

### 5.2 重试退避细节

`backoff_delay`（`retry.rs:160-168`）：`Retry-After` header 优先（capped by max_retry_delay_ms），否则指数退避 `base << attempt(min 6)` + 1-100ms jitter，整体 capped。`Retry-After` 超过 cap 时直接返回 `DelayTooLong` 错误（不静默截断）。`send_with_retry` 用 `reqwest::RequestBuilder::try_clone` 判断请求可重放性（streaming body 不可 clone → 降级为单发）。

---

## 6. 与 nop-ai-agent 的对照：三个缺口

| # | pie 经验 | nop-ai-agent 现状 | 缺口性质 |
|---|---------|-------------------|---------|
| 1 | reasoning 内容是 KV checkpoint 的一部分，字节精确缓存场景**必须**作为 reasoning input item 回放（且顺序 load-bearing） | §3.3 易错点写"DeepSeek 的 `reasoning_content` 则**不应**回传"，§八未涉及 reasoning 回放 | **误导性表述 + 缺失机制**。文档把"托管 API 正确性"（hosted DeepSeek API 不接受 reasoning_content 入参）与"缓存正确性"（本地 DS4 的 KV checkpoint 含采样 reasoning）混为一谈。两者方向相反 |
| 2 | HTTP 409 = 服务器丢失 live state，重试即回放重建；前提是客户端总发完整历史 | §七 retry 覆盖 408/425/429/5xx + 429 子类型 + 流式保护 + 图片 fallback，**无 409**，无"缓存丢失恢复"语义 | **缺失错误语义**。409-Conflict-as-cache-rebuild 是本地推理服务器的约定，对面向大规模自动化的 nop-ai-agent（可能对接本地模型）有实际价值 |
| 3 | cache_write 必须独立记账，是缓存健康度的验证工具 | `tokens_cache_write` schema 列存在但**runtime 从不写入**（`nop-ai-agent-usage-and-billing.md` §2.1）；§八未提缓存记账的验证作用 | **记账缺口（已记录但未实现）**。pie 证明 cache_write 是诊断缓存分歧的关键信号 |

### 6.1 缺口一详解：§3.3 表述需修正

当前 §3.3 原文：

> Anthropic Extended Thinking 要求多轮对话时把上一轮的 thinking block（含 thinkSignature）原样回传，否则 API 报错。DeepSeek 的 reasoning_content 则**不应**回传。两个 Provider 的行为相反，容易搞混。

**问题**："不应回传"只对**托管 DeepSeek API** 成立（API 入参不接受 reasoning_content）。但 nop-ai-agent §八把前缀缓存作为 Layer 2 关注点，而**字节精确 KV 前缀缓存恰恰要求 reasoning 必须回放**（它是 KV checkpoint 的一部分）。一句"不应回传"会让读者误以为所有 DeepSeek 场景都不回放，从而在对接本地 DS4 类服务器时主动破坏自己的缓存。

**正确表述应区分两个层面**：
- **API 正确性**（hosted DeepSeek API）：reasoning_content 不入 messages 入参；
- **缓存正确性**（本地字节精确 KV 缓存服务器）：reasoning 必须按服务器期望的 item 类型 + 顺序回放，否则前缀分歧。

且二者应通过**per-model compat flag**（如 pie 的 `requiresReasoningContentOnAssistantMessages`）在 Dialect/Provider 层选择，而非全局硬规则。

### 6.2 缺口二详解：409 应进入重试语义

nop-ai-agent §七的三种重试模式（NoRetry/Standard/Persistent）+ 429 子类型分类很完善，但 ErrorClassification 枚举里没有"缓存状态丢失"这一类。对于本地推理服务器，409 是一个**可自愈**的瞬态错误（只要客户端总发完整历史）。建议在 ErrorClassification 增加 `CACHE_STATE_LOST`（对应 409），归类为 TRANSIENT，重试策略 = 原样重发（因为它就是回放）。前提：nop-ai-agent 的 ReAct 引擎总是用完整历史重建上下文（`buildBaseExecutionContext` 全量 replay）——这正是现状，满足 409-retry-as-replay 的正确性前提。

### 6.3 缺口三详解：cache_write 记账是验证手段

`nop-ai-agent-usage-and-billing.md` §2.1 已经记录"runtime 从不写入 token 分项"的问题，且 schema 里 `tokens_cache_write` 已存在。pie 的经验补充了一个**动机论证**：cache_write 不只是成本数据，更是**缓存健康度的运行时诊断信号**——健康 session 上 cache write 应远小于 cache read，cache read 坍塌为零 = 前缀分歧。因此前缀缓存设计文档应把"缓存流量双侧记账"列为缓存命中的可观测性保障，而非仅是计费需求。

---

## 7. 可借鉴 / 不可借鉴

### 可借鉴

1. **per-model compat flag 选择 reasoning 回放策略**（`requiresReasoningContentOnAssistantMessages`）——在 ILlmDialect / Provider 适配层用配置驱动，而非全局规则。对应 nop-ai-agent：可作为 `ILlmDialect` 的能力 flag 或 `{provider}.llm.xml` 的配置项。
2. **409 = 缓存状态丢失，可重试**（前提：客户端总发完整历史）——纳入 ErrorClassification。
3. **cache_write 独立记账 + 作为缓存健康诊断信号**——强化 `nop-ai-agent-usage-and-billing.md` 的实现动机，并在前缀缓存文档建立交叉引用。
4. **"ordering is load-bearing"的显式约束**——DS4 把 reasoning item 合并进**后续** message，故 reasoning item 必须在前。任何 reasoning 回放实现都需文档化此 ordering 约定并有单元测试固化（pie 的 `reasoning_idx < assistant_idx` 断言是好范例）。
5. **三项改动全部门控、零硬编码到特定服务器**——可移植性设计范式。

### 不可借鉴 / 已有更好方案

1. **pie 的 prefix cache 实现散落在 `openai_responses.rs` 内联**——nop-ai-agent 的 `ILlmDialect.convertMessage()` 纯函数契约是更干净的抽象（§8.2），reasoning 回放策略应在 Dialect 层实现而非散落。
2. **pie 的 `openai_prompt_cache.rs` 是空 placeholder**——说明 pie 的缓存逻辑尚未模块化，nop-ai-agent 不应照搬这种散落状态。
3. **Loops / triage inbox**——pie 独特的产品定位（个人终端 agent），nop-ai-agent 定位为引擎层，调度/通知由 nop-job / Channel Connector 承接，不需要在引擎内复制此特性。
4. **pie 用 Rust，无 XDSL**——nop-ai-agent 的配置驱动（XDSL + `{provider}.llm.xml`）比 pie 的 `models.json` + 编译期 compat 结构体更具扩展性。

---

## 8. Conclusion

pie 的核心价值贡献是**把"前缀缓存命中"从原则声明落地为三项可操作的运行时机制**，且全部在 Provider 适配层、全部门控、零硬编码。这三项机制（reasoning 回放 / 409 重试 / cache_write 记账）精准对应 nop-ai-agent §八前缀缓存设计的三个运行时缺口。

**沉淀去向**：本分析的结论已沉淀到 `ai-dev/design/nop-ai-agent/nop-ai-agent-llm-layer.md`：
- §3.3 修正"DeepSeek reasoning_content 不应回传"的误导性表述（区分 API 正确性 vs 缓存正确性）；
- §七 retry 增加 409 / 缓存状态丢失语义；
- §八 前缀缓存设计从 4 子节扩展，补入 reasoning 回放策略、缓存丢失恢复、缓存流量记账三个子节。

**被否决的方案**：在 `ChatMessage` 上加 `frozen`/`prefix` 标记。原因：nop-ai-agent §8.3 已明确"前缀不变性是引擎层约束，不是 API 层数据属性"，pie 也未在消息模型上加标记（而是 Provider 层 compat flag 驱动），两条独立证据一致否定此方向。

## References

- pie 源码：`~/ai/pie/crates/ai/src/providers/openai_responses.rs`、`~/ai/pie/crates/ai/src/utils/retry.rs`、`~/ai/pie/crates/ai/src/types.rs`
- pie 文档：`~/ai/pie/docs/ds4.md`、`~/ai/pie/docs/loops.md`、`~/ai/pie/CLAUDE.md`
- nop-ai-agent 设计：`ai-dev/design/nop-ai-agent/nop-ai-agent-llm-layer.md`（§3.3 / §七 / §八）、`ai-dev/design/nop-ai-agent/nop-ai-agent-usage-and-billing.md`（§2.1）
- 前序调研：`ai-dev/analysis/agent-survey/2026-06-10-reasonix-prefix-cache-immutability.md`（Reasonix 前缀缓存不变性源码级分析）、`ai-dev/analysis/2026-06-10-nop-prefix-cache-design-analysis.md`（Nop 前缀缓存设计构想，§8.1–8.3 原则层来源，已被 supersede）
