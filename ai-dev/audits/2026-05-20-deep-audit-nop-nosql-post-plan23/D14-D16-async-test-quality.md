---

# Audit Report: D14 (Async & Transaction Patterns) + D16 (Test Quality) for nop-nosql

## Files Audited

**Source (16 files):**
- `LettuceLock.java`, `LettuceCounter.java`, `LettuceRateLimiter.java`, `LettuceQueue.java`, `LettuceRanking.java`, `LettuceSessionStore.java`
- `LettuceMessageService.java`, `LettuceRedisConnectionProvider.java`, `LettuceExecutor.java`, `LettuceHelper.java`
- `LettuceHashOperations.java`, `LettuceListOperations.java`, `LettuceSetOperations.java`, `LettuceZSetOperations.java`
- `RedisScripts.java`, Lua scripts (5 files)

**Test (1 file):**
- `TestLettuceNosqlService.java`

---

## D14 Findings

### [D14-01] LettuceLock: `volatile` is insufficient for compound check-then-act in unlock across threads
- **File**: `LettuceLock.java:57-65`
- **Evidence**:
  ```java
  public CompletableFuture<Void> unlockAsync() {
      String value = lockValue;       // read volatile
      if (value == null) {
          return CompletableFuture.completedFuture(null);
      }
      return LettuceExecutor.evalScript(async(), RedisScripts.REMOVE_IF_MATCH,
              ScriptOutputType.BOOLEAN, new String[]{key}, new Object[]{value}
      ).thenAccept(removed -> lockValue = null).toCompletableFuture(); // write volatile
  }
  ```
- **Severity**: P2
- **Status**: `volatile` ensures visibility of `lockValue` reads/writes, but there is a TOCTOU gap between reading `lockValue` on line 58 and the Lua script executing on Redis. If Thread A calls `unlock()` while Thread B concurrently calls `tryLock()` on the **same** `LettuceLock` instance, Thread B could succeed, get a new UUID, and then Thread A's Lua script would compare the **old** UUID — which would not match (safe due to CAS). So the CAS Lua script prevents actual corruption. However, the `lockValue = null` write in `thenAccept` happens asynchronously — if `unlockAsync()` is called twice concurrently, both may read the same non-null `lockValue`, both submit the Lua script, the first succeeds and clears the key, the second Lua script also succeeds (reads the key, it's gone, no match — returns false) — but both callbacks set `lockValue = null`. This is benign (idempotent null assignment), so no data loss.
- **Risk**: Low. The CAS in the Lua script is the actual safety net. The `volatile` + async callback pattern is safe enough for the intended single-owner-per-instance usage model. A true concurrent lock-share across threads on the same instance would be a usage error.
- **Recommendation**: Add Javadoc clarifying that a `LettuceLock` instance is intended for single-thread use (or at minimum that concurrent tryLock/unlock on the same instance is undefined). This is a documentation issue, not a correctness bug.
- **False-positive exclusion**: NOT a false positive because the class IS thread-safe enough for its purpose, but the lack of documentation about threading expectations could mislead users.

---

### [D14-02] `.join()` calls do not unwrap `CompletionException` / `ExecutionException`
- **File**: All sync wrapper methods across 8+ implementation files, e.g. `LettuceLock.java:53`, `LettuceCounter.java:48`, `LettuceRateLimiter.java:76`, `LettuceQueue.java:44`, etc.
- **Evidence**:
  ```java
  // LettuceLock.java:52-54
  public boolean tryLock(long leaseTimeMs) {
      return tryLockAsync(leaseTimeMs).join();
  }
  ```
  `CompletableFuture.join()` wraps execution exceptions in `CompletionException`. If the underlying Redis operation fails (connection lost, timeout, Redis error), the caller gets a `CompletionException` wrapping the real cause.
- **Severity**: P3
- **Status**: This is consistent with `CompletableFuture.join()` contract. Callers who want the underlying cause can catch `CompletionException` and call `.getCause()`. However, this differs from common Nop patterns where errors are wrapped in `NopException` with error codes. Lettuce itself throws `RedisCommandExecutionException` etc. which will be wrapped.
- **Risk**: Callers used to Nop's `NopException` pattern may not handle `CompletionException` correctly. Error messages will be Lettuce-specific rather than Nop-canonical.
- **Recommendation**: Consider a utility that unwraps `CompletionException` and re-wraps in `NopException` with an appropriate `ErrorCode`. This is a consistency/policy issue, not a bug.
- **False-positive exclusion**: NOT a false positive — this is a real API contract issue that affects error handling by consumers.

---

### [D14-03] LettuceExecutor.evalScript uses only `EVALSHA` without `EVAL` fallback
- **File**: `LettuceExecutor.java:16-21`
- **Evidence**:
  ```java
  public static <T> RedisFuture<T> evalScript(RedisScriptingAsyncCommands<String, Object> async,
                                              DigestedText script, ScriptOutputType outputType,
                                              String[] keys, Object[] values) {
      String digest = script.getDigestString();
      return async.evalsha(digest, outputType, keys, values);
  }
  ```
- **Severity**: P1
- **Status**: `EVALSHA` can fail with `NOSCRIPT` if the Redis server has been restarted (script cache flushed) or if this is the first call after a flush. The standard Redis pattern is `EVALSHA` → catch `NOSCRIPT` → `EVAL` with full script text. The `DigestedText` class carries both the digest and the full text, so the fallback is feasible.
- **Risk**: After Redis restart or `SCRIPT FLUSH`, all lock unlock, rate limiter, putIfAbsentOrMatch, and removeIfMatch operations will fail with `RedisNoScriptException` until the application is restarted. This is a **production reliability issue**.
- **Recommendation**: Add `EVAL` fallback when `EVALSHA` returns `NOSCRIPT`. Pattern: `evalsha` → on `RedisNoScriptException`, call `eval` with full script text. Alternatively, pre-load all scripts on connection start.
- **False-positive exclusion**: NOT a false positive — this is a known Redis operational concern with `EVALSHA`-only usage.

---

### [D14-04] LettuceCounter.getAsync() NumberFormatException on non-numeric stored values
- **File**: `LettuceCounter.java:33-39, 52-54`
- **Evidence**:
  ```java
  private Long toLong(Object v) {
      if (v == null) return 0L;
      if (v instanceof Number) return ((Number) v).longValue();
      return Long.parseLong(v.toString());  // throws NumberFormatException
  }
  
  public CompletableFuture<Long> getAsync() {
      return async().get(key).thenApply(this::toLong).toCompletableFuture();
  }
  ```
- **Severity**: P2
- **Status**: If a non-numeric value was stored under the same key (via direct Redis access or a bug), `Long.parseLong()` throws `NumberFormatException` — an unchecked exception that propagates through the CompletableFuture pipeline, wrapped as `CompletionException`.
- **Risk**: An uncaught `NumberFormatException` from a seemingly safe `get()` call. The `toLong` method handles `null` and `Number` but the `Long.parseLong` fallback has no try-catch.
- **Recommendation**: Either catch `NumberFormatException` and return 0L (defensive), or log a warning and throw a more descriptive `NopException`. Given the counter contract, returning 0 and logging is likely the safest.
- **False-positive exclusion**: NOT a false positive — the `toLong` method explicitly attempts parsing but has no fallback for unparseable strings.

---

### [D14-05] LettuceRateLimiter stores config as immutable field — no Javadoc about consistency requirement
- **File**: `LettuceRateLimiter.java:20-29`
- **Evidence**:
  ```java
  public class LettuceRateLimiter implements INosqlRateLimiter {
      private final LettuceRedisConnectionProvider client;
      private final String key;
      private final RateLimiterConfig config;  // immutable, final
  
      public LettuceRateLimiter(LettuceRedisConnectionProvider client, String key, RateLimiterConfig config) {
          this.client = client;
          this.key = key;
          this.config = config;
      }
  ```
- **Severity**: P3
- **Status**: The `config` is `final` and `RateLimiterConfig` has `final` fields, so it cannot change after construction. The design is actually safe — each `LettuceRateLimiter` instance is bound to a fixed config. If the caller wants a different config, they must create a new instance via `service.rateLimiter(key, newConfig)`. However, the shared Redis keys (`key + ":tokens"`, `key + ":timestamp"`) would then be accessed with different rate/capacity parameters from different instances, producing inconsistent results. There is no Javadoc warning about this.
- **Risk**: Two instances with different configs but the same key could produce unexpected rate limiting behavior.
- **Recommendation**: Add Javadoc to `INosqlRateLimiter` or `LettuceRateLimiter` clarifying that the same key should always be used with the same config across all instances, and that config is bound to the instance, not the key.
- **False-positive exclusion**: NOT a false positive — the design is correct but undocumented, which can mislead users.

---

### [D14-06] LettuceRedisConnectionProvider.doStop() does not close individual connections in the pool
- **File**: `LettuceRedisConnectionProvider.java:78-81`
- **Evidence**:
  ```java
  @Override
  protected void doStop() {
      if (client != null)
          client.shutdown();
  }
  ```
- **Severity**: P2
- **Status**: `client.shutdown()` shuts down the `RedisClusterClient`, which should close all connections created by it. However, the `RoundRobinSupplier` holds references to `StatefulRedisClusterConnection` instances that were created via `client.connect(codec)`. While `RedisClusterClient.shutdown()` does close connections, if `getConnection()` is called after `doStop()` (e.g., during graceful shutdown of dependent services), `connectionSupplier.get()` will return a closed connection, causing errors. There is no guard against post-stop access.
- **Risk**: During application shutdown, services that depend on Redis may attempt operations after `stop()` is called, getting cryptic errors from closed connections rather than a clean "service stopped" exception.
- **Recommendation**: Add an `isStarted()` check in `getConnection()` that throws a clear `IllegalStateException` or `NopException` if the provider has been stopped. Consider explicitly closing connections in `doStop()` before client shutdown.
- **False-positive exclusion**: NOT a false positive — missing lifecycle guard is a real operational risk during shutdown.

---

### [D14-07] LettuceMessageService.sync methods delegate to async + join() — no special CompletionException handling
- **File**: `LettuceMessageService.java:107-124`
- **Evidence**:
  ```java
  @Override
  public boolean putIfAbsent(String key, Object value) {
      return putIfAbsentAsync(key, value).toCompletableFuture().join();
  }
  
  @Override
  public boolean removeIfMatch(String key, Object object) {
      return removeIfMatchAsync(key, object).toCompletableFuture().join();
  }
  ```
- **Severity**: P3 (subsumed by D14-02)
- **Status**: These are sync wrappers over async methods. The `.join()` has the same `CompletionException` wrapping issue as all other sync methods. This is consistent across the codebase — not a new finding per se, but noting that `putIfAbsent`, `getAndSet`, and `removeIfMatch` specifically now go through async+join (as mentioned in the audit question), which is fine functionally.
- **Risk**: Same as D14-02.
- **Recommendation**: Same as D14-02 — apply a consistent unwrap-and-rethrow policy.
- **False-positive exclusion**: Merged with D14-02.

---

## D16 Findings

### [D16-01] All 6 business patterns are covered by tests ✅
- Lock: `testLock_AcquireAndRelease`, `testLock_TimeoutExpires`, `testLock_CASPreventMisunlock`
- Counter: `testCounter_IncrementAndGet`, `testCounter_GetAndReset`, `testCounter_Decrement`
- Queue: `testQueue_EnqueueAndDequeue`, `testQueue_EmptyDequeue`, `testQueue_Peek`, `testQueue_Batch`, `testQueue_Size`
- RateLimiter: `testRateLimiter_AllowWhenTokensAvailable`, `testRateLimiter_RejectWhenEmpty`, `testRateLimiter_GetAvailableTokens`
- Ranking: `testRanking_AddAndGetScore`, `testRanking_GetRank`, `testRanking_GetTopN`, `testRanking_IncrementScore`, `testRanking_Remove`, `testRanking_GetAround`
- SessionStore: `testSessionStore_SetAndGet`, `testSessionStore_GetField`, `testSessionStore_SetField`, `testSessionStore_TouchRefreshesTTL`, `testSessionStore_Remove`, `testSessionStore_Exists`

All 6 patterns covered. No finding.

---

### [D16-02] All 4 primitive operations are covered by tests ✅
- hashOps: `testHashOps_PutAndGet`
- listOps: `testListOps_AddAndPop`
- setOps: `testSetOps_AddAndMembers`
- zSetOps: `testZSetOps_AddAndScore`

All covered, though minimally (1 test each). No finding for coverage, but see D16-04 for edge cases.

---

### [D16-03] Missing edge case tests for all 6 business patterns
- **File**: `TestLettuceNosqlService.java` (full file)
- **Severity**: P2
- **Status**: The following edge cases are not tested:

  **Lock:**
  - Concurrent lock attempts from multiple threads on different Lock instances for the same Redis key
  - Lock expiry during active operation (what happens to unlock after expiry)
  - Double unlock (unlock called twice in a row)

  **Counter:**
  - `get()` on non-existent key (returns 0 — unverified)
  - Negative increment beyond zero (goes negative — unverified)
  - `getAndIncrement` (untested entirely)
  - `reset` (untested entirely)

  **Queue:**
  - `dequeueBatch(maxCount)` when queue has fewer items than maxCount
  - `dequeueBatch` on empty queue
  - `clear()` (untested entirely)

  **RateLimiter:**
  - Zero permits: `tryAcquire(0)` (untested)
  - Negative permits: `tryAcquire(-1)` (untested — likely hits Lua math issues)
  - Token refill over time (the core feature of token bucket — untested)

  **Ranking:**
  - `getRank` for non-existent member (returns -1 — only tested implicitly via `testRanking_Remove`)
  - `getTopN(0)` or `getTopN` on empty ranking (untested)
  - `getScore` for non-existent member (returns NaN — untested)
  - `getAround` for non-existent member (untested)

  **SessionStore:**
  - `get` non-existent session (should return empty map — untested)
  - `getField` for non-existent field on existing session (untested)
  - `touch` non-existent session (untested)

- **Risk**: Edge cases like counter going negative, lock expiry, and rate limiter token refill are core algorithmic behaviors. Without tests, regressions in these areas can go undetected.
- **Recommendation**: Add targeted edge case tests. Priority order: Counter `get()` on missing key, Queue `dequeueBatch` with fewer items, Ranking `getRank`/`getScore` for missing member, RateLimiter token refill over time, Lock double-unlock, SessionStore `get` for missing session.
- **False-positive exclusion**: NOT a false positive — these are genuinely untested edge cases that verify important behavioral contracts.

---

### [D16-04] Testcontainers `disabledWithoutDocker = true` is correctly set ✅
- **File**: `TestLettuceNosqlService.java:47`
- **Evidence**: `@Testcontainers(disabledWithoutDocker = true)`
- **Status**: Correct. Tests will be silently skipped if Docker is not available. No finding.

---

### [D16-05] Tests are independent — no ordering dependencies ✅
- **File**: `TestLettuceNosqlService.java`
- **Evidence**: Each test uses unique key prefixes (e.g., `test:lock:basic`, `test:lock:timeout`, `test:counter:incr`), and `@AfterEach` calls `service.clear()` which executes `flushdb`.
- **Status**: Tests are properly isolated. No finding.

---

## Summary

| ID | Title | Severity | Category |
|---|---|---|---|
| D14-01 | Lock volatile + async callback threading documentation gap | P2 | Async |
| D14-02 | `.join()` wraps errors in `CompletionException`, inconsistent with Nop error pattern | P3 | Async |
| D14-03 | **EVALSHA-only without EVAL fallback — fails after Redis restart** | **P1** | Async |
| D14-04 | Counter `toLong()` throws `NumberFormatException` on non-numeric stored values | P2 | Async |
| D14-05 | RateLimiter config consistency not documented | P3 | Async |
| D14-06 | ConnectionProvider missing lifecycle guard in `getConnection()` after `stop()` | P2 | Async |
| D16-03 | Missing edge case tests across all 6 patterns (Counter getAndIncrement/reset, Queue dequeueBatch fewer items, RateLimiter refill, etc.) | P2 | Test |

**Critical action item**: D14-03 (EVALSHA without fallback) is the highest-priority finding. After Redis restart, all Lua-script-based operations (lock unlock, rate limiting, putIfAbsentOrMatch, removeIfMatch) will fail until the application is restarted.