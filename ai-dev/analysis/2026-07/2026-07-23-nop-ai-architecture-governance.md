# nop-ai Module Architecture Governance Analysis

> Status: open
> Date: 2026-07-23
> Git SHA: f16b0930e8890fb4072ea896a97118041783e1ac
> Scope: nop-ai (parent POM, 21 modules listed, 23 effective leaf modules, ~1278 Java source files)
> Route: architecture_review + rot_audit

## Context

Analyze the `nop-ai` module using the Architecture Governance Prompt template (from `attractor-guided-engineering-template/docs/skills/architecture-governance-prompt.md`). The `nop-ai` parent POM contains 21 sub-modules (3 more nested under `nop-ai-skills`, total 23 effective) spanning AI chat, agent engine, tool execution, code generation, MCP server, shell, and data persistence layers. This review was prompted to assess structural health before further concept growth.

---

## Design Review Matrix

### Spine — Which existing load-bearing path does this lower into?

`nop-ai` does not lower into a single existing path. It is a **new peer subsystem** alongside `nop-biz`, `nop-task`, `nop-wf`, etc., justified by the distinct domain (LLM interaction, agent orchestration). The core abstractions are:

| Layer | Module | Concept |
|-------|--------|---------|
| LLM tool calling | `nop-ai-toolkit` (`IToolManager`) | LLM function calling abstraction (read-file, bash, graphql, etc. exposed as callable tools) |
| Chat/dialect | `nop-ai-core` (`IChatService`, `ILlmDialect`) | LLM chat + multi-dialect support (OpenAI, Ollama, Gemini, Anthropic) |
| Agent orchestration | `nop-ai-agent` (`IAgentEngine`) | ReAct agent loop, memory, hooks, guardrails, teams |
| Code generation | `nop-ai-coder` (`IDslTool`) | XDEF-based codegen for Nop platform models (ORM, API, auth, etc.) |
| Shell safety | `nop-ai-shell` | Structured shell parser/executor for safe command execution |
| MCP | `nop-ai-mcp-server`, `nop-spring-mcp-server*` | Model Context Protocol server |

Note: `IToolManager` (LLM function calling) and `nop-task-core` (workflow DAG orchestration) are different abstractions. `IToolManager` maps LLM tool calls to command execution; `nop-task-core` orchestrates multi-step business processes. No overlap.

**Finding F1 (low risk):** `nop-ai-agent` depends on `nop-task-core` for plan/step execution while also having its own lifecycle in `DefaultAgentEngine` (ReAct loop, middleware chain). The boundary between agent engine and task engine is lightly documented — the agent engine uses tasks internally for some extended flows, but no doc defines "when to use `IAgentEngine` vs. `ITaskStep` directly".

**Finding F2 (low risk):** `nop-ai-shell` and `nop-ai-toolkit`'s `BashExecutor` are related but not duplicative. `BashExecutor` is a single LLM tool that runs `bash -c "..."`. `nop-ai-shell` is a full structured shell subsystem (parser, command registry, I/O abstraction, safety checkers). The relationship (should BashExecutor be implemented on top of nop-ai-shell? should shell be the safety layer for tool execution?) is not documented.

---

### Surface — Which stability level does each new/changed surface sit on?

`nop-ai` has ~146 public interfaces across ~16 sub-modules with Java sources. Surface grade assessment:

| Stability Level | Count | Modules | Assessment |
|-----------------|-------|---------|------------|
| **Public API** (stable contract) | ~5 | `nop-ai-api` (`IChatService`) | Properly isolated — lowest-risk surface |
| **SPI** (extensible, stable) | ~80 | `nop-ai-core` (`ILlmDialect`, `IVectorStore`, `IPromptTemplate`), `nop-ai-toolkit` (`IToolManager`, `IToolFileSystem`), `nop-ai-agent` (`IAgentEngine`, `IAiMemoryStore`, `IHookRegistry`, etc.) | Well-designed SPI pattern, but many interfaces have incomplete implementation coverage — see F3 |
| **Internal** (module-private) | ~60 | `nop-ai-agent` (compact, conflict, repair, talent, fencing, quota, etc.) | Many interfaces inside `nop-ai-agent` exist alongside only `NoOp*` implementations |
| **Generated BizModel API** | 24 | `nop-ai-service` (`NopAi*BizModel`) | Standard `nop-biz` generated pattern — correct |

**Finding F3 (high risk):** Surface-area-to-implementation ratio is unhealthy in `nop-ai-agent`. Among ~67 interfaces, a significant subset have only `NoOp*` or `InMemory*` implementations:

| Interface | NoOp impl | InMemory impl | Production impl |
|-----------|-----------|---------------|-----------------|
| `IContentGuardrail` | `NoOpContentGuardrail` | — | None |
| `IBudgetProvider` | `NoOpBudgetProvider` | `InMemoryBudgetProvider` (test only) | None |
| `IWriteIntentRegistry` | — | `InMemoryWriteIntentRegistry` | None |
| `IContributionRegistry` | `NoOpContributionRegistry` | `InMemoryContributionRegistry` | None |
| `IFencingTokenService` | `NoOpFencingTokenService` | — | `DefaultFencingTokenService` (in-memory) |
| `ISkillProvider` | `NoOpSkillProvider` | — | `FileSystemSkillProvider` (partial) |
| `ISkillCurator` | `NoOpSkillCurator` | — | `LLMCurator` (basic) |
| `IMemoryStoreProvider` | — | `InMemoryMemoryStoreProvider` | `AdapterBackedMemoryStoreProvider` (partial) |
| `IGoalTracker` | `NoOpGoalTracker` | — | `SessionGoalTracker` (partial) |

Interfaces with production implementations:

| Interface | Production impl | Maturity |
|-----------|----------------|----------|
| `ICheckpointManager` | `FileBackedCheckpointManager`, `DBCheckpointManager` | Production |
| `IRetryPolicy` | `StandardRetryPolicy` | Production (exponential backoff) |
| `ISustainer` | `SisypheanSustainer` | Production (documented design) |
| `ICircuitBreaker` | `ThresholdBreaker` | Production (3-state CLOSED/OPEN/HALF_OPEN, per-model-key state machines) |
| `ICompletionJudge` | `RuleBasedCompletionJudge`, `LlmCompletionJudge` | Production |
| `IConflictStrategy` | `FailFastStrategy` | Production |
| `IAgentMessenger` | `LocalAgentMessenger`, `DBMessageService` | Production |
| `IResourceGuard` | `DefaultResourceGuard` | Production |

Every SPI interface with only `NoOp*` / `InMemory*` is a **deferred liability** — the surface is minted before the implementation exists. If no consumer ever plugs a real implementation, the interface is dead code. If a consumer does, they may find the SPI contract is under-specified.

**Finding F4 (medium risk):** `DefaultAgentEngine` has 9 constructor parameters (lines 655-671) plus ~20 setters for optional dependencies (via `Builder.applyTo`). The engine's construction is **configuration-by-override**: each optional dependency defaults to a `NoOp*` / pass-through / basic implementation in field initializers (lines 163-367). A misconfigured engine (built via constructor directly rather than `Builder`) may silently skip guardrails, budget checks, or skill curation. The `Builder.build()` method calls `warnIfInsecureDefaults()` (line 557) which explicitly warns on `AllowAll*`, `NoOpAuditLogger`, `AutoApproveGate`, `NoOpDenialLedger` — but this check is bypassed when using constructors directly.

---

### Truth — Which facts are restated instead of referenced?

| Artifact | Source | Derived? | Hand-edited? |
|----------|--------|----------|-------------|
| `nop-ai-dao/src/main/java/io/nop/ai/dao/entity/*.java` | `model/nop-ai.orm.xml` | Generated (`_gen/`) | No — correct use of codegen |
| `nop-ai-meta/_vfs/nop/ai/model/NopAi*/*.xmeta` | `model/nop-ai.orm.xml` | Generated (`_gen`) | No — correct |
| `nop-ai-service/_vfs/nop/ai/beans/_service.beans.xml` | Codegen | Generated | No — correct |
| `nop-ai-web/_vfs/nop/ai/pages/NopAi*/*` | Codegen | Generated (`_gen` pages) | No — correct |
| LLM configs (`*llm.xml`) | Manual YAML/XML | No | N/A |
| Tool definitions (`*.tool.xml`, `*.tool.json`) | Manual | No | N/A |

**Finding F5 (low risk):** The ORM model truth chain is clean: `model/nop-ai.orm.xml` → codegen → `_gen/` → retention files. No detected hand-editing of generated files.

**Finding F6 (medium risk):** Tool definitions exist in **three formats** across modules:
- `*.tool.xml` in `nop-ai-toolkit/_vfs/` (23 tool defs, XML schema)
- `*.tool.json` in `nop-ai-tools/_vfs/` (tool defs, JSON)
- `xdef`-based schemas in `nop-ai-coder/_vfs/` (14 XDEF schemas)

These are three representations for the same concept (tool definition). The XML and JSON tool definitions are not cross-validated against a single authoritative source. If a field changes in one format, it may silently drift from the others.

---

### Ownership — Who owns each piece, and do dependencies flow in the declared direction?

The module dependency graph (from pom.xml analysis):

```
api (leaf) → core → toolkit → agent → (agent is the heaviest sink)
                      ↘ shell
core → dsl-orm
core + toolkit + code-analyzer + nop-image + nop-orm-model + ... → coder (widest dependency fan)
```

| Module | Dependencies | Issues |
|--------|-------------|--------|
| `nop-ai-api` | `nop-api-core` | Clean — leaf module, minimal deps |
| `nop-ai-core` | `nop-ai-api`, `nop-api-core`, `nop-dao`, `nop-http-api`, `nop-xlang`, `nop-markdown`, `nop-diff` | Moderate — 7 deps, all justified |
| `nop-ai-coder` | `nop-ai-api`, `nop-ai-core`, `nop-ai-code-analyzer`, `nop-image`, `nop-task-core`, `nop-orm-model`, `nop-rpc-model`, `nop-ui`, `nop-markdown`, `nop-ooxml-markdown`, `nop-report-core`, `nop-converter` | **Widest fan**: 12 deps, touches ORM model, RPC model, UI, report, image — may become a dependency magnet |
| `nop-ai-agent` | `nop-ai-toolkit`, `nop-ai-core`, `nop-task-core` | Clean — 3 deps |
| `nop-ai-toolkit` | `nop-ai-api`, `nop-xlang`, `nop-http-api`, `nop-search-api`, `nop-api-core`, `nop-diff` | Clean — 6 deps, all leaf/infra |
| `nop-ai-service` | `nop-ai-dao`, `nop-ai-meta`, `nop-biz`, `nop-biz-file-core`, `nop-config`, `nop-ioc`, `nop-sys-dao` | Clean — standard biz service pattern |

Note: `nop-ai-code-analyzer` lives under `nop-ai-skills/` as a nested module, not a top-level parent module.

**Finding F7 (low risk):** `nop-ai-coder` has the widest dependency fan (12 modules) and is the riskiest to refactor. Any change in ORM model, RPC model, UI, report, or converter may cascade into it. No documented boundary preventing this fan from growing further.

---

### Negative Path — Defined behavior for denied, failed, partial, stale, cancelled outcomes?

| Scenario | Coverage | Evidence |
|----------|----------|----------|
| Tool call failure | Covered | `FailFastStrategy`, `ConflictResult`; per-tool error reporting |
| LLM timeout/stale | Partial | `DefaultAiChatService` has timeout; no stale-state detection documented |
| Tool execution cancellation | Implemented | `DefaultAgentEngine.cancelSession()` (line 2055) supports forced/interrupt cancellation with event publication |
| Agent session crash/restart | Implemented | `DefaultAgentEngine.restoreSession()` (line 2710) — checkpoint consistency, status transitions, event publication |
| Sticky-pause recovery | Implemented | `DefaultAgentEngine.resumeSession()` (line 2540) — denial ledger reset, tenant-scoped recovery, audit event |
| Batch session recovery | Contract defined | `IAgentEngine.restorePendingSessions()` default throws; implementation pending |
| Idempotency for tool exec | Not defined | No idempotency keys, dedup, or retry-safe semantics in tool definitions |
| Quota exhaustion | Contract defined | `QuotaDecision`, `QuotaDimension` defined; `DefaultResourceGuard` exists |

**Finding F8 (high risk):** Negative-path definition is strongest in the agent engine lifecycle (cancel/resume/restore are all implemented in `DefaultAgentEngine`) but weakest for tool-level semantics. Idempotency, stale detection, and duplicate-execution prevention have neither contract nor implementation. An agent system that may replay tool calls (after crash/restart) without idempotency guarantees is unsafe for production workflows with side effects.

---

### Time — What happens on retry, replay, duplicate execution, or partial restart?

| Scenario | Coverage | Evidence |
|----------|----------|----------|
| Retry after failure | Implemented | `StandardRetryPolicy` (exponential backoff), `RetryContext`, `RetryDecision` |
| Duplicate execution | Not addressed | No idempotency keys, no dedup in tool executors |
| Partial restart (crash) | Implemented | `FileBackedCheckpointManager`, `DBCheckpointManager`; `DefaultAgentEngine.restoreSession()` |
| Compaction after crash | Implemented | `IContextCompactor`, `PipelineCompactor` with Layer2/Layer3 strategies |
| Replay (re-execute from checkpoint) | Not addressed | No replay protocol defined |

**Finding F9 (medium risk):** The checkpoint/restore infrastructure is architecturally complete and implemented in `DefaultAgentEngine`. However, `restorePendingSessions()` (batch scan-and-restore for startup) still throws `UnsupportedOperationException`. A deployment wanting automated crash recovery after process restart must implement this orchestration themselves, with no guidance on threading, lock renewal, or partial-crash semantics.

---

### Guards — Which machine check enforces each relied-on rule?

| Rule | Machine Check | Can it fail? | Notes |
|------|---------------|-------------|-------|
| Data access authorization | `data-auth.xml` (Nop platform auth) | Yes | Standard platform guard — inherited |
| Tool access permissions | `IPathAccessChecker`, `IToolAccessChecker`, `IDenialLedger` | Yes | Multiple checkers defined; `DefaultToolAccessChecker`/`DefaultPathAccessChecker` are the defaults |
| Content safety | `IContentGuardrail` | Could, but NoOp by default | Default is `NoOpContentGuardrail` — the SPI exists but is not wired in the default assembly |
| Budget limits | `IBudgetProvider` | | Default is `NoOpBudgetProvider` |
| Resource quotas | `IResourceGuard` | | `DefaultResourceGuard` exists but its configuration is not documented |
| Write conflict detection | `IWriteIntentRegistry` | | Default is `InMemoryWriteIntentRegistry` — conflicts lost on restart |
| Session takeover safety | `ISessionTakeoverLock` | | Default is `NoOpSessionTakeoverLock` — no HA safety |
| Approval gate | `IApprovalGate` | Yes | Default is `DefaultApprovalGate` which **denies RESTRICTED** operations (not auto-approve). `AutoApproveGate` is a separate opt-in class |

**Finding F10 (high risk):** Several critical safety guards default to `NoOp*` implementations. The `Builder.build()` method calls `warnIfInsecureDefaults()` (line 557) to detect some of these (e.g., `AllowAllToolAccessChecker`, `NoOpAuditLogger`), but this check is **bypassed when using constructors directly** (lines 607-671 vs. Builder). The constructor chain also does not warn for `NoOpContentGuardrail`, `NoOpBudgetProvider`, or `NoOpSessionTakeoverLock`. An engine built via constructor without explicit configuration will run silently without content safety, budget limits, or HA takeover protection.

**Finding F11 (medium risk):** Three guards (`IContentGuardrail`, `IBudgetProvider`, `IWriteIntentRegistry`) have only in-memory or NoOp implementations with no documented path to a production-grade deployment. The gap between SPI existence and production implementation is widest in these areas.

---

### Budget — What concept growth is introduced, and what does it retire?

| New Concept | Count | Retires/Simplifies What? | Assessment |
|-------------|-------|--------------------------|------------|
| Sub-modules | 21 listed, 23 effective | Nothing directly | Large surface; each module adds build, test, maintenance cost |
| Interfaces | ~146 total | Nothing | SPI count is high relative to implementation density |
| Agent-specific abstractions | ~20+ packages | Would ideally simplify over raw LLM calls | Good if completed; bad if interfaces remain with NoOp impls |
| Tool definition formats | 3 (XML, JSON, XDEF) | Nothing | Redundant formats — should consolidate to one authoritative format |
| Shell module | 1 (nop-ai-shell) | Would simplify safety over raw `BashExecutor` | Good intent, but its integration with `IToolManager` is unclear |
| MCP server | 2 sub-modules | Standardizes tool exposure over custom GraphQL APIs | Good — follows industry protocol |
| Codegen module | 1 (nop-ai-coder) | Automates boilerplate generation | Good — aligns with Nop platform philosophy |

**Finding F12 (medium risk):** Concept count (21 parent modules, ~146 interfaces, ~20 agent-specific packages) grows without documented retirements. The governing rule from the prompt: "if it introduces a new top-level concept, what does it retire or simplify?" Answer is "nothing" for most additions. The module count (21 parent POM modules, 23 effective) is substantially larger than nop-wf (3 modules), nop-job (3-4), or nop-task (2-3).

---

## Rot Indicators

| Indicator | Measurement | Direction |
|-----------|-------------|-----------|
| **Interface-to-impl gap** | Several SPI interfaces in nop-ai-agent with only NoOp impl | 🟡 Growing |
| **Public surface growth** | 21 parent POM modules, ~146 interfaces, 24 CRUD BizModels | 🔴 High — no retirement |
| **Concept count** | 23 effective modules, ~146 interfaces | 🔴 High — no retirement documented |
| **Duplicate truth** | Tool defs in 3 formats (XML/JSON/XDEF), no cross-validation | 🔴 Present |
| **Constructor bypass pattern** | Direct constructors bypass `warnIfInsecureDefaults` | 🟡 Perpetuating |
| **Guard last-failed date** | NoOp guardrails cannot fail by design | 🔴 Wallpaper risk |
| **Hand-edited derived files** | Not detected for codegen files | 🟢 Clean |
| **Sideways imports** | `nop-ai-coder` imports 12 modules, widest fan | 🟡 Expanding |
| **Skipped tests** | Not measured (separate audit needed) | 🟡 Unknown |

---

## Findings Summary (Ranked by Risk)

| ID | Risk | Finding | Affected Modules |
|----|------|---------|-----------------|
| F3 | High | SPI interfaces in `nop-ai-agent` with only `NoOp*`/`InMemory*` implementations (IContentGuardrail, IBudgetProvider, IContributionRegistry, etc.) — minted promises without delivery | `nop-ai-agent` |
| F8 | High | Tool-level negative-path semantics underdefined: no idempotency, stale detection, or duplicate-execution prevention | Cross-module (tool executors) |
| F10 | High | Constructor chain bypasses `warnIfInsecureDefaults`; several safety guards NoOp by default (content, budget, session takeover) | `nop-ai-agent` |
| F4 | Medium | `DefaultAgentEngine` Builder calls `warnIfInsecureDefaults` but direct constructors (9-param chain) skip it; ~20 optional deps default to NoOp/pass-through | `nop-ai-agent` |
| F6 | Medium | Tool definitions in 3 formats (XML/JSON/XDEF) without authoritative source or cross-validation | `nop-ai-toolkit`, `nop-ai-tools`, `nop-ai-coder` |
| F9 | Medium | `restorePendingSessions()` (batch crash recovery) is `UnsupportedOperationException`; single-session restore is implemented | `nop-ai-agent` |
| F11 | Medium | Three guards (content safety, budget limits, write conflict detection) have only in-memory/NoOp impls with no production migration path | `nop-ai-agent` |
| F12 | Medium | Concept growth without retirements — 23 effective modules with no documented simplification | Cross-module |
| F7 | Low | `nop-ai-coder` widest dependency fan (12 modules), no documented boundary | `nop-ai-coder` |
| F1 | Low | Lightly documented boundary between `IAgentEngine` and `ITaskStep` | `nop-ai-agent`, `nop-task-core` |
| F2 | Low | Relationship between `nop-ai-shell` and `BashExecutor` not documented | `nop-ai-shell`, `nop-ai-toolkit` |
| F5 | Low | ORM codegen chain is clean | `nop-ai-dao`, `nop-ai-meta` |

---

## Required Checks

Before any major structural change to `nop-ai`:

- [ ] **NoOp baseline audit**: enumerate every SPI interface and its production impl status. Tag interfaces with `@Internal` if no production impl is planned within 2 releases.
- [ ] **Constructor guard check**: add `warnIfInsecureDefaults` call to the terminal constructor (line 655) to remove the builder-only gap.
- [ ] **Tool format consolidation**: pick one authoritative format (recommend XDEF-based) and deprecate the other two. Add a validation check that cross-references formats.
- [ ] **Negative-path specification**: document idempotency and duplicate-execution semantics for each tool executor, especially those with side effects (write-file, bash, etc.).
- [ ] **Module count budget**: set a hard limit (e.g., 25 effective modules within nop-ai). New modules must be accompanied by a retirement or deduplication plan.
- [ ] **restorePendingSessions implementation**: ship the batch crash-recovery orchestration to close the gap between single-session restore and automated startup recovery.

---

## Residual Risks

1. **NoOp debt is deferred, not eliminated.** If no explicit plan drives production impls of the NoOp-only interfaces (IContentGuardrail, IBudgetProvider, IContributionRegistry, etc.), they become dead code within 2 releases. The interface surface continues to accrue maintenance cost without delivering safety.
2. **Tool-level non-idempotency.** The agent engine has cancel/resume/restore, but individual tool executors lack idempotency keys. After a crash/restore, re-executed tool calls may produce duplicate side effects.
3. **Dependency fan growth on `nop-ai-coder`** is unchecked. Without a boundary document, it may continue absorbing dependencies and become the module most likely to break on platform changes.
4. **No ownership doc exists** for any nop-ai sub-module in `docs-for-ai/` or `ai-dev/design/`. All structural knowledge is implicit in code structure and javadoc. This is a documentation gap that compounds architecture drift over time.

---

## Open Questions

- [ ] Should the NoOp-only interfaces in nop-ai-agent be annotated with `@Beta` or `@Internal` to signal that their SPI contract is not yet stable?
- [ ] Should `BashExecutor` be implemented on top of `nop-ai-shell` for structured command safety, or are these independent paths?
- [ ] What is the ownership assignment for nop-ai sub-modules? (Currently none recorded in docs-for-ai/)

---

## References

- Git SHA: `f16b0930e8890fb4072ea896a97118041783e1ac`
- `nop-ai/pom.xml` (21 sub-modules)
- `nop-ai/nop-ai-api/src/main/java/io/nop/ai/api/chat/IChatService.java`
- `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/IAgentEngine.java`
- `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/DefaultAgentEngine.java` (lines 163-367 field initializers, 607-671 constructors, 540-561 Builder.build/warnIfInsecureDefaults, 2055 cancelSession, 2540 resumeSession, 2710 restoreSession)
- `nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/api/IToolManager.java`
- Architecture Governance Prompt: `C:\can\nop\attractor-guided-engineering-template\docs\skills\architecture-governance-prompt.md`