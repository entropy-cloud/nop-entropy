# Deep Audit Summary: nop-nosql (Post Plan 23)

**Date**: 2026-05-20
**Scope**: nop-persistence/nop-nosql/ (core + lettuce)
**Trigger**: Post-implementation review of Plan 23 (business pattern layer)
**Dimensions audited**: D01, D02, D03, D09, D14, D15, D16, D17, D18, D19

## Executive Summary

The nop-nosql module is in **good structural shape** overall — clean module boundaries, zero driver leakage in core, excellent doc-code consistency, and well-separated business pattern implementations. The module correctly implements all 6 business patterns (Lock, Counter, Queue, RateLimiter, Ranking, SessionStore) with 4 primitive operation interfaces, matching the design document precisely.

However, the audit surfaces **3 systemic issues** that warrant immediate attention:

1. **EVALSHA without EVAL fallback** (D14-03): All Lua-based operations fail after Redis restart until app restart — a production reliability risk.
2. **68 raw `.join()` calls** (D09-01, D14-02): All sync wrapper methods propagate `CompletionException` instead of unwrapping to real causes, violating Nop platform error conventions.
3. **Silent no-op / null returns** (D09-02, D09-03, D15-09, D02-03): `forEachEntry`, `forEachEntryAsync`, `getMessageService` silently do nothing or return null.

**Finding count by severity**: 0 P0, 5 P1, 15 P2, 18 P3 — total **38 findings** across 10 dimensions.

## Findings by Severity

### P0 (Must Fix)

None.

### P1 (Should Fix)

| ID | Dimension | Title | Cross-ref |
|----|-----------|-------|-----------|
| D09-01 | Error Handling | All 68 sync wrapper `.join()` calls hide real Redis errors behind `CompletionException` | D14-02, D14-07 |
| D14-03 | Async Patterns | `EVALSHA`-only without `EVAL` fallback — fails after Redis restart | - |
| D15-01 | Type Safety | `INosqlListOperations` lacks sync methods — inconsistent with all sibling interfaces | D19-F1 |
| D15-09 | Type Safety | `forEachEntryAsync()` returns `null` CompletionStage — latent NPE on chaining | D09-02, D02-03 |
| D01-01 | Dependencies | `nop-dao` dependency entirely unused in `nop-nosql-core` — unnecessary transitive coupling | - |

### P2 (Nice to Fix)

| ID | Dimension | Title |
|----|-----------|-------|
| D01-02 | Dependencies | `INosqlListOperations` exposes Redis command names (leftPop/rightPop) |
| D02-01 | Responsibility | `async()`/`sync()` boilerplate duplicated across 11 classes |
| D02-03 | Responsibility | `forEachEntry` silent no-op, `forEachEntryAsync` returns null, `getMessageService` returns null |
| D03-01 | API Contracts | Ranking vs ZSetOperations inconsistent null handling for score/rank on same data |
| D03-02 | API Contracts | `INosqlSessionStore.set()` non-atomic HMSET+PEXPIRE undocumented in interface |
| D03-03 | API Contracts | `INosqlHashOperations` expiry methods apply TTL to entire hash key, not individual fields |
| D03-04 | API Contracts | `CompletionStage` vs `CompletableFuture` inconsistency across interfaces |
| D03-05 | API Contracts | Edge-case return values undocumented across all interfaces |
| D14-01 | Async | `LettuceLock` volatile + async callback threading — documentation gap |
| D14-04 | Async | `LettuceCounter.toLong()` throws `NumberFormatException` on non-numeric stored values |
| D14-06 | Async | `LettuceRedisConnectionProvider` missing lifecycle guard after `stop()` |
| D15-02 | Type Safety | Raw type cast `(Map)` in `LettuceHashOperations.putAllAsync` |
| D15-03 | Type Safety | Raw type cast `(Map)` in `LettuceMessageService.putAllAsync` |
| D15-05 | Type Safety | `Object[]` cast from Lua eval result in `LettuceRateLimiter` without validation |
| D15-06 | Type Safety | Mixed `CompletableFuture` vs `CompletionStage` return types |
| D15-07 | Type Safety | `NosqlCache.stats()` returns null without `@Nullable` |
| D15-08 | Type Safety | `LettuceMessageService.getMessageService()` returns null without `@Nullable` |
| D16-03 | Test Quality | Missing edge case tests across all 6 business patterns |
| D17-01 | Code Style | Import order violation in `NosqlCache` |
| D17-02 | Code Style | Unused `sync()` method + `RedisAdvancedClusterCommands` import in 6 classes |

### P3 (Informational)

| ID | Dimension | Title |
|----|-----------|-------|
| D01-03 | Dependencies | `INosqlZSetOperations` uses Redis-specific terminology (card, revRank) |
| D02-02 | Responsibility | `sync()` declared but never used in `LettuceCounter` and `LettuceQueue` |
| D03-06 | API Contracts | `INosqlLock.isHeld()` checks local state only, not Redis state |
| D03-07 | API Contracts | `INosqlService.getMessageService()` is dead API (returns null) |
| D09-02 | Error Handling | `forEachEntry()` silently does nothing, `forEachEntryAsync()` returns null |
| D09-03 | Error Handling | `getMessageService()` returns null — silent failure |
| D14-02 | Async | `.join()` wraps errors in `CompletionException` (subsumed by D09-01) |
| D14-05 | Async | `LettuceRateLimiter` config consistency not documented in Javadoc |
| D14-07 | Async | `LettuceMessageService` sync methods delegate to async+join (subsumed by D14-02) |
| D15-04 | Type Safety | Unnecessary redundant cast in `LettuceSessionStore.getAsync` |
| D17-03 | Code Style | Empty class `LettuceNosqlService` |
| D17-04 | Code Style | Empty interface `NosqlConstants` |
| D17-05 | Code Style | AI-style inline comments in `LettuceSessionStore` restating Redis command names |
| D17-06 | Code Style | AI-style comment in `LettuceRanking` |
| D17-07 | Code Style | AI-style block comment in `LettuceRateLimiter` (borderline useful) |
| D17-08 | Code Style | `protected` methods that could be `private` (acceptable for extensibility) |
| D19-F1 | Naming | `INosqlListOperations` is async-only, breaking sync+async convention |
| D19-F2 | Naming | Primitive layer uses 3 different names for "element count" (getSize/size/card) |

## Cross-Dimension Themes

### Theme 1: `.join()` / CompletionException Unwrapping (D09-01 + D14-02 + D14-07)

The most pervasive issue, touching all 11 implementation files. Every sync wrapper calls `.toCompletableFuture().join()` which wraps real Redis errors in `CompletionException`. The platform provides `FutureHelper.syncGet()` precisely for this purpose, and `LettuceMessageService` already imports it but doesn't use it for sync calls.

**Impact**: All consumers of the module. Error diagnosis in production is significantly harder.
**Fix**: Single mechanical find-and-replace across 68 call sites. Change `.toCompletableFuture().join()` → `FutureHelper.syncGet(...)`.

### Theme 2: Silent No-Op / Null Returns (D09-02 + D09-03 + D02-03 + D15-07 + D15-08 + D15-09 + D03-07)

Multiple methods silently fail or return null without any error signal:
- `forEachEntry()` — empty body, no iteration
- `forEachEntryAsync()` — returns `null` CompletionStage (NPE on chaining)
- `getMessageService()` — returns `null`
- `NosqlCache.stats()` — returns `null`

**Root cause**: Interface methods inherited from parent types (`IAsyncMap`, `INosqlService`, `ICache`) that were not applicable to the Redis implementation but weren't given explicit unsupported/error handling.

**Fix**: Either implement properly, throw `UnsupportedOperationException`, or annotate with `@Nullable` + document.

### Theme 3: INosqlListOperations Incompleteness (D15-01 + D19-F1 + D01-02)

`INosqlListOperations` is the only primitive interface missing sync methods, breaking the convention established by all other interfaces. It also uses Redis-specific naming (leftPop/rightPop) rather than direction-neutral names.

**Root cause**: Likely added later without following the sync+async convention of sibling interfaces.
**Fix**: Add sync wrapper methods to match the pattern. Optionally rename to direction-neutral terms.

### Theme 4: EVALSHA Reliability (D14-03)

`LettuceExecutor.evalScript()` uses only `EVALSHA` without `EVAL` fallback. After Redis restart or `SCRIPT FLUSH`, all 5 Lua-script-based operations (lock unlock, rate limiter, putIfAbsent, removeIfMatch, message CAS) will fail with `RedisNoScriptException`.

**Impact**: Production reliability — requires app restart to recover.
**Fix**: Add EVAL fallback on `RedisNoScriptException`, or pre-load scripts on connection start.

### Theme 5: Hash Expiry Semantic Mismatch (D03-03)

`INosqlHashOperations` inherits `putExAsync`, `getExAsync`, `setTimeoutAsync` from `INosqlKeyValueOperations`. These methods set TTL on the entire hash key, not on individual fields — violating the field-level semantics that the parameter names suggest. Redis hash fields do not support individual TTLs.

**Impact**: Callers expecting field-level expiry get key-level expiry — potential data loss for co-located hash fields.
**Fix**: Document the behavior, or override these methods to throw `UnsupportedOperationException` with a clear message.

### Theme 6: Duplicated Boilerplate (D02-01 + D17-02)

11 implementation classes each declare identical `async()` (and 7 declare `sync()`) methods. 6 classes declare `sync()` but never use it. A common abstract base class would eliminate this duplication.

## Recommended Action Plan

### Batch 1: Critical Production Fixes (1-2 hours)

1. **D14-03**: Add `EVAL` fallback in `LettuceExecutor.evalScript()` for `NOSCRIPT` errors
2. **D09-01**: Replace all 68 `.toCompletableFuture().join()` calls with `FutureHelper.syncGet()`
3. **D15-09 / D09-02**: Fix `forEachEntryAsync()` — return `CompletableFuture.completedFuture(null)` or throw `UnsupportedOperationException`; fix `forEachEntry()` to delegate properly

### Batch 2: API Contract & Safety (2-3 hours)

4. **D01-01**: Remove `nop-dao` dependency from `nop-nosql-core/pom.xml`; verify with `mvn compile`
5. **D15-01**: Add sync methods to `INosqlListOperations` and `LettuceListOperations`
6. **D03-02**: Add Javadoc to `INosqlSessionStore.set()` documenting non-atomicity of HMSET+PEXPIRE
7. **D03-03**: Add Javadoc or override to clarify that hash expiry applies to the whole key
8. **D03-05**: Add Javadoc to all interfaces documenting edge-case return values (null, -1, NaN, 0)
9. **D14-04**: Add defensive `NumberFormatException` handling in `LettuceCounter.toLong()`
10. **D14-06**: Add `isStarted()` guard in `LettuceRedisConnectionProvider.getConnection()`

### Batch 3: Code Quality (2-3 hours)

11. **D02-01**: Extract `AbstractLettuceOperations` base class with `async()`/`sync()` methods
12. **D17-02 / D02-02**: Remove unused `sync()` methods and imports from classes that don't use them
13. **D17-01**: Fix import order in `NosqlCache`
14. **D03-01**: Document the difference in null handling between `INosqlRanking` and `INosqlZSetOperations`
15. **D15-07 / D15-08 / D09-03**: Add `@Nullable` annotations or return empty/default values for `stats()`, `getMessageService()`

### Batch 4: Test Coverage (2-3 hours)

16. **D16-03**: Add edge case tests prioritized as: Counter get on missing key, Queue dequeueBatch fewer items, Ranking getRank/getScore for missing member, RateLimiter token refill, Lock double-unlock, SessionStore get for missing session

### Batch 5: Cosmetic (1 hour)

17. Remove AI-style comments from `LettuceSessionStore`, `LettuceRanking`, `LettuceRateLimiter` (D17-05/06/07)
18. Remove empty `LettuceNosqlService` class and `NosqlConstants` interface (D17-03/04)
19. Standardize "element count" naming across primitive interfaces (D19-F2)

## Dimension Files

- [D01: Dependency Graph](D01-dependency-graph.md) — 3 findings (1 P1, 1 P2, 1 P3)
- [D02+D03: Module Responsibility & API](D02-D03-module-api.md) — 10 findings (2 HIGH, 5 MEDIUM, 3 LOW)
- [D09: Error Handling](D09-error-handling.md) — 3 findings (1 P1, 1 P2, 1 P3)
- [D14+D16: Async & Test Quality](D14-D16-async-test-quality.md) — 7 findings (1 P1, 4 P2, 2 P3)
- [D15+D17: Type Safety & Code Style](D15-D17-type-safety-code-style.md) — 15 findings (2 P1, 7 P2, 6 P3)
- [D18+D19: Doc & Naming](D18-D19-doc-naming.md) — 2 findings (both LOW)
