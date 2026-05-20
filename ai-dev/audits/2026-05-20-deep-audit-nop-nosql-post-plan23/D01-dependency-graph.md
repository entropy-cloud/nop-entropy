---

# Audit Dimension 01: Dependency Graph & Module Boundaries — nop-nosql

## Summary of Checks Performed

| Check | Scope | Result |
|-------|-------|--------|
| Lettuce/Jedis imports in core | All 20 .java files in nop-nosql-core | ✅ **CLEAN** — zero matches |
| Impl package containment | All 18 .java files in nop-nosql-lettuce | ✅ **CLEAN** — all implementations in `impl/`, `codec/` is appropriate |
| Redis data structure names in business interfaces | All 12 core interfaces | ⚠️ **2 findings** (P2, P3) |
| nop-dao dependency justification | pom.xml + import analysis | 🔴 **1 finding** (P1) |
| Circular dependencies | Both pom.xml files | ✅ **CLEAN** — strictly one-directional |

---

### [D01-01] nop-dao dependency is entirely unused in nop-nosql-core

- **File**: `nop-persistence/nop-nosql/nop-nosql-core/pom.xml:25-28`
- **Evidence**:
  ```xml
  <dependency>
      <groupId>io.github.entropy-cloud</groupId>
      <artifactId>nop-dao</artifactId>
  </dependency>
  ```
  Searched all 20 .java files for `import io.nop.dao` → **zero matches**. The only nop-core imports are `io.nop.core.resource.*` (for Lua script loading in `RedisScripts.java`).
- **Severity**: **P1**
- **Status**: nop-dao is declared as a compile dependency but never used. It transitively pulls in nop-xlang → nop-xdefs → nop-antlr4-common, adding unnecessary classpath weight.
- **Risk**: Every consumer of nop-nosql-core transitively gets nop-dao (with its full transitive tree: nop-xlang, nop-xdefs, nop-antlr4-common). This increases compile time, binary size, and creates hidden coupling to the DAO layer that could block future refactoring.
- **Recommendation**: Remove the `nop-dao` dependency from `nop-nosql-core/pom.xml`. The only actual usage from nop-core is `io.nop.core.resource.*` (for loading Lua classpath scripts), which is already satisfied by the existing `nop-core` dependency. Verify with `mvn compile` after removal.
- **False-positive exclusion**: NOT a false positive. Exhaustive `grep` for `import io.nop.dao` across all 20 source files returned zero results. No indirect usage via reflection, resource files, or annotation processors was found either.

---

### [D01-02] INosqlListOperations exposes Redis command names (leftPop/rightPop)

- **File**: `nop-persistence/nop-nosql/nop-nosql-core/src/main/java/io/nop/nosql/core/INosqlListOperations.java:28-33`
- **Evidence**:
  ```java
  CompletableFuture<Object> leftPopAsync();
  CompletableFuture<Object> rightPopAsync();
  CompletableFuture<List<Object>> leftPopMultiAsync(int maxCount);
  ```
  These method names directly mirror Redis `LPOP`/`RPOP` commands. A business-level abstraction would use names like `dequeueFront`/`dequeueBack`.
- **Severity**: **P2**
- **Status**: The core module provides two abstraction tiers: (a) business-pattern interfaces (`INosqlQueue`, `INosqlRanking`, etc.) which are clean, and (b) data-structure interfaces (`INosqlListOperations`, `INosqlZSetOperations`) which leak Redis terminology. The `INosqlQueue` interface correctly uses `enqueue`/`dequeue`, but `INosqlListOperations` is a raw Redis list abstraction with command-level naming.
- **Risk**: Callers using `INosqlListOperations` are coupled to Redis semantics (left/right list operations). If the backend were swapped to a different KV store without ordered-directional pops, this interface would need breaking changes. However, the business-pattern `INosqlQueue` is not affected.
- **Recommendation**: Consider renaming to direction-neutral names (`popFirst`/`popLast`, `popFirstMulti`), or document that `INosqlListOperations` is intentionally a low-level Redis list API and consumers should prefer `INosqlQueue` for business logic.
- **False-positive exclusion**: NOT a false positive. The business-pattern interfaces (INosqlQueue, INosqlRanking, etc.) are genuinely clean. This finding specifically targets the data-structure layer which uses Redis vocabulary in a core abstraction module.

---

### [D01-03] INosqlZSetOperations uses Redis-specific terminology (card, revRank, revRange)

- **File**: `nop-persistence/nop-nosql/nop-nosql-core/src/main/java/io/nop/nosql/core/INosqlZSetOperations.java:39-49`
- **Evidence**:
  ```java
  CompletableFuture<Long> cardAsync();
  long card();
  // ...
  CompletableFuture<Long> revRankAsync(String member);
  CompletableFuture<List<ZSetEntry>> revRangeAsync(long start, long end);
  ```
  `card()` directly maps to Redis `ZCARD`, `revRank()` to `ZREVRANK`, `revRange()` to `ZREVRANGE`. The `INosqlService.zSetOps()` factory method also directly names the Redis data structure type.
- **Severity**: **P3**
- **Status**: Same dual-abstraction-tier issue as D01-02. The business-pattern `INosqlRanking` correctly provides a clean abstraction (`getRank`, `getTopN`, `getAround`). `INosqlZSetOperations` is the lower-level data-structure API with Redis naming.
- **Risk**: Lower risk than D01-02 because sorted sets are more Redis-specific by nature and there's no common cross-store abstraction for them. The clean `INosqlRanking` interface covers most business use cases.
- **Recommendation**: Consider renaming `card()` → `size()`, `revRank()` → `reverseRank()`, `revRange()` → `reverseRange()` for consistency with generic naming conventions. The `zSetOps()` method name is acceptable given the explicit data-structure context.
- **False-positive exclusion**: NOT a false positive, but severity is reduced because `INosqlRanking` provides the business-level abstraction. This finding targets the data-structure API layer only.

---

## Clean Checks (No Findings)

### nop-nosql-core: Zero lettuce/jedis imports
Searched all 20 `.java` files for `io.lettuce` and `redis.clients.jedis` — zero matches. Core module has no driver dependency leakage.

### nop-nosql-lettuce: Implementation containment is correct
All 13 implementation classes live in `io.nop.nosql.lettuce.impl`. The 3 non-impl classes at the module's top level are:
- `LettuceNosqlService` — empty stub class, only referenced by its own test (potentially dead code but not a boundary violation)
- `IRedisConnectionProvider` — a lettuce-internal SPI interface, correctly scoped to the lettuce module
- `codec/PrefixTextCodec` — a codec utility in an appropriate sub-package

No lettuce-specific types leak through any core interface.

### Circular dependencies: None detected
- `nop-nosql-core/pom.xml` depends on: nop-commons, nop-core, nop-dao (no reference to lettuce)
- `nop-nosql-lettuce/pom.xml` depends on: lettuce-core, nop-nosql-core (plus test deps)
- Dependency is strictly one-directional: `lettuce → core`. No reverse dependency exists.