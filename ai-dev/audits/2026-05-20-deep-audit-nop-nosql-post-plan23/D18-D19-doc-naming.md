---

# Audit Results: D18 (Doc-Code Consistency) + D19 (Naming & Terminology) for nop-nosql

## D18: Doc-Code Consistency

### §4 Business Pattern Interfaces — Zero findings

All 6 business pattern interfaces match the design doc exactly:

| Interface | Design Methods | Code Status |
|---|---|---|
| INosqlQueue | enqueue/enqueueBatch/dequeue/dequeueBatch/peek/size/clear | ✅ All 7 present, sync+async |
| INosqlLock | tryLock/unlock/isHeld | ✅ All 3 present. isHeld = sync-only (intentional: local state) |
| INosqlRateLimiter | tryAcquire/getAvailableTokens + RateLimiterConfig | ✅ All present. RateLimiterConfig(rate, capacity) confirmed |
| INosqlRanking | add/incrementScore/getRank/getScore/getTopN/getAround/size/remove | ✅ All 8 present |
| INosqlCounter | increment/get/getAndIncrement/reset/getAndReset | ✅ All 5 present |
| INosqlSessionStore | get/getField/set/setField/touch/remove/exists | ✅ All 7 present |

### §5 INosqlService Factory Methods — Zero findings

All 11 factory methods in the design match actual `INosqlService.java` signatures exactly: `queue(key)`, `lock(key)`, `rateLimiter(key, config)`, `ranking(key)`, `counter(key)`, `sessionStore(prefix)`, `hashOps(key)`, `listOps(key)`, `setOps(key)`, `zSetOps(key)`, `getMessageService()`.

### §6 Implementation Status — Zero findings

- 6 Lettuce business-pattern implementations exist (LettuceLock/Counter/Queue/RateLimiter/Ranking/SessionStore) ✅
- 4 Lettuce primitive implementations exist (LettuceHash/List/Set/ZSetOperations) ✅
- 5 Lua scripts registered in RedisScripts ✅
- Known debts verified: `forEachEntry` = empty body (line 137), `forEachEntryAsync` = returns null (line 211), `getMessageService()` = returns null (line 294-295) ✅
- No beans.xml files in either module ✅
- `LettuceNosqlService` is indeed an empty placeholder class ✅

### README.md Maturity Section — Zero findings

All claims in README's maturity section verified accurate against code.

---

## D19: Naming & Terminology Consistency

### Finding D19-F1 — INosqlListOperations is async-only, breaking the sync+async convention

**Severity**: Low (code-level inconsistency, no doc mismatch since §6 marks it ✅ without qualification)

**What**: `INosqlListOperations` provides **only** async methods (10 methods, all return `CompletableFuture`). Every other interface in the module provides both sync and async variants:
- Business patterns (Queue, Lock, RateLimiter, Ranking, Counter, SessionStore): all have sync+async ✅
- Primitive interfaces: INosqlSetOperations sync+async ✅, INosqlZSetOperations sync+async ✅, INosqlHashOperations inherits sync+async ✅
- Design §3.4 states the principle: "同时提供同步方法（`get`/`put`）和异步方法（`getAsync`/`putAsync`）"

**Where**: `nop-nosql-core/.../INosqlListOperations.java` — 10 methods, zero sync counterparts.

**Risk**: Users who follow the design's stated convention and call sync methods on primitive interfaces will be surprised that ListOps is async-only. Also inconsistent with §3.4's statement.

**Recommendation**: Either (a) add sync wrapper methods to INosqlListOperations matching the pattern of the other interfaces, or (b) document this as a deliberate exception in the design (§3.4 and §6).

---

### Finding D19-F2 — Primitive layer uses 3 different names for "element count"

**Severity**: Low (naming inconsistency within primitive layer)

**What**: The three collection-type primitive interfaces use different method names for the same semantic operation ("how many elements"):

| Interface | Method Name | Maps to Redis |
|---|---|---|
| INosqlListOperations | `getSizeAsync()` | LLEN |
| INosqlSetOperations | `sizeAsync()` | SCARD |
| INosqlZSetOperations | `cardAsync()` | ZCARD |

By contrast, the business pattern layer is consistent: INosqlQueue uses `size()`, INosqlRanking uses `size()`.

**Where**: Three files in `nop-nosql-core/`.

**Risk**: Minor cognitive friction for users switching between primitive types. The business layer is clean; this only affects infrastructure-level code.

**Recommendation**: Standardize to `sizeAsync()`/`size()` across all three (it's the most neutral name). The Redis command name is an implementation detail that shouldn't leak into the interface.

---

### Items Checked with Zero Findings

- **Terminology**: "Queue"/"Ranking"/"Counter"/"SessionStore" used consistently — no "Leaderboard", "AtomicCounter", or "SessionManager" anywhere ✅
- **Async suffix**: All async methods consistently use `Async` suffix across all interfaces ✅
- **isHeld() no async**: Intentional (local state check per design §4.2) ✅
- **Data class names**: RateLimiterConfig, RankingEntry, RateLimitResult, ZSetEntry — all match design ✅
- **Factory parameter names**: `key` for single-entity patterns, `prefix` for sessionStore (semantically correct), `sessionId`/`member` in method params — all consistent ✅
- **enqueue/dequeue vs add/remove**: Queue uses domain-specific enqueue/dequeue; primitives use generic add/remove — appropriate layering ✅

---

**Summary**: 2 low-severity findings total. D18 doc-code consistency is excellent — all method signatures, factory methods, implementation claims, and maturity statements match the actual code precisely. D19 surfaces two naming inconsistencies within the primitive layer (async-only ListOperations and inconsistent size/card naming), both low severity. The business pattern layer and its documentation are fully consistent.