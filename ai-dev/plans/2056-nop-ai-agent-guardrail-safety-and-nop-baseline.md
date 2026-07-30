# 2056 nop-ai-agent Guardrail Safety & NoOp Baseline

> Plan Status: active
> Last Reviewed: 2026-07-23
> Source: `ai-dev/analysis/2026-07/2026-07-23-nop-ai-architecture-governance.md` (Findings F3, F4, F10, F11)
> Related: `nop-ai/nop-ai-agent/`

## Purpose

Fix the immediate safety gap where `DefaultAgentEngine` constructors bypass `warnIfInsecureDefaults`, and establish a documented baseline of which SPI interfaces have only in-memory/no-persistent-production implementations.

## Current Baseline

- `DefaultAgentEngine` has 8 constructors (2-param through 9-param, lines 607-671) that accept `IContentGuardrail`, `IModelRouter`, `IContextCompactor` etc. with NoOp fallbacks
- `Builder.build()` (line 551) calls the **static** method `warnIfInsecureDefaults(...)` (line 557) passing 8 specific arguments — this checks for AllowAllToolAccessChecker, AllowAllPathAccessChecker, NoOpAuditLogger, AutoApproveGate, NoOpSecurityLevelResolver, PassThroughPermissionMatrix, NoOpDenialLedger, PassThroughPostDenialGuard
- The constructor chain does NOT call `warnIfInsecureDefaults` — an engine built via `new DefaultAgentEngine(...)` runs without any guard validation
- `warnIfInsecureDefaults` does NOT check `NoOpContentGuardrail` or `NoOpBudgetProvider`
- `NoOpSessionTakeoverLock` javadoc (lines 338-341) explicitly states "no insecure-default WARN is emitted" by design — it is a single-process incremental capability, not a security downgrade
- `setAuditLogger` javadoc (lines 1206-1212) promises "this setter re-runs warnIfInsecureDefaults after the assignment" but the actual code does not — a stale javadoc gap
- Several SPI interfaces in `nop-ai-agent` have only in-memory/no-persistent-production implementations: IContentGuardrail, IBudgetProvider, IContributionRegistry, IWriteIntentRegistry, IFencingTokenService, ISkillProvider, ISkillCurator, IGoalTracker

## Goals

1. Eliminate constructor-bypass gap: every `DefaultAgentEngine` construction path triggers `warnIfInsecureDefaults`
2. Expand awareness coverage: log INFO-level messages for components that have only NoOp/in-memory implementations available (NoOpContentGuardrail, NoOpBudgetProvider) — not WARN, because no production alternative exists to swap to
3. Document non-production-grade SPI interfaces with `{@code @apiNote}` javadoc tags and a design note

## Non-Goals

- Not changing the `NoOpSessionTakeoverLock` design decision (no WARN for single-process mode)
- Not implementing production versions of in-memory-only SPI interfaces (separate plan)
- Not fixing the stale `setAuditLogger` javadoc (out of scope — requires setter change, not constructor)
- Not consolidating tool definition formats
- Not documenting IAgentEngine/ITaskStep boundary

## Scope

### In Scope

- `nop-ai-agent` module: `DefaultAgentEngine.java`, SPI interfaces in `io.nop.ai.agent.*`
- Design doc: `ai-dev/design/nop-ai-agent/guardrail-contract.md`
- Javadoc `{@code @apiNote}` tags on NoOp-only interface files

### Out Of Scope

- Other nop-ai modules (toolkit, coder, core, etc.)
- Production impl of in-memory-only interfaces
- IContentGuardrail, IBudgetProvider, IWriteIntentRegistry implementations
- `setAuditLogger` javadoc stale promise (minor doc issue, not blocking)
- `NoOpSessionTakeoverLock` is excluded from new checks (code design decision stands)

## Design Decision

### warnIfInsecureDefaults refactoring approach

Convert from **static method** (8-param, line 723) to **private instance method** `warnIfInsecureDefaults()` that reads from `this.*` fields. This removes the signature mismatch problem (constructor doesn't have all 8 params) and allows any code path to trigger the check.

### NoOp awareness level: INFO not WARN

The existing 8 checks fire at WARN level because they detect an **explicit downgrade**: the user replaced a secure default (e.g., `DefaultToolAccessChecker`) with an insecure one (`AllowAllToolAccessChecker`). The new `NoOpContentGuardrail` and `NoOpBudgetProvider` checks detect a **missing implementation**: no production alternative exists. WARN would fire on every construction with no remediation path, creating noise. These new checks use **INFO level** instead, with a message like "No production implementation available for X — deployment will lack Y capability."

### NoOpSessionTakeoverLock excluded

The `NoOpSessionTakeoverLock` javadoc (lines 338-341) records a design decision: single-process deployments keep relying on the engine's in-process `runningExecutions.putIfAbsent` guard. The takeover lock is incremental capability (NoOp → engine walks existing path), not a security downgrade. Phase 2 does not add a check for this — the design decision stands.

### Builder path double-fire

1. `Builder.build()` → `new DefaultAgentEngine(...)` → constructor calls `this.warnIfInsecureDefaults()`
2. `applyTo(engine)` → may override fields
3. `engine.warnIfInsecureDefaults()` → second check on final (post-applyTo) field values

Duplicate WARN/INFO messages are acceptable (better than silent bypass). No dedup flag needed.

## Execution Plan

### Phase 1 - Convert warnIfInsecureDefaults to instance method + add to constructor

Status: planned
Targets: `DefaultAgentEngine.java` (lines 557, 670, 723)

- Item Types: `Fix`

- [ ] Convert `warnIfInsecureDefaults` (line 723) from `private static void` (8 params) to `private void warnIfInsecureDefaults()` (no params, reads `this.*`)
- [ ] Call `this.warnIfInsecureDefaults()` as the LAST statement in the terminal constructor (line 670, after `this.tokenEstimator = ...`)
- [ ] In `Builder.build()` (lines 551-561): replace static `warnIfInsecureDefaults(toolAccessChecker, pathAccessChecker, ...)` with instance `engine.warnIfInsecureDefaults()` (keep after `applyTo(engine)`)
- [ ] Verify: `./mvnw test -pl nop-ai-agent -am` passes

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] Each of the 8 existing warn checks fires correctly on both Builder path and direct constructor path (verified via `TestAuditLoggerDefault`, `TestSecureByDefault`, `TestLayer23SecureDefaults`)
- [ ] Existing tests pass without modification
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - Expand awareness coverage

Status: planned
Targets: `DefaultAgentEngine.java` only (add `instanceof` checks in `warnIfInsecureDefaults`)

- Item Types: `Fix`

Must execute AFTER Phase 1 (both modify the same method).

- [ ] Add `instanceof NoOpContentGuardrail` check in instance `warnIfInsecureDefaults()`: emit **INFO** level message (not WARN), e.g. "No production implementation available for IContentGuardrail — content safety is not enforced. Provide a custom implementation via setContentGuardrail() for production use."
- [ ] Add `instanceof NoOpBudgetProvider` check: emit **INFO** level, e.g. "No production implementation available for IBudgetProvider — execution budget is unlimited. Provide a custom implementation via setBudgetProvider() for production use."
- [ ] Do NOT add a check for `NoOpSessionTakeoverLock` (design decision recorded in javadoc lines 338-341 stands — single-process incremental capability, not a security downgrade)
- [ ] Verify: `./mvnw test -pl nop-ai-agent -am` passes

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] Constructing engine with `NoOpContentGuardrail` (the default) emits an INFO message (verify via test)
- [ ] Constructing engine with `NoOpBudgetProvider` emits an INFO message (verify via test)
- [ ] Constructing engine with `NoOpSessionTakeoverLock` does NOT emit any new message (existing design decision)
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - NoOp/in-memory baseline documentation

Status: planned
Targets: `ai-dev/design/nop-ai-agent/guardrail-contract.md` (new), interface javadoc files

- Item Types: `Decision | Proof`

- [ ] Enumerate every SPI interface in `nop-ai-agent` and classify:
  - **production-ready**: has a production-grade implementation (IRetryPolicy→StandardRetryPolicy, ICheckpointManager→FileBackedCheckpointManager/DBCheckpointManager, ICircuitBreaker→ThresholdBreaker, ISustainer→SisypheanSustainer)
  - **partial**: has a functional but non-persistent implementation (IWriteIntentRegistry→InMemoryWriteIntentRegistry, IFencingTokenService→DefaultFencingTokenService in-memory, IContributionRegistry→InMemoryContributionRegistry)
  - **no-production**: only NoOp or test-only implementation (IContentGuardrail→NoOpContentGuardrail, IBudgetProvider→NoOpBudgetProvider)
  - **partial-with-implementation**: has a basic implementation that works but is not production-grade (ISkillProvider→FileSystemSkillProvider, ISkillCurator→LLMCurator, IGoalTracker→SessionGoalTracker)
- [ ] Write `ai-dev/design/nop-ai-agent/guardrail-contract.md` with:
  - Which guards must be non-NoOp for minimum production safety (toolAccessChecker, pathAccessChecker, denialLedger, auditLogger)
  - Classification status for each SPI interface (why it's production/partial/no-production)
  - Recommended minimum guard configuration for production deployments
  - Note on `NoOpSessionTakeoverLock` design rationale (single-process incremental capability)
- [ ] Add `{@code @apiNote}` javadoc tags to interfaces with no-production or partial classification: IContentGuardrail, IBudgetProvider, IContributionRegistry, IWriteIntentRegistry, IFencingTokenService, ISkillProvider, ISkillCurator, IGoalTracker

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] `ai-dev/design/nop-ai-agent/guardrail-contract.md` exists and lists each SPI interface with its status classification
- [ ] All 8 identified interfaces have `{@code @apiNote}` javadoc tags
- [ ] Owner-doc updated: `ai-dev/design/nop-ai-agent/guardrail-contract.md`
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [ ] All construction paths (direct constructors + Builder) call `warnIfInsecureDefaults` — verified via unit test with log capture
- [ ] `NoOpContentGuardrail`, `NoOpBudgetProvider` emit INFO messages at construction time — verified via test
- [ ] `NoOpSessionTakeoverLock` does NOT emit any new message — verified via test
- [ ] No-production-grade SPI interfaces annotated with javadoc `{@code @apiNote}` — verified via grep
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect
- [ ] 受影响的 owner docs 已同步：`ai-dev/design/nop-ai-agent/guardrail-contract.md`
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：closure audit 已验证 `warnIfInsecureDefaults` 确实被构造函数调用（不只是类型存在），且 `applyTo` 之后的二次检查仍然触发
- [ ] `./mvnw compile -pl nop-ai-agent -am`
- [ ] `./mvnw test -pl nop-ai-agent -am`

## Deferred But Adjudicated

### IContentGuardrail / IBudgetProvider / IWriteIntentRegistry production impl

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: Implementation of production-grade content guardrails, budget providers, and write-conflict detection is a separate feature-delivery effort, not a safety-gap fix. This plan only ensures that missing these is surfaced (INFO-level awareness) and documented (`@apiNote`).
- Successor Required: `yes`
- Successor Path: Future plan for nop-ai-agent SPI implementation maturity

### restorePendingSessions

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: Single-session restore is already implemented; batch orchestration is a deployment convenience, not a safety gap.
- Successor Required: `yes`
- Successor Path: Future plan for nop-ai-agent deployment hardening

## Non-Blocking Follow-ups

- Archive the architecture governance analysis into `ai-dev/archived/` after this plan closes (the analysis that triggered this plan)

## Closure

Status Note: <<完成或关闭时填写：为什么这个 plan 可以关闭>>
Completed: YYYY-MM-DD

Closure Audit Evidence:

- Reviewer / Agent: <<独立子 agent>>
- Evidence:

Follow-up:

- No remaining plan-owned work