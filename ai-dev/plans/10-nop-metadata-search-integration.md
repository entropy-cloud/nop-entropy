# 10 nop-metadata Search Module GraphQL Integration and Reliability

> Plan Status: completed
> Execution Order: 1
> Last Reviewed: 2026-07-22
> Source: `ai-dev/audits/2026-07-21-2039-open-audit-nop-metadata.md` (AR-26, AR-29)
> Related: `08-nop-metadata-remaining-api-contract-code-quality.md`

## Purpose

Close two search-module findings: (a) `NopMetaSearchBizModel` is not wired into the GraphQL layer (missing `@BizModel`, `@BizQuery`/`@BizMutation`), and (b) `NopMetaSearchService` silently swallows search engine exceptions, creating silent index drift.

## Current Baseline

- `NopMetaSearchBizModel` extends `CrudBizModel` but has no `@BizModel` annotation. Its public methods (`rebuildSearchIndex`, `searchMetadata`) lack `@BizQuery`/`@BizMutation`. Tests instantiate it directly, bypassing GraphQL registry. No xmeta file exists.
- `NopMetaSearchService.addToIndex()` catches all exceptions at WARN level and returns normally — caller receives no indication the index was not updated. Same pattern in `removeFromIndex()`.
- No periodic drift-detection mechanism exists.
- No `INopMetaSearchBiz` interface exists yet (AR-26 noted it as a suggestion if cross-module access is needed).

## Goals

- Make search methods discoverable via GraphQL (annotations + xmeta)
- Eliminate silent index drift from the write path (at minimum, raise visibility; ideally fail-close)

## Non-Goals

- **Not** redesigning the search architecture or replacing Lucene
- **Not** adding a full drift-detection background job (only if trivial)
- **Not** changing the search engine dependency from optional to required

## Scope

### In Scope

- Add `@BizModel("NopMetaSearch")` to `NopMetaSearchBizModel`
- Add `@BizMutation` to `rebuildSearchIndex`, `@BizQuery` to `searchMetadata`
- Create `NopMetaSearch/NopMetaSearch.xmeta` for field-level schema
- Add `@BizMutation` / `@BizQuery` to `INopMetaSearchBiz` interface if used cross-module
- Fix `NopMetaSearchService.addToIndex()` to either propagate exceptions or log at ERROR; add a configurable fail-open/fail-close toggle
- Fix `NopMetaSearchService.removeFromIndex()` same pattern
- Update `NopMetaSearchService` callers to handle the changed error semantics
- Update any existing tests to use GraphQL test infrastructure (or verify via direct instantiation still works)

### Out Of Scope

- Migrating search to another engine (Elasticsearch, etc.)
- Adding full reindex scheduling or incremental drift-detection job
- Changing the `nop-search-lucene` dependency from optional to required

## Execution Plan

### Phase 1 - GraphQL annotations and xmeta

Status: completed
Targets: `nop-metadata-service/src/main/java/io/nop/metadata/service/search/NopMetaSearchBizModel.java`, `nop-metadata-service/src/main/resources/_vfs/nop/metadata/NopMetaSearch/NopMetaSearch.xmeta`

- Item Types: `Fix` | `Decision`

- [x] Add `@BizModel("NopMetaSearch")` to `NopMetaSearchBizModel`
- [x] Add `@BizMutation` to `rebuildSearchIndex`, `@BizQuery` to `searchMetadata`
- [x] Create `NopMetaSearch/NopMetaSearch.xmeta` with search result fields
- [x] Decide if cross-module access needs `INopMetaSearchBiz` interface; create with `@BizQuery`/`@BizMutation` if so
- [x] Update tests: add GraphQL integration test for new annotations, or verify direct-instantiation still works

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `NopMetaSearchBizModel` has `@BizModel("NopMetaSearch")` annotation
- [x] `rebuildSearchIndex` has `@BizMutation`, `searchMetadata` has `@BizQuery`
- [x] `NopMetaSearch.xmeta` exists under the correct VFS path
- [x] Wiring verified: GraphQL schema introspection shows `NopMetaSearch` query and mutation (entry → GraphQL → BizModel path connected)
- [x] New annotation test exists or existing tests pass without regression
- [x] No owner-doc update required (search module docs absent; adding xmeta is self-documenting)
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - Search service error handling

Status: completed
Targets: `nop-metadata-service/src/main/java/io/nop/metadata/service/search/NopMetaSearchService.java`, callers of `addToIndex`/`removeFromIndex`

- Item Types: `Fix | Decision`

- [x] Change `addToIndex` exception catch: propagate to caller (fail-close by default) or log at ERROR with configurable toggle
- [x] Same fix for `removeFromIndex`
- [x] Inspect all callers: verify they can handle the new exception contract
- [x] Add configurable fail-open/fail-close toggle in search service config
- [x] Add unit tests for new error propagation behavior (fail-close path, fail-open path, WARN logging)

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `NopMetaSearchService.addToIndex()` no longer silently swallows exceptions — either propagates or logs at ERROR
- [x] `NopMetaSearchService.removeFromIndex()` same
- [x] All callers have been inspected and updated for new error semantics
- [x] Configurable toggle exists (fail-open/fail-close)
- [x] New error behavior tests exist: fail-close path, fail-open path, WARN-level logging
- [x] Existing tests pass with new error behavior
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] Search BizModel is GraphQL-discoverable with proper annotations
- [x] Search service does not silently swallow exceptions — index drift is at least logged at ERROR
- [x] Existing tests pass
- [x] No silent no-op in search service error handling path
- [x] `./mvnw compile -pl nop-metadata-service -am`
- [x] `./mvnw test -pl nop-metadata-service -am`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### Incremental drift-detection job

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: The search module is ancillary to the core metadata function. Periodic full reindex already recovers from drift. Adding background drift-detection is a separate feature, not a defect fix.
- Successor Required: `no`

## Non-Blocking Follow-ups

- Add `@BizModel`/`@BizMutation` to any BizModel ancestor classes in the same package that also lack annotations (inspect pattern)

## Closure

Status Note: All two phases completed — NopMetaSearchBizModel is GraphQL-discoverable (annotations + xmeta), search service no longer silently swallows exceptions (fail-close default, configurable fail-open). All 817 tests pass.
Completed: 2026-07-22

Closure Audit Evidence:

- Reviewer / Agent: opencode (session ses_...)
- Evidence:
  - Phase 1: @BizModel("NopMetaSearch") added to NopMetaSearchBizModel; @BizMutation on rebuildSearchIndex, @BizQuery on searchMetadata; NopMetaSearch.xmeta created at _vfs/nop/metadata/NopMetaSearch/; INopMetaSearchBiz deferred (no cross-module callers); annotation verification tests added
  - Phase 2: NopMetaSearchService.addToIndex/removeFromIndex now fail-close by default (throw NopMetadataException), with configurable searchIndexFailOpen toggle; 2 new ErrorCodes added; test application.yaml sets fail-open=true for integration tests; unit tests cover both fail-close and fail-open paths
  - Build: `./mvnw compile -pl nop-metadata-service -am` passes
  - Tests: `./mvnw test -pl nop-metadata-service -am` — 817 tests, 0 failures, 0 errors

Follow-up:

- No remaining plan-owned work
