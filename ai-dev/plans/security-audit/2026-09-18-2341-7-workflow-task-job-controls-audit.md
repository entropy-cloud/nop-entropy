# Security Audit Plan 5 — Workflow / Task / Job Controls Assurance Audit

> Plan Status: completed
> Last Reviewed: 2026-09-18
> Mission: security-audit
> Work Item: 5. 工作流与任务引擎审计 (`nop-wf`, `nop-task`, `nop-job`)
> Source: `ai-dev/backlog/security-audit-roadmap.md` (item 5, stages WF-01..WF-03)
> Related: plans 1-4 (this mission), `docs-for-ai/02-core-guides/workflow-configuration.md`,
> prior audits `ai-dev/audits/2026-05-18-adversarial-review-nop-job*`, `deep-audit-nop-job-full`
> Naming: date-prefix per mission-subdirectory convention.

## Execution Rules And Evidence Contract

Same contract as plan 3 (read `ai-dev/audits/README.md` + owner docs first;
dual severity labels; no real secrets; Fix handoff fields; roadmap item 5 →
`planned` at execution entry; task-owned-change assertion vs recorded start
status; no commit unless requested).

## Purpose

Read-only control-effectiveness audit of workflow approval delegation,
task-execution isolation, and job scheduling (roadmap item 5). Reports for
item 9 consolidation; audit only.

## Current Baseline

Verified against live repo on 2026-09-18:

- Modules exist: `nop-wf/` (approval engine; WF delegation control =
  `nop-wf-dao/.../NopWfUserDelegate.java` + `nop-wf-core/.../WfActorAssignSupport.java`
  / `WorkflowEngineImpl.java` — NOT NopAuthUserSubstitution, which nop-wf
  never references), `nop-task/` (logical flow / step executors), `nop-job/`
  (scheduled jobs, distributed locks — 11 prior audit records through
  `2026-06-19-0931-adversarial-review-nop-job` exist under `ai-dev/audits/`).
- Prior audits: the full nop-job series (2026-05-18 reviews/deep audits
  through 2026-06-19-0931 adversarial review) is the closure re-verification
  anchor set; this plan re-verifies durable closures against live code rather
  than re-litigating. Auth-side login substitution (`DaoUserDelegateService`
  in nop-auth-service) is OUT of nop-wf's delegation surface — recorded as an
  ownership note for item 9 (no current owner among items 2-8 baselines).
- Known surfaces: wf delegation (delegate/substitute permissions), task step
  executors (permission context propagation), job scheduling (distributed
  lock correctness, retry semantics, `BeanMethodJobInvoker` cron dispatch —
  referenced by nop-metadata checkpoint scheduler).

## Goals

- WF-01: workflow approval delegation security (anchor `NopWfUserDelegate` /
  `WfActorAssignSupport`) — who can delegate, scope checks (tenant/user),
  audit trail of delegated approvals.
- WF-02: task-execution isolation — step executor permission context, secret
  propagation between steps, logical-flow input validation.
- WF-03: job scheduling security — distributed lock correctness (single
  execution under concurrency), retry semantics (no amplification), job
  trigger authorization, prior-audit closure re-verification.
- Reports `{date}-wf-audit-WF-0N` (N=1..3), severity-classified, item-9 ready.

## Non-Goals

No product changes; no re-audit of auth/RBAC checkers (item 2), ORM tenant
(item 1), GraphQL (item 4); no re-opening of adjudicated prior-audit findings
without new live evidence.

## Scope

In: `nop-wf/`, `nop-task/`, `nop-job/` (+ `_vfs` DSL, `_gen` spot check,
`@cfg:` secret sampling); mission audit dir reports.
Out: other module groups; remediation.

## Execution Plan

### Phase 1 - Evidence Collection

Status: completed
Targets: `nop-wf/`, `nop-task/`, `nop-job/` (read-only), `_tmp/security-audit/`

- Item Types: `Proof | Follow-up`

- [x] Control inventory → `_tmp/security-audit/wf-inventory.md` (delegation
  classes, step executors, job scheduler/lock/retry classes).
- [x] QA static analysis (report-only, read outputs) for the three modules.
- [x] Secret sweep + `_gen` spot check; config-default inventory.
- [x] Test-coverage map; prior-audit closure anchor list.
- [x] Task-owned git-status assertion.

Exit Criteria:
- [x] Inventory exists, every entry backed by a live repo path.
- [x] QA outputs captured AND read; hits registered or dispositioned.
- [x] Secret sweep dispositions + _gen spot verdicts recorded.
- [x] Config-default inventory with anchors.
- [x] Test-coverage map + prior-audit closure anchor list recorded.
- [x] No task-owned product change vs recorded starting git status.
- [x] `ai-dev/logs/` entry updated.

### Phase 2 - Control-Effectiveness Review (WF-01..03)

Status: completed
Targets: `ai-dev/audits/security-audit/` reports

- Item Types: `Proof | Decision | Follow-up`

- [x] WF-01 delegation review; [x] WF-02 task isolation review;
- [x] WF-03 job scheduling review (incl. prior-audit closures re-verified);
- [x] Three reports with findings + no-finding statements + owner mapping.
- [x] Focused tests: `./mvnw test -pl nop-wf/nop-wf-service,nop-task/nop-task-service,nop-job/nop-job-service -am`
  (module paths verified live); results recorded.

Exit Criteria:
- [x] Three reports exist with repo-resolvable anchors; every inventory entry classified exactly once.
- [x] Findings carry Fix handoff fields.
- [x] Focused tests green or triaged pre-existing with evidence.
- [x] `ai-dev/logs/` entry updated.

### Phase 3 - Adjudication, Roadmap Sync, Closure

Status: completed

- [x] Adjudicate; [ ] roadmap item 5 transitions; [ ] self-contained reports; log.

Exit Criteria:
- [x] Zero pending findings (each remediation-target / adjudicated-no-fix with reason).
- [x] Roadmap item 5 transitions correct.
- [x] Reports self-contained.
- [x] `ai-dev/logs/` entry updated.

## Closure Gates

- [x] Three deliverables severity-classified with anchors.
- [x] Findings adjudicated with Fix handoff; nothing silently deferred.
- [x] Roadmap item 5 `done` strictly after independent closure audit.
- [x] Independent sub-agent closure audit + evidence in `## Closure`.
- [x] Anti-Hollow + textual consistency checks.
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/security-audit/2026-09-18-2341-7-workflow-task-job-controls-audit.md --strict` exit 0.
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0.

## Deferred But Adjudicated

### Prior nop-job audit findings (2026-05-18)

- Classification: `watch-only residual`
- Why Not Blocking Closure: adjudicated in prior audits with recorded
  ownership; this plan re-verifies live behavior and reports only NEW
  regressions or assumption breaks.
- Successor Required: no; Path: prior audit records.

## Non-Blocking Follow-ups

- Reusable patterns to `ai-dev/skills/` if applicable.

## Closure

Status Note: Closed after independent combined closure audit ALLOW. item 5 (WF/task/job): three deliverables verified; 8-anchor spot check incl. dead-code NopWfUserDelegate + live DaoUserDelegateService source-break assertion; prior-audit closure map 14/17 live-fixed; 9 findings adjudicated.
Read-only audit; findings consolidated via roadmap item 9 (fix batches).
Completed: 2026-09-19

Closure Audit Evidence:

- Reviewer / Agent: independent fresh-session closure auditor (agent_f7726c60,
  combined audit of items 4/5/6), verdict **ALLOW**.
- All sections PASS: reports on disk with dual severity labels + Fix handoff;
  27 live anchors re-verified across the three audits (incl. the HIGH F-AI4-1
  metrics-label leak and the WF dead-code/live-source assumption break);
  focused tests exit 0 with zero failing lines (item-tests logs); coverage
  gaps recorded as gaps (not clean evidence); no re-litigation of items 1-3;
  roadmap untouched pre-closure; checklist + doc-links exit 0 (auditor-run).
- Task-owned-change live check: no product modifications in the audited
  module groups during execution (workspace dirty files predate execution).
- Roadmap done-transition executed strictly AFTER this evidence (last step).

Follow-up:

- Findings feed item 9 consolidation → fix batches. No remaining plan-owned
  work.
