# 147 IContentTrustEvaluator Interface + DefaultContentTrustEvaluator

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: L1-8b
> **Last Reviewed**: 2026-06-13
> **Source**: `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §4 Layer 1 L1-8b, `ai-dev/design/nop-ai-agent/nop-ai-agent-security-and-permissions.md` §5.1

## Purpose

Add `IContentTrustEvaluator` interface and `DefaultContentTrustEvaluator` to the security package. This component evaluates whether content from a given origin is trusted, which is a prerequisite for Layer 2's `ISecurityLevelResolver` (L2-13) to compute `trustedSource` hints. Although the roadmap labels this L1-8b, architecturally `IContentTrustEvaluator` is a Layer 2 helper (per `nop-ai-agent-security-and-permissions.md` §5.1) being implemented early as a standalone work item for availability before Layer 2 development begins.

## Current Baseline

- `io.nop.ai.agent.security` package contains: `IPermissionProvider`, `IToolAccessChecker`, `IPathAccessChecker`, `IAuditLogger` and their implementations — all Layer 1 security interfaces are implemented (L1-6 through L1-8a ✅)
- `AgentExecutionContext` exists in `io.nop.ai.agent.engine` with fields for agentModel, messages, sessionId, metadata, etc.
- No `ContentOrigin` enum exists anywhere in nop-ai-agent
- No `IContentTrustEvaluator` interface exists anywhere in nop-ai-agent
- Design doc `nop-ai-agent-security-and-permissions.md` §5.1 specifies `IContentTrustEvaluator` with method `boolean isTrustedSource(ContentOrigin origin, AgentExecutionContext ctx)` and `ContentOrigin` enum with values: `CHANNEL_INPUT`, `WEB_FETCH`, `FILE_READ`, `AGENT_GENERATED`
- Default trust policy (from design doc): `CHANNEL_INPUT` and `AGENT_GENERATED` → trusted; `WEB_FETCH` and `FILE_READ` → untrusted
- ~180+ tests pass for nop-ai-agent

## Goals

- `ContentOrigin` enum exists with 4 values per design doc
- `IContentTrustEvaluator` interface exists in `io.nop.ai.agent.security` per design doc §5.1
- `DefaultContentTrustEvaluator` implements the documented default trust policy
- All new code has corresponding unit tests
- Existing tests continue to pass

## Non-Goals

- Implementing `ISecurityLevelResolver` (L2-13, Layer 2)
- Implementing `IContentGuardrail` (L2-7, Layer 2)
- Adding XDSL configuration support for trust policies (Layer 2+)
- Changing any existing security interface or implementation
- Modifying `AgentExecutionContext`

## Scope

### In Scope

- `ContentOrigin` enum in `io.nop.ai.agent.security` package
- `IContentTrustEvaluator` interface in `io.nop.ai.agent.security` package
- `DefaultContentTrustEvaluator` class in `io.nop.ai.agent.security` package
- Unit tests for `ContentOrigin`, `IContentTrustEvaluator`, and `DefaultContentTrustEvaluator`

### Out Of Scope

- Any Layer 2 security component
- Changes to existing Layer 1 interfaces
- XDSL schema for trust policy configuration

## Execution Plan

### Phase 1 - Create ContentOrigin Enum, Interface, and Default Implementation

Status: completed
Targets: `io.nop.ai.agent.security` package

- Item Types: `Decision`, `Proof`

- [x] Create `ContentOrigin` enum in `io.nop.ai.agent.security` with values: `CHANNEL_INPUT`, `WEB_FETCH`, `FILE_READ`, `AGENT_GENERATED`
- [x] Create `IContentTrustEvaluator` interface in `io.nop.ai.agent.security` with method: `boolean isTrustedSource(ContentOrigin origin, AgentExecutionContext ctx)`
- [x] Create `DefaultContentTrustEvaluator` in `io.nop.ai.agent.security` implementing `IContentTrustEvaluator`: returns `true` for `CHANNEL_INPUT` and `AGENT_GENERATED`, returns `false` for `WEB_FETCH` and `FILE_READ`
- [x] Create `TestDefaultContentTrustEvaluator` verifying: (1) `CHANNEL_INPUT` → trusted, (2) `AGENT_GENERATED` → trusted, (3) `WEB_FETCH` → untrusted, (4) `FILE_READ` → untrusted, (5) null context does not affect result (evaluator is origin-based, not context-dependent for default). Enum values are exercised through the trust evaluation tests; no standalone `TestContentOrigin` needed.

Exit Criteria:

- [x] `ContentOrigin.java` exists in `io.nop.ai.agent.security` with exactly 4 enum values
- [x] `IContentTrustEvaluator.java` exists in `io.nop.ai.agent.security` with `boolean isTrustedSource(ContentOrigin, AgentExecutionContext)` method
- [x] `DefaultContentTrustEvaluator.java` exists in `io.nop.ai.agent.security` and implements `IContentTrustEvaluator` with the default trust policy from design doc §5.1
- [x] `TestDefaultContentTrustEvaluator.java` exists in test tree, exercises all 4 `ContentOrigin` values, and all assertions pass
- [x] **No silent no-op**: `DefaultContentTrustEvaluator` returns explicit boolean values, not placeholder defaults
- [x] **No wiring verification applicable**: `IContentTrustEvaluator` has no consumer in Layer 1; L2-13 `ISecurityLevelResolver` will be the first consumer
- [x] **New test rule compliance**: each new class (`ContentOrigin`, `IContentTrustEvaluator`, `DefaultContentTrustEvaluator`) has corresponding test coverage
- [x] `./mvnw compile -pl nop-ai-agent -am` passes
- [x] `./mvnw test -pl nop-ai-agent -am` passes (existing + new tests)
- [x] No owner-doc update required: design doc §5.1 already specifies this interface and implementation
- [x] `ai-dev/logs/` updated

## Closure Gates

- [x] `ContentOrigin` enum has 4 values matching design doc §5.1
- [x] `IContentTrustEvaluator` interface matches design doc §5.1 signature
- [x] `DefaultContentTrustEvaluator` trust policy matches design doc §5.1 default policy
- [x] All new classes have corresponding tests
- [x] `./mvnw compile -pl nop-ai-agent -am` passes
- [x] `./mvnw test -pl nop-ai-agent -am` passes
- [x] **Anti-Hollow Check**: `DefaultContentTrustEvaluator` has real logic (boolean switch on ContentOrigin), not empty body or stub
- [x] Independent closure audit completed and evidence recorded

## Deferred But Adjudicated

### XDSL configuration for trust policy

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: XDSL-based trust policy configuration is a Layer 2+ concern. The default implementation is sufficient for Layer 1 operation.
- Successor Required: yes
- Successor Path: L2-13 (ISecurityLevelResolver) or Layer 2 enhancement plan

## Non-Blocking Follow-ups

- L2-13: `ISecurityLevelResolver` interface + `NoOpSecurityLevelResolver` — will consume `IContentTrustEvaluator` for `trustedSource` hint computation
- L2-7: `IContentGuardrail` — related content security component at Layer 2

## Closure

Status Note: All Phase 1 items completed. 195 tests pass. ContentOrigin enum (4 values), IContentTrustEvaluator interface, DefaultContentTrustEvaluator (with real switch-based logic), and TestDefaultContentTrustEvaluator (11 test methods) all created in `io.nop.ai.agent.security`.

Closure Audit Evidence:

- Reviewer / Agent: opencode (glm-5.1) executing plan 147
- Evidence:
  - `ContentOrigin.java` exists with CHANNEL_INPUT, WEB_FETCH, FILE_READ, AGENT_GENERATED — matches design doc §5.1
  - `IContentTrustEvaluator.java` exists with `boolean isTrustedSource(ContentOrigin, AgentExecutionContext)` — matches design doc §5.1 signature
  - `DefaultContentTrustEvaluator.java` implements switch-based logic: CHANNEL_INPUT/AGENT_GENERATED → true, WEB_FETCH/FILE_READ → false — matches design doc §5.1 default policy. No silent no-op, no empty body, no stub.
  - `TestDefaultContentTrustEvaluator.java` has 11 test methods covering all 4 origins with and without null context, null origin, and all-values iteration
  - `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` → BUILD SUCCESS, 195 tests pass (0 failures, 0 errors)
  - No owner-doc update required (design doc already specifies this interface)
