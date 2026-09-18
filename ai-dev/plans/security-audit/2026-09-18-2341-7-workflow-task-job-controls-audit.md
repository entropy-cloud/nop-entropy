# Security Audit Plan 5 — Workflow / Task / Job Controls Assurance Audit

> Plan Status: draft
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

Status: planned
Targets: `nop-wf/`, `nop-task/`, `nop-job/` (read-only), `_tmp/security-audit/`

- Item Types: `Proof | Follow-up`

- [ ] Control inventory → `_tmp/security-audit/wf-inventory.md` (delegation
  classes, step executors, job scheduler/lock/retry classes).
- [ ] QA static analysis (report-only, read outputs) for the three modules.
- [ ] Secret sweep + `_gen` spot check; config-default inventory.
- [ ] Test-coverage map; prior-audit closure anchor list.
- [ ] Task-owned git-status assertion.

Exit Criteria:
- [ ] Inventory exists, every entry backed by a live repo path.
- [ ] QA outputs captured AND read; hits registered or dispositioned.
- [ ] Secret sweep dispositions + _gen spot verdicts recorded.
- [ ] Config-default inventory with anchors.
- [ ] Test-coverage map + prior-audit closure anchor list recorded.
- [ ] No task-owned product change vs recorded starting git status.
- [ ] `ai-dev/logs/` entry updated.

### Phase 2 - Control-Effectiveness Review (WF-01..03)

Status: planned
Targets: `ai-dev/audits/security-audit/` reports

- Item Types: `Proof | Decision | Follow-up`

- [ ] WF-01 delegation review; [ ] WF-02 task isolation review;
- [ ] WF-03 job scheduling review (incl. prior-audit closures re-verified);
- [ ] Three reports with findings + no-finding statements + owner mapping.
- [ ] Focused tests: `./mvnw test -pl nop-wf/nop-wf-service,nop-task/nop-task-service,nop-job/nop-job-service -am`
  (module paths verified live); results recorded.

Exit Criteria:
- [ ] Three reports exist with repo-resolvable anchors; every inventory entry classified exactly once.
- [ ] Findings carry Fix handoff fields.
- [ ] Focused tests green or triaged pre-existing with evidence.
- [ ] `ai-dev/logs/` entry updated.

### Phase 3 - Adjudication, Roadmap Sync, Closure

Status: planned

- [ ] Adjudicate; [ ] roadmap item 5 transitions; [ ] self-contained reports; log.

Exit Criteria:
- [ ] Zero pending findings (each remediation-target / adjudicated-no-fix with reason).
- [ ] Roadmap item 5 transitions correct.
- [ ] Reports self-contained.
- [ ] `ai-dev/logs/` entry updated.

## Closure Gates

- [ ] Three deliverables severity-classified with anchors.
- [ ] Findings adjudicated with Fix handoff; nothing silently deferred.
- [ ] Roadmap item 5 `done` strictly after independent closure audit.
- [ ] Independent sub-agent closure audit + evidence in `## Closure`.
- [ ] Anti-Hollow + textual consistency checks.
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/security-audit/2026-09-18-2341-7-workflow-task-job-controls-audit.md --strict` exit 0.
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0.

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

Status Note: (pending)
Completed: (pending)
Closure Audit Evidence: (pending)
Follow-up: (pending)
