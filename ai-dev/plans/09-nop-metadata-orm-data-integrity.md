# 09 nop-metadata ORM Model Integrity & Cross-DB Join Data Correctness

> Plan Status: active
> Execution Order: 2
> Last Reviewed: 2026-07-22
> Source: `ai-dev/audits/2026-07-21-2039-multi-audit-nop-metadata.md` (ORM-03, ORM-05, ORM-06, ORM-07, ORM-08), `ai-dev/audits/2026-07-21-2039-open-audit-nop-metadata.md` (AR-21, AR-24)
> Related: `06-nop-metadata-bizmodel-api-contract.md` (resolved ORM-01, ORM-02, ORM-04), `08-nop-metadata-remaining-api-contract-code-quality.md`

## Purpose

Close all remaining ORM model design issues and CrossDbJoinMerger data correctness findings in nop-metadata. Fixes data integrity at both the schema level (indexes, constraints) and the runtime join level (column collision resolution, key type consistency).

## Current Baseline

- `NopMetaBusinessDomain` is referenced by 6 other entities but has no conventional indexes — tree queries (parent-child traversal) have no index support.
- `NopMetaQualityCheckpoint` has an optional FK (`qualityScoreId`) combined with `cascadeDelete="true"` — when the FK is null, cascade delete behavior is semantically undefined.
- `NopMetaTableJoin` has dual FK systems (entity-level + table-level to-one relations to `NopMetaTable`) with no mutual exclusion constraint — both can be set simultaneously, producing ambiguous join semantics.
- `NopMetaModelChangedEvent` has an indentation inconsistency in `orm.xml`.
- `NopMetaSemanticType` has no conventional indexes despite being referenced in join queries.
- `CrossDbJoinMerger.mergeRow()` computes `leftKeys` from the first left row's `keySet()` only. Heterogeneous SQL results cause right-side columns with matching names to not get prefixed, leading to silent column overwrite.
- `CrossDbJoinMerger.verifyCrossDbKeyTypeConsistency()` checks type consistency using only the first non-null key per side. Mixed types within a column set pass the check but may cause silent key mismatch.

## Goals

- All ORM model entities have appropriate indexes for their query patterns
- No contradictory FK + cascadeDelete combinations in ORM model
- Dual FK systems have mutual exclusion constraint documented or enforced
- CrossDbJoinMerger correctly handles heterogeneous column sets and case-insensitive column names
- Cross-DB join key type validation covers all rows, not just the first non-null

## Non-Goals

- **Not** changing BizModel API signatures or error handling (Plan 08)
- **Not** adding new test coverage (Plan 10)
- **Not** regenerating DDL or running migration scripts (model-first — generation at `mvn install`)
- **Not** restructuring CrossDbJoinMerger into separate processors

## Scope

### In Scope

- Add conventional indexes to `NopMetaBusinessDomain` (tree query — `parentId`, `displayName`)
- Resolve `NopMetaQualityCheckpoint.qualityScoreId` optional FK + cascadeDelete conflict
- Add mutual exclusion constraint/documentation for `NopMetaTableJoin` dual FK system
- Fix indentation in `NopMetaModelChangedEvent` in `orm.xml`
- Add conventional index to `NopMetaSemanticType`
- Fix `CrossDbJoinMerger.leftKeys` to aggregate from all left rows (not just first); use `TreeSet(String.CASE_INSENSITIVE_ORDER)`
- Fix `CrossDbJoinMerger.verifyCrossDbKeyTypeConsistency()` to check type uniformity across all rows
- Run code generation after ORM model changes

### Out Of Scope

- ErrorCode additions (Plan 08)
- Test coverage for CrossDbJoinMerger fixes (Plan 10)
- ORM findings already fixed by Plan 06 (ORM-01, ORM-02, ORM-04)

## Execution Plan

### Phase 1 — ORM model index and constraint fixes

Status: planned
Targets: `model/nop-metadata.orm.xml`

- Item Types: `Fix`

- [ ] Add indexes to `NopMetaBusinessDomain` (on `parentId` for tree traversal, `displayName` for listing)
- [ ] Resolve `NopMetaQualityCheckpoint.qualityScoreId` FK contradiction: either make FK non-optional + preserve cascadeDelete, or make FK optional + remove cascadeDelete. Document decision.
- [ ] Fix indentation in `NopMetaModelChangedEvent` entity definition
- [ ] Add mutual exclusion constraint/documentation to `NopMetaTableJoin` dual FK (entity-level `tableId` vs `entityTableId`)
- [ ] Add index to `NopMetaSemanticType` (on `name` or `displayName` for lookup)

Exit Criteria:

- [ ] `NopMetaBusinessDomain` has indexes on `parentId` and `displayName`
- [ ] `NopMetaQualityCheckpoint.qualityScoreId` FK + cascadeDelete semantics are consistent
- [ ] `NopMetaModelChangedEvent` indentation is consistent with surrounding entities
- [ ] `NopMetaTableJoin` dual FK has clear mutual exclusion constraint or documented rule
- [ ] `NopMetaSemanticType` has conventional index
- [ ] `./mvnw compile -pl nop-metadata/nop-metadata-dao -am` passes (codegen triggered)
- [ ] **Owner-doc update**: `docs-for-ai/02-core-guides/model-first-development.md` updated if index naming convention was clarified; otherwise `No owner-doc update required`
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 — CrossDbJoinMerger data correctness

Status: planned
Targets: `CrossDbJoinMerger.java` in `nop-metadata-service/.../query/`

- Item Types: `Fix`

- [ ] Fix `mergeRow()` leftKeys computation: aggregate `keySet()` from all left rows; use `TreeSet(String.CASE_INSENSITIVE_ORDER)` for case-insensitive column name matching
- [ ] Fix `verifyCrossDbKeyTypeConsistency()` to validate type consistency across all rows (not just first non-null). If full iteration is too expensive, document heuristic limitation explicitly in Javadoc.

Exit Criteria:

- [ ] `leftKeys` computed from all left rows (not just `leftRows.get(0).keySet()`) — verify via code review
- [ ] `TreeSet(String.CASE_INSENSITIVE_ORDER)` used for case-insensitive column collision resolution — verify via grep
- [ ] `verifyCrossDbKeyTypeConsistency()` either checks all rows or has explicit Javadoc documenting the single-row heuristic limitation
- [ ] `./mvnw compile -pl nop-metadata/nop-metadata-service -am` passes
- [ ] **No owner-doc update required** (internal fix, no public API change)
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [ ] All in-scope ORM model constraints and indexes are applied
- [ ] CrossDbJoinMerger handles heterogeneous column sets and case variations
- [ ] CrossDbJoinMerger key type check covers all rows or documents limitation
- [ ] `./mvnw compile -pl nop-metadata -am` passes
- [ ] `./mvnw test -pl nop-metadata/nop-metadata-service -am` passes
- [ ] Independent subagent closure audit completed and evidence recorded

## Deferred But Adjudicated

(none)

## Non-Blocking Follow-ups

- Regenerating production DDL is handled by `mvn install`; no separate action required

## Closure

Status Note: (to be filled at close)
Completed:

Closure Audit Evidence:

- Reviewer / Agent:
- Evidence:
