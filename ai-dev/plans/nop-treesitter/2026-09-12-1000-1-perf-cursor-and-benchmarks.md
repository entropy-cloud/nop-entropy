# 17 Tree-sitter Performance Closure: allocation-free hot paths + C benchmark verdict

> Plan Status: completed
> Last Reviewed: 2026-09-13
> Source: `nop-treesitter/docs/perf-tuning.md`; JFR walk+render attribution passes 2026-09-12; adversarial review round 1 (agent_50709c8e): 1 Blocker + 4 Major fixed in this revision; closure audit agent_a85f82b6: 0 Blocker, 1 Major (evidence-tick) + 2 Minor all dispositioned
> Related: roadmap item 17 (done — this is its user-directed successor), item 13 benchmark suite

## Revision Note

Round-1 review found B1: the plan's Phase 2 mechanisms (cursor/scanner paths)
cannot affect the four C-vs-Java benchmarks — their workload is parse +
`toSexpString` (arena-layer render), and json/java have no external scanner.
This revision re-scopes to the three paths the benchmarks actually allocate
on (cursor walk, render, arena growth), fixes the M2 attribution error
(`flattenedIndexOf`, not `flattenedChildCount`, is the O(k²) source), locks
M3/M4/M5 design requirements, and folds in m6–m9.

## Purpose

Close the remaining performance gap of the pure-Java runtime: make the three
allocation-dominant hot paths (tree-walk, tree serialization, arena growth)
allocation-light, re-measure the item 13 C-vs-Java suite on the optimized
HEAD, and iterate within scope until every shipped benchmark is within the
3x-of-C target — or until the residual is site-attributed with a named
blocking mechanism routed to a successor plan.

## Current Baseline

Verified against live repo 2026-09-12 (HEAD 2f8456a89f):

- JNI-vs-pure parity (JniVsPureBenchmark, 8 KB TypeScript parse+walk): pure
  42.5 ops/s vs JNI 39.2 (overlapping error bars); pure allocation 86 MB/op
  (was 433 before the item 17 scanner-validation fix).
- The four C-vs-Java benchmarks (json-10k/100k/1m, java-single) run
  **parse + `toSexpString(false)`** — a different path from the walk. Their
  last measured ratios (2.29x / 2.56x / 3.50x / 4.28x) predate the scanner
  fix and have NOT been re-run on current HEAD. The two over-target rows
  cannot have been improved by the scanner fix (json/java have no external
  scanner); their attribution (perf-tuning.md): arena column doubling, side
  table copies, render allocations, no pooling.
- Allocation sites per path, verified in code:
  1. **Cursor walk** (compat `TSNode.getChild`/`getChildCount`, io.nop
     `TSNode.child`/`childCount`): every access materializes a
     `TSTreeCursor` (TreeNavigator + ArrayList stack). `child(index)` costs
     O(index) `ChildRef` + O(index) `int[2]` via the gotoChild rescan, plus
     the constructor's per-ancestor `flattenedIndexOf` which itself rescans
     from index 0 per ancestor candidate (O(k²) CPU + allocations), plus
     `Integer` boxing at `TSTreeCursor` construction (ids `List<Integer>`,
     line 73) and `Frame` records per descent step. NOTE: `flattenedChildCount`
     is O(1)-per-chain — the quadratic term is `flattenedIndexOf`.
  2. **Render** (`toSexpString`): one unsized `StringBuilder` per call and one
     `ChildMeta` record (alias + fieldName String) per child — ≈1 record per
     node per op, plus per-child String lookups from the field table.
  3. **Arena growth** (parse side): parallel int columns + `Subtree` record
     cache double geometrically (final capacity ≈2x live, all previous arrays
     become garbage); the three GLRParser side tables grow by copy the same
     way. For json-1m (~463k nodes) this is the dominant parse-side term.
  4. **Scanner VM** (external-scanner grammars): `ScannerVM.run` builds a new
     VM per token scan (call stack `int[64]` + operand stack `int[256]` ≈
     1.25 KB per scan) and allocates the `validSymbols` boolean[] per scan.
- Cursor ownership hazard (round-1 M3): `TSNode.cursor()` hands a cursor to
  the caller to hold across time; `TSQueryCursor.matchChildren` holds one
  across `matchSteps` while `child.parent()` runs — up to 3 overlapping
  cursors on one thread. Any thread-local reuse slot MUST NOT include
  `cursor()`/`TSTree.cursor()` (kept permanently fresh), or the query engine
  silently corrupts.

## Goals

- All three hot paths allocation-light: walk via reused scratch cursor +
  primitive child location; render via sized builder + primitive per-child
  metadata; arena growth via source-length-derived pre-reservation (columns,
  record cache, side tables).
- Re-run the full C-vs-Java suite on the optimized HEAD: **all four
  benchmarks within 3x of C**. If a benchmark remains over target after the
  in-scope fixes land: a JFR site table that accounts for the measured
  residual, a mechanism argument per remaining site for why it is out of
  this plan's scope, the blocking mechanism named with its code location, and
  a successor plan path — this is the only accepted alternative closure, and
  it must survive an independent auditor re-deriving the numbers.
- JNI-vs-pure parity maintained; corpus suites unchanged; module green.

## Non-Goals

- Per-subtree refcount reclamation, arena recycling, TSTree release APIs
  (successor design documented in perf-tuning.md).
- Public API signature changes (thread-local reuse only, with re-entrancy
  guards; `cursor()`/`TSTree.cursor()` stay fresh allocations by design).
- Parse semantics, tree shapes, blob format.

## Scope

### In Scope

- `cursor/TreeNavigator.java`: allocation-free child location (mutable
  instance state instead of `int[2]`/`ChildRef` on the hot path; recursion
  carries state linearly, verified compatible); `flattenedIndexOf` /
  `visibleChildCount` / `namedChildCount` made allocation-free; existing
  `ChildRef` API retained for non-hot callers (only internal + TSTreeCursor
  call it).
- `cursor/TSTreeCursor.java`: `resetTo(TSNode)` reuse mode — must rebuild the
  TreeNavigator when the tree differs, reuse Frames (mutable frames or
  parallel int arrays), replace the constructor's `List<Integer>` ancestor
  chain (boxing line 73) and per-ancestor `flattenedIndexOf` allocations with
  primitive scratch, and eliminate the gotoChild/gotoNamedChild descent
  `ArrayList`s (parallel-array choice locks the frame design).
- `TSNode.java` (io.nop): `child`/`namedChild`/`parent`/`childCount`/
  `namedChildCount` via a re-entrancy-guarded thread-local reused cursor
  (guard: depth counter, falls back to fresh cursor on re-entry);
  `cursor()`/`TSTree.cursor()` permanently fresh (query-engine hazard).
- `TSTree.java` render path: `toSexpString` sized builder (from arena size
  estimate) + per-child metadata computed into primitive locals instead of
  `ChildMeta` records.
- `subtree/SubtreeArena.java` + `GLRParser` side tables: pre-reserve capacity
  from the source byte length (observed ≈2.2–2.3 bytes/node) so large parses
  stop paying geometric-doubling garbage; small parses keep current behavior
  (no regression floor for tiny inputs).
- `scanner/ScannerVM.java`: per-thread VM reuse with explicit reset of ALL
  eleven mutable fields (pc, position, tokenStart, markEnd, resultSymbol to
  −1 — the failure sentinel —, result, flag, state, steps, callDepth,
  operandTop; finals program/source/validSymbols rebound to mutable; stack
  CONTENT need not be cleared, only tops) + reset performed at run() entry
  (self-healing after mid-scan exceptions) + reuse/caching of the
  `validSymbols` boolean[] — keyed **per parseState** (the array depends on
  `hasActions(parseState, …)`, NOT on the external lex state alone: several
  parseStates share one extState with different action sets; caching per
  extState would corrupt scans).
- `bench/run-c-reference.sh` additions: compile step
  (`./mvnw -q -pl nop-treesitter test-classes`) so the JMH run uses current
  bytecode; final verdicts stated as same-session back-to-back ratios
  (±60% error bars on absolute numbers).
- `nop-treesitter/docs/perf-tuning.md`: final numbers.

### Out Of Scope

- Per-subtree refcounts, arena recycling, TSTree release APIs; public API
  signature changes; blob/extractor changes; new grammars (follow-on plan).

## Execution Plan

### Phase 1 - Same-session baseline + design lock

Status: completed
Targets: daily log, plan text

- Item Types: `Proof`, `Decision`

- [x] Walk-path JFR attribution on current HEAD (done during drafting;
      numbers in Current Baseline; to be re-confirmed post-fix in Phase 2).
- [x] Compile current bytecode, then run `bench/run-c-reference.sh` BEFORE
      changes: same-session C-vs-Java baseline for all four benchmarks.
      (Measured 2026-09-13 on HEAD 2312ea9eb4 with WIP stashed; the script's
      `test-classes` phase name bug — fixed in the WIP — required running its
      steps manually. Results in `ai-dev/logs/2026/09-13.md`: json-10k 1.38x,
      json-100k 1.41x, json-1m 1.86x, java-single 2.75x.)
- [x] ThreadMXBean per-op totals recorded (parse-only / walk delta / render
      share) for the json-100k and TS-8KB workloads. (Probe
      `bench/AllocProbe.java`: parse-only 22,581 KB/op, parse+render
      22,996 KB/op, render share 414 KB/op, ts-8kb walk 75,803 KB/op.)
- [x] Design decision record in the daily log: per-site replacement table
      (site → mechanism), re-entrancy argument for the thread-local cursor
      (enumerated in-scope nested paths: TSQueryCursor overlap is excluded by
      keeping `cursor()` fresh; compat `getChildByFieldName` uses its own
      cursor — keep fresh or second slot, decided in the record),
      reset-semantics list for the scanner VM, pre-reservation sizing rule
      and small-input floor. The sizing rule MUST resolve the ratio-vs-cap
      tension explicitly: the 2.2–2.3 bytes/node figure is json-specific
      (java ≈1.6, TS ≈1.6), so a global 2.3 ratio under-reserves harmless
      (growth backs it up) but over-reserves node-sparse large inputs
      (10 MB × 2.3 ≈ 304 MB of slots) — decide per-language ratios
      (first-parse adaptive or blob metadata), the cap value, and the
      worst-case waste formula; do NOT settle for a global constant with a
      large cap. (Decided: first-parse adaptive via
      `Language.observedNodesPerByte`, margin 1.25x, cap 2^21 slots; full
      record in `ai-dev/logs/2026/09-13.md`.)

Exit Criteria:

- [x] Same-session pre-change C-vs-Java baseline recorded for all four
      benchmarks.
- [x] Per-site allocation table + design decision record in the daily log.
      (JFR site table recorded; **gate reconciliation applied**: in-scope
      parse-side sites bound the achievable reduction at ≈−24%, so the Phase 2
      json-100k gate is re-derived to −15% — see Phase 2 — and the walk gate
      ≤ 10 MB/op is retained because all its sites are in scope.)
- [x] No owner-doc update required (measurement only).

### Phase 2 - Allocation-light hot paths

Status: completed
Targets: `TreeNavigator.java`, `TSTreeCursor.java`, `TSNode.java` (io.nop),
`TSTree.java`, `compat/TSNode.java`, `SubtreeArena.java`, `GLRParser.java`
(side tables), `ScannerVM.java`

- Item Types: `Fix`

- [x] Cursor path: TreeNavigator primitive child location (no per-step
      records/arrays), TSTreeCursor resetTo + frame reuse + primitive
      ancestor chain (no boxing), descent collections eliminated (parallel
      arrays), io.nop TSNode child accessors (child, namedChild, parent,
      childCount, namedChildCount, nextSibling, prevSibling if present) via
      guarded thread-local reuse (cursor()/TSTree.cursor() excluded by
      design). Two live defects found and fixed en route: (1)
      `SubtreeArena.allocateChildren` filled child slots from
      `children.length` (scratch capacity) instead of `count`, feeding stale
      ids into the child graph — the parse-time infinite loop the inherited
      WIP's `halt(7)` debug probes were chasing; (2) `TSTreeCursor.entryVisible`
      threw `symbol id out of range` for container-chain frames (reserved
      symbol = symbolCount+aliasCount) and builtin ERROR/ERROR_REPEAT frames —
      chains now count as invisible, builtins resolve through
      `symbolVisibleOrBuiltin` (reachable from `depth()`/`gotoParent`/
      `currentFieldId` on any >8-children or recovered tree).
- [x] Render path: sized StringBuilder + primitive per-child metadata (no
      ChildMeta records; field names keep referencing the interned table
      Strings — there is no per-child String allocation today, and none may
      be introduced).
- [x] Arena/side-table pre-reservation from source length (per-language
      ratio per the Phase 1 design record); verify small-input behavior
      unchanged (tiny parses must not over-allocate: cap the reservation
      with geometric growth beyond). Landed as first-parse adaptive
      (`Language.observedNodesPerByte` max-keeper, margin 1.25x, cap 2^21;
      first parse and small inputs stay at the tiny initial capacity);
      GLRParser's three side tables (subtreeSize/DynPrec/ErrorCost) sized
      once from `arena.capacity()`; incremental path wired identically.
- [x] ScannerVM per-thread reuse with the full eleven-field reset + valid
      symbol array reuse (cache keyed per parseState in `Language`).
- [x] Focused equivalence tests: (a) random-access walks fresh-vs-reused
      byte-identical on all six grammar fixtures (chain containers >8
      children, hidden nodes, aliases, extras, fields); (b) back-to-back
      walks on DIFFERENT trees reusing thread-local state; (c) nested /
      re-entrant child access; (d) query-engine regression (TSQueryCursor
      overlap paths); (e) render output byte-identical pre/post on all six
      grammars (pre-change golden files generated on HEAD 2312ea9eb4 via
      `RenderGoldenDump`, committed under
      `src/test/resources/render-golden/`); (f) large-input parse (json-1m
      fixture) byte-identical with pre-reservation. All six live in
      `cursor/CursorReuseEquivalenceTest` — 6/6 green.
- [x] Full module suite green; corpus pass rates identical (405 tests green,
      2 env-gated skips).

Exit Criteria:

- [x] Equivalence tests (a)–(f) exist and pass.
- [x] ThreadMXBean: TS-walk total ≤ 10 MB/op AND json-100k parse+render
      allocation reduced by ≥ 15% vs the Phase 1 same-session baseline
      (22,996 KB/op → ≤ 19,547 KB/op) — each measured, numbers in the daily
      log. **Measured 2026-09-13**: ts-8kb walk **153 KB/op** (baseline
      75,803 — −99.8% vs baseline, ~67x margin under the 10 MB/op gate),
      json-100k parse+render **16,559 KB/op**
      = **−28%** (gate −15%; site-table bound −24% exceeded because the
      sized builder also removed render-path growth copies). Gate
      re-derivation recorded in `ai-dev/logs/2026/09-13.md`.
- [x] JNI-vs-pure JMH re-run: parity maintained, allocation improved.
      (JMH 2026-09-13: pure **101.4 ±0.6 ops/s** vs JNI 45.2 ±1.1 — parity
      exceeded ~2.2x; allocation 86 MB → **3.8 MB/op**. Evidence:
      `ai-dev/logs/2026/09-13.md`.)
- [x] `./mvnw test -pl nop-treesitter` green; no corpus regression.
- [x] Owner docs: `No owner-doc update required` in this phase (Phase 3 owns
      perf-tuning.md).
- [x] `ai-dev/logs/` entry updated.

### Phase 3 - C benchmark verdict + docs + closure

Status: completed
Targets: `perf-tuning.md`, plan closure

- Item Types: `Fix`, `Proof`

- [x] Re-run the full C-vs-Java suite on the optimized HEAD (same-session,
      compiled bytecode): verdict per benchmark vs the 3x target.
      (2026-09-13, `bench/run-c-reference.sh`: json-10k 1.20x, json-100k
      1.26x, json-1m 1.56x, java-single 2.76x — all four within target,
      including the two rows over target when item 13 closed.)
- [x] If any benchmark remains over 3x: JFR/ThreadMXBean site table for that
      workload accounting for the measured residual (sites summing to the
      number), a mechanism argument per remaining site, the blocking
      mechanism named at its code location, and a successor plan created and
      referenced — this is the only alternative closure and an independent
      auditor must be able to re-derive every number. (Not triggered: no
      benchmark remains over 3x. Residual candidates — GLR reduce boxing,
      per-lex LexOutcome/Token — are recorded with JFR shares in
      perf-tuning.md as non-blocking follow-ups.)
- [x] perf-tuning.md updated with final numbers, what worked, remaining
      candidates. ("Perf closure (2026-09-13)" section.)
- [x] `ai-dev/logs/` closure entry; independent closure audit with evidence
      below.

Exit Criteria:

- [x] All four benchmarks ≤ 3x of C on the final HEAD, or the evidence-
      backed floor closure described above (audit-verifiable, no vocabulary
      adjudications). **Met outright: 1.20x / 1.26x / 1.56x / 2.76x.**
- [x] perf-tuning.md reflects the final measured state.
- [x] `./mvnw test -pl nop-treesitter` green at closure. (405 tests, 0
      failures, 0 errors, 2 env-gated skips.)
- [x] Independent closure audit evidence recorded in this file. (Agent
      a85f82b6, session agent_a85f82b6-5af8-4728-ad50-f63cacc7b112 — see
      `## Closure`.)

## Closure Gates

- [x] All four item-13 benchmarks measured on final HEAD (same-session,
      compiled bytecode), each within 3x of C — or the audit-verifiable
      evidence-backed floor closure. (1.20x / 1.26x / 1.56x / 2.76x — met
      outright.)
- [x] JNI-vs-pure parity maintained. (Exceeded: pure 101.4 ops/s vs JNI 45.2.)
- [x] Walk and render allocation residuals measured; walk total ≤ ~10 MB/op
      and json-100k parse+render ≥ −15% vs the same-session baseline
      (re-derived gate; both met, numbers recorded). (153 KB/op; −28%.)
- [x] Equivalence tests (a)–(f) exist and pass. (`CursorReuseEquivalenceTest`
      6/6; golden files under `src/test/resources/render-golden/`.)
- [x] No corpus regression; full module suite green. (405 tests, 0 failures,
      0 errors, 2 env-gated skips.)
- [x] perf-tuning.md synced with final numbers. ("Perf closure (2026-09-13)"
      section.)
- [x] Independent closure audit evidence recorded below.
- [x] `./mvnw test -pl nop-treesitter` green.

## Deferred But Adjudicated

(none — no in-scope item required adjudication; both live defects found during
execution were fixed in commit 070f8a652e, not deferred)

## Closure

Status Note: All three phases landed and independently audited. The two
over-target item-13 benchmarks (json-1m, java-single) came within the 3x-of-C
target outright (final ratios 1.20x / 1.26x / 1.56x / 2.76x, same-session),
walk allocation dropped 75.8 MB → 153 KB/op, and JNI parity was exceeded
(pure 101.4 vs JNI 45.2 ops/s). Both live defects surfaced by the new
equivalence tests (allocateChildren child-slot corruption; entryVisible
chain/builtin symbol range) were fixed at root cause with focused coverage.
Completed: 2026-09-13

Closure Audit Evidence:

- Reviewer / Agent: independent subagent (fresh session, agent_a85f82b6)
- Audit Session: agent_a85f82b6-5af8-4728-ad50-f63cacc7b112
- Evidence:
  - Phase 1 Exit Criteria: PASS (baseline numbers 1.38x/1.41x/1.86x/2.75x and
    ThreadMXBean totals match the daily log; site table recorded)
  - Phase 2 Exit Criteria: PASS (all six anti-hollow traces verified at code
    level with file:line — TSNode scratch cursor + fresh `cursor()`,
    ScannerVM 11-field reset counted, sized render builder + Meta, both parse
    paths pre-reserve and write back node counts, GLR side tables from
    `arena.capacity()`, validSymbols keyed per parseState; equivalence tests
    6/6 reproduced; gates re-verified: −28% vs −15%, 153 KB/op vs 10 MB/op)
  - Phase 3 Exit Criteria: PASS (ratios re-derived: 1.20x / 1.26x / 1.56x /
    2.76x, all ≤ 3x; perf-tuning.md table consistent with plan and log)
  - Defect fixes: PASS (`allocateChildren` count-based fill at
    SubtreeArena.java:128-135; `entryVisible` chain/builtin handling at
    TSTreeCursor.java:446-449; zero `Runtime.halt`/`printStackTrace` probes in
    main sources; ts.debug prints pre-existing and gated)
  - Test reproduction: PASS (`./mvnw -pl nop-treesitter test` → 405 run / 0
    failures / 0 errors / 2 skipped, BUILD SUCCESS)
  - `node ai-dev/tools/check-plan-checklist.mjs <plan> --strict` exit code 0
  - `node ai-dev/tools/scan-hollow-implementations.mjs --module
    nop-treesitter --severity high` exit code 0 (0 findings at all severities)
  - Deferred classification check: PASS — Deferred But Adjudicated empty; no
    in-scope live defect downgraded (residual GLR boxing / LexOutcome shares
    are post-gate optimization candidates recorded in perf-tuning.md)
  - Audit findings disposition: 1 Major (unticked JNI exit criterion —
    evidence existed in the daily log) fixed by ticking with evidence
    reference; 2 Minor (gate-margin wording "495x" corrected to −99.8% vs
    baseline / ~67x under gate; golden-file provenance accepted on the
    indirect evidence chain: dump format byte-identical to test reader format
    plus corpus-render pinning) addressed in this revision
- Audit working-tree check: `git status --short` empty at audit end

Follow-up:

- Optimization candidates (non-blocking, recorded in perf-tuning.md):
  GLR reduce-path `List<Integer>` boxing (~12% of json-100k parse allocation)
  and per-lex `LexOutcome`/`Token` objects (~5%); per-subtree refcount
  reclamation remains with the successor design documented in perf-tuning.md.
- No remaining plan-owned work.
