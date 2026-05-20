Confirmed. Now I have all the data. Here's the complete audit report:

---

# D02+D03 Audit: nop-nosql Module Responsibility & API Contracts

## D02: Module Responsibility & File Boundaries

**Files checked:** 15 implementation classes + 12 interfaces + 3 helper/utility classes across `nop-nosql-core` and `nop-nosql-lettuce`.

### Finding D02-01: `async()`/`sync()` boilerplate duplicated across 11 classes [MEDIUM]

**What:** Every `Lettuce*` implementation class (11 total) declares an identical `async()` method:
```java
protected RedisAdvancedClusterAsyncCommands<String, Object> async() {
    return client.getConnection().async();
}
```
And 7 of them also declare an identical `sync()` method. Both methods are copy-pasted across LettuceMessageService, LettuceHashOperations, LettuceSetOperations, LettuceZSetOperations, LettuceListOperations, LettuceQueue, LettuceRanking, LettuceCounter, LettuceLock, LettuceRateLimiter, LettuceSessionStore.

**Why it matters:** If the connection acquisition pattern ever changes (e.g., adding tracing, metrics, error handling), all 11 files need identical edits. A common abstract base class (e.g., `AbstractLettuceOperations`) holding the `client` field and these two accessors would eliminate this duplication.

**Where:** All files in `nop-nosql-lettuce/src/main/java/io/nop/nosql/lettuce/impl/`

**Severity:** MEDIUM — code smell, maintenance burden

---

### Finding D02-02: `sync()` declared but never used in LettuceCounter and LettuceQueue [LOW]

**What:** `LettuceCounter` (lines 29-31) and `LettuceQueue` (lines 33-35) declare `sync()` but all their operations go through `async()` exclusively. `LettuceSetOperations`, `LettuceZSetOperations`, and `LettuceListOperations` already don't declare `sync()` — they're clean.

**Why it matters:** Dead code. If a base class is extracted (D02-01), the `sync()` method should only exist where it's actually used (LettuceMessageService, LettuceHashOperations, LettuceSessionStore, LettuceRanking, LettuceLock, LettuceRateLimiter).

**Where:** `LettuceCounter.java:29-31`, `LettuceQueue.java:33-35`

**Severity:** LOW

---

### Finding D02-03: LettuceMessageService silently no-ops `forEachEntry()` and returns null from `forEachEntryAsync()` [MEDIUM]

**What:** 
- `forEachEntry()` (lines 137-139) has an empty body — the consumer is never invoked.
- `forEachEntryAsync()` (lines 211-213) returns `null` instead of a completed future.
- `getMessageService()` (lines 294-296) returns `null`.

These are inherited from `IAsyncMap` and `INosqlService` respectively but provide no working implementation and no error signal.

**Why it matters:** Callers using `forEachEntry()` get silent data loss. Callers using `forEachEntryAsync()` may get NPE on the returned null. `getMessageService()` callers get a null they can't distinguish from "not configured."

**Where:** `LettuceMessageService.java:137-139, 211-213, 294-296`

**Severity:** MEDIUM — silent failure, potential NPE

---

### D02 Non-findings (verified clean)

- **No files over 500 lines.** Largest implementation is LettuceMessageService at 327 lines, largest test at 477 lines. All reasonable.
- **LettuceHashOperations (259 lines) is justified.** It implements ~25 methods from the `IAsyncMap → INosqlKeyValueOperations → INosqlHashOperations` chain. Each method is a 1-3 line thin wrapper. The file is long because the interface surface is wide, not because it does too many things.
- **LettuceMessageService (327 lines) dual role is acceptable.** It serves as both (a) global KV operations facade and (b) factory for per-key operation objects. Both responsibilities are naturally part of being the Redis service entry point. The factory methods (lines 273-326) are trivial one-liners.
- **Business pattern classes are well-separated.** Queue (110), Lock (76), RateLimiter (95), Ranking (159), Counter (90), SessionStore (143) — each class handles exactly one pattern with a single responsibility.

---

## D03: API Surface & Contract Consistency

### Finding D03-01: Ranking vs ZSetOperations — inconsistent null handling for identical underlying data [HIGH]

**What:** Both `INosqlRanking` and `INosqlZSetOperations` query Redis sorted sets (ZSET), but handle "member not found" differently:

| Operation | Interface | Return Type | Missing Member |
|-----------|-----------|-------------|----------------|
| `getScore()` | INosqlRanking | `double` (primitive) | `Double.NaN` |
| `score()` | INosqlZSetOperations | `Double` (boxed) | `null` |
| `getRank()` | INosqlRanking | `long` (primitive) | `-1L` |
| `rank()` | INosqlZSetOperations | `Long` (boxed) | `null` |

This means calling `ranking.getScore("x")` on a non-existent member returns `NaN`, while `zSetOps.score("x")` returns `null`. Both are wrappers around ZSCORE, but one forces a default and the other doesn't.

**Why it matters:** Callers using `INosqlRanking` can't distinguish "member doesn't exist" from "score is NaN" (though NaN scores are unlikely). Callers using `INosqlZSetOperations` must null-check. The different conventions create confusion for users who expect consistent behavior for the same underlying data structure.

**Where:** `INosqlRanking.java:24-28`, `INosqlZSetOperations.java:27-33`, `LettuceRanking.java:61-80`, `LettuceZSetOperations.java:70-97`

**Severity:** HIGH — API contract inconsistency for identical operations

---

### Finding D03-02: `INosqlSessionStore.set()` non-atomicity is undocumented in the interface [HIGH]

**What:** `INosqlSessionStore.set(sessionId, data, ttlMs)` executes as two separate Redis commands: `HMSET` followed by `PEXPIRE`. The implementation has a code comment noting this (`// set: HMSET + PEXPIRE (non-atomic, noted in design doc)` at line 70), but the **interface** has zero Javadoc mentioning this.

**Why it matters:** If the process crashes after HMSET but before PEXPIRE, the session data is stored but has no TTL — it persists indefinitely. This is a correctness risk that callers cannot mitigate without reading the implementation source. The interface should document the non-atomic nature and suggest using Lua scripts if atomicity is required.

**Where:** `INosqlSessionStore.java:23-24`, `LettuceSessionStore.java:70-83`

**Severity:** HIGH — undocumented non-atomicity risk

---

### Finding D03-03: `INosqlHashOperations` inherits expiry methods with wrong field-level semantics [MEDIUM]

**What:** `INosqlHashOperations` extends `INosqlKeyValueOperations`, which exposes methods like `putExAsync(field, value, timeout)`, `getExAsync(field, timeout)`, `getTimeoutAsync(field)`, `setTimeoutAsync(field, timeout)`. The parameter name in `INosqlKeyValueOperations` is "key", but in the hash context it's interpreted as "field". 

The problem: Redis hash fields don't support individual TTLs. The `LettuceHashOperations` implementation silently applies the TTL to the **entire hash key**, not the individual field:
```java
// Line 194-197: Sets TTL on the entire key, not just the field
public CompletionStage<Void> putExAsync(String field, Object value, long timeout) {
    return async().hset(key, field, value).thenCompose(v ->
            async().pexpire(key, timeout).thenApply(Functionals.toVoid()));
}
```

**Why it matters:** A caller calling `hashOps.putEx("fieldA", value, 5000)` expects fieldA to expire in 5 seconds, but actually the entire hash key (with ALL its fields) gets a 5-second TTL. This is a semantic contract violation that can cause data loss across multiple hash fields.

**Where:** `INosqlHashOperations.java` (extends `INosqlKeyValueOperations`), `LettuceHashOperations.java:194-253`

**Severity:** MEDIUM — misleading API semantics (could be HIGH if widely used)

---

### Finding D03-04: `CompletionStage` vs `CompletableFuture` inconsistency across interfaces [MEDIUM]

**What:** The async return types are inconsistent across the interface hierarchy:

- `IAsyncMap` / `INosqlKeyValueOperations` / `INosqlHashOperations` → `CompletionStage<T>`
- `INosqlListOperations`, `INosqlSetOperations`, `INosqlZSetOperations`, `INosqlQueue`, `INosqlLock`, `INosqlCounter`, `INosqlRanking`, `INosqlRateLimiter`, `INosqlSessionStore` → `CompletableFuture<T>`

**Why it matters:** `CompletionStage` is the more abstract type (cannot be directly joined). `CompletableFuture` is more concrete (can be joined, cancelled). The split creates an inconsistent mental model — callers of `hashOps.getAsync()` get a `CompletionStage` (cannot join directly) while callers of `setOps.addAsync()` get a `CompletableFuture` (can join). This also forces implementation classes to add `.toCompletableFuture()` calls when not needed.

**Where:** All `INosql*` interfaces in `nop-nosql-core`

**Severity:** MEDIUM — API style inconsistency

---

### Finding D03-05: Edge-case return values are undocumented across all interfaces [MEDIUM]

**What:** None of the interfaces have Javadoc documenting edge-case behavior:

| Interface | Method | Missing/Empty Behavior | Documented? |
|-----------|--------|------------------------|-------------|
| INosqlCounter | `get()` | Returns `0` for non-existent key | No |
| INosqlRanking | `getRank()` | Returns `-1` for non-existent member | No |
| INosqlRanking | `getScore()` | Returns `Double.NaN` for non-existent member | No |
| INosqlQueue | `dequeue()` | Returns `null` on empty queue | No |
| INosqlQueue | `peek()` | Returns `null` on empty queue | No |
| INosqlSetOperations | `pop()` | Returns `null` on empty set | No |
| INosqlSetOperations | `randomMember()` | Returns `null` on empty set | No |
| INosqlLock | `isHeld()` | Returns true based on local state, not Redis state | No |
| INosqlRateLimiter | `getAvailableTokens()` | Returns capacity for non-existent key | No |

**Why it matters:** Callers cannot distinguish "value is 0" from "key doesn't exist" for counters, or "queue is empty" from "dequeued null value" without reading implementation code. The `-1` sentinel for ranking rank and `NaN` for missing scores are conventions that should be in the API contract.

**Where:** All `INosql*` interfaces in `nop-nosql-core`

**Severity:** MEDIUM — incomplete API contracts

---

### Finding D03-06: `INosqlLock.isHeld()` checks local state only, not Redis state [LOW]

**What:** `LettuceLock.isHeld()` returns `lockValue != null` — it checks a local `volatile` field, not whether the lock key still exists in Redis. After the lease expires:
1. The lock is gone from Redis (another thread could acquire it)
2. `isHeld()` still returns `true` locally
3. `unlock()` will try REMOVE_IF_MATCH with the old UUID, which fails silently (doesn't remove the new owner's lock), but still clears `lockValue`

**Why it matters:** This is a known limitation of simple Redis distributed locks (no watchdog/heartbeat). The contract should document that `isHeld()` reflects local acquisition state, not distributed lock validity. The unlock behavior (silently clearing local state even if Redis remove failed) is correct but undocumented.

**Where:** `INosqlLock.java:21`, `LettuceLock.java:73-74`

**Severity:** LOW — documentation gap (behavior is correct for simple lock pattern)

---

### Finding D03-07: `INosqlService.getMessageService()` is dead API (returns null) [LOW]

**What:** `INosqlService.getMessageService()` declares it returns `IMessageService`, but the only implementation (`LettuceMessageService`) returns `null` unconditionally. This method appears to be a placeholder for Redis Pub/Sub integration that was never implemented.

**Why it matters:** Callers who discover `INosqlService` may attempt to use `getMessageService()` for message publishing, only to get NPE. Either remove the method or implement it. As-is, it's misleading dead code in the public API.

**Where:** `INosqlService.java:21`, `LettuceMessageService.java:294-296`

**Severity:** LOW — dead/misleading API

---

### D03 Non-findings (verified clean)

- **Factory method count (11 total) is appropriate.** 4 primitive data structures (hash, list, set, zset) + 6 business patterns (queue, lock, rateLimiter, ranking, counter, sessionStore) + 1 message service (dead). Each factory method is a single responsibility — create a typed operation object bound to a key.
- **INosqlQueue.dequeue() and INosqlSetOperations.pop() have consistent semantics.** Both return `Object` (nullable), both return null when empty, both are destructive reads. The similarity is correct — Queue is semantically a FIFO view over a Redis list, Set.pop is a random destructive read.
- **INosqlCounter.get() returning `long` (primitive) vs INosqlRanking.getScore() returning `double` (primitive) is consistent** in style (both primitives), though the sentinel values differ (0 vs NaN). This is a natural difference — counters genuinely default to 0, while NaN is a floating-point convention for "not found."

---

## Summary

| Dimension | Findings | High | Medium | Low |
|-----------|----------|------|--------|-----|
| D02 (Responsibility) | 3 | 0 | 2 | 1 |
| D03 (API Contracts) | 7 | 2 | 3 | 2 |
| **Total** | **10** | **2** | **5** | **3** |

**Top priority fixes:**
1. **D03-01**: Unify null handling between `INosqlRanking` and `INosqlZSetOperations` for score/rank operations on the same underlying data structure.
2. **D03-02**: Document the non-atomic `HMSET+PEXPIRE` in `INosqlSessionStore.set()` interface, or replace with a Lua script implementation.