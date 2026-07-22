# 11 nop-metadata Lineage Performance Optimization and Dead Code Cleanup

> Plan Status: active
> Execution Order: 2
> Last Reviewed: 2026-07-22
> Source: `ai-dev/audits/2026-07-21-2039-open-audit-nop-metadata.md` (AR-25, AR-28)

## Purpose

Fix the N+1 upsert pattern in lineage edge extraction (P2 performance concern) and remove the empty `NopMetadataConstants` interface (P3 dead code).

## Current Baseline

- `NopMetaLineageEdgeQueryAction` has 3 upsert methods (`upsertSqlParseEdge`, `upsertColumnSqlParseEdge`, `upsertMeasureParseEdge`) that each issue 1 SELECT + 1 INSERT/UPDATE per candidate edge in a loop (lines 394-465). For a 50-column SQL view with 150 candidates, this produces 300 SQL statements in a single transaction.
- `NopMetadataConstants.java` is an empty interface with zero members and zero references. It is a leftover stub.

## Goals

- Reduce lineage extraction SQL statements from O(N) per candidate to O(1) batch operations
- Remove the dead `NopMetadataConstants` interface

## Non-Goals

- **Not** redesigning the lineage extraction algorithm or data model
- **Not** adding parallel extraction
- **Not** changing the entity schema or FK/indexes

## Scope

### In Scope

- Refactor 3 upsert methods in `NopMetaLineageEdgeQueryAction` to batch-query existing edges and batch-save new ones, using the same pattern as `loadExistingTableIds` (line 381-392)
- Remove `NopMetadataConstants.java` entirely
- Remove any imports referencing the deleted interface

### Out Of Scope

- Parallel extraction of lineage edges from multiple SQL views concurrently
- Adding extraction progress reporting or cancellation
- Changing the transactional boundary

## Execution Plan

### Phase 1 - N+1 upsert batch fix

Status: planned
Targets: `nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaLineageEdgeQueryAction.java`

- Item Types: `Fix`

- [ ] In each of the 3 upsert methods, replace per-candidate SELECT with a single batch query for all existing edges by the key 5-tuple `(sourceTableId, targetTableId, lineageSource)`
- [ ] Batch-save new edges (one INSERT with multiple rows, or batched saves within the same transaction)
- [ ] Ensure existing edges are updated in batch if possible, or at most 1 UPDATE per matched edge
- [ ] Verify `extractColumnLineageFromSql`, `extractLineageFromSql`, `extractMeasureLineage` callers are consistent with new batch pattern

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] Each upsert method issues at most 1 SELECT batch query (not 1 per candidate)
- [ ] New edges are batch-saved within the same transaction
- [ ] Old per-candidate SELECT/INSERT/UPDATE loop pattern is removed
- [ ] `./mvnw test -pl nop-metadata-service -am` passes
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - Remove empty constants interface

Status: planned
Targets: `nop-metadata-service/src/main/java/io/nop/metadata/service/NopMetadataConstants.java`

- Item Types: `Fix`

- [ ] Delete `NopMetadataConstants.java`
- [ ] Grep for any imports of `NopMetadataConstants` and remove them

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] `NopMetadataConstants.java` no longer exists
- [ ] No remaining imports reference `io.nop.metadata.service.NopMetadataConstants`
- [ ] `./mvnw compile -pl nop-metadata-service -am` passes
- [ ] `./mvnw test -pl nop-metadata-service -am` passes
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [ ] Lineage edge extraction no longer produces N+1 SELECT/INSERT per candidate
- [ ] Empty `NopMetadataConstants` interface removed with no remaining references
- [ ] `./mvnw compile -pl nop-metadata-service -am`
- [ ] `./mvnw test -pl nop-metadata-service -am`

## Deferred But Adjudicated

(none)

## Non-Blocking Follow-ups

- Consider adding a focused test that verifies batch behavior (e.g., extract lineage for a view with multiple candidates and assert exactly 1 batch SELECT was issued)

## Closure

Status Note: (to be filled on closure)
Completed: (to be filled on closure)

Closure Audit Evidence:

- Reviewer / Agent: (to be filled on closure)
- Evidence: (to be filled on closure)

Follow-up:

- (to be filled on closure)
