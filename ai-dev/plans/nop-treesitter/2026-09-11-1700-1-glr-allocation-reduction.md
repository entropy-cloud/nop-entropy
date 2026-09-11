# 16 GLR Allocation Reduction (roadmap item 17)

> Plan Status: completed
> Last Reviewed: 2026-09-11
> Source: `ai-dev/backlog/nop-treesitter-roadmap.md` item 17; `nop-treesitter/docs/perf-tuning.md` (434 MB/op floor analysis)
> Related: item 15 (done), item 16 (done), item 13 perf baseline

## Purpose

Close roadmap item 17 "GLR allocation reduction": quantify the remaining
≈434 MB/op allocation breakdown, land the pooling reductions that are safe
within the current architecture, and adjudicate dead-branch reclamation with a
concrete reference-graph analysis rather than an open "future work" note.

## Current Baseline

Verified against live repo 2026-09-11 (HEAD b55f87ca6c):

- Landed already (2026-09-10): Subtree record caching (+46% throughput),
  cons-cell pop paths (+44% cumulative, 11.3 → 16.3 ops/s on the
  JNI-vs-pure JMH), snapshot elision, allocation-free lexer decode.
  JNI-vs-pure gap 4.17x → ≈2.69x (perf-tuning.md: JNI 43.8 / pure 16.3).
  Note: the item 13 "within 3x of C" target belongs to the C-vs-Java
  json/java benchmarks (met on small/mid JSON, recorded miss at json-1m
  3.50x and java-single 4.28x); the JNI-vs-pure gap is a separate migration
  metric. The roadmap item 17 line carries the same 2.56x error — the
  Phase 3 write-back corrects it.
- Remaining allocation: ≈434 MB/op on the 8 KB TypeScript JMH
  (`JniVsPureBenchmark`), attributed in perf-tuning.md to dead-branch arena
  column growth, GSS bookkeeping, side-table growth, and pop-path
  traversal — with no per-term quantification yet.
- Allocation-relevant structures and their ownership:
  - `SubtreeArena` parallel int columns — owned by the adopted `TSTree` after
    parse; cannot be pooled without a tree-release API (GC handles discard).
  - `GLRParser` per-parse state — `GSSNode` objects (each with two `int[8]`
    link arrays), `Version` objects, the three side tables
    (`subtreeSize/subtreeDynPrec/subtreeErrorCost`), `gss`/`versions` arrays —
    all private to the parse, never escape, currently garbage per parse.
  - Pop-path `Slice`/`ArrayList`/`Iter` — short-lived per reduce.
- Dead-branch reclamation without per-subtree refcounts is unsafe. The
  reference paths that pin subtree ids (seed for the Phase 3 adjudication;
  not exhaustive until Phase 3 completes it):
  1. parent child slots (reduce consumes popped links into new parents —
     losers of shouldReplace selection are garbage while their children are
     SHARED with the winner, so reclamation must count per arena slot, not
     per parent);
  2. merged GSS links (addLink dedup keeps one id on several paths; its
     dynPrec replacement can orphan the old equivalent subtree);
  3. in-flight slices during reduce;
  4. chain-container hidden nodes for >8-children productions;
  5. the arena free list amplifies any wrong free: a freed id is immediately
     reused by allocate, so all stale references silently point at unrelated
     nodes;
  6. non-paths (safe): versions' summary entries, PausedToken, cachedToken
     carry no subtree ids; the incremental reuse cursor holds a SEPARATE old
     arena and reused leaves are copied into the new arena — no cross-arena
     ids; post-parse consumers (TSTree/TSNode/toSexpString/query) live after
     adopt, so parse-time reclamation assumes single-threaded parse ownership.
  C solves this with `Subtree` refcounts; the Java arena has no refcount
  column.

## Goals

- Per-term quantification of the remaining allocation (arena columns vs GSS
  bookkeeping vs side tables vs pop-path) on the TypeScript JMH workload.
- Land the safe pooling: GSS nodes + side tables + version arrays pooled
  per-parse-engine (ThreadLocal), since they are parse-private.
- Re-measure JMH; record the delta honestly (positive, neutral, or negative).
- Adjudicate dead-branch reclamation: record the complete reference-graph
  argument for why it requires the C-parity per-subtree refcount design, and
  hand it to successor work with the design sketch in perf-tuning.md (no
  in-plan refcounting implementation).
- Update perf-tuning.md with the final state; roadmap item 17 write-back.

## Non-Goals

- Changing parse semantics or tree shapes (all corpus suites must stay green).
- A tree-release/close public API for arena recycling.
- JNI, FFM, or any native dependency.
- Re-baselining the item 13 benchmark targets.

## Scope

### In Scope

- `GLRParser.java` internals: pooling of parse-private structures.
- `SubtreeArena.java`: only if pooling requires a reset hook.
- `nop-treesitter/docs/perf-tuning.md`: measured results + reclamation
  adjudication.
- Focused measurement probe (test-scope) for the per-term breakdown.

### Out Of Scope

- TSTree/TSParser public API changes; blob/extractor changes; corpus tests'
  semantics.

## Execution Plan

### Phase 1 - Allocation breakdown measurement

Status: completed
Targets: test-scope probe (JFR allocation-site profiling or scratch
instrumentation), daily log

- Item Types: `Proof`

- [x] Build a lightweight accounting probe: JFR ObjectAllocationSample over
      25 parses + ThreadMXBean exact per-op totals + arena live/dead split
      (5,205 nodes, all live); GLR-private term counters added temporarily and
      REMOVED after measurement (disposition in the daily log). Tree-walk term
      measured separately (77–90 MB/op).
- [x] Re-run `JniVsPureBenchmark` on the current HEAD: JNI 43.1 ops/s /
      334 KB/op, pure 16.5 ops/s / 433 MB/op — the 433 MB figure confirmed
      current BEFORE the fix; re-measured after it (42.5 / 86 MB).
- [x] Numbers in the daily log. Landing decision: NO pooling term reached the
      5% threshold (GSSNode ≈0.2% of sampled allocation, side tables ≈0.05%,
      arena final ≈0.3%); the ≥5% term was scanner-program re-validation
      (≈96%) — a caching fix, landed in Phase 2 scope as the data-driven
      outcome; the walk term (≈18%) recorded as successor candidate.

Exit Criteria:

- [x] Per-term breakdown table in the daily log (term → bytes/op → share).
- [x] Current-HEAD JMH baseline recorded (ops/s, alloc rate).
- [x] Landing decision recorded (which terms Phase 2 targets).
- [x] No owner-doc update required in this phase (Phase 3 owns the
      perf-tuning.md update).

### Phase 2 - Safe pooling of parse-private structures

Status: completed
Targets: `GLRParser.java` (pool hooks), focused measurement

- Item Types: `Fix`

- [x] Pooling NOT landed — Phase 1 measured every pooling term below the 5%
      threshold (GSSNode ≈0.2%, side tables ≈0.05%): the reset-semantics and
      equivalence-test requirements were designed for it, but landing pooling
      for <1% terms adds risk without measurable return. The ≥5% term
      (scanner re-validation, ≈96%) was fixed instead via memoized validation
      on Language (57a3ec5b03) — a strictly simpler change with the same goal.
      Pool-cap/reset semantics recorded here as the design for any future
      pooling revisit.
- [x] Reset semantics: not applicable (pooling not landed); the zero-default
      dependency traps are documented in this plan and in the review round 2
      should pooling ever be revisited.
- [x] Thread-safety: N/A — pooling was not landed; the landed memoization
      uses a volatile flag over immutable bytes (benign double-check race,
      verified in closure audit).
- [x] Re-run JniVsPureBenchmark + the full module suite; record the delta.

Exit Criteria:

- [x] Focused equivalence test: the trigger (pool reuse) does not exist since
      pooling was not landed; the full corpus suites (399 tests incl. 6-grammar
      corpora and back-to-back parse sequences) cover the landed scanner-cache
      change.
- [x] Pooling decision recorded with numbers (all terms <1% vs 5% threshold);
      the ≥5% allocation term fixed via scanner-validation memoization.
- [x] `JniVsPureBenchmark` re-measured: pure 42.5 ops/s / 86 MB/op (was 16.5 /
      433); delta recorded in the daily log and perf-tuning.md.
- [x] `./mvnw test -pl nop-treesitter` green (399; all corpus suites unchanged).
- [x] No semantic change: corpus pass rates identical to baseline.
- [x] `ai-dev/logs/` entry updated.

### Phase 3 - Dead-branch reclamation adjudication + docs + closure

Status: completed
Targets: `perf-tuning.md`, roadmap, plan closure

- Item Types: `Decision`, `Follow-up`

- [x] Reference-graph adjudication written into perf-tuning.md: six reference
      paths enumerated (parent child slots with winner/loser sharing, merged
      GSS links + addLink orphaning, in-flight slices, chain containers,
      free-list amplifier, non-paths), C-parity successor design sketched
      (refcount column + release-on-GSS-release) with its risk profile.
- [x] Walk term recorded as the dominant residual (77–90 MB/op) and successor
      optimization candidate.
- [x] Roadmap item 17 write-back with measured numbers, honest phrasing
      ("safe pooling subset adjudicated below threshold; scanner validation
      caching landed; dominant residual adjudicated to successor"), and the
      2.56x figure corrected (recorded run ≈2.69x, now parity 42.5 vs 39.2).
- [x] Daily log entry; doc link checker exit 0; independent closure audit
      with evidence below.

Exit Criteria:

- [x] perf-tuning.md carries the final per-term table, the landed scanner
      fix results, and the reclamation adjudication (successor with design
      sketch).
- [x] Roadmap item 17 status written back (`todo` → `done`).
- [x] Independent subagent closure audit completed; evidence below.

## Closure Gates

- [x] Per-term allocation breakdown measured and recorded.
- [x] Safe pooling landed (or explicitly adjudicated not-worth with numbers).
- [x] Dead-branch reclamation adjudicated to the successor refcount design
      with the concrete reference-graph rationale — not a vague "future
      work".
- [x] No corpus regression; full module suite green.
- [x] perf-tuning.md + roadmap synced with live state.
- [x] Independent closure audit evidence recorded below.
- [x] `./mvnw test -pl nop-treesitter` green.

## Deferred But Adjudicated

### Tree-walk cursor materialization (dominant residual, ≈77–90 MB/op)

- Classification: `optimization candidate`
- Why Not Blocking Closure: it is the walk-side term measured by Phase 1
  (compat `TSNode.getChild` materializes a `TSTreeCursor` + `TreeNavigator` per
  child); already tracked as perf-tuning candidate #3 ("node access without
  cursor materialization"); parse-side allocation dropped below it
  (8.1 MB/op), and throughput reached JNI parity without it.
- Successor Required: `no`
- Successor Path: n/a

### Per-subtree refcounting for dead-branch reclamation

- Classification: `optimization candidate`
- Why Not Blocking Closure: it requires the C-parity refcount design whose
  safety argument (reference paths enumerated in Current Baseline, to be
  completed in Phase 3) makes it a dedicated correctness effort, not a perf
  patch; this plan lands only the safe pooling subset and the design sketch
  (no conditional in-plan implementation of refcounting).
- Successor Required: `no` (if adjudicated) — the design sketch lives in
  perf-tuning.md
- Successor Path: n/a

## Closure

Status Note: Item 17 closes on the honest state: the Phase 1 measurement
overturned the recorded attribution (the 433 MB/op was scanner-program
re-validation per token scan, ~96% — not dead-branch arena growth, which the
item 15 ordering fixes had already made negligible), the ≥5% term was fixed by
memoizing the validation per language (57a3ec5b03; pure runtime 16.5 → 42.5
ops/s, reaching JNI parity at 39.2, allocation 433 → 86 MB/op), the pooling
terms measured below the 5% landing threshold and were adjudicated with
numbers, and dead-branch reclamation plus the walk residual were adjudicated
to successor designs with complete reference-path and measurement evidence.
Completed: 2026-09-11

Closure Audit Evidence:

- Reviewer / Agent: independent subagent closure auditor (fresh session)
- Audit Session: agent_4f409ca2-276c-4c81-99f4-6e1804ef342d
- Evidence:
  - Fix verified real and safe: `Language.validatedScannerProgram()` over
    `private final byte[]` + volatile flag; benign double-check race (worst
    case duplicate validation); all production scan entries go through the
    memoized path; invalid-program rejection still covered by 5 direct
    `ScannerProgram.validate` assertions in ScannerVMTest.
  - Tests: focused scanner suites 37/37 green; corpus spot checks
    Py 115/117, JS 116/116, TS 110/111, TSX 110/111; full module 399 green.
  - Numbers consistent across perf-tuning.md / roadmap / daily log / commits
    (42.5, 39.2, 86 MB, 8.1 MB, 433, ~96%, <1%); roadmap 2.56x error removed.
  - Fallback branch compliance: pooling adjudicated below threshold with
    recorded numbers in plan/log/perf-tuning/roadmap.
  - Deferred classification check: PASS (refcount successor design with
    6-path enumeration; walk residual added with measured numbers).
  - Anti-Hollow: PASS — no empty paths in 57a3ec5b03; ts.stats scaffolding
    confirmed never committed and removed from the working tree.
  - Tools: check-plan-checklist --strict exit 0 (re-run after write-back);
    check-doc-links --strict exit 0; scan-hollow-implementations exit 0.
  - `./mvnw test -pl nop-treesitter`: 399 tests, 0 failures, 2 skipped.

Follow-up:

- successor optimization candidates recorded in perf-tuning.md:
  cursor-materialization-free walk (dominant residual) and per-subtree
  refcount reclamation design (with reference-graph argument);
  no remaining plan-owned work.
