# Audit Report: nop-nosql — Dimensions 15 (Type Safety) & 17 (Code Style)

**Scope**: 38 Java files across `nop-nosql-core` (20 files) and `nop-nosql-lettuce` (18 files)

---

## D15: Type Safety Findings

### [D15-01] INosqlListOperations lacks sync methods — inconsistent with sibling interfaces
- **File**: `nop-nosql-core/.../INosqlListOperations.java:15-35`
- **Evidence**:
```java
public interface INosqlListOperations {
    CompletableFuture<Long> getSizeAsync();
    CompletableFuture<Void> clearAsync();
    CompletableFuture<Void> addAsync(Object value);
    CompletableFuture<List<Object>> getRangeAsync(long start, int maxCount);
    // ... only async methods, no sync counterparts
}
```
- **Severity**: P1
- **Status**: `INosqlSetOperations`, `INosqlZSetOperations`, `INosqlQueue`, `INosqlCounter`, `INosqlRanking`, `INosqlLock`, `INosqlRateLimiter`, and `INosqlSessionStore` all provide both `xxxAsync()` and `xxx()` sync convenience methods. `INosqlListOperations` and `INosqlHashOperations` (via `IAsyncMap` inheritance) are the only interfaces missing sync wrappers.
- **Risk**: Consumers must call `.join()` themselves for list ops, inconsistent API ergonomics, suggests the interface was added later and the sync methods were forgotten.
- **Recommendation**: Add sync `getSize()`, `clear()`, `add(Object)`, `addAll(Collection<?>)`, `getRange(long, int)`, `trim(long, long)`, `leftPop()`, `rightPop()`, `leftPopMulti(int)`, `forEachItem(Consumer)` to match the pattern used by sibling interfaces.
- **False-positive exclusion**: NOT a false positive. The Lettuce implementation (`LettuceListOperations`) only implements async methods — confirming the interface is incomplete.

### [D15-02] Raw type cast `(Map)` in LettuceHashOperations.putAllAsync
- **File**: `nop-nosql-lettuce/.../LettuceHashOperations.java:95`
- **Evidence**:
```java
public CompletionStage<Void> putAllAsync(Map<? extends String, ?> map) {
    if (map == null || map.isEmpty())
        return CompletableFuture.completedFuture(null);
    return async().hset(key, (Map) map).thenApply(Functionals.toVoid());
}
```
- **Severity**: P2
- **Status**: Raw type `(Map)` casts `Map<? extends String, ?>` to `Map` (raw), producing an unchecked warning. The Lettuce `hset` API accepts `Map<K, V>`.
- **Risk**: Suppresses compile-time generic safety. Functionally works because `?` is compatible at runtime.
- **Recommendation**: Use `async().hset(key, (Map<String, Object>) (Map) map)` or introduce a helper to bridge the wildcard, acknowledging the inherent variance limitation.
- **False-positive exclusion**: NOT FP — raw type usage is a verifiable fact; IDEs flag this as an unchecked cast.

### [D15-03] Raw type cast `(Map)` in LettuceMessageService.putAllAsync
- **File**: `nop-nosql-lettuce/.../LettuceMessageService.java:176`
- **Evidence**:
```java
public CompletionStage<Void> putAllAsync(Map<? extends String, ?> map) {
    return async().mset((Map) map);
}
```
- **Severity**: P2
- **Status**: Same raw type pattern as D15-02. Also a parallel issue at line 103 (`sync().mset((Map<String, Object>) map)`).
- **Risk**: Same as D15-02 — unchecked cast.
- **Recommendation**: Same as D15-02 — acknowledge with `@SuppressWarnings("unchecked")` or add an intermediate cast.
- **False-positive exclusion**: NOT FP — same verifiable raw type.

### [D15-04] Unnecessary redundant cast in LettuceSessionStore.getAsync
- **File**: `nop-nosql-lettuce/.../LettuceSessionStore.java:46-50`
- **Evidence**:
```java
return cmd.hgetall(key)
    .thenApply((Map<String, Object> map) -> {
        if (map == null)
            map = new HashMap<>();
        return (Map<String, Object>) new HashMap<>(map);
    }).toCompletableFuture();
```
- **Severity**: P3
- **Status**: `new HashMap<>(map)` already returns `HashMap<String, Object>` (type inferred from `map`). The outer `(Map<String, Object>)` cast is redundant because `HashMap<K,V>` extends `Map<K,V>`.
- **Risk**: Minor code smell — confusing for readers, suggests a type issue that doesn't exist.
- **Recommendation**: Simplify to `return new HashMap<>(map);`
- **False-positive exclusion**: NOT FP — the cast is provably redundant per Java type system.

### [D15-05] Object[] cast from Lua eval result in LettuceRateLimiter
- **File**: `nop-nosql-lettuce/.../LettuceRateLimiter.java:66-68`
- **Evidence**:
```java
.thenApply(result -> {
    Object[] arr = (Object[]) result;
    boolean allowed = Long.valueOf(1L).equals(arr[0]);
    long remaining = ((Number) arr[1]).longValue();
```
- **Severity**: P2
- **Status**: `evalScript` with `ScriptOutputType.MULTI` returns `Object` that's actually `Object[]`. This is an unchecked cast inherent to the Lettuce API design. Could throw `ClassCastException` if the Lua script returns unexpected format.
- **Risk**: No compile-time safety. If the Lua script changes or fails, this produces `ClassCastException` instead of a meaningful error.
- **Recommendation**: Add a defensive check: `if (!(result instanceof Object[] arr)) throw new NopException(...).param("result", result);`
- **False-positive exclusion**: NOT FP — this is a real unchecked cast with no validation.

### [D15-06] Mixed async return types: CompletableFuture vs CompletionStage
- **File**: Multiple interfaces across `nop-nosql-core`
- **Evidence**:
```java
// INosqlSetOperations uses CompletableFuture:
CompletableFuture<Boolean> addAsync(Object value);

// INosqlKeyValueOperations/IAsyncMap uses CompletionStage:
CompletionStage<Object> getAsync(String key);

// INosqlHashOperations uses CompletionStage:
CompletionStage<Map<String, Object>> getAllAsync();
```
- **Severity**: P2
- **Status**: Interfaces use a mix of `CompletableFuture` and `CompletionStage` for async return types. The split follows the parent interface (`IAsyncMap` uses `CompletionStage`) vs. new interfaces (`INosqlSetOperations` etc. use `CompletableFuture`). This is technically valid (CF extends CS) but inconsistent.
- **Risk**: Consumers must know which type to expect. Limits composability when chaining operations across different interface boundaries.
- **Recommendation**: Standardize on `CompletionStage` in interfaces (more abstract) and return `CompletableFuture` in implementations, following the established pattern in `IAsyncMap`/`INosqlKeyValueOperations`.
- **False-positive exclusion**: NOT FP — the inconsistency is verifiable.

### [D15-07] `NosqlCache.stats()` returns null
- **File**: `nop-nosql-core/.../cache/NosqlCache.java:194-196`
- **Evidence**:
```java
@Override
public CacheStats stats() {
    return null;
}
```
- **Severity**: P2
- **Status**: Returning `null` from a method declared to return `CacheStats` forces every caller to null-check. The `ICache` interface doesn't document nullability.
- **Risk**: NPE for callers that don't expect null (e.g., `cache.stats().getHitRate()`).
- **Recommendation**: Return `CacheStats.EMPTY` or a zero-valued `CacheStats` instance, or annotate with `@Nullable`.
- **False-positive exclusion**: NOT FP — `return null` from a non-`@Nullable` method is a type-safety gap.

### [D15-08] LettuceMessageService.getMessageService() returns null
- **File**: `nop-nosql-lettuce/.../LettuceMessageService.java:294-296`
- **Evidence**:
```java
@Override
public IMessageService getMessageService() {
    return null;
}
```
- **Severity**: P2
- **Status**: Same pattern as D15-07. The `INosqlService` interface declares `IMessageService getMessageService()` without `@Nullable`. Implementation returns null.
- **Risk**: NPE for any caller that uses `nosqlService.getMessageService().send(...)`.
- **Recommendation**: Either implement the method properly, throw `UnsupportedOperationException`, or annotate the interface method with `@Nullable`.
- **False-positive exclusion**: NOT FP — contract violation via null return.

### [D15-09] LettuceMessageService.forEachEntryAsync() returns null
- **File**: `nop-nosql-lettuce/.../LettuceMessageService.java:211-213`
- **Evidence**:
```java
@Override
public CompletionStage<Void> forEachEntryAsync(BiConsumer<? super String, ? super Object> consumer) {
    return null;
}
```
- **Severity**: P1
- **Status**: Returns null from a `CompletionStage<Void>` method. This is a latent NPE — any code that calls `.thenApply()`, `.toCompletableFuture()`, etc. on the result will crash.
- **Risk**: High — runtime crash on any usage. Unlike `stats()` and `getMessageService()`, a `CompletionStage` return is expected to be chained.
- **Recommendation**: Return `CompletableFuture.completedFuture(null)` or throw `UnsupportedOperationException`.
- **False-positive exclusion**: NOT FP — returning null from a CompletionStage is dangerous.

---

## D17: Code Style Findings

### [D17-01] Import order violation: jakarta.* placed between io.nop.* and java.* in NosqlCache
- **File**: `nop-nosql-core/.../cache/NosqlCache.java:10-21`
- **Evidence**:
```java
import io.nop.commons.cache.CacheConfig;       // io.nop.*
import io.nop.commons.cache.CacheStats;
import io.nop.commons.cache.ICache;
import io.nop.nosql.core.INosqlKeyValueOperations;

import jakarta.annotation.Nonnull;              // jakarta.* (WRONG position)
import jakarta.annotation.Nullable;
import java.util.Collection;                    // java.* (WRONG position)
import java.util.Map;
```
- **Severity**: P2
- **Status**: Project convention requires `java.*` → `jakarta.*` → `third-party` → `io.nop.*`. Here jakarta and java imports appear after io.nop imports.
- **Risk**: Inconsistent with project style. May trigger checkstyle violations if rules are enabled.
- **Recommendation**: Reorder to: `java.*` → `jakarta.*` → `io.lettuce.*` (third-party) → `io.nop.*`.
- **False-positive exclusion**: NOT FP — the import order is directly verifiable.

### [D17-02] Unused import: `RedisAdvancedClusterCommands` in 5 classes
- **File**: Multiple Lettuce impl files (5 files)
- **Evidence**: These classes import and declare `sync()` but never call it in their method bodies:
  - `LettuceRateLimiter.java:12` — `import ...RedisAdvancedClusterCommands;` + line 35: `protected ... sync()`
  - `LettuceCounter.java:11` — same pattern (only uses `async()`)
  - `LettuceLock.java:13` — same pattern (only uses `async()`)
  - `LettuceQueue.java:11` — same pattern (only uses `async()`)
  - `LettuceRanking.java:12` — same pattern (only uses `async()`)
  - `LettuceSessionStore.java:11` — same pattern (only uses `async()`)
- **Severity**: P2
- **Status**: These 6 classes declare `protected sync()` and import `RedisAdvancedClusterCommands` but never use the sync commands. The `sync()` method is declared but never invoked within the class.
- **Risk**: Dead code, unnecessary imports. The `protected` access means subclasses could use it, but none currently exist.
- **Recommendation**: Either remove unused `sync()` method + import from these 6 classes, or add a comment explaining why it's kept (e.g., "for subclass extension").
- **False-positive exclusion**: NOT FP — I verified each file has zero `sync().` calls in the method bodies.

### [D17-03] Empty class: LettuceNosqlService
- **File**: `nop-nosql-lettuce/.../LettuceNosqlService.java:10-11`
- **Evidence**:
```java
public class LettuceNosqlService {
}
```
- **Severity**: P3
- **Status**: An empty class in a production source tree. The actual service implementation is `LettuceMessageService`. This class serves no purpose.
- **Risk**: Confusion for developers who expect the service to be here. May be a leftover from a refactoring.
- **Recommendation**: Remove the empty class or repurpose it as the main entry point (e.g., rename `LettuceMessageService` to `LettuceNosqlService`).
- **False-positive exclusion**: NOT FP — the class is verifiably empty.

### [D17-04] Empty interface: NosqlConstants
- **File**: `nop-nosql-core/.../NosqlConstants.java:10-11`
- **Evidence**:
```java
public interface NosqlConstants {
}
```
- **Severity**: P3
- **Status**: An empty constants interface. No constants defined. This is a marker for future use or a leftover.
- **Risk**: Minor dead code. Unused interfaces should be removed to avoid confusion.
- **Recommendation**: Remove if no constants are planned, or add a comment explaining the intent.
- **False-positive exclusion**: NOT FP — the interface is verifiably empty.

### [D17-05] AI-style inline comments restating method semantics
- **File**: `nop-nosql-lettuce/.../LettuceSessionStore.java:40,58,70,90,104,116,130`
- **Evidence**:
```java
// get: HGETALL
@Override
public CompletableFuture<Map<String, Object>> getAsync(String sessionId) { ... }

// getField: HGET
@Override
public CompletableFuture<Object> getFieldAsync(...) { ... }

// set: HMSET + PEXPIRE (non-atomic, noted in design doc)
@Override
public CompletableFuture<Void> setAsync(...) { ... }

// setField: HSET
// touch: PEXPIRE (refresh TTL)
// remove: DEL
// exists: EXISTS
```
- **Severity**: P3
- **Status**: Seven inline comments that just restate the Redis command name. The method names already convey the semantics. The "non-atomic, noted in design doc" comment is useful, the rest are noise.
- **Risk**: Low — visual noise, no functional impact.
- **Recommendation**: Remove the obvious comments (`// get: HGETALL`, `// getField: HGET`, etc.). Keep only the "non-atomic" comment as it adds value.
- **False-positive exclusion**: NOT FP — these are verifiable AI-style comments.

### [D17-06] AI-style comment in LettuceRanking
- **File**: `nop-nosql-lettuce/.../LettuceRanking.java:59`
- **Evidence**:
```java
// getRank = ZREVRANK (0-based, highest score = rank 0)
@Override
public CompletableFuture<Long> getRankAsync(String member) {
```
- **Severity**: P3
- **Status**: Comment restates what the implementation shows (`zrevrank`). Not harmful but not adding information.
- **Risk**: Visual noise.
- **Recommendation**: Remove or convert to Javadoc if the `ZREVRANK` detail is important.
- **False-positive exclusion**: NOT FP — AI-style inline comment.

### [D17-07] AI-style block comment in LettuceRateLimiter
- **File**: `nop-nosql-lettuce/.../LettuceRateLimiter.java:39-50`
- **Evidence**:
```java
/**
 * Uses rate_limit.lua script which implements token bucket algorithm.
 * 
 * KEYS[1] = tokens_key (key + ":tokens")
 * KEYS[2] = timestamp_key (key + ":timestamp")
 * ARGV[1] = rate (tokens per second)
 * ARGV[2] = capacity (max burst)
 * ARGV[3] = now (current timestamp in ms)
 * ARGV[4] = requested (number of permits)
 * 
 * Returns: {allowed (0/1), remaining_tokens}
 */
```
- **Severity**: P3
- **Status**: This Javadoc comment is actually useful — it documents the Lua script contract. However, it's AI-generated style (overly detailed parameter mapping).
- **Risk**: Low — this one is borderline useful.
- **Recommendation**: Keep but move the Lua script parameter documentation to the Lua script file itself. The method Javadoc should just say "Implements token bucket rate limiting via Lua script."
- **False-positive exclusion**: Borderline — the comment IS useful, just overly verbose for a method Javadoc.

### [D17-08] `protected` methods that could be `private` — async() and sync()
- **File**: Multiple Lettuce impl files (14 declarations across 11 files)
- **Evidence**: `protected RedisAdvancedClusterAsyncCommands<String, Object> async()` appears in every Lettuce impl class. None of these classes are extended.
- **Severity**: P3
- **Status**: All `async()` and `sync()` methods are `protected` but there are no subclasses. They could be `private` or package-private. However, `protected` is defensible if future extension is anticipated. This is a common pattern in the codebase for framework extensibility.
- **Risk**: Minor — slightly wider access than needed. Allows subclassing to inject different behavior, which may be intentional.
- **Recommendation**: Keep as `protected` if extension is a design goal (consistent with Nop platform patterns). Consider adding a brief comment: `/* protected for potential subclass extension */`.
- **False-positive exclusion**: ACKNOWLEDGE as borderline — `protected` is acceptable for framework extensibility.

---

## Summary

| Dimension | P0 | P1 | P2 | P3 | Total |
|-----------|----|----|----|----|-------|
| D15 (Type Safety) | 0 | 2 | 5 | 1 | 8 |
| D17 (Code Style) | 0 | 0 | 2 | 5 | 7 |
| **Total** | **0** | **2** | **7** | **6** | **15** |

**Key concerns** (P1):
1. `forEachEntryAsync()` returning `null` (CompletionStage) — **latent NPE** on any chaining call
2. `INosqlListOperations` missing sync methods — **API inconsistency** with all sibling interfaces

**Most impactful P2s**:
- Raw type casts `(Map)` in 2 files (D15-02, D15-03)
- Null returns from `stats()`, `getMessageService()` without `@Nullable` annotation (D15-07, D15-08)
- Mixed `CompletableFuture` vs `CompletionStage` return types across interfaces (D15-06)
- Import order violation in `NosqlCache` (D17-01)
- Unused `sync()` + import in 6 classes (D17-02)

**Verified as clean**:
- ✅ All files have standard copyright headers
- ✅ No `@SuppressWarnings("unchecked")` found
- ✅ No raw type usage on variable declarations (only in casts)
- ✅ Naming conventions (I+PascalCase for interfaces, PascalCase for classes, camelCase methods) are correct
- ✅ 4-space indentation is consistent throughout
- ✅ Line lengths are reasonable (<120 chars)
- ✅ No unused imports (except the sync-related ones noted in D17-02)
- ✅ No missing copyright headers