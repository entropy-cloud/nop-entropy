# Complete MA2 — Remaining ORM/BizModel/Service Audits

> Plan Status: active
> Last Reviewed: 2026-07-30
> Source: `ai-dev/backlog/audit-remediation-roadmap.md` §MA2, `ai-dev/audits/arm-index.md`, `ai-dev/skills/deep-audit-prompts.md`, `ai-dev/skills/orm-model-audit-prompt.md`
> Mission: audit-remediation
> Work Item: MA2.2 + MA2.3 + MA2.5 + MA2.6 + MA2.7

## Purpose

Complete the remaining five sub-items of MA2 (ORM/BizModel/Service Layer Audit) for nop-ai module group: generation pipeline integrity, Delta compliance, XMeta alignment, GraphQL/API layer, and IoC/Bean configuration.

## Current Baseline

- `ai-dev/backlog/audit-remediation-roadmap.md` tracks MA2.2, MA2.3, MA2.5, MA2.6, MA2.7 as `todo`
- `ai-dev/audits/arm-index.md` shows all five as unstarted
- MA2.1 (ORM model audit) and MA2.4 (BizModel audit) are done — report at `ai-dev/audits/2026-07-30-2130-arm-MA2.1-2.4-nop-ai-orm-biz.md` with 1 P0, 4 P1, 6 P2/P3 findings
- P0-MA2-01 (dual ORM source drift) fixed via `ai-dev/plans/2026-07-30-2130-arm-fix-p0-ma2-01.md`
- MR1 (P1 batch fix for MA1+MA2 findings) depends on MA2 completion
- Generation pipeline structure: `nop-ai-dao/` has `_gen/` under `src/main/java/io/nop/ai/dao/entity/`; `nop-ai-service/` has **no** `_gen/` Java output (service-layer codegen produces `_service.beans.xml` and BizModel proxies); `nop-ai-web/` has per-entity `_gen/` under `src/main/resources/_vfs/nop/ai/pages/*/`
- Production Delta directories: no `_vfs/_delta/` dirs exist in any nop-ai production module (only in `nop-ai-agent/src/test/resources/`)

## Goals

- Produce audit report for MA2.2 (generation pipeline: model→codegen→dao→meta→service→web)
- Produce audit report for MA2.3 (Delta customization compliance)
- Produce audit report for MA2.5 (XMeta vs BizModel alignment)
- Produce audit report for MA2.6 (GraphQL/API layer)
- Produce audit report for MA2.7 (IoC/Bean configuration)
- Update `ai-dev/audits/arm-index.md` with findings and status

## Non-Goals

- Fixing any findings discovered (will be addressed in MR1)
- Re-auditing MA2.1 or MA2.4 (already done)
- Auditing MA1, MA3, MA4, MA5, MA6 items (separate plans)
- MCP modules (excluded per roadmap)

## Scope

### In Scope

- MA2.2: `nop-ai/model/` (codegen pipeline integrity), `nop-ai/nop-ai-dao/src/main/java/io/nop/ai/dao/entity/_gen/`, `nop-ai/nop-ai-service/` (verify service-layer codegen produces correct `_service.beans.xml` and BizModel proxies — no `_gen/` Java dir exists, which is expected), `nop-ai/nop-ai-web/src/main/resources/_vfs/nop/ai/pages/*/_gen/` — verify model→codegen→dao→meta→service→web chain completeness
- MA2.3: `nop-ai/*/src/main/resources/_vfs/` — assess `x:extends` usage in non-Delta files; note: no production `_vfs/_delta/` directories exist
- MA2.5: `nop-ai/nop-ai-meta/` xmeta files vs `nop-ai/nop-ai-service/` BizModel methods
- MA2.6: `nop-ai/nop-ai-service/` GraphQL schema / API exposure
- MA2.7: `nop-ai/*/beans.xml` — injection patterns, generated file boundaries
- `ai-dev/audits/arm-index.md` — update with findings

### Out Of Scope

- Code fixes (deferred to MR1)
- MA2.1 and MA2.4 (already audited)
- MCP modules
- Other milestones

## Execution Plan

### Phase 1 — MA2.2: Generation Pipeline Integrity Audit

Status: planned
Targets: `nop-ai/model/`, `nop-ai/nop-ai-dao/src/main/java/io/nop/ai/dao/entity/_gen/`, `nop-ai/nop-ai-service/` (check `_service.beans.xml` and proxy generation), `nop-ai/nop-ai-meta/` (generated xmeta), `nop-ai/nop-ai-web/src/main/resources/_vfs/nop/ai/pages/*/_gen/`

- Item Types: `Proof | Decision`

- [ ] Execute dimension D05 audit (generation pipeline integrity) using `deep-audit-prompts.md` skill
- [ ] Verify model→codegen→dao→meta→service→web chain: confirm `_gen/` artifacts exist at each expected layer; identify any gaps
- [ ] Note: service module has no `_gen/` Java dir — verify that `_service.beans.xml` + BizModel proxy gen is correct and expected
- [ ] Iterate deep-dive rounds (up to 3 rounds or until no new findings)
- [ ] Execute independent review pass on findings
- [ ] Save report at `ai-dev/audits/{YYYY-MM-DD-HHMM}-arm-MA2.2-nop-ai-pipeline.md`
- [ ] Categorize findings with Finding IDs following `P<level>-MA2-<seq>` format (existing IDs go up to P3-MA2-11; new findings start from P<level>-MA2-012)

Exit Criteria:

- [ ] Audit report saved with documented findings, severities, file locations
- [ ] All findings reviewed and categorized
- [ ] No owner-doc update required (audit-only plan)
- [ ] `ai-dev/logs/` updated

### Phase 2 — MA2.3: Delta Customization Compliance Audit

Status: planned
Targets: `nop-ai/*/src/main/resources/_vfs/` (assess x:extends usage, hand-edit vs generated boundary; no production `_vfs/_delta/` dirs exist, so D06 audit focuses on `x:extends` patterns in non-Delta `_vfs/` files)

- Item Types: `Proof | Decision`

- [ ] Execute dimension D06 audit (Delta customization compliance) using `deep-audit-prompts.md` skill
- [ ] Note: scan `_vfs/` for `x:extends` usage (in beans.xml, pages, models); no production `_delta/` dirs exist — audit report states this explicitly
- [ ] Iterate deep-dive rounds (up to 3 rounds or until no new findings)
- [ ] Execute independent review pass on findings
- [ ] Save report at `ai-dev/audits/{YYYY-MM-DD-HHMM}-arm-MA2.3-nop-ai-delta.md`
- [ ] Categorize findings with Finding IDs following `P<level>-MA2-<seq>` format

Exit Criteria:

- [ ] Audit report saved with documented findings, severities, file locations
- [ ] All findings reviewed and categorized
- [ ] No owner-doc update required (audit-only plan)
- [ ] `ai-dev/logs/` updated

### Phase 3 — MA2.5: XMeta vs BizModel Alignment Audit

Status: planned
Targets: `nop-ai/nop-ai-meta/` (xmeta files), `nop-ai/nop-ai-service/` (BizModel files)

- Item Types: `Proof | Decision`

- [ ] Execute dimension D11 audit (XMeta vs BizModel alignment) using `deep-audit-prompts.md` skill
- [ ] Iterate deep-dive rounds (up to 3 rounds or until no new findings)
- [ ] Execute independent review pass on findings
- [ ] Save report at `ai-dev/audits/{YYYY-MM-DD-HHMM}-arm-MA2.5-nop-ai-xmeta.md`
- [ ] Categorize findings with Finding IDs following `P<level>-MA2-<seq>` format

Exit Criteria:

- [ ] Audit report saved with documented findings, severities, file locations
- [ ] All findings reviewed and categorized
- [ ] No owner-doc update required (audit-only plan)
- [ ] `ai-dev/logs/` updated

### Phase 4 — MA2.6: GraphQL & API Layer Audit

Status: planned
Targets: `nop-ai/nop-ai-service/` (BizModel annotations, GraphQL schema), `nop-ai/nop-ai-meta/` (xMeta for GraphQL field definitions)

- Item Types: `Proof | Decision`

- [ ] Execute dimension D12 audit (GraphQL/API layer) using `deep-audit-prompts.md` skill
- [ ] Primary targets: `@BizQuery`/`@BizMutation` annotations, selection mechanisms, pagination in service module; xMeta-driven field exposure from meta module; `nop-ai-api/` interfaces are pure Java contracts (no GraphQL annotations) — include structural check only
- [ ] Iterate deep-dive rounds (up to 3 rounds or until no new findings)
- [ ] Execute independent review pass on findings
- [ ] Save report at `ai-dev/audits/{YYYY-MM-DD-HHMM}-arm-MA2.6-nop-ai-graphql.md`
- [ ] Categorize findings with Finding IDs following `P<level>-MA2-<seq>` format

Exit Criteria:

- [ ] Audit report saved with documented findings, severities, file locations
- [ ] All findings reviewed and categorized
- [ ] No owner-doc update required (audit-only plan)
- [ ] `ai-dev/logs/` updated

### Phase 5 — MA2.7: IoC & Bean Configuration Audit

Status: planned
Targets: `nop-ai/*/src/main/resources/_vfs/**/*.beans.xml`

- Item Types: `Proof | Decision`

- [ ] Execute dimension D08 audit (IoC/Bean configuration) using `deep-audit-prompts.md` skill
- [ ] Scan all beans.xml for injection style compliance, generated file boundary, unused bean definitions
- [ ] Iterate deep-dive rounds (up to 3 rounds or until no new findings)
- [ ] Execute independent review pass on findings
- [ ] Save report at `ai-dev/audits/{YYYY-MM-DD-HHMM}-arm-MA2.7-nop-ai-ioc.md`
- [ ] Categorize findings with Finding IDs following `P<level>-MA2-<seq>` format

Exit Criteria:

- [ ] Audit report saved with documented findings, severities, file locations
- [ ] All findings reviewed and categorized
- [ ] No owner-doc update required (audit-only plan)
- [ ] `ai-dev/logs/` updated

### Phase 6 — Index Update

Status: planned
Targets: `ai-dev/audits/arm-index.md`

- Item Types: `Fix | Follow-up`

- [ ] Register MA2.2, MA2.3, MA2.5, MA2.6, MA2.7 reports in `arm-index.md`
- [ ] Update P1 findings summary with any new P1 findings
- [ ] Update roadmap: MA2.2, MA2.3, MA2.5, MA2.6, MA2.7 → `done`

Exit Criteria:

- [ ] `arm-index.md` reflects all five completed audits with report links
- [ ] Roadmap markers updated
- [ ] `ai-dev/logs/` updated

## Closure Gates

- [ ] All five MA2 sub-item audit reports saved in `ai-dev/audits/`
- [ ] All findings categorized and registered in `arm-index.md`
- [ ] No in-scope audit item left uncovered
- [ ] Independent closure audit completed and recorded

## Deferred But Adjudicated

(None — audit-only plan; code fixes deferred to MR1.)

## Non-Blocking Follow-ups

- MA2 findings will be expanded into concrete fix items in MR1 (separate plan)

## Closure

Status Note: *(To be filled on closure)*
Completed:

Closure Audit Evidence:

- Reviewer / Agent:
- Audit Session:
- Evidence:

Follow-up:

- No remaining plan-owned work beyond handoff to MR1
