# Plan 58: nop-code P0 Bug Fixes

> Plan Status: **completed**
> Created: 2026-05-26
> Module: `nop-code`

## Purpose

Fix 5 critical (P0) bugs found during deep audit round 1. These bugs cause incorrect results, broken APIs, or resource leaks.

## Goals

1. Fix `CommunityDetector.findDominantPackage()` — `getByQualifiedName(symbolId)` should be `getById(symbolId)`; causes all community labels to fallback to "unknown"
2. Fix `searchBySymbolName()` — `FilterBeans.eq("kind", language)` should be `FilterBeans.eq("language", language)`; filters wrong column
3. Fix `getSymbolSourceCode()` / `showSymbolSource()` — always returns null; use stored file sourceCode to extract symbol-level source
4. Fix `ChangeAnalyzer.parseGitDiff()` — Process and BufferedReader not closed in finally block; resource leak
5. Fix `FlowDetector.DefaultSpringEntryPointPatternProvider.isEntryPoint()` — annotation FQNs checked against method qualifiedName which never matches; fix to use extData annotation info

## Non-Goals

- Adding new language features (Java records, TS call extraction) — P1/P2, separate plan
- daoProvider null guard refactor — P1, separate plan
- AnalysisCache TTL/size limit — P2
- Incremental analysis bug in ProjectAnalyzer — P1, separate plan

## Current Baseline

- Build: passes (`./mvnw clean install -pl nop-code -am -T 1C -DskipTests`)
- Tests: all pass (`./mvnw test -pl nop-code -am -T 1C`)
- Community labels always show "cluster_N" instead of meaningful package-based labels
- `searchCode` with `language` filter returns wrong results
- `showSymbol` API returns shell DTO with no source code
- `ChangeAnalyzer` leaks file descriptors on each `git diff` call
- Spring entry point detection via annotations never matches any symbols

## Exit Criteria

- [x] `CommunityDetector` community labels use package names (not "unknown"/"cluster_N") when symbols have qualified names
- [x] `searchBySymbolName` applies `language` filter to the `language` column, not `kind`
- [x] `getSymbolSourceCode` returns actual source code for symbols when file source is available in DB
- [x] `showSymbolSource` returns `SymbolSourceDTO` with populated `sourceCode` field
- [x] `ChangeAnalyzer.parseGitDiff` uses try-with-resources to close Process and BufferedReader
- [x] `FlowDetector` annotation-based entry point detection works correctly
- [x] All existing tests pass: `./mvnw test -pl nop-code -am -T 1C`
- [x] New tests added for each fix

## Closure Gates

- [x] All exit criteria verified by running tests
- [x] Code review: no new TODO/FIXME/stub introduced
- [x] Daily log updated

Closure Audit Evidence (retroactive):

- Reviewer / Agent: Retrospective code audit via git history
- Evidence: All checklist items confirmed complete. Plan status verified consistent with codebase state.

## Execution

### Slice 1: Fix CommunityDetector label bug
- [x] Change `getByQualifiedName(symbolId)` to `getById(symbolId)` at line 791
- [x] Add test verifying community labels use package names

### Slice 2: Fix searchBySymbolName language filter bug
- [x] Change `FilterBeans.eq("kind", language)` to `FilterBeans.eq("language", language)` at line 792
- [x] Add test for searchCode with language filter

### Slice 3: Fix symbol source code retrieval
- [x] Implement `getSymbolSourceCode()` to read from file sourceCode stored in DB
- [x] Update `showSymbolSource()` to use the new implementation
- [x] Add test verifying showSymbol returns actual source code

### Slice 4: Fix ChangeAnalyzer resource leak
- [x] Wrap Process/BufferedReader in try-with-resources
- [x] Verify existing tests still pass

### Slice 5: Fix FlowDetector Spring entry point matching
- [x] Fix `isEntryPoint()` to match annotation info from CodeSymbol.extData or signature
- [x] Add test verifying Spring-annotated methods are detected as entry points
