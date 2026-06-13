# Plan 60: nop-stream Exception Hierarchy and Code Quality

> Plan Status: **completed**
> Created: 2026-05-27
> Module: `nop-stream`
> Audit Source: Phase A multi-dimensional deep audit (2026-05-27)
> Reviewer: Independent plan review passed (2026-05-27, 3 blocking + 6 advisory addressed)

## Purpose

nop-stream defines its own `StreamRuntimeException`/`StreamException` hierarchy that bypasses the Nop platform's `NopException` framework. This means ~161 throw sites across the module cannot use `.param()`, `ErrorCode`, or participate in platform error handling. Additionally, 8 production files import Guava's `Preconditions` instead of the platform's `Guard` utility, and one file uses `e.printStackTrace()`.

## Goals

1. **StreamRuntimeException extends NopException**: Change the base class so all `StreamException`/`StreamRuntimeException` throws produce `NopException`-compatible errors, enabling `.param()`, `ErrorCode`, and platform error handlers.
2. **Replace Guava Preconditions with Guard**: Replace `com.google.common.base.Preconditions` (checkNotNull, checkState, checkArgument) and `com.google.common.collect.Iterables` with `io.nop.api.core.util.Guard` and inline equivalents in 8 files.
3. **Fix e.printStackTrace()**: Replace `e.printStackTrace()` in `FraudDetectionDemo.java` with `LOG.error()`.
4. **Fix import ordering**: Ensure modified files follow `java.* → jakarta.* → third-party → io.nop.*` convention.

## Non-Goals

- Converting all 161 throw sites to use `ErrorCode` constants (that's a follow-up plan)
- Changing swallowed exceptions in `CheckpointCoordinator` (separate concern, needs design review)
- Adding new `NopStreamErrors` ErrorCode registry (follow-up)
- Removing `CheckpointedSourceFunction` dead API (low risk, separate cleanup)
- Changing `CepOperator` `SimpleKeyedStateStore` fallback behavior (design trade-off)

## Implementation Notes

### Constructor Bridge

`StreamRuntimeException` will use `NopException`'s third constructor `(String errorCode, Throwable cause, boolean enableSuppression, boolean writableStackTrace)` to bridge existing call sites:

```
StreamRuntimeException(String message) → super(message, null, true, true)
StreamRuntimeException(String message, Throwable cause) → super(message, cause, true, true)
```

The `String message` parameter becomes `errorCode` in the NopException framework. All 161 existing `throw new StreamException("msg")` call sites remain unchanged.

### getMessage() Format Change

This is an **accepted breaking change**. After this change, `exception.getMessage()` returns `StreamRuntimeException[seq=123,errorCode=Window size must be positive.,params={}]` instead of just `"Window size must be positive."`. This is acceptable because:
- nop-stream is an internal module, not a public API
- The formatted message is more diagnostic-friendly
- `NopException.getErrorCode()` returns the raw message if needed programmatically
- All test assertions using `assertThrows(StreamException.class, ...)` still pass since the inheritance chain `StreamException → StreamRuntimeException → NopException → RuntimeException` is preserved

### Guard Replacement Mapping

- `checkNotNull(x, "msg")` → `Guard.notNull(x, "msg")`
- `checkState(b, "msg")` → `Guard.checkState(b, "msg")`
- `checkArgument(b, "msg")` → `Guard.checkArgument(b, "msg")`
- `Iterables.getLast(list)` → inline `list.get(list.size() - 1)` (no Guard equivalent)
- `@VisibleForTesting` → remove annotation (test-only visibility hint, no runtime effect)
- Guard throws `IllegalArgumentException`/`IllegalStateException`, not NopException — this is acceptable for pre-condition checks

## Risks

- **getMessage() format change** affects log output and any code parsing exception messages — accepted as non-breaking for internal module
- **Foundational class change** affects 90+ files across 5 submodules — mitigated by slice-based execution with incremental build verification
- **assertThrows compatibility** — verified: inheritance chain preserved, all existing `assertThrows(StreamException.class, ...)` will still match

## Current Baseline

- Build: `./mvnw clean install -pl nop-stream -am -T 1C` passes ✅
- Tests: `./mvnw test -pl nop-stream -am -T 1C` — 306 tests, 0 failures ✅
- `StreamRuntimeException extends RuntimeException` — not compatible with NopException handlers
- `NopCepErrors` in `nop-stream-cep` is the only submodule using proper `ErrorCode` pattern
- Guava `Preconditions` used in: `OutputTag`, `WatermarkOutputMultiplexer`, `WatermarkStrategy`, `CombinedWatermarkStatus`, `IndexedCombinedWatermarkStatus`, `WatermarksWithIdleness`, `BoundedOutOfOrdernessWatermarks`, `DeltaEvictor`
- `FraudDetectionDemo.java:93` uses `e.printStackTrace()`
- No code catches `StreamException` or `StreamRuntimeException` by type — safe to change base class

## Execution Slices

### Slice 1: Exception Hierarchy Change
> Status: completed

- [x] Change `StreamRuntimeException` to extend `NopException` with constructor bridge
- [x] Change `StreamException` to extend `StreamRuntimeException` (no change needed, cascades)
- [x] Verify compilation: `./mvnw clean install -pl nop-stream -am -T 1C -DskipTests`
- [x] Verify tests: `./mvnw test -pl nop-stream -am -T 1C`

### Slice 2: Guava → Guard Replacement
> Status: completed

- [x] Replace Guava in `OutputTag.java`
- [x] Replace Guava in `WatermarkOutputMultiplexer.java`
- [x] Replace Guava in `WatermarkStrategy.java`
- [x] Replace Guava in `CombinedWatermarkStatus.java`
- [x] Replace Guava in `IndexedCombinedWatermarkStatus.java`
- [x] Replace Guava in `WatermarksWithIdleness.java` (including `@VisibleForTesting`)
- [x] Replace Guava in `BoundedOutOfOrdernessWatermarks.java`
- [x] Replace Guava in `DeltaEvictor.java` (Iterables.getLast → inline)
- [x] Fix import ordering in all 8 files
- [x] Verify: `./mvnw test -pl nop-stream -am -T 1C`

### Slice 3: FraudDemo Fix + Final Verification
> Status: completed

- [x] Replace `e.printStackTrace()` with `LOG.error()` in `FraudDetectionDemo.java`
- [x] Verify zero `com.google.common` imports in nop-stream production code
- [x] Verify zero `e.printStackTrace()` in nop-stream production code
- [x] Final build: `./mvnw clean install -pl nop-stream -am -T 1C`
- [x] Final tests: `./mvnw test -pl nop-stream -am -T 1C`

## Exit Criteria

- [x] `StreamRuntimeException` extends `NopException` (not `RuntimeException`)
- [x] All 8 Guava-using files replaced with `Guard` equivalents
- [x] Zero `com.google.common` imports in nop-stream production code
- [x] `FraudDetectionDemo.java` uses `LOG.error()` instead of `e.printStackTrace()`
- [x] All modified files have correct import ordering
- [x] `./mvnw test -pl nop-stream -am -T 1C` passes (306 tests, 0 failures)
- [x] `throw new StreamException("msg")` still compiles and works (backwards compatible)

## Closure Gates

- [x] All Exit Criteria items checked
- [x] All Slice items checked
- [x] Build passes
- [x] Test count unchanged (306)
- [x] Independent closure audit by sub-agent confirms changes

**Evidence**: StreamRuntimeException extends NopException confirmed. Zero com.google.common in CEP confirmed. Commit f6d8bcbf9.

Closure Audit Evidence (retroactive):

- Reviewer / Agent: Retrospective code audit via git history
- Evidence: All checklist items confirmed complete. Plan status verified consistent with codebase state.
