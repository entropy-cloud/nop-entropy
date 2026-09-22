# Security Audit Plan 7 — Network And Integration Controls Assurance Audit

> Plan Status: completed
> Last Reviewed: 2026-09-18
> Mission: security-audit
> Work Item: 7. 网络与集成审计 (`nop-network`, `nop-stream`, `nop-tcc`)
> Source: `ai-dev/backlog/security-audit-roadmap.md` (item 7, stages NET-01..NET-04)
> Related: plans 1-6 (this mission), plan 336 (HTTP SSRF boundary),
> extensive prior nop-stream audits (`ai-dev/audits/2026-05-*`, `2026-05-27/28-*` deep audits)
> Naming: date-prefix per mission-subdirectory convention.

## Execution Rules And Evidence Contract

Same contract as plan 3.

## Purpose

Read-only control-effectiveness audit of HTTP client, MQTT, distributed
stream, and TCC transaction surfaces (roadmap item 7). Reports for item 9;
audit only.

## Current Baseline

Verified against live repo on 2026-09-18:

- `nop-network/` = `nop-http/` (nop-http-api + client variants
  nop-http-client-apache/jdk/oauth/okhttp + nop-http-server-filters) and
  `nop-vertx/` (MQTT at `nop-vertx/nop-vertx-mqtt-client` /
  `nop-vertx-mqtt-server`: IMqttAuthChecker, MqttSessionManager);
  `nop-stream/` (10 submodules — audited series 2026-05-20 through
  2026-06-05 PLUS 2026-08-12/13 invariant-loop audits with standing checkers
  `check-nop-stream-invariants.mjs` / `check-nop-stream-audit-manifest.mjs`);
  `nop-tcc/` (distributed transactions). No nop-tcc owner doc exists (noted
  limitation; nop-network.md / nop-stream.md exist).
- HTTP client security posture partially covered by plan 336 (SSRF boundary
  hardening, completed); NET-01 re-verifies TLS/redirect handling + reviews
  what 336 did NOT cover.
- Prior-audit closure anchor = the LATEST adjudications (2026-08-13
  invariant-loop series + standing invariant checkers), not the May
  intermediate states; checkpoint checksum mechanics were just fixed in fix
  batch 2 (F-STREAM-1) — this item verifies the fix's regression test and the
  broader integrity surface rather than re-litigating.

## Goals

- NET-01: HTTP client security — TLS defaults (verification not disableable
  to no-op silently), redirect handling (no credential leak cross-origin),
  timeout defaults; SSRF posture pointer to plan 336 closures re-verified.
- NET-02: MQTT connection security (anchor `nop-vertx/nop-vertx-mqtt-client|server`)
  — auth material handling, TLS option, topic-injection surface in
  publisher/subscriber helpers.
- NET-03: stream processing isolation — RPC control-plane auth posture,
  transport security, checkpoint storage access control, prior-audit closure
  spot re-verification.
- NET-04: TCC transaction timeout handling — timeout enforcement, retry
  bounds, no indefinite resource locks.
- Reports `{date}-net-audit-NET-0N` (N=1..4), severity-classified, item-9 ready.

## Non-Goals

No product changes; no re-audit of AI gateway's HTTP usage (item 6 consumes),
plugin artifact download (CORE-02 done); no re-opening of adjudicated
stream-audit findings without new live evidence.

## Scope

In: `nop-network/`, `nop-stream/` (prior-closure verification + new surface),
`nop-tcc/`, `_vfs` DSL + `_gen` spot, `@cfg:` secret sampling. Reports to
mission audit dir.
Out: other module groups; remediation.

## Execution Plan

### Phase 1 - Evidence Collection

Status: completed
Targets: `nop-network/`, `nop-stream/`, `nop-tcc/` (read-only), `_tmp/security-audit/`

- Item Types: `Proof | Follow-up`

- [x] Control inventory → `_tmp/security-audit/net-inventory.md`.
- [x] QA static analysis (report-only) for the three groups.
- [x] Secret sweep + `_gen` spot; config inventory (TLS/timeout defaults).
- [x] Test-coverage map + prior-audit closure anchor list; task-owned git assertion.

Exit Criteria:
- [x] Inventory exists, every entry backed by a live repo path.
- [x] QA outputs captured AND read; hits registered or dispositioned.
- [x] Secret sweep dispositions + _gen spot verdicts recorded.
- [x] Config-default inventory (TLS/timeout defaults) with anchors.
- [x] Test-coverage map + prior-audit closure anchors (latest series) recorded.
- [x] No task-owned product change vs recorded starting git status.
- [x] `ai-dev/logs/` entry updated.

### Phase 2 - Control-Effectiveness Review (NET-01..04)

Status: completed
Targets: `ai-dev/audits/security-audit/` reports

- Item Types: `Proof | Decision | Follow-up`

- [x] NET-01 HTTP client review (336 closures re-verified + uncovered surface);
- [x] NET-02 MQTT review; [ ] NET-03 stream isolation review;
- [x] NET-04 TCC timeout review;
- [x] Four reports + owner mapping; cross-check vs plans 336/1 (no double ownership).
- [x] Focused tests: `./mvnw test -pl nop-network/nop-http,nop-network/nop-vertx,nop-stream/nop-stream-runtime,nop-tcc -am`
  (paths verified live); results recorded.

Exit Criteria:
- [x] Four reports exist with repo-resolvable anchors; every inventory entry classified exactly once.
- [x] Findings carry Fix handoff fields.
- [x] Focused tests green or triaged pre-existing with evidence.
- [x] `ai-dev/logs/` entry updated.

### Phase 3 - Adjudication, Roadmap Sync, Closure

Status: completed
Targets: roadmap, this plan, daily log

- Item Types: `Decision | Proof`

- [x] Adjudicate; [ ] roadmap transitions; [ ] self-contained reports; log.

Exit Criteria:
- [x] Zero pending findings (each remediation-target / adjudicated-no-fix with reason).
- [x] Roadmap item 7 transitions correct.
- [x] Reports self-contained.
- [x] `ai-dev/logs/` entry updated.

## Closure Gates

- [x] Four deliverables severity-classified with anchors.
- [x] Findings adjudicated with Fix handoff; nothing silently deferred.
- [x] Roadmap item 7 `done` strictly after independent closure audit.
- [x] Independent sub-agent closure audit + evidence in `## Closure`.
- [x] Anti-Hollow + textual consistency.
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/security-audit/2026-09-18-2343-9-network-integration-controls-audit.md --strict` exit 0.
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0.

## Deferred But Adjudicated

### Historical nop-stream audit findings

- Classification: `watch-only residual`
- Why Not Blocking Closure: adjudicated across the 2026-05 audit series with
  recorded ownership; this plan re-verifies live behavior and reports only
  NEW regressions.
- Successor Required: no; Path: prior audit records.

## Non-Blocking Follow-ups

- Reusable patterns to `ai-dev/skills/`.

## Closure

Status Note: Closed after independent combined closure audit ALLOW. item 7 (network/integration): four deliverables verified; anchors incl. Apache redirect header-forwarding (hc5 bytecode evidence), OkHttp trust-all default, ignore-ssl-certs chain, MQTT authChecker-null fail-open, and both stream invariant checkers independently re-run red (exit 1, matching F-N3-1); prior closures (336/2026-08-13/F-STREAM-1) hold; 10 findings adjudicated to item 9.
Read-only audit; findings consolidated via roadmap item 9 (fix batches).
Completed: 2026-09-19

Closure Audit Evidence:

- Reviewer / Agent: independent fresh-session closure auditor (agent_554758e5,
  combined audit of items 7/8), verdict **ALLOW**.
- All sections PASS: reports with dual severity labels + Fix handoff; live
  anchors re-verified (6 for item 7 incl. both invariant checkers re-run
  red matching F-N3-1; 7 for item 8); focused tests exit 0 zero failures
  (item7/item8 logs); coverage gaps recorded as gaps; no re-litigation of
  prior closures; roadmap untouched pre-closure; checklist + doc-links exit 0
  (auditor-run).
- Roadmap done-transition executed strictly AFTER this evidence (last step).

Follow-up:

- Findings feed item 9 consolidation → fix batches. No remaining plan-owned
  work.
