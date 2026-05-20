---

# Audit Dimension 09: Error Handling & Error Codes — nop-nosql

## Summary of what was checked

| Check | Scope | Result |
|-------|-------|--------|
| Raw RuntimeException/IllegalStateException/IllegalArgumentException throws | All 37 `.java` files in `src/main/` | **0 findings** — clean |
| Swallowed catch blocks | All `src/main/` files | **0 findings** — no `catch` blocks exist at all |
| System.out / System.err | All `src/main/` files | **0 findings** — clean |
| Hardcoded Chinese strings | All `src/main/` files | **0 violations** (1 Chinese comment in Javadoc, acceptable) |
| ErrorCode / NopException usage | All `src/main/` files | **0 usage** — module uses neither |
| `.join()` without CompletionException handling | 11 impl files, 68 `.join()` calls | **1 systemic finding** (all joins affected) |
| Silent no-op implementations | `forEachEntry`, `forEachEntryAsync`, `getMessageService` | **1 finding** |

---

## Findings

### [D09-01] All sync wrapper methods use raw `.join()` instead of `FutureHelper.syncGet()`, hiding real Redis errors behind CompletionException

- **File**: All 11 Lettuce implementation files across `nop-nosql-lettuce/src/main/java/io/nop/nosql/lettuce/impl/`
  - `LettuceMessageService.java`: lines 108, 113, 123 (3 occurrences)
  - `LettuceHashOperations.java`: lines 41, 58, 68, 78, 88, 100, 110, 121, 131, 146, 156, 166, 180, 190 (14 occurrences)
  - `LettuceZSetOperations.java`: lines 40, 56, 66, 76, 86, 96, 106, 116, 135 (9 occurrences)
  - `LettuceCounter.java`: lines 48, 58, 68, 78, 88 (5 occurrences)
  - `LettuceSessionStore.java`: lines 55, 67, 87, 101, 113, 127, 141 (7 occurrences)
  - `LettuceLock.java`: lines 53, 69 (2 occurrences)
  - `LettuceQueue.java`: lines 44, 54, 64, 78, 88, 98, 108 (7 occurrences)
  - `LettuceSetOperations.java`: lines 39, 49, 59, 69, 87, 97, 111, 121, 131 (9 occurrences)
  - `LettuceListOperations.java`: lines 35 (via `getSizeAsync`), all sync wrappers via async `.join()` pattern (no explicit sync overrides)
  - `LettuceRanking.java`: lines 46, 56, 69, 79, 105, 135, 145, 157 (8 occurrences)
  - `LettuceRateLimiter.java`: lines 76, 93 (2 occurrences)

- **Evidence** (representative sample from `LettuceHashOperations.java`):
  ```java
  // Line 41
  public Object get(String field) {
      return getAsync(field).toCompletableFuture().join();
  }
  
  // Line 88
  public void put(String field, Object value) {
      putAsync(field, value).toCompletableFuture().join();
  }
  
  // Line 131
  public void remove(String field) {
      removeAsync(field).toCompletableFuture().join();
  }
  ```

- **Severity**: **P1** — affects all callers of the module; exceptions become harder to diagnose and don't conform to platform conventions

- **Status**: Every sync wrapper in the module delegates to its async counterpart and calls `.join()` directly. When a Redis operation fails (connection timeout, cluster redirect failure, command timeout, auth error, etc.), Lettuce wraps the exception inside `java.util.concurrent.CompletionException`. The raw `.join()` call propagates this `CompletionException` as-is, which means:
  1. Callers see `CompletionException` instead of the real cause (e.g., `RedisConnectionException`, `RedisCommandTimeoutException`)
  2. The exception is NOT adapted to `NopException`, violating platform convention
  3. Stack traces show `CompletableFuture.join` as the immediate source, obscuring the real failure origin

- **Risk**: 
  - **Diagnosing production Redis failures becomes significantly harder** — the real cause is buried inside `CompletionException.getCause()`
  - **Nop platform's global error handling** (which expects `NopException` with `ErrorCode`) cannot classify these errors properly
  - **Monitoring/alerting** that relies on exception types will miss Redis-specific errors
  - **Error messages in API responses** will expose raw `java.util.concurrent.CompletionException` rather than meaningful business error codes

- **Recommendation**: 
  Replace all `someAsync().toCompletableFuture().join()` calls with `FutureHelper.syncGet(someAsync())`. The `FutureHelper.syncGet()` method (in `io.nop.api.core.util.FutureHelper`) already:
  1. Properly unwraps `CompletionException` and `ExecutionException` to expose the real cause
  2. Adapts the cause via `NopException.adapt(e.getCause())` to conform to platform conventions
  
  `LettuceMessageService` already imports `FutureHelper` (line 17), but doesn't use it for the sync methods.
  
  Suggested pattern for all sync wrappers:
  ```java
  // BEFORE
  public Object get(String field) {
      return getAsync(field).toCompletableFuture().join();
  }
  
  // AFTER
  public Object get(String field) {
      return FutureHelper.syncGet(getAsync(field));
  }
  ```
  
  Note: `FutureHelper.syncGet()` returns `CompletionStage<T>` → uses internal `syncGet` which handles the unwrapping. For methods that currently use `CompletionStage<T>.toCompletableFuture().join()`, the fix is simply replacing `.toCompletableFuture().join()` with wrapping in `FutureHelper.syncGet()`.

- **False-positive exclusion**: NOT a false positive. The platform convention is explicitly documented in `docs-for-ai/02-core-guides/error-handling.md` — "Prefer NopException + ErrorCode for business errors, Include parameters via .param(...) and keep the original cause". The platform provides `FutureHelper.syncGet()` precisely for this purpose. The module's `LettuceMessageService` already imports `FutureHelper` but doesn't use it for sync calls.

---

### [D09-02] `forEachEntry()` and `forEachEntryAsync()` silently do nothing — no error, no iteration

- **File**: `nop-nosql-lettuce/src/main/java/io/nop/nosql/lettuce/impl/LettuceMessageService.java`: lines 137–139 and 211–213

- **Evidence**:
  ```java
  // Lines 137-139
  @Override
  public void forEachEntry(BiConsumer<? super String, ? super Object> consumer) {
      // empty body - no iteration happens
  }
  
  // Lines 211-213
  @Override
  public CompletionStage<Void> forEachEntryAsync(BiConsumer<? super String, ? super Object> consumer) {
      return null;  // returns null CompletionStage!
  }
  ```

- **Severity**: **P2** — silent data loss / no-op in production code

- **Status**: These methods silently do nothing. The sync version returns void without executing the consumer. The async version returns `null`, which violates the `CompletionStage<Void>` contract and will cause `NullPointerException` if any caller chains `.thenApply()` or `.thenCompose()` on the result.

- **Risk**: 
  - Callers that rely on `forEachEntry` to process all Redis keys will silently skip all data — **data-dependent business logic simply doesn't execute**
  - `forEachEntryAsync` returning `null` will cause NPE in downstream code that expects a non-null `CompletionStage`
  - No error, no log, no indication that something is wrong — makes this extremely hard to debug

- **Recommendation**: 
  1. **For `forEachEntryAsync`**: Implement using Redis `SCAN` command (via Lettuce's `KeyScanCursor`), or if full iteration is intentionally unsupported, throw `UnsupportedOperationException` with a clear message rather than returning null.
  2. **For `forEachEntry`**: Delegate to `forEachEntryAsync` via `FutureHelper.syncGet()`, or throw `UnsupportedOperationException`.
  3. If scanning is intentionally not supported for the top-level key-value store, the interface should document this, and the implementation should throw rather than silently ignore.

- **False-positive exclusion**: NOT a false positive. A method named `forEachEntry` that accepts a `BiConsumer` strongly implies it will iterate over entries and invoke the consumer. Silently doing nothing violates the method contract. The async version returning `null` is a guaranteed NPE for callers that chain operations.

---

### [D09-03] `getMessageService()` returns null — silent failure for message queue integration

- **File**: `nop-nosql-lettuce/src/main/java/io/nop/nosql/lettuce/impl/LettuceMessageService.java`: lines 294–296

- **Evidence**:
  ```java
  @Override
  public IMessageService getMessageService() {
      return null;
  }
  ```

- **Severity**: **P3** — design smell; callers may NPE

- **Status**: The `INosqlService` interface requires a `getMessageService()` method. The implementation returns `null` without any documentation or guard. Any caller that uses `nosqlService.getMessageService().publish(...)` will get NPE.

- **Risk**: 
  - Callers that assume a non-null `IMessageService` will encounter `NullPointerException` at runtime
  - No indication that this feature is intentionally unimplemented

- **Recommendation**: Either:
  1. Return a no-op `IMessageService` implementation that does nothing (or throws `UnsupportedOperationException` for each method)
  2. Add `@Nullable` annotation and document that message service is not provided by this implementation
  3. If the interface requires it, throw `UnsupportedOperationException` with a clear message

- **False-positive exclusion**: NOT a false positive. Returning null from a method that returns an interface type is a well-known source of NPE. At minimum it needs `@Nullable` annotation or documentation.

---

## What was clean (zero findings)

| Check | Files scanned | Result |
|-------|--------------|--------|
| `throw new RuntimeException` / `IllegalStateException` / `IllegalArgumentException` | 37 files | **0 hits** |
| `catch` blocks (any kind) | 37 files | **0 hits** — module has no catch blocks |
| `System.out` / `System.err` | 37 files | **0 hits** |
| Hardcoded Chinese in code (non-comment) | 37 files | **0 hits** (1 Javadoc comment in `RedisConfig.java:32` is acceptable) |
| ErrorCode definitions | Glob for `*ErrorCode*` | **0 files found** — module defines no error codes |

## Overall Assessment

The nop-nosql module's error handling is **structurally minimal** — it performs no error adaptation at all. It has zero `throw` statements, zero `catch` blocks, zero `NopException` usage, and zero `ErrorCode` definitions. The module acts as a thin wrapper over Lettuce's async API.

The primary systemic issue is **D09-01**: 68 `.join()` calls across 11 files that wrap Lettuce errors in `CompletionException` instead of using the platform-standard `FutureHelper.syncGet()` which properly unwraps and adapts exceptions. This is the single highest-impact fix — replacing `.join()` with `FutureHelper.syncGet()` would bring the module into conformance with Nop platform error handling conventions.

The secondary issues (D09-02, D09-03) are silent no-op/null returns that violate method contracts and can cause subtle runtime failures.