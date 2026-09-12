# nop-treesitter performance tuning log

> Roadmap item 13 (PERF-01..05). Numbers measured on the development machine
> (Apple M-series arm64, macOS 25.6.0, JDK 26.0.1, JMH 1.33, 1 fork, 3×1s warmup,
> 5×1s measurement, gc profiler). Absolute numbers carry wide error bars on
> this shared machine (up to ±60% on some suites); the Java-vs-C ratios are
> measured back-to-back and are robust in direction. Reproduce with:
>
> - Java: `nop-treesitter/bench/run-c-reference.sh` (full pipeline), or
>   `java -cp "nop-treesitter/target/classes:nop-treesitter/target/test-classes:$(cat nop-treesitter/_tmp/ts-bench-test-cp.txt)" io.nop.treesitter.bench.TreeSitterBenchmarkRunner`
> - C: same script, or `_tmp/ts-bench/c_ref` after building.

## Benchmark inputs

Deterministic seeded fixtures (`BenchSources`, seed 0x5EED); truncated at
top-level value boundaries, so final sizes differ from the nominal targets.
The exact same bytes feed both runtimes:

| fixture | nominal | actual bytes |
| --- | --- | --- |
| json-10k | 10 KB | 10,925 |
| json-100k | 100 KB | 67,408 |
| json-1m | 1 MB | 1,050,302 |
| java-single | ~10 KB | 8,252 |

Work per op (both runtimes): parse + render the tree to a string + free.

## Throughput (2026-09-10 run)

| benchmark | Java ops/s | C ops/s | C/Java ratio | vs 3x target |
| --- | --- | --- | --- | --- |
| json-10k | 617.8 ±167 | 1416.8 | **2.29x** | within |
| json-100k | 91.5 ±16.5 | 234.5 | **2.56x** | within |
| json-1m | 4.23 ±0.75 | 14.8 | **3.50x** | over |
| java-single | 273.5 ±105 | 1169.5 | **4.28x** | over |
| java-project-25-files | 96.7 ±63 (≈2417 files/s) | — | n/a (Java-only) | — |

**Roadmap target verdict ("within 3x of C on JSON/Java benchmarks"): met on
small/mid JSON, missed at 1 MB JSON (3.5x) and on the Java grammar (4.3x).**
The Java side also pays the tree snapshot (parse arena → owned TSTree deep
copy) per op, which the C runtime avoids (its parse result is the tree). The
gap concentrates in (a) the snapshot copy, (b) per-node side-table copying,
(c) no subtree pooling.

## Memory profile (PERF-04)

| fixture | arena nodes | source bytes/node | gc.alloc.rate.norm | heap delta (probe) |
| --- | --- | --- | --- | --- |
| json-10k | 4,925 | 2.2 | 6.0 MB/op | ~11 MB |
| json-100k | 29,938 | 2.3 | 31.8 MB/op | ~30 MB |
| json-1m | 463,364 | 2.3 | 537 MB/op | ~286 MB |
| java-single | 5,024 | 1.6 | 7.1 MB/op | ~16 MB |

Allocation is a stable ≈550–600 bytes of garbage per source byte (≈270 B per
arena node): the arena's parallel int columns double geometrically, and every
parse throws the whole arena + snapshot away. A recycled arena pool (C uses a
subtree pool) is the single largest optimization candidate.

## What worked (items 2–11 design choices that held up)

- **Parallel `int[]` arena columns, no per-node heap objects** — the core
  decision from the architecture analysis; nodes cost ~16–60 B in columns vs
  hundreds of bytes as objects, and the hot parser path is allocation-free
  apart from column growth.
- **Linear-stack fast path inside GLR** — unambiguous inputs never build
  graph versions; JSON/Java single-version parses stay on the same code path
  the LR design used.
- **Small-parse-table + large-state split, blob v4** — TS-sized tables (5,986
  states) load in milliseconds and fit `u16`/`u32` encodings.
- **Byte-level DFA lexer with keyword-capture fallback** — no per-character
  object churn; lexing does not appear as a hotspot in any profile taken
  during items 4–10.

## What did not work / what was rejected

- **Per-node heap allocation** (early item 3 sketch) — rejected before landing
  in favor of the arena; the ast-grep Rust rewrite's write-up reached the same
  conclusion.
- **Eager arena reservation** — item 3 tried pre-reserving capacity by input
  size; the geometric growth with free-list reuse measured the same or better
  and the reservation was dropped.
- **Position-tracking columns on subtrees** (item 9) — removed as write-only;
  the edit-intersection reuse gate needs no per-node version data.

## Recorded optimization candidates (not scheduled)

1. Recyclable arena/tree pool to cut the ≈550 B/byte garbage rate (largest
   single win; would also absorb the snapshot copy for same-thread reuse).
2. Snapshot elision or copy-on-write for the parse-result tree.
3. `toSexpString` char[] reuse instead of StringBuilder growth (visible in the
   serialize share of the op).

## JNI embedded runtime vs pure Java (migration verification, 2026-09-10)

JMH (`JniVsPureBenchmark`, 8.2 KB TypeScript source, work = parse + full tree
walk, 1 fork, 3×1s warmup, 5×1s measurement, gc profiler):

| runtime | ops/s | gc.alloc.rate.norm |
| --- | --- | --- |
| JNI embedded (io.github.bonede 0.25.3 + tree-sitter-typescript 0.23.2) | 47.26 ±0.54 | **334 KB/op** |
| pure Java (compat layer) | 11.33 ±1.42 | **445 MB/op** |

**JNI is 4.17x faster and allocates ~1334x less.** The allocation asymmetry —
not native-vs-JIT speed — is the dominant root cause:

1. **Dead GLR branch accumulation**: TypeScript forks stack versions on every
   conflict; abandoned fork nodes stay in the parse arena forever (no
   reclamation). A valid 8 KB file churns through hundreds of thousands of
   dead nodes.
2. **Per-parse snapshot deep copy**: every `TSParser.parse` deep-copies the
   reachable tree into the result arena (the parse arena is discarded).
3. **No pooling**: each parse allocates fresh arena columns, side tables and
   pop-slice ArrayLists; C reuses subtree/node pools.
4. Cursor-mediated node access allocates a `TSTreeCursor` + navigation state
   per `childCount()`/`child(i)` call during tree walks.

Optimization candidates in expected-impact order: (1) arena reuse/pooling +
dead-branch reclamation, (2) slice-ArrayList pooling in the GLR pop paths,
(3) node-access without cursor materialization.

Landed so far:
- **Snapshot elision** (`TSTree.adopt`): fresh parses transfer the parse arena
  instead of deep-copying the reachable tree. Invisible on the 8 KB benchmark
  (the copy was ~200 KB of the 445 MB — the parse itself dominates), but saves
  a full-tree copy per parse for large inputs.
- **Allocation-free lexer decode** (`decodePacked`): the scan hot loop no
  longer allocates an `int[2]` per decoded codepoint.

The JMH measurement itself settled the JNI question with rigor: identical
work through both APIs, gc profiler attached, 5 forks-measured iterations —
the 4.17x ratio and the 1334x allocation asymmetry are not measurement
artifacts. The allocation asymmetry (445 MB/op for an 8 KB file ≈ 54 KB
allocated per input byte) pins the gap on GLR bookkeeping rather than the
DFA/parse core: every reduce materializes parent nodes for abandoned forks
and every pop allocates slice ArrayLists. C has the same algorithmic shape
but reclaims abandoned subtrees through its subtree pool, keeping live
allocation proportional to the tree, not to the search.

Re-measured after the recovery-correctness fixes (per-version scanner state,
absolute padding, python grammar): JNI 40.3 ops/s, pure 9.7 ops/s.

**Subtree record caching (roadmap item 17, landed 2026-09-10):** the arena
now builds the immutable `Subtree` record once per node at `allocate` time
and caches it in a dedicated column; `get(id)` returns the cached record
instead of allocating a fresh 16-field record per call. Measured impact:
pure throughput **11.3 → 16.5 ops/s (+46%)**, JNI-vs-pure gap **4.17x →
2.66x**. The remaining 433 MB/op is the GLR search itself (pop-slice
ArrayLists, Iter objects, column growth for dead branches) — reducing it
requires structural pooling/reclamation.

| runtime | ops/s | gc.alloc.rate.norm |
| --- | --- | --- |
| JNI embedded | 43.8 ±3.1 | 334 KB/op |
| pure Java (with Subtree cache + cons-cell pop + condense NONE fix) | **16.3 ±0.9** | 434 MB/op |
| pure Java (with Subtree cache only) | 16.5 ±0.9 | 433 MB/op |
| pure Java (before optimizations) | 11.3 ±1.4 | 445 MB/op |

The cons-cell pop optimization replaces the per-fork `ArrayList` copy with a
16-byte `SubList` cell — the throughput gain is from reduced GC pressure
(object count, not bytes), and the alloc bytes stay roughly the same because
the cons cells total ≈ the ArrayList backing arrays they replace. The 434
MB/op floor is the GLR search itself: dead-branch nodes in arena columns,
side-table growth, and pop-path traversal are inherent to the algorithm when
operating on ambiguous grammars like TypeScript.

## Item 17 closure (2026-09-11): scanner validation caching + allocation breakdown

Phase 1 measurement (JFR `jdk.ObjectAllocationSample` over the JniVsPureBenchmark
workload, 8 KB TypeScript source, plus `ThreadMXBean.getCurrentThreadAllocatedBytes`
for exact per-op totals) overturned the recorded attribution: the ≈433 MB/op was
NOT dead-branch arena growth. **≈96% was `ScannerProgram.validate` re-running per
external token scan** (`ScannerVM.run` invoked the full HashMap-based
stack-discipline/jump-target BFS on every lookahead). The GLR-private terms the
pooling plan targeted were all below 1% (GSSNode ≈0.2%, side-table growth
≈0.05%; the parse of this input is essentially linear — arena 5,205 nodes all
live, 5,205 GSS nodes, no version explosion after the item 15 ordering fixes).

Landed: `Language.validatedScannerProgram()` memoizes the structural validation
(the program bytes are immutable per language); `ScannerVM.run` no longer
re-validates per scan.

Measured impact (JMH, gc profiler, same machine/method as above):

| runtime | ops/s | gc.alloc.rate.norm |
| --- | --- | --- |
| JNI embedded | 39.2 ±18.3 | 334 KB/op |
| pure Java (before) | 16.5 ±0.4 | 433 MB/op |
| pure Java (after) | **42.5 ±11.0** | **86 MB/op** |

The pure runtime reaches JNI parity on this benchmark (error bars overlap) and
the allocation gap narrows from ~1300x to ~250x. Parse-only allocation is
8.1 MB/op (`ThreadMXBean` exact measurement).

### Remaining allocation terms (post-fix, measured)

| term | share | disposition |
| --- | --- | --- |
| tree-walk cursor materialization (compat `TSNode.getChild` builds a `TSTreeCursor` + `TreeNavigator` + descent collections per child) | ≈77–90 MB/op, now dominant | successor optimization candidate (perf-tuning candidate #3, "node access without cursor materialization") |
| arena columns + Subtree record cache (final ≈1.2 MB, ≤2.5 MB with doubling) | small | inherent; arena recycling needs a tree-release API |
| GSS nodes / side tables / slices | <1% | pooling adjudicated not worth landing (below the 5% threshold the plan set) |

### Dead-branch reclamation: successor design adjudication

Reclaiming abandoned GLR subtrees into the arena free list is unsafe without
per-subtree refcounts. Reference paths that pin a subtree id (complete
enumeration):

1. **Parent child slots** — reduce consumes popped links into new parents; the
   losers of the `shouldReplace` selection are garbage while their children are
   shared with the winner, so reclamation must count per arena slot, not per
   parent.
2. **Merged GSS links** — `addLink` dedup keeps one id reachable from several
   paths, and its dynamic-precedence replacement can orphan the previous
   equivalent subtree.
3. **In-flight slices** during a reduce.
4. **Chain-container hidden nodes** for >8-children productions.
5. **The arena free list amplifies any wrong free**: a freed id is immediately
   reused by `allocate`, so stale references silently point at unrelated nodes.
6. **Non-paths (safe)**: summary entries, `PausedToken`, and the cached token
   carry no subtree ids; incremental reuse holds a separate old arena and copies
   reused leaves into the new one (no cross-arena ids); post-parse consumers
   (TSTree/TSNode/serialization/query) live after adopt, so parse-time
   reclamation assumes single-threaded parse ownership.

C solves this with `Subtree` refcounts + `stack_node` refcounts; the successor
design is a refcount column in `SubtreeArena` plus release-on-GSS-node-release,
a dedicated correctness effort (any missed reference corrupts parses silently
via the free-list amplifier) rather than a perf patch. Recorded as the successor
design; not scheduled.

## Perf closure (2026-09-13): allocation-free hot paths — all four C-vs-Java benchmarks within 3x

Plan: `ai-dev/plans/nop-treesitter/2026-09-12-1000-1-perf-cursor-and-benchmarks.md`
(item 17's user-directed successor). Three hot paths made allocation-light:

1. **Tree walk** — `TSTreeCursor` frames moved from per-node `Frame` records and a
   boxed `List<Integer>` ancestor chain to parallel int arrays with `resetTo`
   reuse; child location moved from per-step `ChildRef`/`int[2]` to the
   primitive `TreeNavigator.locateChild` scan; `TSNode` structural accessors
   reuse a depth-guarded thread-local cursor (`cursor()`/`TSTree.cursor()` stay
   fresh by design — the query engine holds cursors across child accessors).
   `TSTreeCursor.entryVisible` now treats container-chain frames as invisible
   and resolves builtin ERROR/ERROR_REPEAT ids through `symbolVisibleOrBuiltin`
   (both previously threw `symbol id out of range` from `depth()`/`gotoParent`).
   A parse-time infinite loop was fixed at the root cause:
   `SubtreeArena.allocateChildren` had filled child slots from the scratch
   array's capacity instead of the child count, feeding stale ids into the
   child graph.
2. **Render** — `toSexpString` sizes its builder from the arena node count;
   per-child metadata goes through one reused `Meta` holder instead of a
   `ChildMeta` record per child; `Language.fieldIdAt` walks the master field
   table in place.
3. **Arena growth** — first-parse adaptive pre-reservation: `Language`
   keeps the max observed nodes-per-source-byte ratio (≥ 4 KB inputs only);
   the next parse reserves `min(len × ratio × 1.25 + 64, 2^21)` slots (≈66 B
   per slot worst-case waste, capped ≈140 MB of columns); the first parse and
   small inputs stay at the tiny initial capacity so geometric growth remains
   the backstop. GLRParser's three side tables are sized once from
   `arena.capacity()` instead of shadowing the doubling chain.

Measured (same-session back-to-back, `bench/run-c-reference.sh`, 2026-09-13;
absolute numbers carry ±60% error bars — ratios are the verdict):

| benchmark | Java (before → after) | C (same session) | ratio after |
| --- | --- | --- | --- |
| json-10k | 1016 → 1142 ops/s | 1371 ops/s | **1.20x** |
| json-100k | 160 → 179 ops/s | 225 ops/s | **1.26x** |
| json-1m | 7.8 → 9.2 ops/s | 14.4 ops/s | **1.56x** |
| java-single | 408 → 404 ops/s | 1115 ops/s | **2.76x** |

All four item-13 benchmarks are within the 3x-of-C target on the final HEAD —
including the two rows (json-1m, java-single) that were over target when item 13
closed. Same-session ThreadMXBean (`bench/AllocProbe`): json-100k parse+render
23.0 → 16.6 MB/op (−28%); ts-8kb walk 75.8 MB → **153 KB/op** (−99.8%).
JNI-vs-pure (JMH): pure 42.5 → **101.4 ops/s** vs JNI 45.2 — the pure runtime
now exceeds JNI throughput by ~2.2x on parse+walk; allocation 86 MB → 3.8 MB/op.

Remaining candidates (not blocking, measured residuals): GLR reduce-path
`List<Integer>` boxing and per-lex `LexOutcome`/`Token` objects (~12% + ~5% of
json-100k parse allocation, JFR shares); per-subtree refcount reclamation stays
with the successor design below; json-1m's remaining 1.56x is dominated by
parse-side per-node work, not allocation.
