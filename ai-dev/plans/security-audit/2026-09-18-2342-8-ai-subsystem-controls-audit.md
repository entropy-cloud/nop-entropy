# Security Audit Plan 6 — AI Subsystem Controls Assurance Audit

> Plan Status: active
> Last Reviewed: 2026-09-18
> Mission: security-audit
> Work Item: 6. AI子系统审计 (`nop-ai`)
> Source: `ai-dev/backlog/security-audit-roadmap.md` (item 6, stages AI-01..AI-04)
> Related: plans 1-5 (this mission), plan 333/335/336 (auth/bash/SSRF boundaries),
> `ai-dev/audits/2026-07-31-0539-arm-MA4.2-nop-ai-style.md`, module map in
> `docs-for-ai/01-repo-map/module-groups.md` (nop-ai layering table)
> Naming: date-prefix per mission-subdirectory convention.

## Execution Rules And Evidence Contract

Same contract as plan 3.

## Purpose

Read-only control-effectiveness audit of the AI subsystem (roadmap item 6):
gateway account switching, tool-call sandboxing, prompt-injection posture,
LLM response handling. Reports for item 9; audit only.

## Current Baseline

Verified against live repo on 2026-09-18:

- `nop-ai/` layering per module-groups.md: ai-api (contracts), ai-core (LLM
  + reliability), ai-agent (engine), ai-toolkit (tool executors), ai-tools,
  ai-gateway (failover + channel + scan-login — partially audited: AUTH-06
  covered scan-login MFA + autoconfig wiring; AI-01 owns the account-switch/
  failover surface), ai-shell (sandbox), coder/rag/mcp etc.
- Sandbox seams (plan 335 precedent, corrected anchors): toolkit layer =
  `IBashSandbox`/`HostBashSandbox`/`DockerBashSandbox` in nop-ai-toolkit
  (BashExecutor fail-closed wiring); agent-engine layer =
  `ISandboxBackend`/`DockerSandboxBackend` (+TestSandboxWiring). `nop-ai-shell`
  is an independent bash parsing/validation library with ZERO in-repo consumers
  (no pom deps, no imports) — disposition as unwired library (report-worthy).
  Toolkit executors (ReadFile/Bash/Http/GraphqlQuery/ApplyDelta/Patch/Skill)
  are the tool-call surface; `LocalToolFileSystem` the path boundary;
  `check-ai-tool-executor-boundary.mjs` is an existing invariant checker.
- Credential resolution for LLM calls goes through nop-credential
  (`IAiModelCredentialResolver`) — audited item 3; AI-04 owns response-side
  key-leak handling.
- Prior style audit 2026-07-31 (MA4.2) exists; not security, pointer only.

## Goals

- AI-01: gateway account/provider switching — transparent account switch on
  failover cannot leak cross-account context or credentials; provider-chain
  selection is server-configured (not client-influenced). INCLUDES the
  channel/scan-login surface (`ChannelLoginApiBizModel`,
  `TestChannelLoginApi`, `TestChannelLoginAccessCode`) — NOT covered by
  AUTH-06 (which audited only nop-auth-sso + nop-oauth server).
- AI-02: tool-call sandboxing — toolkit executor permission model, path
  confinement (`LocalToolFileSystem`), BOTH sandbox seams (toolkit
  `IBashSandbox` family + agent `ISandboxBackend` family), Http executor SSRF
  posture (plan 336 boundary), and `nop-ai-shell` unwired-library
  disposition.
- AI-03: prompt-injection posture — classify platform defenses (system-prompt
  isolation of tool outputs, structured-output parsing); document absence as
  limitation where applicable (LLM-layer mitigation, not framework guarantee).
- AI-04: LLM response handling — no credential/key material logged or echoed
  in responses/diagnostics; error paths don't leak provider payloads.
- Reports `{date}-ai-audit-AI-0N` (N=1..4), severity-classified, item-9 ready.

## Non-Goals

No product changes; no re-audit of credential storage (item 3), scan-login
MFA (AUTH-06 done), autoconfig wiring (fixed in batch 2); no model-safety
evaluation (jailbreak research) — posture classification only.

## Scope

In: `nop-ai/` (api, core, agent, toolkit, tools, gateway, shell; rag/coder
as consumers where AI-02/04 apply), `_vfs` DSL + `_gen` spot, `@cfg:` secret
sampling. Reports to mission audit dir.
Out: other module groups; remediation.

## Execution Plan

### Phase 1 - Evidence Collection

Status: in progress
Targets: `nop-ai/` (read-only), `_tmp/security-audit/`

- Item Types: `Proof | Follow-up`

- [ ] Control inventory → `_tmp/security-audit/ai-inventory.md`.
- [ ] QA static analysis (report-only) for `nop-ai` group.
- [ ] Run `node ai-dev/tools/check-ai-tool-executor-boundary.mjs` (existing
  invariant checker) and record results.
- [ ] Secret sweep (API keys in logs/diagnostics) + `_gen` spot; config inventory.
- [ ] Test-coverage map; task-owned git assertion.

Exit Criteria:
- [ ] Inventory exists, every entry backed by a live repo path.
- [ ] QA outputs captured AND read; hits registered or dispositioned.
- [ ] `check-ai-tool-executor-boundary.mjs` result recorded.
- [ ] Secret sweep dispositions + _gen spot verdicts recorded.
- [ ] Config-default inventory with anchors.
- [ ] Test-coverage map with gap notes.
- [ ] No task-owned product change vs recorded starting git status.
- [ ] `ai-dev/logs/` entry updated.

### Phase 2 - Control-Effectiveness Review (AI-01..04)

Status: planned
Targets: `ai-dev/audits/security-audit/` reports

- Item Types: `Proof | Decision | Follow-up`

- [ ] AI-01 account-switch review; [ ] AI-02 sandbox review (toolkit+shell+http);
- [ ] AI-03 injection-posture classification; [ ] AI-04 response-handling review;
- [ ] Four reports + owner mapping; cross-check vs items 2/3 (no double ownership).
- [ ] Focused tests: `./mvnw test -pl nop-ai/nop-ai-toolkit,nop-ai/nop-ai-core,nop-ai/nop-ai-gateway -am`
  (adjust to live paths); results recorded.

Exit Criteria:
- [ ] Four reports exist with repo-resolvable anchors; every inventory entry classified exactly once.
- [ ] Findings carry Fix handoff fields.
- [ ] Focused tests green or triaged pre-existing with evidence.
- [ ] `ai-dev/logs/` entry updated.

### Phase 3 - Adjudication, Roadmap Sync, Closure

Status: planned
Targets: roadmap, this plan, daily log

- Item Types: `Decision | Proof`

- [ ] Adjudicate; [ ] roadmap transitions; [ ] self-contained reports; log.

Exit Criteria:
- [ ] Zero pending findings (each remediation-target / adjudicated-no-fix with reason).
- [ ] Roadmap item 6 transitions correct.
- [ ] Reports self-contained.
- [ ] `ai-dev/logs/` entry updated.

## Closure Gates

- [ ] Four deliverables severity-classified with anchors.
- [ ] Findings adjudicated with Fix handoff; nothing silently deferred.
- [ ] Roadmap item 6 `done` strictly after independent closure audit.
- [ ] Independent sub-agent closure audit + evidence in `## Closure`.
- [ ] Anti-Hollow + textual consistency.
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/security-audit/2026-09-18-2342-8-ai-subsystem-controls-audit.md --strict` exit 0.
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0.

## Deferred But Adjudicated

### Prompt-injection as LLM-layer concern

- Classification: `watch-only residual`
- Why Not Blocking Closure: framework can enforce structural isolation
  (tool-output channel separation) but cannot guarantee model behavior;
  absence of model-level defenses is a documented limitation for item 9.
- Successor Required: no; Path: item 9.

## Non-Blocking Follow-ups

- Reusable patterns to `ai-dev/skills/`.

## Closure

Status Note: (pending)
Completed: (pending)
Closure Audit Evidence: (pending)
Follow-up: (pending)
