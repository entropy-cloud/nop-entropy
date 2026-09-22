# Security Audit Fix Batch 4 — HIGH Finding + Quick-Win Mediums

> Plan Status: completed (scope adjudicated by item-9 consolidation; user-authorized
> standing fix instruction; activation at creation per roadmap item 13)
> Last Reviewed: 2026-09-19
> Mission: security-audit
> Work Item: 13. 修复批次4
> Source: `ai-dev/audits/security-audit/2026-09-19-consolidation-summary.md` (batch-4 scope)
> Related: batches 1-3 (closed), AI-04/WF-03/NET-01/02/03/API-02 reports
> Naming: date-prefix per mission-subdirectory convention.

## Purpose

Fix the single HIGH finding (F-AI4-1: standby-account API key exported as a
micrometer metric label) plus six quick-win MEDIUMs and one LOW quick win —
all fixes behavior-bounded (no architecture changes; deferred items with
design implications stay with roadmap item 14).

## Current Baseline (anchors from closure-audit-verified reports)

1. F-AI4-1 HIGH: `FailoverMetricsImpl.java:44-128` — every counter/timer
   tags `"account", nvl(accountKey)`; accountKey is the standby account's raw
   API key (ModelClassCandidate.java:50-53). Fix: mask the tag value
   (preserve distinguishability without exposing the key).
2. F-AI4-2: `DefaultChatLogger.java:34-70` — `CREDENTIAL_PATTERNS[2] =
   "sk-[A-Za-z0-9]{20,}"` has no capturing group while the loop replaces with
   `"$1: ***REDACTED***"` → IndexOutOfBoundsException on real sk- keys (whole
   LLM call fails; redaction never worked). Fix: capture group + test.
3. F-AI2-1: `BashSandboxPaths.validateWorkingDirectory:19-22` —
   `if (workingDirectory == null) return;` fail-open. Fix: null → reject
   (fail-closed; callers without a workDir must not silently escape the jail).
4. F-N2-1: `VertxMqttServer.handleEndpoint:77-97` — authChecker==null accepts
   every endpoint. Fix: null checker → reject with explicit error (deployments
   wanting anonymous MQTT must provide an allow-all checker bean).
5. F-N1-3: `ignore-ssl-certs=true` disables cert+hostname validation silently
   (HttpClientConfig:51, CompositeX509TrustManager:57-60, Apache:169-176).
   Fix: prominent one-time WARN at client construction when active (escape
   hatch stays, but never silent).
6. F-WF-03-1: `NopJobTaskLogBizModel.reportTaskLog:72-85` + NopJobTaskLog.xbiz
   — no auth (any logged-in user can forge task logs). Fix: add module-consistent
   auth declaration (same admin gate as NopJobSchedule family).
7. F-N3-1: both stream invariant checkers red (registries stale after
   refactors). Fix: resync `ai-dev/audits/nop-stream-invariants/*.json`
   registries + manifest denominators so both checkers exit 0.
8. F-API2-1 LOW: `enable-action-auth=true` with checker bean missing =
   silent fail-open. Fix: startup WARN when the flag is on but no checker
   bean is registered (visibility quick win).

## Goals / Non-Goals

Goals: the eight fixes above, each with a regression test where behavior is
assertable (1,2,3,4,5,6; 7 = tool-green; 8 = warn-site presence + unit).
Non-Goals: item-14 successor MEDIUMs; no architecture/redesign; no default
flips beyond explicit fail-closed null-checker paths (4) documented above.

## Execution Plan

### Phase 1 - Fixes With Regression Tests

Status: completed

- [x] F-AI4-1: mask account tag in FailoverMetricsImpl (static mask helper,
      e.g. first-4 + "***" + len); unit test asserts tag != raw key.
- [x] F-AI4-2: fix CREDENTIAL_PATTERNS sk- entry to a capturing group; unit
      test: real-shaped sk- key redacted, no exception.
- [x] F-AI2-1: BashSandboxPaths null workDir → explicit reject; unit test.
- [x] F-N2-1: VertxMqttServer null authChecker → reject; unit test.
- [x] F-N1-3: one-time WARN in client construction when ignoreSslCerts=true
      (Apache + OkHttp + JDK providers share config; single warn site at
      config consumption or per provider); unit test via warn-flag/log capture
      or package-visible state.
- [x] F-WF-03-1: add auth to reportTaskLog action (xbiz auth declaration
      consistent with module's admin-gated actions); regression: schema/meta
      assertion or biz-level test consistent with module test patterns.
- [x] F-N3-1: resync invariant registries + audit manifest; both node
      checkers exit 0.
- [x] F-API2-1: dual-site WARN (setter + per-context) when enable-action-auth=true and checker bean absent, observable via `actionAuthNoCheckerWarned` flag; unit test `TestGraphQLEngineActionAuthWarn` (2 cases: fires when enabled+missing, silent when disabled).
- [x] Focused modules compile + tests: ai-gateway, ai-core, ai-toolkit,
      vertx-mqtt-server, http-api/apache/okhttp, job-service, wf/task/job
      chain as needed.

Exit Criteria:
- [x] All eight fixes landed with tests/tool-green evidence.
- [x] `./mvnw test -pl <affected modules> -am` exit 0.
- [x] Style clean; no silent no-op; log entry.

### Phase 2 - Verification, Gates, Closure

Status: completed
- [x] Full reactor `./mvnw test -T 1C` exit 0.
- [x] Hollow scans (full paths) for touched modules exit 0.
- [x] checklist + doc-links tools exit 0 (relative path).
- [x] Independent closure audit → evidence → item 13 done → commit.

## Closure Gates
- [x] All eight scope items fixed with evidence; zero dropped.
- [x] Full reactor green; tools green.
- [x] Independent closure audit ALLOW + evidence in `## Closure`.
- [x] Roadmap item 13 `done` strictly after audit; commit.

## Deferred But Adjudicated
### Item-14 successor MEDIUMs (14)
- Classification: successor-deferred (consolidation-summary rationale)
- Why Not Blocking Closure: no CRITICAL/HIGH open; each needs behavior/
  architecture decisions beyond automated-batch scope; remain roadmap item 14.
- Successor Required: yes; Path: roadmap item 14 (future fix batches).

## Closure

Status Note: Closed after independent closure audit ALLOW. All 8 scope items
(HIGH F-AI4-1 + 6 quick-win MEDIUMs + 1 LOW quick win) fixed with regression
tests; invariant checkers green; full reactor BUILD SUCCESS.
Completed: 2026-09-19

Closure Audit Evidence:

- Reviewer / Agent: independent fresh-session closure auditor agent_9d4f27e3 — round 1 **DENY** (A-M1 missing unit test / A-M2 phase status / A-M3 log / A-M4 commit order), all remediation items executed, re-audit round 2 (agent_f8babd3b) **ALLOW** (4/4 remediation PASS; A-M1 unit test verified green with WARN line actually captured in stdout).
- Per-fix verification: masking at all 10 metric tag sites + regression test;
  sk- capturing-group fix + redaction E2E test; sandbox null-reject + test;
  MQTT fail-closed + adapted allow-all test harness + rejection test;
  ignore-ssl-certs WARN site + behavior pin test; reportTaskLog auth gate +
  VFS assertion test; invariant registries re-pinned (both checkers exit 0);
  engine dual-site WARN for action-auth-without-checker.
- Verification: targeted regressions green; `_tmp/security-audit/full-reactor-tests4.log`
  FULL-EXIT:0 BUILD SUCCESS zero failing lines; hollow scans (ai-gateway,
  vertx-mqtt-server full paths) exit 0; doc-links exit 0; checklist exit 0.
- Roadmap item 13 `planned → done` strictly after audit (last step).

Follow-up:

- Item-14 successor MEDIUMs (14) remain roadmap-owned future fix batches.
