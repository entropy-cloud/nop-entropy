# Guardrail & NoOp Baseline Contract

> Status: active
> Plan: 2056 (Guardrail Safety & NoOp Baseline)
> Last Updated: 2026-07-31

## Purpose

Document the minimum guard configuration required for production safety, and
classify every SPI interface in `nop-ai-agent` by implementation maturity.

## Minimum Production Safety Guards

The following guards **must** be non-NoOp for minimum production safety.
Constructing an engine with any of these in insecure-default mode emits a
**WARN** via `DefaultAgentEngine.warnIfInsecureDefaults()`.

| Guard | Secure Default | Insecure (WARN) | Why |
|-------|---------------|-----------------|-----|
| `IToolAccessChecker` | `DefaultToolAccessChecker` | `AllowAllToolAccessChecker` | Blocks dangerous tools (bash, write-file, etc.) |
| `IPathAccessChecker` | `DefaultPathAccessChecker` | `AllowAllPathAccessChecker` | Blocks sensitive paths (~/.ssh/, /etc/, .env) |
| `IAuditLogger` | `Slf4jAuditLogger` | `NoOpAuditLogger` | Records tool decisions; silent = no audit trail |
| `IDenialLedger` | `DefaultDenialLedger` | `NoOpDenialLedger` | Counts denials and pauses sessions on threshold |
| `IPostDenialGuard` | `DefaultPostDenialGuard` | `PassThroughPostDenialGuard` | Blocks blind retries of denied operations |
| `ISecurityLevelResolver` | `DefaultSecurityLevelResolver` | `NoOpSecurityLevelResolver` | Classifies operations by security level |
| `IPermissionMatrix` | `DefaultPermissionMatrix` | `PassThroughPermissionMatrix` | Enforces channel × level permissions |
| `IApprovalGate` | `DefaultApprovalGate` | `AutoApproveGate` | Defense-in-depth deny for RESTRICTED operations |

## SPI Interface Classification

Every SPI interface in `nop-ai-agent` is classified into one of:

- **production-ready**: production-grade implementation shipped
- **partial**: functional but non-persistent (in-memory) implementation
- **partial-with-implementation**: basic implementation that works but is not production-grade
- **no-production**: only NoOp or test-only implementation

### Production-Ready

| Interface | Shipped Implementation | Notes |
|-----------|----------------------|-------|
| `IRetryPolicy` | `StandardRetryPolicy` | Configurable retry with backoff |
| `ICheckpointManager` | `FileBackedCheckpointManager` / `DBCheckpointManager` | Persistent checkpoint recording |
| `ICircuitBreaker` | `ThresholdBreaker` | Configurable failure threshold |
| `ISustainer` | `SisypheanSustainer` | Configurable sustain rounds |

### Partial (In-Memory Only)

| Interface | Shipped Implementation | Limitation |
|-----------|----------------------|------------|
| `IWriteIntentRegistry` | `InMemoryWriteIntentRegistry` | Cross-process detection is a future successor |
| `IFencingTokenService` | `DefaultFencingTokenService` | Single-JVM; cross-process fencing is a successor |
| `IContributionRegistry` | `InMemoryContributionRegistry` | Contributions lost on JVM restart |

### Partial-With-Implementation

| Interface | Shipped Implementation | Limitation |
|-----------|----------------------|------------|
| `ISkillProvider` | `FileSystemSkillProvider` | Basic filesystem provider; no DB support |
| `ISkillCurator` | `LLMCurator` | Basic LLM-based curator; no rule-based mode |
| `IGoalTracker` | `SessionGoalTracker` | In-memory per-session; no persistent tracking |

### No-Production (NoOp Only)

| Interface | Shipped Implementation | Notes |
|-----------|----------------------|-------|
| `IContentGuardrail` | `NoOpContentGuardrail` | No production alternative exists. INFO-level awareness at construction. |
| `IBudgetProvider` | `NoOpBudgetProvider` | No production alternative exists. INFO-level awareness at construction. |

## NoOp Awareness Design

Two INFO-level checks fire at construction when NoOp-only interfaces are detected:

1. **NoOpContentGuardrail**: "No production implementation available for
   IContentGuardrail — content safety is not enforced."
2. **NoOpBudgetProvider**: "No production implementation available for
   IBudgetProvider — execution budget is unlimited."

These fire at **INFO** (not WARN) because no production alternative exists to
swap to — WARN would create noise with no remediation path.

## NoOpSessionTakeoverLock Design Rationale

`NoOpSessionTakeoverLock` is **excluded** from INFO/WARN checks. Single-process
deployments rely on the engine's in-process `runningExecutions.putIfAbsent`
guard (plan 197). The takeover lock is incremental capability (NoOp → engine
walks existing path), not a security downgrade. The design decision is recorded
in the `NoOpSessionTakeoverLock` javadoc (lines 338-341).

## Recommended Minimum Guard Configuration for Production

```java
DefaultAgentEngine engine = DefaultAgentEngine.builder(chatService, toolManager)
    .toolAccessChecker(new DefaultToolAccessChecker())
    .pathAccessChecker(new DefaultPathAccessChecker())
    .auditLogger(new Slf4jAuditLogger())  // or custom DB logger
    .approvalGate(new DefaultApprovalGate())
    .securityLevelResolver(new DefaultSecurityLevelResolver())
    .permissionMatrix(new DefaultPermissionMatrix())
    .denialLedger(new DefaultDenialLedger())
    .postDenialGuard(new DefaultPostDenialGuard())
    .build();
```

All defaults above are already the engine's shipped defaults (constructor /
Builder), so the builder call without overrides satisfies the minimum
production guard configuration.
