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
