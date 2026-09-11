# 16 GLR Allocation Reduction (roadmap item 17)

> Plan Status: active
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

Status: planned
Targets: test-scope probe (JFR allocation-site profiling or scratch
instrumentation), daily log

- Item Types: `Proof`

- [ ] Build a lightweight accounting probe: for one TypeScript JMH-shaped
      parse, record arena final capacity (bytes) and live/dead node split via
      the public TSTree.arena() accessors; the GLR-private terms (GSS nodes
      incl. link arrays, side tables, pop-slice/Iter/SubList allocations,
      Integer boxing of subtree ids) via JFR allocation-site profiling or a
      temporary instrumented run whose scaffolding is removed after
      measurement (disposition recorded in the daily log). Include the
      benchmark's tree-walk term (compat TSNode/TSTreeCursor allocations).
      Record the per-term share of the ≈434 MB/op.
- [ ] Re-run `JniVsPureBenchmark` on the current HEAD for an up-to-date
      ops/s + gc.alloc.rate.norm baseline (the recorded 434 MB/op predates the
      item 15 fixes).
- [ ] Record numbers in the daily log; decide which pooled terms are worth
      landing (any term ≥ ~5% of the total).

Exit Criteria:

- [ ] Per-term breakdown table in the daily log (term → bytes/op → share).
- [ ] Current-HEAD JMH baseline recorded (ops/s, alloc rate).
- [ ] Landing decision recorded (which terms Phase 2 targets).
- [ ] No owner-doc update required in this phase (Phase 3 owns the
      perf-tuning.md update).

### Phase 2 - Safe pooling of parse-private structures

Status: planned
Targets: `GLRParser.java` (pool hooks), focused measurement

- Item Types: `Fix`

- [ ] Implement a ThreadLocal pool for the parse-private structures Phase 1
      identified (GSSNode objects + link arrays, side tables, versions array,
      gss array): acquired at parse start, returned in try/finally (parse
      errors must not leak the borrow), with the pool cap decided per-array
      LENGTH (oversized arrays are dropped to GC, not retained).
- [ ] Reset semantics: returned structures must be restored to fresh
      allocation semantics (GSSNode errorCost/nodeCount/dynPrec zeroed and
      errorDiscontinuity=false, linkCount=0 + NO_LINK links; side tables
      zeroed — chain-container ids from buildChain are read via
      subtreeDynPrec/subtreeErrorCost without recordSubtreeSize writes;
      Version defaults null/0). The current code relies on Java zero
      defaults; pooling must not leak any of it.
- [ ] Thread-safety: the pool must not share mutable state across concurrent
      parses (ThreadLocal or equivalent); document the chosen mechanism in
      code structure (no comments needed — the mechanism should be evident).
- [ ] Re-run JniVsPureBenchmark + the full module suite; record the delta.

Exit Criteria:

- [ ] Focused equivalence test: same-thread back-to-back parses (second parse
      immediately after a first, pool reused) produce trees byte-identical to
      fresh single parses — including an input that triggers >8-children
      buildChain paths and an error-recovery input (the three zero-default
      dependency traps: GSSNode accumulators/errorDiscontinuity, chain-container
      side-table entries, Version defaults).
- [ ] Pooling implemented for the terms selected in Phase 1 (or the phase
      closes with a recorded decision that no term was worth pooling, with
      numbers).
- [ ] `JniVsPureBenchmark` re-measured; delta recorded in the daily log and
      perf-tuning.md. The pool cap policy (per-array length, oversized arrays
      dropped) and the per-thread retention bound are recorded.
- [ ] `./mvnw test -pl nop-treesitter` green (all corpus suites unchanged).
- [ ] No semantic change: corpus pass rates identical to baseline.
- [ ] `ai-dev/logs/` entry updated.

### Phase 3 - Dead-branch reclamation adjudication + docs + closure

Status: planned
Targets: `perf-tuning.md`, roadmap, plan closure

- Item Types: `Decision`, `Follow-up`

- [ ] Write the reference-graph adjudication into perf-tuning.md: which
      reference paths (parent child slots, merged GSS links, in-flight slices)
      make link-subtree freeing unsafe without per-subtree refcounts; sketch
      the C-parity design (refcount column, release on GSS-node release,
      arena free-list reuse) as successor work with its risk profile.
- [ ] Roadmap item 17 write-back with measured numbers. The write-back must
      say "safe pooling subset landed; dominant dead-branch term adjudicated
      to a successor refcount design" — item 17 closes on the honest state,
      not on "allocation reduction complete". The write-back also fixes the
      roadmap's 2.56x figure (→ the measured JNI-vs-pure ratio, ≈2.69x on the
      recorded run).
- [ ] Daily log entry; doc link checker; independent closure audit with
      evidence below.

Exit Criteria:

- [ ] perf-tuning.md carries the final per-term table, the landed-pooling
      results, and the reclamation adjudication (landed or successor with
      design sketch).
- [ ] Roadmap item 17 status written back.
- [ ] Independent subagent closure audit completed; evidence below.

## Closure Gates

- [ ] Per-term allocation breakdown measured and recorded.
- [ ] Safe pooling landed (or explicitly adjudicated not-worth with numbers).
- [ ] Dead-branch reclamation adjudicated to the successor refcount design
      with the concrete reference-graph rationale — not a vague "future
      work".
- [ ] No corpus regression; full module suite green.
- [ ] perf-tuning.md + roadmap synced with live state.
- [ ] Independent closure audit evidence recorded below.
- [ ] `./mvnw test -pl nop-treesitter` green.

## Deferred But Adjudicated

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

Status Note: (pending)
Completed: (pending)

Closure Audit Evidence:

- Reviewer / Agent: (pending)
- Audit Session: (pending)
- Evidence: (pending)
