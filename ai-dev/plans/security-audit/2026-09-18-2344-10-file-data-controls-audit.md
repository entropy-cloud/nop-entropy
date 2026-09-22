# Security Audit Plan 8 — File And Data Operations Controls Assurance Audit

> Plan Status: completed
> Last Reviewed: 2026-09-18
> Mission: security-audit
> Work Item: 8. 文件与数据操作审计 (`nop-file`, `nop-datav`, `nop-metadata`)
> Source: `ai-dev/backlog/security-audit-roadmap.md` (item 8, stages FILE-01/02, DATA-01, META-01)
> Related: plans 1-7 (this mission), `docs-for-ai/03-modules/` module docs
> Naming: date-prefix per mission-subdirectory convention.

## Execution Rules And Evidence Contract

Same contract as plan 3.

## Purpose

Read-only control-effectiveness audit of file upload/download, data-view,
and BI metadata surfaces (roadmap item 8). Reports for item 9; audit only.

## Current Baseline

Verified against live repo on 2026-09-18:

- File upload/download CONTROL IMPLEMENTATION lives in
  `nop-service-framework/nop-biz-file-core` (`NopFileStoreBizModel`,
  `AbstractGraphQLFileService`, `IFileStore`, `MediaTypeHelper`,
  `TestChunkFileUploadHandler`) — boundary-crossing in-scope read per the
  plan-3 AESTextCipher precedent (nop-file-dao depends on it). `nop-file/`
  (dao/web entities), `nop-datav/` (data-view service; no dedicated owner doc
  — recorded limitation), `nop-metadata/` (federated metadata/BI
  semantic layer/lineage/quality/reconciliation per module-groups.md §2.6 —
  includes native-SQL JOIN paths `queryAggregation`/`queryJoinData`/
  `queryTableData`, auto lineage extraction, quality checkpoint cron).
- SQL-builder injection defense verified repo-wide in CORE-04 (FilterBean/
  QueryBean whitelist + parameterization); DATA-01 audits the datav/metadata
  OWN dynamic-SQL construction on top (view definitions, cross-source JOIN
  assembly, aggregation SQL) against that baseline.
- File storage path handling: `nop-file` upload paths + download endpoints;
  FILE-01 audits path traversal + content-type handling beyond the plugin
  coordinate whitelist precedent (CORE-02).

## Goals

- FILE-01: file upload/download path-traversal defense — filename
  sanitization, storage key construction, download path authorization,
  content-type/extension validation.
- FILE-02: file storage access control — object-level authorization on
  download/delete, dedup side channels, metadata leakage.
- DATA-01: data-view/metadata injection risk — datav view DSL → SQL path,
  metadata cross-source JOIN assembly, native SQL item parameterization;
  classify against CORE-04 baseline (validated/parameterized/vulnerable).
- META-01: metadata lineage/quality security — lineage extractor input
  boundaries, quality-checkpoint scheduler authorization (cron entry),
  reconciliation data exposure.
- Reports `{date}-data-audit-{FILE-01|FILE-02|DATA-01|META-01}`,
  severity-classified, item-9 ready.

## Non-Goals

No product changes; no re-audit of SQL builder internals (CORE-04 done),
ORM tenant isolation (CORE-05), credential storage (item 3).

## Scope

In: `nop-file/`, `nop-datav/`, `nop-metadata/`, plus boundary-crossing reads
(+ focused tests) of `nop-service-framework/nop-biz-file-core`; `_vfs` DSL +
`_gen` spot, `@cfg:` secret sampling. Reports to mission audit dir.
Out: other module groups; remediation.

## Execution Plan

### Phase 1 - Evidence Collection

Status: completed
Targets: `nop-file/`, `nop-datav/`, `nop-metadata/`, `nop-service-framework/nop-biz-file-core` (read-only), `_tmp/security-audit/`

- Item Types: `Proof | Follow-up`

- [x] Control inventory → `_tmp/security-audit/data-inventory.md`.
- [x] QA static analysis (report-only) for the three groups.
- [x] Secret sweep + `_gen` spot; config inventory.
- [x] Test-coverage map; task-owned git assertion.

Exit Criteria:
- [x] Inventory exists, every entry backed by a live repo path.
- [x] QA outputs captured AND read; hits registered or dispositioned.
- [x] Secret sweep dispositions + _gen spot verdicts recorded.
- [x] Config-default inventory with anchors.
- [x] Test-coverage map with gap notes.
- [x] No task-owned product change vs recorded starting git status.
- [x] `ai-dev/logs/` entry updated.

### Phase 2 - Control-Effectiveness Review (FILE-01/02, DATA-01, META-01)

Status: completed
Targets: `ai-dev/audits/security-audit/` reports

- Item Types: `Proof | Decision | Follow-up`

- [x] FILE-01 traversal review; [ ] FILE-02 access-control review;
- [x] DATA-01 dynamic-SQL review (vs CORE-04 baseline);
- [x] META-01 lineage/quality/scheduler review;
- [x] Four reports + owner mapping; cross-check vs item 1 (no double ownership).
- [x] Focused tests: `./mvnw test -pl nop-file/nop-file-service,nop-datav/nop-datav-service,nop-metadata,nop-service-framework/nop-biz-file-core -am`
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
- [x] Roadmap item 8 transitions correct.
- [x] Reports self-contained.
- [x] `ai-dev/logs/` entry updated.

## Closure Gates

- [x] Four deliverables severity-classified with anchors.
- [x] Findings adjudicated with Fix handoff; nothing silently deferred.
- [x] Roadmap item 8 `done` strictly after independent closure audit.
- [x] Independent sub-agent closure audit + evidence in `## Closure`.
- [x] Anti-Hollow + textual consistency.
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/security-audit/2026-09-18-2344-10-file-data-controls-audit.md --strict` exit 0.
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0.

## Deferred But Adjudicated

(none)

## Non-Blocking Follow-ups

- Reusable patterns to `ai-dev/skills/`.

## Closure

Status Note: Closed after independent combined closure audit ALLOW. item 8 (file/data): four deliverables verified; anchors incl. bare-CrudBizModel NopFileRecord + published sensitive columns (F-F2-1) and queryEntityData full-column bypass (F-D1-1); traversal defenses + datav parameterization verified clean vs CORE-04; 11 findings adjudicated to item 9.
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
