# Agent Design Elements — Structured Cross-Project Summary

> Status: open
> Date: 2026-06-05
> Scope: 12 agent projects under ~/ai/ — design elements extraction
> Purpose: Input for "necessary parts, differentiators, standardization" synthesis

---

## Legend

| Symbol | Meaning |
|--------|---------|
| ✓ | Present, mature |
| ◐ | Present, partial/early |
| ✗ | Absent |
| — | Not applicable (by design) |

---

## 1. Core Loop Pattern

| Project | Sync/Async | Streaming | Event Types | Loop Style | Special |
|---------|-----------|-----------|-------------|------------|---------|
| **pi-agent** | Async (TS) | EventStream (assistant message events) | `agent_start/end`, `turn_start/end`, `message_start/update/end`, `tool_exec_start/update/end` | Agent loop: `prompt()→stream→tools→continue` | Steering/Follow-up queue (inject messages mid-run) |
| **oh-my-pi** | Async (TS+Bun) | Inherits pi EventStream | 22+ ExtensionEvent types | Same as pi, enhanced with TTSR stream rules | **TTSR**: regex-triggered stream interruption + injection; **Hashline** content-hash edits |
| **oh-my-claudecode** | Async (TS) | Claude Code streaming | 11 lifecycle hooks (UserPromptSubmit, PreToolUse, PostToolUse, Stop, PreCompact, SubagentStart/Stop, etc.) | Hook-driven loop; Stop-hook implements Sisyphean model | **Sisyphean**: Stop-hook intercepts exit, checks todo, forces continuation |
| **oh-my-opencode** | Async (TS/Bun) | OpenCode streaming | 54+ hook types (chat.params, chat.message, tool.execute.before/after, event, command.execute.before) | Hook-driven loop; 10 Discipline Agents | **IntentGate**: analyzes user intent before classification/execution |
| **VoltAgent** | Async (TS) | Vercel AI SDK v6 streaming | 13 agent hooks (onStart, onEnd, onHandoff, onToolStart/End, onError, onRetry, onFallback, etc.) | Agent.generateText/streamText + tool loop | **Bail signal**: sub-agent can `bail(result)` early exit |
| **DeepAgents** | Sync + Async (dual) | LangGraph streaming | Middleware before/after each step | LangGraph StateGraph execution | **Middleware pipeline**: 7 standard middlewares compose the agent |
| **AgentScope Java** | Reactive (Project Reactor) | Flux<Event> streaming | 11 hook events (PreCall, PreReasoning, ReasoningChunk, PostReasoning, PreActing, ActingChunk, PostActing, PreSummary, SummaryChunk, PostSummary, PostCall, Error) | ReAct loop: reasoning→acting→summarizing, streaming-first | **Graceful shutdown** with state save; pending tool recovery |
| **Solon AI** | Sync + Reactive (streaming only) | Flux<ChatResponse> streaming | Interceptor chain | Three-tier: SimpleAgent (single call) → ReActAgent (Think→Act→Observe) → TeamAgent (Flow graph) | **8 collaboration protocols** in TeamAgent |
| **Spring AI Alibaba** | Reactive (Spring Reactor) | Flux streaming | 4-position hooks (BEFORE/AFTER AGENT/MODEL) + Interceptors | StateGraph (nodes+edges) + ReactAgent (LLM+Tool+Hook nodes) | **JumpTo**: hooks can redirect execution flow |
| **PilotDeck** | Async (TS, AsyncGenerator) | AsyncGenerator<AgentEvent> | 35+ event types | AgentLoop.run() yields events; auto-compression → routing → model → tool exec → circuit breaker | **Content Gate**: once yielded to consumer, fallback locked; **Circuit breaker**: 3 consecutive empty tool turns → abort |
| **Hermes Agent** | **Sync** (Python, no async) | Sequential responses | 6 lifecycle hooks (pre/post_tool_call, pre/post_llm_call, on_session_start/end) | Simple while loop: model call → tool calls → repeat | **Budget grace call**: one extra call after budget exhaustion |
| **DeepSeek-Reasonix** | Async (TS) | Streaming with cache-aware ordering | Event-sourced (CQRS): events + pure-function reducers | Dispatch: prefix-locked → append log → volatile scratch → tool exec → repair | **Event Sourcing kernel**: state rebuilt from events; **Cache-First 3-zone context** |

---

## 2. Tool System

| Project | Schema Validation | Registration | Execution Pipeline | Parallelism | Special |
|---------|------------------|-------------|-------------------|-------------|---------|
| **pi-agent** | TypeBox schema | `AgentTool<TParameters>` | beforeToolCall/afterToolCall hooks | ✓ parallel + sequential modes | 7 built-in tools (read, bash, edit, write, grep, find, ls) |
| **oh-my-pi** | TypeBox + custom | Inherits pi + extensions + MCP | Same as pi, Rust-native execution | ✓ parallel (Rust in-process) | **Hashline** edit (content-hash anchored, 68.3% success); **ast_grep/ast_edit** (50+ tree-sitter grammars) |
| **oh-my-claudecode** | Zod + AJV | MCP tools + Claude Code native | PreToolUse/PostToolUse hooks + approval | ◐ (team mode via tmux) | 19 specialized agents as tool-callable; LSP tools (12), AST tools (2), Python REPL |
| **oh-my-opencode** | Zod v4 | Plugin API + MCP + hooks | tool.execute.before/after hooks | ◐ (team mode via tmux) | ast-grep MCP (25 languages); Context7 docs lookup; Grep.app GitHub search |
| **VoltAgent** | Zod-first | `createTool()` with Zod params/output schema | onStart/onEnd hooks + needsApproval gate | ✓ (AI SDK managed) | **Tool routing**: large tool pools auto-generate `search_tools`+`call_tool`; **Toolkit grouping**; client-side tools (no execute) |
| **DeepAgents** | Pydantic / LangChain | `@tool` decorator / LangChain StructuredTool | Middleware before/after agent step | ✓ (LLM decides) | **SubAgentMiddleware**: task tool spawns temporary child agent |
| **AgentScope Java** | JSON Schema (victools) | `@Tool` annotation (scan POJO) or `AgentTool` interface | Hook pipeline (PreActing→ActingChunk→PostActing) | ✓ parallel/sequential, configurable | **SubAgentTool** (agent as tool), **SchemaOnlyTool** (external tool), **McpTool** |
| **Solon AI** | JSON Schema | `@ToolMapping` annotation + FunctionTool | Interceptor chain | ✓ (Solon Flow parallel) | **Talent dynamic injection**: `getTools(Prompt)` returns context-dependent tools; 17 pre-built Talents |
| **Spring AI Alibaba** | Spring AI ToolCallback | `@Tool` + programmatic AsyncToolCallback | Interceptor (ToolInterceptor) + Hook (BEFORE/AFTER) | ✓ CompletableFuture + Semaphore concurrency limit | **StateAwareToolCallback**, **CancellableAsyncToolCallback**, dynamic injection via ModelInterceptor |
| **PilotDeck** | JSON Schema (per-tool) | Plugin manifest + builtin | Interrupt check → lookup → validate → PreToolUse hook → permission → audit → execute → size limit → PostToolUse hook | ✓ ConcurrentToolScheduler (safe parallel) + SequentialToolScheduler | **CanonicalToolCall** abstraction; 19 built-in tools |
| **Hermes Agent** | JSON Schema (per-tool) | `registry.register()` auto-discovery from tools/*.py | pre_tool_call/post_tool_call plugin hooks | ✓ (batch delegate_task) | **82+ tools in 43 toolsets**; terminal with 6 backends (local/Docker/SSH/Modal/Daytona/Singularity) |
| **DeepSeek-Reasonix** | JSON Schema | Builtin + MCP | Repair pipeline (flatten→scavenge→truncation→storm) → permission → execute | ✓ `parallelSafe` flag, grouped dispatch | **4-stage tool-call repair**: flatten (>10 params), scavenge (recover from reasoning), truncation (fix broken JSON), storm (deduplicate call-storms) |

---

## 3. Memory System

| Project | Short-term | Long-term | Storage | Retrieval | Compression |
|---------|-----------|-----------|---------|-----------|-------------|
| **pi-agent** | Transcript (in-memory) | ✗ | JSONL session files | Session tree navigation | ✓ Context Compaction (auto-summarize near token limit) |
| **oh-my-pi** | Transcript + readFileState | **Hindsight** retain/recall/reflect | Project memory bank (files) | recall tool (semantic?) + auto-load mental model on first turn | ✓ Inherits pi compaction + TTSR survives compaction |
| **oh-my-claudecode** | Notepad (priority/working/manual) | **Project Memory** (read/write/add_note) | better-sqlite3 | Project memory read/write MCP tools | ✓ PreCompact hook saves state before compaction |
| **oh-my-opencode** | Session transcript | boulder-state work tracking | File-based + boulder-state package | Session recovery from errors/context limits | ✓ **Preemptive Compaction**: proactive window management |
| **VoltAgent** | Conversation (StorageAdapter) | **Working Memory** (per-session KV, JSON or Markdown) | 5 backends (LibSQL, PostgreSQL, Supabase, Cloudflare D1, VoltOps) + InMemory | **Semantic search** via EmbeddingAdapter + VectorAdapter | ✗ (AI SDK manages context) |
| **DeepAgents** | LangGraph state (memory) | AGENTS.md (loaded as system prompt) | Backend protocol (memory, filesystem, store, composite) | ✗ (explicit memory middleware) | ✓ **SummarizationMiddleware**: configurable triggers (token/msg count/window %); history offloading + parameter truncation |
| **AgentScope Java** | InMemoryMemory (CopyOnWriteArrayList) | **LongTermMemory** (Mem0, Bailian extensions) | Session (InMemory, Json, Redis, MySQL) | ✗ (direct access) | ◐ (summarizing() at max iterations) |
| **Solon AI** | FlowContext JSON | Session (InMemory, File, Redis) | Session interface (key-value) | ✗ (direct access) | ✓ ReActAgent has context compression |
| **Spring AI Alibaba** | OverAllState (graph state) | **CheckpointSaver** | 6 backends (PostgreSQL, MySQL, Oracle, MongoDB, Redis, FileSystem) | Checkpoint resume | ✗ (context engineering is manual) |
| **PilotDeck** | Append-only log + volatile scratch | **EdgeClaw** white-box memory (~14.7K lines) | File-based (JSON/JSONL, no DB) | EdgeClaw retrieve(); FTS5 session search | ✓ **Three-tier**: MicroCompaction (truncate tool_result) → SnipEngine (trim middle turns) → CompactionEngine (LLM summarization) |
| **Hermes Agent** | Conversation history | **9 memory provider plugins** (honcho, mem0, supermemory, etc.) | SQLite (FTS5) + provider-specific | FTS5 full-text search → LLM summarization → context injection | ◐ (LLM summarization of search results, but no auto-compression engine) |
| **DeepSeek-Reasonix** | **Immutable Prefix** + **Append-Only Log** + **Volatile Scratch** | User memory (`~/.reasonix/memory/`) + Project memory (REASONIX.md) | File-based | ✗ (text matching only) | ✓ **Turn-end auto-compress**: tool results >3000 tokens compressed at turn end |

---

## 4. Model Provider Abstraction

| Project | Abstraction | Multi-Provider | Provider Count | Special |
|---------|------------|---------------|----------------|---------|
| **pi-agent** | `Model<TApi>` + `ApiProvider` + `apiProviderRegistry` | ✓ | 34 known providers, 9 API types | OAuth support (GitHub Copilot, OpenAI Codex, Anthropic); auto-generated `models.generated.ts` |
| **oh-my-pi** | Inherits pi + extended | ✓ | 40+ providers + 14 web search backends | **Model-per-role**: different models for default/smol/slow/plan/commit roles |
| **oh-my-claudecode** | Claude Code host + Codex CLI + Gemini CLI | ✓ (3 ecosystems) | 3 (Claude, Codex, Gemini) | **CCG**: three-model advisor synthesis; complexity-scored routing (LOW/MEDIUM/HIGH) |
| **oh-my-opencode** | Per-agent model binding with fallback chains | ✓ | 7+ (Claude, GPT, Gemini, Kimi, GLM, MiniMax, Qwen) | **Discipline Agent**: per-model deep prompt engineering; **Category delegation** (visual-engineering, deep, quick, ultrabrain) |
| **VoltAgent** | Vercel AI SDK v6 (19 @ai-sdk/* packages) | ✓ | 24+ providers | **ModelProviderRegistry** from models.dev (auto-generated, lazy load); **Model fallback** array |
| **DeepAgents** | LangChain ChatModel (any LangChain-compatible) | ✓ | Any LangChain provider | **Provider-agnostic** by design; default Claude Sonnet 4.5 |
| **AgentScope Java** | `Model` interface (2 methods: stream + getName) | ✓ | 5 implementations (DashScope, OpenAI, Gemini, Anthropic, Ollama) | **Formatter pattern**: per-provider message format converter |
| **Solon AI** | `ChatModel` + `ChatDialect` | ✓ | 5 dialects (OpenAI, Ollama, DashScope, Gemini, Anthropic) | **Dialect SPI**: Solon IoC `AiPlugin` scan + static fallback registration |
| **Spring AI Alibaba** | Spring AI `ChatModel` | ✓ | Any Spring AI ChatModel | Provider-agnostic interface; DashScope in separate repo |
| **PilotDeck** | **Canonical Message** format + 2 protocol adapters (Anthropic, OpenAI) | ✓ | 30+ models via 2 protocols | **Smart Router**: session sticky → custom router → scenario detect → token saver (judge model classify complexity) → auto-orchestration → fallback chain |
| **Hermes Agent** | **29 model provider plugins** | ✓ | 28+ providers | **Lazy dependency install**: provider-specific deps installed on demand; **ProviderProfile** registration |
| **DeepSeek-Reasonix** | Single-provider (DeepSeek only) | ✗ (by design) | 1 (DeepSeek) | **Cache-First**: prefix hash fixed per session, 99.82% cache hit; **Model self-upgrade**: `<<<NEEDS_PRO>>>` marker triggers pro model retry |

---

## 5. Routing / Cost Control

| Project | Smart Routing | Model Selection | Budget Tracking | Special |
|---------|-------------|----------------|-----------------|---------|
| **pi-agent** | ✗ | User-configured default model | ✗ | Config hierarchy for model defaults |
| **oh-my-pi** | ✓ Model-per-role (default/smol/slow/plan/commit) | Per-role configurable | ◐ (cost tracking via stats dashboard) | 14 web search backends |
| **oh-my-claudecode** | ✓ **Three-tier** (LOW=haiku, MEDIUM=sonnet, HIGH=opus) | Complexity scoring + agent-specific override + auto-upgrade | ◐ (claims 30-50% token savings) | Cross-provider validation (Claude+Codex+Gemini) |
| **oh-my-opencode** | ✓ **Category delegation** (visual-engineering, deep, quick, ultrabrain) | Agent declares category → harness maps to model | ✗ | IntentGate pre-classification |
| **VoltAgent** | ✗ (user selects model) | Per-agent model config + fallback array | ✗ | Model fallback array on Agent |
| **DeepAgents** | ✗ | User-specified at creation | ◐ (iteration budget in AIAgent) | SummarizationMiddleware auto-configures from model's max_input_tokens |
| **AgentScope Java** | ✗ | User-specified | ✗ | Graceful shutdown saves state |
| **Solon AI** | ✗ | User-specified per ChatModel | ✗ | — |
| **Spring AI Alibaba** | ✓ LlmRoutingAgent | LLM decides routing | ✗ | ToolCallLimitHook enforces tool call limits |
| **PilotDeck** | ✓ **Smart Router** (6-step pipeline) | Token Saver: judge model → simple/medium/complex → route to cost model | ✓ Per-session cost tracking | **~77% cost savings** (README A/B data); daily budget for Always-on |
| **Hermes Agent** | ◐ | User-specified per profile | ✓ iteration_budget + budget_grace_call | Profile isolation; per-profile API key pools |
| **DeepSeek-Reasonix** | ✓ **Flash-first** (flash/auto/pro) | Turn-end auto-compress + model self-upgrade (`<<<NEEDS_PRO>>>`) | ✓ **Four-tier cost control**; per-turn cost display (green/yellow/red) | **99.82% cache hit** saves ~80% cost; all auxiliary calls hardcoded to v4-flash |

---

## 6. Multi-Agent

| Project | Sub-Agent | Delegation | Supervisor | Orchestration | Isolation |
|---------|----------|-----------|-----------|--------------|-----------|
| **pi-agent** | ✗ | ✗ | ✗ | Single agent loop | ✗ |
| **oh-my-pi** | ✓ In-process sub-agent | `createAgentSession()` nested | ✗ | Schema-validated output | Git worktree (APFS/btrfs clones) |
| **oh-my-claudecode** | ✓ 19 specialized agents | Agent MD files + task tools | ✓ **Team staged pipeline** (plan→prd→exec→verify→fix loop) | tmux CLI workers (real processes); lead+member pattern | Git worktree per worker |
| **oh-my-opencode** | ✓ 10 Discipline Agents | Category delegation + IntentGate | ✓ Team Mode (lead + 8 members) | tmux real-time visualization; hyperplan (5 critics) | Git worktree |
| **VoltAgent** | ✓ SubAgent auto-converted to tools | `andAgent()` in workflow | ✓ Supervisor config with systemMessage | PlanAgent (write_todos + task sub-agents); 16-step workflow DSL | Bail signal for early exit |
| **DeepAgents** | ✓ `SubAgent` (declarative) | `SubAgentMiddleware` (task tool) | ✗ | LLM decides delegation | **State filtering**: parent state doesn't leak to child; independent context window |
| **AgentScope Java** | ✓ SubAgentTool | Agent as tool | ◐ (Pipeline patterns) | SequentialPipeline + FanoutPipeline + MsgHub (pub/sub) | Independent memory per agent |
| **Solon AI** | ✓ TeamAgent nesting | 8 protocols | ✓ HIERARCHICAL protocol | SEQUENTIAL, HIERARCHICAL, SWARM, MARKET_BASED, CONTRACT_NET, BLACKBOARD, A2A, NONE | Board→Tenant isolation in HIERARCHICAL |
| **Spring AI Alibaba** | ✓ `asNode()` nesting | Sequential/Parallel/Routing/Loop agents | ✓ SequentialAgent chain | StateGraph (directed graph) + SubGraphNode + ParallelNode | A2aRemoteAgent for cross-service |
| **PilotDeck** | ✓ 4 built-in types (general/explore/plan/verify) | Fork message history | ✗ | Single parent dispatches sub-agents | **Fork model**: clone history + isolate tool registry; depth limit |
| **Hermes Agent** | ✓ delegate_task (single/batch) | role="leaf"/"orchestrator" | ◐ Kanban board | **Kanban**: SQLite-backed persistent board + Dispatcher (60s polling) | Profile isolation; max_concurrent_children (default 3) |
| **DeepSeek-Reasonix** | ✓ Simple sub-agent spawn | flash+high for sub-agents | ✗ | Single-task spawn, no orchestration | ✗ |

---

## 7. Workflow / Engine

| Project | DAG | Chain | State Machine | Declarative DSL | Suspend/Resume |
|---------|-----|-------|--------------|----------------|----------------|
| **pi-agent** | ✗ | ✗ | ✗ | ✗ | ✗ |
| **oh-my-pi** | ✗ | ✗ | ✗ | ✗ | ✗ |
| **oh-my-claudecode** | ✗ | ✓ Team staged pipeline | ✓ Phase controller | Agent MD + hook scripts | ◐ (pre-compact state save) |
| **oh-my-opencode** | ✗ | ✗ | ✓ boulder-state | ✗ | ✓ Session recovery |
| **VoltAgent** | ✓ (workflow chain) | ✓ `.andThen()` | ✓ `.andDoWhile()` | ✓ **16 step types** (andThen, andWhen, andAll, andRace, andForEach, andBranch, andGuardrail, andSleep, etc.) | ✓ State persisted to StorageAdapter; **Time Travel** replay |
| **DeepAgents** | ✓ (LangGraph StateGraph) | ✓ Sequential | ✓ (LangGraph) | ✓ Python (LangGraph API) | ✓ LangGraph checkpointing + Studio |
| **AgentScope Java** | ✗ | ✓ SequentialPipeline | ✗ | ✗ | ✗ (but graceful shutdown saves state) |
| **Solon AI** | ✓ Solon Flow graph | ✓ AiFlow YAML | ✓ Flow engine | ✓ **YAML flow** (Dify-style); Agent-as-Flow-Node | ✓ FlowContext JSON serialization/deserialization |
| **Spring AI Alibaba** | ✓ **StateGraph** (nodes+edges) | ✓ SequentialAgent | ✓ Conditional routing | ✓ Java fluent API | ✓ CheckpointSaver (6 backends) + InterruptableAction (HITL) |
| **PilotDeck** | ✗ | ✓ Always-on 5-stage | ✓ Cron state machine | ✗ | ✓ Transcript replay for resume |
| **Hermes Agent** | ✗ | ✗ | ✗ | ✗ | ✗ |
| **DeepSeek-Reasonix** | ✗ | ✗ | ✓ Event Sourcing (CQRS) | ✗ | ✓ Event replay rebuilds state |

---

## 8. Plugin / Extension

| Project | Hook System | Contribution Points | Skill System | Marketplace | Special |
|---------|------------|-------------------|-------------|-------------|---------|
| **pi-agent** | 22 ExtensionEvent types | Tool, command, shortcut, flag, provider, UI component registration | ✓ SKILL.md format | ✗ (discovery paths: .pi/extensions/) | ExtensionFactory + ExtensionAPI surface |
| **oh-my-pi** | 22+ events (inherits pi) | Same as pi + MCP tools | ✓ Skills (inherits pi) | ✗ | Cross-tool config inheritance (8 formats); SDK + RPC + ACP embedding |
| **oh-my-claudecode** | 11 lifecycle hooks | Agent MD files, MCP tools, skills | ✓ ~40 built-in skills; `/skillify` extracts from debug sessions | ✗ | **Magic keywords** trigger behavior enhancement |
| **oh-my-opencode** | 54+ hooks | Plugin API, MCP, agents | ✓ Skills (configurable IDs) | ✗ | `/init-deep` layered AGENTS.md generation |
| **VoltAgent** | 13 agent hooks | Tool, Memory adapter, Workflow step, Guardrail, MCP | ✗ | ✗ | MCP Client+Server; A2A server; AG-UI adapter |
| **DeepAgents** | Middleware before/after | Middleware (add tools, modify prompt, intercept steps) | ✓ SKILL.md + YAML frontmatter (agentskills.io spec) | agentskills.io (emerging) | **Progressive disclosure**: metadata in prompt, full instructions on demand |
| **AgentScope Java** | 11 hook events | Extension modules (25+) | ✗ | ✗ | Studio debug tool; Kotlin DSL extension |
| **Solon AI** | Interceptor chain | Talent, Tool, Flow component, MCP | ✓ 17 pre-built Talents | ✗ | **Talent dynamic admission**: `isSupported(Prompt)` activates only when relevant |
| **Spring AI Alibaba** | 4-position hooks + Interceptors | Tool, Hook, Interceptor, Checkpoint backend | ✗ | ✗ | Studio (embedded debug UI) + Admin (full platform, Dify-style) |
| **PilotDeck** | 28 hook events | **7 contribution types**: Tool, Command, Hook, MCP Server, Permission Rule, Prompt, Router | ✓ 6 built-in skill packs | ✗ | **5 hook executors**: Agent, Callback, Command (shell), HTTP, Prompt |
| **Hermes Agent** | 6 lifecycle hooks | **3 plugin faces**: General, Memory Provider, Model Provider | ✓ **169 skill packs** (74 built-in + 95 optional) | agentskills.io (Skills Hub) | **Curator**: auto-create, review, improve, archive, pin skills; lazy dependency install |
| **DeepSeek-Reasonix** | ✗ (port/adapter only) | Port interfaces (6: ModelClient, ToolHost, EventSink, MemoryStore, HookRunner, CheckpointStore) | ✓ Skills loading | ✗ | **Hexagonal architecture**: ports vs adapters |

---

## 9. Permission / Security

| Project | Approval Gates | Guardrails | Sandboxing | HITL |
|---------|---------------|-----------|-----------|------|
| **pi-agent** | ✗ (tools execute freely) | ✗ | ✗ (bash executes directly) | ✗ |
| **oh-my-pi** | ◐ (inherits pi, minimal) | ✗ | ✓ pi-iso crate (APFS/btrfs/overlayfs isolation) | ✗ |
| **oh-my-claudecode** | ✓ PermissionRequest hook | ✗ | ✗ | ✓ HumanInTheLoop for designated tools |
| **oh-my-opencode** | ✓ tool.execute.before hooks | ✗ | ✗ | ✓ Background task approval |
| **VoltAgent** | ✓ `needsApproval` per tool | ✓ **Input/Output Guardrails** (first-class); 12 pre-built (PII, profanity, prompt injection, HTML sanitize, length limit); streaming guardrail support | ✓ Sandbox packages (E2B, Daytona, Blaxel) | ✓ needsApproval gate |
| **DeepAgents** | ✓ `interrupt_on` config | ✗ | ✓ Sandbox backends (Modal, Daytona, Runloop) | ✓ HumanInTheLoopMiddleware |
| **AgentScope Java** | ✓ Interrupt checkpoints | ✗ | ✓ Harness module (sandbox for untrusted tools) | ✓ UserAgent |
| **Solon AI** | ✓ Harness module permissions | ✗ | ✓ Harness sandbox | ✓ HITL interceptor |
| **Spring AI Alibaba** | ✓ HITL hook | ✗ | ✓ Sandbox module | ✓ HumanInTheLoopHook |
| **PilotDeck** | ✓ **5 permission modes** (default/plan/acceptEdits/bypassPermissions/dontAsk) | ✗ | ✓ WorkSpace isolation (git worktree + snapshot copy) | ✓ ask_user_question tool; Elicitation protocol |
| **Hermes Agent** | ✓ Plugin pre_tool_call hooks | ✗ | ✓ Terminal 6 backends (Docker/SSH/Modal/Daytona/Singularity) | ✓ clarify tool |
| **DeepSeek-Reasonix** | ✓ `/apply` review gate (SEARCH/REPLACE) | ✗ | ✗ | ✓ User must approve edits via /apply |

---

## 10. Channel / Transport

| Project | CLI | Web | IM Adapters | MCP | Other |
|---------|-----|-----|------------|-----|-------|
| **pi-agent** | ✓ TUI (custom framework) | ✗ | ✗ | ✗ | RPC mode (JSON-RPC over stdio for IDE) |
| **oh-my-pi** | ✓ TUI (enhanced pi) | ✓ Stats dashboard (SolidJS+React) | ✗ | ✓ MCP client | SDK (Node), RPC (stdio), ACP (editor protocol) |
| **oh-my-claudecode** | ✓ (Claude Code host) | ✗ | Telegram, Discord, Slack, webhook, OpenClaw | ✓ MCP tools (LSP, AST, Python REPL, Skills, State) | Notification system (Telegram, Discord, Slack, webhook) |
| **oh-my-opencode** | ✓ (OpenCode host) | ✗ | ✗ | ✓ MCP (websearch, Context7, Grep.app, ast-grep, LSP) | PostHog telemetry |
| **VoltAgent** | ✗ | ✓ HTTP (Hono, Elysia, serverless) | ✗ | ✓ **Client + Server** | A2A server, AG-UI (CopilotKit), Voice, Resumable Streaming (Redis) |
| **DeepAgents** | ✓ Textual TUI | ✗ | ✗ | ✗ | ACP integration; LangGraph Studio (debug) |
| **AgentScope Java** | ✗ | ✗ | ✗ | ✓ MCP client | Spring Boot/Quarkus/Micronaut starters |
| **Solon AI** | ✗ | ✓ (via Solon web) | ✗ | ✓ **Client + Server** (SSE, Streamable HTTP, STDIO) | A2A, ANP, ACP, AGUI adapters; framework-agnostic embedding |
| **Spring AI Alibaba** | ✗ | ✓ Studio (embedded debug) + Admin (full platform) | ✗ | ✓ MCP client | A2A (A2aRemoteAgent) |
| **PilotDeck** | ✓ CLI | ✓ React Web UI | ✓ **23 channels** (cli, tui, web, feishu, weixin, qq, telegram, discord, slack, matrix, mattermost, signal, whatsapp, bluebubbles, dingtalk, wecom, email, sms, homeassistant, api_server, webhook, test) | ✓ MCP client + resources | Gateway protocol (WebSocket server); Cron scheduling |
| **Hermes Agent** | ✓ Ink TUI (dual-process) | ✓ Dashboard (embedded TUI via PTY) | ✓ **30 platform adapters** (Telegram, Discord, Slack, WhatsApp, Signal, Matrix, Mattermost, Email, SMS, Home Assistant, Feishu×3, DingTalk, WeCom×2, WeChat, Yuanbao×4, BlueBubbles, Webhook, API Server, MS Graph, etc.) | ✗ | Voice memo transcription + TTS reply; Cron scheduling |
| **DeepSeek-Reasonix** | ✓ Ink TUI | ✓ Dashboard SPA (embedded HTTP server) | QQ, WeChat, Telegram | ✓ MCP client (stdio + SSE) | Tauri desktop client (pre-release); ACP adapter |

---

## 11. Context Management

| Project | Token Counting | Compression Strategy | Budget Awareness | Prompt Assembly |
|---------|---------------|---------------------|-----------------|----------------|
| **pi-agent** | Estimation | Auto-compaction near token limit (summarization) | ✓ Token estimation drives compaction | System prompt + messages + tools |
| **oh-my-pi** | BPE tokenizer (Rust tokens crate) | Compaction + **TTSR survives compaction** | ✓ | System prompt + mental model auto-load + tools |
| **oh-my-claudecode** | Host-managed | PreCompact hook saves state; host compacts | ◐ | Sisyphean Oath + agent prompts + project memory + notepad |
| **oh-my-opencode** | Host-managed | **Preemptive compaction** (proactive window management) | ✓ | Discipline Agent prompts (model-specific) + AGENTS.md + rules-engine |
| **VoltAgent** | AI SDK managed | ✗ | ◐ | instructions + model + tools + memory + retriever |
| **DeepAgents** | Model-aware (max_input_tokens) | **SummarizationMiddleware**: configurable triggers, history offloading, parameter truncation | ✓ | Middleware composes: system prompt, AGENTS.md, skills (progressive), tools |
| **AgentScope Java** | Model-reported | summarizing() at max iterations | ✓ max_iterations limit | Hook-injected prompts + tool descriptions |
| **Solon AI** | ChatModel managed | ReActAgent context compression | ◐ | ChatModel builder + Talent dynamic instructions |
| **Spring AI Alibaba** | Spring AI managed | Context engineering via hooks | ✓ ToolCallLimitHook | Hook + Interceptor composed prompts |
| **PilotDeck** | **Tiktoken o200k_base** (exact, not estimated) | **Three-tier**: MicroCompaction (truncate tool_result, no model) → SnipEngine (trim middle, keep head/tail anchors) → CompactionEngine (LLM summary) | ✓ Per-turn budget check; circuit breaker on 3 empty turns | CanonicalMessage format; router may inject orchestration prompt |
| **Hermes Agent** | ✗ (prompt caching) | LLM summarization of search results | ✓ iteration_budget + grace_call | Memory prefetch → system prompt → messages |
| **DeepSeek-Reasonix** | **DeepSeek tokenizer** (ported, exact) | **Turn-end auto-compress** (>3000 token tool results compressed); model self-upgrade to pro | ✓ Four-tier cost control; per-turn cost display | **Cache-First 3-zone**: Immutable Prefix (fixed hash) + Append-Only Log + Volatile Scratch (never uploaded) |

---

## 12. Observability

| Project | Tracing | Logging | Metrics | Dashboard |
|---------|---------|---------|---------|-----------|
| **pi-agent** | ✗ | ✓ JSONL session logs | ✗ | ✗ |
| **oh-my-pi** | ✗ | ✓ JSONL + audit | ✓ Stats dashboard | ✓ SolidJS+React stats dashboard |
| **oh-my-claudecode** | ✓ Trace tools | ✓ | ✗ | ✗ (but notification system) |
| **oh-my-opencode** | ✗ | ✓ | ✓ PostHog (anonymous, default on) | ✗ |
| **VoltAgent** | ✓ **OpenTelemetry** (WebSocketSpanProcessor → VoltOps Console) | ✓ | ✓ OTEL metrics | ✓ **VoltOps Console** (cloud/self-hosted: trace visualization, prompt builder, memory management, eval dashboard, RAG knowledge base) |
| **DeepAgents** | ✓ LangGraph Studio | ✓ | ✗ | ✓ LangGraph Studio (debug visualization) |
| **AgentScope Java** | ✓ OpenTelemetry | ✓ | ✓ OTEL | ✓ Studio debug tool |
| **Solon AI** | ✗ | ✓ Solon logging | ✗ | ✗ |
| **Spring AI Alibaba** | ✓ **Graph observation** (OTEL + starter) | ✓ | ✓ | ✓ **Studio** (embedded debug UI) + **Admin** (full platform, React) |
| **PilotDeck** | ✓ Depth tracking | ✓ JSONL transcript | ✗ | ✓ React Web UI |
| **Hermes Agent** | ✗ | ✓ SQLite FTS5 session search | ✗ | ✓ Embedded TUI dashboard (via PTY bridge) |
| **DeepSeek-Reasonix** | ✓ Event log (CQRS) | ✓ Transcript log + replay | ✓ Cache hit rate, cost per turn | ✓ Web Dashboard SPA (cost/token/cache real-time); Tauri desktop dashboard |

---

## 13. Session / State

| Project | Persistence Format | Resume | Transcript | Branching |
|---------|-------------------|--------|-----------|-----------|
| **pi-agent** | JSONL (append-only) | ✓ | ✓ | ✓ Session tree (branch/fork/navigate) |
| **oh-my-pi** | JSONL + SQLite (auth) | ✓ | ✓ | ✓ Inherits pi session tree |
| **oh-my-claudecode** | better-sqlite3 + JSONL | ✓ (Boulder state recovery) | ✓ | ✗ |
| **oh-my-opencode** | File-based + boulder-state | ✓ Session recovery | ✓ | ✗ |
| **VoltAgent** | StorageAdapter (5 backends + InMemory) | ✓ Workflow suspend/resume | ✓ (via StorageAdapter) | ✗ |
| **DeepAgents** | LangGraph BaseStore + CheckpointSaver | ✓ LangGraph checkpointing | ✓ (history offloading to backend) | ✓ LangGraph thread branching |
| **AgentScope Java** | Session (InMemory, Json, Redis, MySQL); StateModule save/load | ✓ Graceful shutdown + restore | ✓ (InMemory) | ✗ |
| **Solon AI** | FlowContext JSON | ✓ JSON serialization/deserialization | ✓ | ✗ |
| **Spring AI Alibaba** | CheckpointSaver (6 backends) + OverAllState | ✓ Checkpoint resume + InterruptableAction | ✓ (graph state snapshot) | ✓ Sub-graph branching |
| **PilotDeck** | JSON/JSONL (no database) | ✓ Transcript replay rebuilds AgentSession | ✓ JSONL + InMemory buffer + Chain | ✓ FileHistoryStore (undo/rollback) |
| **Hermes Agent** | SQLite (FTS5) | ✓ Checkpoint save/restore | ✓ (FTS5 full-text searchable) | ✓ Profile isolation (separate HERMES_HOME) |
| **DeepSeek-Reasonix** | Event log (append-only events) | ✓ Event replay rebuilds state | ✓ Transcript log | ✗ |

---

## 14. Configuration

| Project | Format | Hierarchy | Hot Reload | Profiles/Env |
|---------|--------|-----------|-----------|-------------|
| **pi-agent** | JSON | Default → Global (~/.pi/) → Project (.pi/) → CLI args | ✗ | ✗ |
| **oh-my-pi** | **YAML** (different from pi) | Global (~/.omp/) → Project (.omp/) → Path scope | ✓ Background persistence + sync get/set | ✗ |
| **oh-my-claudecode** | JSONC | Built-in → User (~/.config/) → Project (.claude/) → Env vars | ✗ | ✗ |
| **oh-my-opencode** | JSONC | User global → Project (.opencode/) walked to $HOME | ✗ | ✗ |
| **VoltAgent** | TypeScript (code-first) | Per-agent config in constructor | ✗ | ✗ |
| **DeepAgents** | Python (code-first) | Per-agent config in `create_deep_agent()` | ✗ | ✗ |
| **AgentScope Java** | Builder pattern | Per-agent config; Spring Boot/Quarkus starters for external | ✗ | GraalVM native image support |
| **Solon AI** | YAML (Solon properties) | Solon config hierarchy | ✓ Solon hot reload | ✓ Multi-framework support |
| **Spring AI Alibaba** | YAML (Spring properties) | Spring Boot config hierarchy | ✓ Spring DevTools | ✓ Spring profiles |
| **PilotDeck** | YAML (pilot config) | Global → Project → Path scope | ✓ Hot reload via gateway protocol | ✓ Profile instances |
| **Hermes Agent** | YAML (plugin.yaml) + Python config | Global (HERMES_HOME) → Per-profile isolation | ✗ | ✓ **Multi-profile**: fully isolated instances (separate config/API key/memory/sessions/skills/gateway) |
| **DeepSeek-Reasonix** | TypeScript (code-first) | Global (~/.reasonix/) → Project (REASONIX.md) → CLI flags | ✗ | ✗ |

---

## 15. Unique Differentiator

| Project | What Makes It Special |
|---------|----------------------|
| **pi-agent** | **Cleanest layered architecture** (4 packages: LLM API → Agent runtime → Coding agent → TUI) + most extensible plugin system (22 event types, register tools/commands/providers/UI) + session tree branching |
| **oh-my-pi** | **~32.6K lines Rust eliminating fork/exec** (all operations in-process via N-API) + **Hashline** content-hash-anchored editing (6.7% → 68.3% success) + **TTSR** stream-time travel rules + real LSP/DAP integration (13+27 operations) |
| **oh-my-claudecode** | **Sisyphean execution model** (Stop-hook ensures task completion, never gives up) + **19 specialized agents** as Markdown definitions + **Team staged pipeline** (plan→prd→exec→verify→fix) via tmux workers |
| **oh-my-opencode** | **Discipline Agent** pattern (per-model deep prompt engineering with fallback chains) + **193K lines of tests** (test > production code) + **IntentGate** intent analysis + **boulder-state** work tracking state machine |
| **VoltAgent** | **16-step workflow DSL** (andThen, andRace, andAll, andSleep, suspend/resume, time travel) + **three-adapter Memory** (Storage + Embedding + Vector) + **Guardrails as first-class concept** (input/output + streaming + 12 pre-built) + **VoltOps Console** platform |
| **DeepAgents** | **Backend Protocol** (pluggable storage/execution with CompositeBackend routing) + **Middleware pipeline** (7 composable middlewares) + **LangGraph-native** production features (streaming, checkpointing, Studio) + best context management (auto-summarize + history offload + param truncation) |
| **AgentScope Java** | **Full reactive architecture** (Project Reactor throughout) + **Formatter pattern** (per-provider message conversion) + **GraalVM native image** (200ms cold start) + most production-grade features in Java (graceful shutdown, interrupt, pending tool recovery) |
| **Solon AI** | **Widest Java compatibility** (JDK 8-26) + **8 collaboration protocols** (far exceeding peers) + **Talent dynamic admission** (context-aware skill activation) + **Agent-as-Flow-Node** + **framework-agnostic embedding** (Spring Boot, Vert.X, Quarkus, Micronaut) + **15 vector stores** + **deep MCP** (client+server+all transports) |
| **Spring AI Alibaba** | **Most mature graph engine in Java** (StateGraph rivaling LangGraph: conditional routing, sub-graph, snapshot, interrupt/resume) + **6 Checkpoint backends** + **4-position Hook model** with JumpTo flow control + **Studio + Admin** visualization tools |
| **PilotDeck** | **23 channel adapters** (broadest IM/web/CLI coverage) + **Smart Router with measured A/B savings** (~77%) + **Always-on 5-stage pipeline** (offline task discovery) + **EdgeClaw white-box memory** (auditable/editable/rollbackable) + **three-tier context compression** + **7 Plugin Contribution types** + **Canonical Message abstraction** |
| **Hermes Agent** | **Self-improvement loop** (Curator: auto-create, review, improve, archive skills) + **169 skill packs** (largest skill library) + **30 platform adapters** + **29 model providers** (lazy install) + **Kanban multi-agent collaboration** (SQLite-backed) + **Profile multi-instance isolation** |
| **DeepSeek-Reasonix** | **Cache-First architecture** (99.82% cache hit, 3-zone context: ImmutablePrefix + AppendOnlyLog + VolatileScratch) + **4-stage tool-call repair pipeline** (flatten/scavenge/truncation/storm) + **Event Sourcing kernel** (CQRS) + **four-tier cost control** + **DeepSeek tokenizer** (exact counting) |

---

## Cross-Cutting Synthesis: Standardized vs. Differentiated

### Near-Universal (Every Project Has These)
1. **Tool system** with schema validation (TypeBox/Zod/JSON Schema/Pydantic)
2. **Event/hook lifecycle** for intercepting agent steps
3. **Session persistence** (JSONL, SQLite, or database)
4. **Context/window management** (compaction, summarization, or truncation)
5. **LLM provider abstraction** (unified interface hiding provider differences)

### Common (Most Projects Have These)
6. **Sub-agent / delegation** (all except pi-agent have some form)
7. **MCP client** (9 of 12 support MCP)
8. **Configuration hierarchy** (global → project → CLI)
9. **Streaming response** handling
10. **Token counting** (estimated or exact)

### Differentiated (Only Some Projects Excel)
11. **Smart routing / cost control** — only PilotDeck, Reasonix, oh-my-claudecode, oh-my-opencode have sophisticated routing
12. **Workflow engine** — only VoltAgent (16-step DSL), DeepAgents (LangGraph), Spring AI Alibaba (StateGraph), Solon AI (YAML Flow) have declarative workflow
13. **Guardrails** — only VoltAgent has first-class input/output guardrails
14. **Multi-channel (IM)** — only PilotDeck (23), Hermes (30) have broad IM adapter support
15. **Always-on / background tasks** — only PilotDeck has discovery-based offline execution
16. **Self-improvement** — only Hermes Agent has Curator-based skill auto-creation/improvement
17. **Cache optimization** — only Reasonix has engineering-grade prefix-cache management
18. **Tool-call repair** — only Reasonix has a systematic repair pipeline
19. **Event sourcing kernel** — only Reasonix uses CQRS for state management
20. **Content-hash editing** — only oh-my-pi and oh-my-opencode have Hashline

### Design Philosophy Clusters

| Cluster | Projects | Philosophy |
|---------|----------|-----------|
| **Agent Harness** (complete application) | pi, oh-my-pi, oh-my-claudecode, oh-my-opencode, PilotDeck, Hermes, Reasonix | Batteries-included agent that you extend/customize |
| **Agent Framework** (library/platform) | VoltAgent, DeepAgents, AgentScope Java, Solon AI, Spring AI Alibaba | Build your own agent using provided abstractions |
| **Model-native** | Reasonix, oh-my-opencode | Optimize deeply for specific model quirks |
| **Self-improving** | Hermes Agent | Agent learns from experience and improves its own skills |
| **OS-like** | PilotDeck | Agent as operating system (workspaces, always-on, multi-channel) |
