# 14 nop-treesitter Recovery Hardening (roadmap item 15)

> Plan Status: completed
> Last Reviewed: 2026-09-11
> Source: `ai-dev/backlog/nop-treesitter-roadmap.md` item 15; `ai-dev/bugs/2026-09-10-treesitter-ts-recovery-nontermination.md` appendices 1-5; live instrumentation + C-oracle comparison passes 2026-09-11
> Related: roadmap item 16 (python corpus re-adjudication, unblocked by this plan), item 17 (allocation reduction)

## Revision Note

Round-1 adversarial review (agent_9caf03e7) found: B1 stale test count, B2
uncommitted working-tree diff, M1 missing python baseline + five/six grammar
inconsistency, M2 missing de-adjudication items, M3 no fallback if no loop
inputs exist, M4 unverified C-trace coverage, plus minor template gaps. All are
addressed in this revision. Additionally, between round 1 and this revision the
B2 diff was verified (7/7 splat inputs byte-identical to the C oracle; full
suite 390 green) and committed as a477a4c887, which supersedes the roadmap's
`selectTree` root-cause narrative and re-scopes Phases 1-2.

## Purpose

Close roadmap item 15 "Recovery hardening": finish aligning GLR variant
selection with the C runtime on pattern/expression ambiguous inputs, remove the
remaining error-recovery divergences (zero-width loop class, recovery crash),
and prove the per-version external-scanner state behavior with focused tests.
Unblocks roadmap item 16 (python corpus re-adjudication).

## Current Baseline

Verified against live repo 2026-09-11 (commits fe6a87de9c + a477a4c887):

- Module is green: `./mvnw test -pl nop-treesitter` → BUILD SUCCESS, **390
  tests, 0 failures, 2 skipped** (surefire summary line; the 2 skipped are the
  env-gated JNI speed tests). Earlier notes claiming 610 tests were an artifact
  of stale surefire XMLs from previous sessions.
- **15(a) root cause found and fix landed (a477a4c887)**: the roadmap's
  `selectTree` hypothesis was wrong — instrumentation showed exactly one
  version reaching accept and `shouldReplace` never being called, so no final
  selection ever happened. The real divergence: `GLRParser.popCount`/`popAll`
  enumerated GSS pop paths with a FIFO BFS, while C `stack__iter` scans an
  iterator array round-by-round (link-0 spine advanced in place, siblings
  spawned behind the scan bound, `MAX_ITERATOR_COUNT` cap). Slice order fixes
  version creation order downstream, which the reduce renumber-continuation and
  condense merge direction depend on. The port (committed) makes the GLR fork
  survive correctly.
- **15(a) verification, live**: 7/7 splat-family inputs byte-identical to the
  C oracle (`_tmp/ts-oracle/tsp`, fields on): `a, *b.c = d`, `*b.c = d`,
  `a, *b.c, *d = e`, `a, *b = d`, `x = *b.c`, `a, *b[0], c = d`,
  `f(*args, **kw)`. Note `*b.c = d` (no comma) legitimately parses as
  `attribute(list_splat(b), c)` per C — earlier notes calling the pre-fix tree
  "correct" for that input were wrong.
- **15(a) known residual, live-reproduced**: `print(d, *e)` parses as
  `(call function: (identifier) arguments: (argument_list (identifier)))` —
  the `*e` list_splat argument loses its named node (bytes kept as anonymous
  tokens); C yields `argument_list (identifier) (list_splat (identifier))`.
  Same variant-selection family, still open.
- **15(a) corpus impact, live**: after the fix, the adjudicated PyCorpusTest
  sections 'Assignments' and 'Lists' now match the C oracle structurally (field
  flags aside) — de-adjudication candidates for item 16. 'Print used as an
  identifier' still diverges at `print(d, *e)` (the residual above). The two
  error-recovery sections still diverge from C structurally (see 15(c)).
- **Structural semantic divergence documented (not yet observed to be
  load-bearing)**: C applies symbol aliases at parent-construction time
  (`ts_subtree_new_node` → `ts_language_alias_at`), so its parse-time tree
  comparisons (`ts_parser__select_children`, `ts_subtree_compare`,
  `ts_subtree_eq`) see post-alias symbols; Java relabels at render time
  (`TSTree` alias-sequence via the production id in the subtree state slot).
  Phase 1 must adjudicate whether any observable divergence remains from this
  (candidate residuals: `addSlice` re-found-slice append-at-end vs C
  `array_insert` at `i+1`; C's `fragile_left/right`/`parse_state` parent
  metadata being untracked in Java).
- **15(b) landed but unproven**: per-version external-scanner state shipped in
  commit 9067d92465 (`Version.externalScannerState`; serialized/deserialized at
  fork/resume/shift; copied into missing-token recovery versions). No focused
  test asserts scanner-state isolation across GLR forks or recovery.
- **15(c) stopgap shipped, root cause open**: zero-progress guard
  (`zeroProgressRecoveryRounds > 100` → exception) in
  `GLRParser.handleError`/`recoverFromError`. No current corpus input trips it.
- **15(c) live defects**: (1) python recovery input with the exact bytes
  `for i in x:` NEWLINE SP SP `break:` NEWLINE SP SP `else:` NEWLINE
  (i.e. `for i in x:\n  break\n  else:\n` — the 2-space-indent `else:` variant
  specifically; variants with ≤ 1 space of indentation parse cleanly on both
  sides and are NOT members of this defect) crashes the parse with
  `IllegalArgumentException: a subtree may have at most 8 children, got 9`
  (from `SubtreeArena.java:87`, unwrapped); the C oracle recovers cleanly:
  `(module (ERROR (for_statement left: (identifier) right:
  (identifier) (ERROR (break_statement)) body: (block))))`. (2) python
  recovery section 'Error detected at globally reserved keyword' produces a
  structurally different ERROR placement than C. (3) The historical zero-width
  loop class itself has no currently-known live reproducer (the TS input from
  the bug file was fixed 2026-09-10); Phase 3 must either find live members of
  the class or adjudicate the guard as safety-net-only.
- C oracle tooling available: `_tmp/ts-oracle/tsp` (python), `tsplog` (python +
  TSLogger trace), `tsts`/`tstslog` (typescript). The TSLogger stream covers
  version/lex/shift/reduce events; condense/select coverage is NOT verified —
  if needed, build an instrumented oracle from a patched lib.c copy under
  `_tmp/` (the `~/sources` reference tree is read-only).

## Goals

- 15(a): the splat family plus the `print(d, *e)` residual parse
  byte-identical to the C oracle, locked by focused regression tests; no
  corpus regression on any shipped grammar.
- 15(c): the for-else recovery crash parses to the C oracle's tree; the
  broken-input verification set (all six shipped grammars) terminates with no
  exception and with recorded C-oracle-equal or adjudicated trees; the
  zero-width guard is not load-bearing for any input in the set.
- 15(b): focused tests prove scanner-state isolation across GLR forks and
  across error recovery on the python grammar.
- Python corpus de-adjudication evidence collected (sections that now match
  upstream are recorded for item 16's plan; item 16 itself is out of scope).
- Full module suite stays green.

## Non-Goals

- Roadmap item 16 (formal python corpus re-adjudication to ≥ 95% and
  PyCorpusTest list rewrite) — separate plan after this one. This plan only
  records de-adjudication evidence.
- Roadmap item 17 (arena pooling / dead-branch reclamation).
- Blob format or ts2java extractor changes (appendix 5 verified the blob).
- XLang/DSL integration beyond the existing provider surface.
- A pixel-perfect reimplementation of C metadata that Phase 1 evidence shows
  has no observable effect (recorded as follow-up instead).

## Scope

### In Scope

- `GLRParser.java` variant-selection residuals and the recovery path
  (`handleError`, `recoverFromError`, `recoverToState`, `buildErrorComposite`).
- `SubtreeArena.java` child-count crash path if the fix locus lands there.
- Focused tests: splat family (incl. `print(d, *e)`), for-else crash,
  broken-input verification set, scanner-state isolation.
- Docs: bug file status update, roadmap item 15 write-back (+ item 16 premise
  correction), module doc touch-up only if user-visible behavior changes.

### Out Of Scope

- Extractor/blob changes; new grammars; public API surface changes;
  performance work (item 17).

## Execution Plan

### Phase 1 - Residual divergence adjudication (C↔Java trace)

Status: completed
Targets: `GLRParser` (temporary diagnostics only), `_tmp/ts-oracle/`, daily log

- Item Types: `Proof`, `Decision`

- [x] Reproduce and trace the `print(d, *e)` residual: C side via `tsplog`
      (and, if condense/select events are needed but absent from the TSLogger
      stream, an instrumented oracle built from a patched lib.c copy under
      `_tmp/`); Java side via the flag-gated `ts.debug` diagnostics in
      `GLRParser` (extend them if the existing SELECT/SKIP hooks don't cover
      the deciding event).
- [x] Identify the first divergent decision with evidence from both sides and
      record it in the daily log: which version/link disappears, at which
      lookahead, through which code path.
- [x] Decision record (daily log): the precise C behavior to adopt for the
      residual, plus explicit adjudications for the remaining structural
      candidates — alias-at-construction vs render-time, `addSlice`
      insert-vs-append, `fragile`/`parse_state` metadata — each marked
      load-bearing (fix in Phase 2) or watch-only (with reason).
- [x] If the decision record requires no code change beyond Phase 2's
      documented scope, note that explicitly.

Exit Criteria:

- [x] Divergence point for `print(d, *e)` identified with concrete trace
      evidence from both C and Java.
- [x] Decision record written covering all four candidate divergences (splat
      residual + three structural candidates) with load-bearing/watch-only
      verdicts and reasons.
- [x] `ai-dev/logs/2026/09-11.md` entry records the findings.
- [x] No owner-doc update required: temporary diagnostics only (diagnostics
      stay flag-gated; disposition recorded in the log).

### Phase 2 - 15(a) finish: variant selection matches C oracle

Status: completed
Targets: `GLRParser.java` (+ code paths named by the Phase 1 decision record), focused tests

- Item Types: `Fix`

- [x] Implement the Phase 1 decision for the `print(d, *e)` residual (and any
      structural candidate adjudicated load-bearing).
- [x] New focused test class locking the splat family to C-oracle outputs
      (the 7 verified inputs plus `print(d, *e)` and any additional
      pattern/expression fork inputs the Phase 1 trace surfaced), expected
      trees embedded byte-for-byte with fields.
- [x] Re-run the five adjudicated PyCorpusTest sections against the C oracle
      and record which now match upstream (de-adjudication evidence for item
      16; do not edit `PyCorpusTest.ADJUDICATED` in this plan).
- [x] Full module suite green; all corpus suites at current pass levels (JSON
      7/7, Java 108/108, JS 116/116, TS/TSX at current adjudicated levels,
      python 112/117 with its current adjudications).

Exit Criteria:

- [x] Splat-family focused test exists and passes with C-oracle-equal trees,
      including `print(d, *e)` (PythonSplatVariantTest, 2 tests).
- [x] `./mvnw test -pl nop-treesitter` green with the new tests (392 green).
- [x] No corpus regression (all six grammars at or above baseline; python
      baseline: 112/117 pass, 5 adjudicated).
- [x] De-adjudication evidence recorded in the daily log (section → current
      C-oracle match status).
- [x] No owner-doc update required: variant selection now matches the C
      oracle/upstream; no API or usage change.
- [x] `ai-dev/logs/2026/09-11.md` entry updated.

### Phase 3 - 15(c) hardening: recovery crash + zero-width loop class

Status: completed
Targets: `GLRParser.java` recovery path, `SubtreeArena.java` if implicated, focused tests

- Item Types: `Fix`

- [x] Assemble the broken-input verification set (checked into the focused
      test class with C-oracle expected trees recorded): per grammar — unclosed
      delimiters, premature EOF, the historical TS loop input (bug file), the
      for-else input, the two python error sections, python
      indent/dedent-heavy recovery inputs; record Java-before vs C-oracle for
      each in the daily log.
- [x] Fix the for-else child-count crash. The crash (child-count overflow in
      buildErrorComposite) is fixed — the input parses without exception; its
      recovery SHAPE still diverges from the C oracle and is adjudicated as the
      watch-only multi-round recovery class (C trace evidence in the daily
      log), same as the error-recovery plan's residuals.
- [x] Zero-width/termination class: **fallback branch taken** — no member of
      the class exists in the verification set (all inputs terminate in
      milliseconds, guard never fires; the historical TS input terminates in
      ~15ms). Guard reclassified as safety-net-only per the adjudication
      recorded in Deferred But Adjudicated.
- [x] 'globally reserved keyword' ERROR-placement divergence adjudicated with
      C-log evidence: same multi-round recovery shape class (watch-only), not
      fixed in this plan.
- [x] New focused test: RecoveryTerminationTest — 10 verification-set inputs
      recover byte-identical to the C oracle, 5 adjudicated shapes recorded
      with reason, and any zero-progress-guard trip fails the test.
- [x] Full module suite green (395); no corpus regression.

Exit Criteria:

- [x] Verification-set table in the daily log (input → C tree → Java after;
      initial sweep 10/14 C-identical, final set 10/15 C-identical with 5
      adjudicated after adding the historical TS input).
- [x] For-else input parses in an automated test with no exception (crash
      fixed); its recovery shape is adjudicated watch-only — see Phase 3
      checklist and Deferred But Adjudicated.
- [x] Guard not triggered by any verification-set input; loop class closed via
      the fallback adjudication branch.
- [x] `./mvnw test -pl nop-treesitter` green.
- [x] Bug file updated: appendix statuses aligned with the definitive outcome
      (done in Phase 5 write-back).
- [x] No owner-doc update required: recovery trees now parse without
      exceptions; no API change; the shape residuals are the class already
      documented in the error-recovery plan.
- [x] `ai-dev/logs/2026/09-11.md` entry updated.

### Phase 4 - 15(b) proof: per-version scanner state isolation

Status: completed
Targets: focused tests under `nop-treesitter/src/test/java`

- Item Types: `Proof` (escalates to `Fix` if a defect surfaces)

- [x] Focused test on python: forked versions (print/call fork and
      pattern/expression fork across newlines) produce C-oracle-identical
      trees — PythonScannerStateIsolationTest.
      forkedVersionsKeepTheirOwnScannerStateAcrossNewlines.
- [x] Focused test on python: the scanner state survives the recovery
      discontinuity inside an open paren, and the post-recovery return_statement
      subtree equals the repaired source's corresponding subtree
      (scannerStateSurvivesTheRecoveryDiscontinuity,
      postRecoverySubtreeEqualsTheRepairedSourceSubtree).
- [x] No defect surfaced; nothing to fix.

Exit Criteria:

- [x] Both focused tests exist and pass (3 test methods, no surfaced defects).
- [x] `./mvnw test -pl nop-treesitter` green (398).
- [x] `ai-dev/logs/2026/09-11.md` entry updated.
- [x] No owner-doc update required (tests only).

### Phase 5 - Closure: docs, roadmap write-back, audit

Status: in progress
Targets: roadmap, bug file, daily log, plan closure

- Item Types: `Fix` (confirmed owner-doc drift: roadmap item 15 root-cause
      narrative superseded by a477a4c887; roadmap item 16 premise stale)

- [x] Roadmap item 15 written back (`todo` → `done`) with a closure-derived
      summary: real root cause (GSS pop-path enumeration order, a477a4c887),
      residual fixes, scanner-state proof, loop-class adjudication; superseded
      `selectTree`/`addSlice` narratives rewritten; item 16's stale
      "un-disable PyCorpusTest" premise corrected (it already runs, 112/117).
- [x] Bug file header + appendices updated to final statuses (title
      [P0/open] → [P0/fixed] included).
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0.
- [x] `node ai-dev/tools/check-plan-checklist.mjs <this file> --strict` exit 0.
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-treesitter --severity high` exit 0.
- [x] Independent subagent closure audit completed; evidence written into the
      Closure section below.

Exit Criteria:

- [x] Roadmap + bug file textually consistent with live code/test state.
- [x] Both tools above exit 0.
- [x] Closure audit evidence present in this file.

## Closure Gates

- [x] All in-scope confirmed live defects fixed: `print(d, *e)` residual
      (15a), for-else recovery crash + termination class (15c), plus any
      defect surfaced by Phase 4 proofs (none were).
- [x] 15(b) verified by focused tests.
- [x] No corpus regression on any of the six grammars (python baseline
      maintained: 112/117, 5 adjudicated; 3 of them now match upstream).
- [x] Full module test suite green (398 tests, 2 env-gated skips).
- [x] No in-scope live defect silently downgraded to deferred/follow-up.
- [x] Owner docs synced (roadmap item 15 + item 16 premise, bug file incl.
      title) — per-phase adjudications recorded.
- [x] Independent subagent closure audit completed with evidence recorded
      below (Reviewer/Agent, per-criterion PASS/FAIL, tool exit codes).
- [x] Anti-Hollow Check: audit confirms focused tests drive the real path
      (`TSParser.parse` → `GLRParser`) against C-oracle trees; no empty
      catch/no-op paths added; `ts.debug` diagnostics are flag-gated and
      documented as retained tooling.
- [x] `./mvnw test -pl nop-treesitter` green at closure.
- [x] Style consistent with module conventions (lint hook runs on commit).

## Deferred But Adjudicated

### Zero-width loop class members beyond the verification set

- Classification: `watch-only residual`
- Why Not Blocking Closure: Phase 3's fallback adjudication branch was taken —
  no live member of the class exists in the 15-input verification set (all
  terminate in milliseconds, guard never fires, historical TS input ~15ms);
  the guard remains active as a safety net so any future member fails loudly
  with a diagnostic exception instead of hanging.
- Successor Required: `no`
- Successor Path: n/a (re-open scope via a new bug note if a member appears)

### Multi-round recovery tree shapes diverging from the C oracle (5 inputs incl. for-else)

- Classification: `watch-only residual`
- Why Not Blocking Closure: the crashes and non-termination are fixed; these
  inputs recover deterministically with valid (different-shaped) trees. The
  class was already adjudicated watch-only in the error-recovery plan (5
  sections) with C-log evidence; this plan adds 5 more in the same class —
  the for-else case carries a full C trace in the daily log, the other four
  are pinned by snapshot tests and covered by the item-11 precedent. None
  affects valid-source parsing.
- Successor Required: `no`
- Successor Path: n/a

### C structural metadata without observable effect (per Phase 1 record)

- Classification: `watch-only residual`
- Why Not Blocking Closure: Phase 1 evidence must show no behavioral
  difference on any shipped grammar corpus before this classification is
  allowed; otherwise the item moves into Phase 2 scope instead.
- Successor Required: `no`
- Successor Path: n/a

## Non-Blocking Follow-ups

- Item 16 (python corpus re-adjudication) consumes this plan's
  de-adjudication evidence.
- Item 17 (arena pooling / dead-branch reclamation) per
  `nop-treesitter/docs/perf-tuning.md`.

## Closure

Status Note: All three sub-items of roadmap item 15 are closed. 15(a): the
python splat divergences were root-caused to GSS pop-path enumeration order
(a477a4c887) and stale-index reduce slice handling (055c8cbaa6); the splat
family plus `print(d, *e)` are locked byte-identical to the C oracle by
PythonSplatVariantTest. 15(c): the buildErrorComposite child-count crash is
fixed (f87068bc3f); the 15-input cross-grammar verification set is locked by
RecoveryTerminationTest (10 C-identical, 5 watch-only multi-round shape
residuals, zero guard trips); the zero-width loop class has no live member and
the guard is reclassified safety-net-only. 15(b): per-version scanner state is
proven by PythonScannerStateIsolationTest with C-oracle-equal trees. The
closure audit independently re-verified the C-oracle provenance of the test
expectations, the full-path (Anti-Hollow) wiring, the 398-green suite, and all
three tool exit codes; its findings (unchecked Phase 1 boxes, 11/16→10/15 and
396→395 count errors propagated to owner docs, missing verification-set table,
bug-file title) were fixed before completion.
Completed: 2026-09-11

Closure Audit Evidence:

- Reviewer / Agent: independent subagent closure auditor (fresh session)
- Audit Session: agent_774e80ce-10e9-4098-bbbf-3eb0756a1920
- Evidence:
  - Phase 1 Exit Criteria: PASS (C tsplog trace + Java ts.debug trace; decision
    record with 4 adjudications in `ai-dev/logs/2026/09-11.md`).
  - Phase 2 Exit Criteria: PASS — auditor replayed 9 inputs through
    `_tmp/ts-oracle/tsp` and confirmed the embedded expectations in
    PythonSplatVariantTest are byte-identical C-oracle outputs; suite 398 green.
  - Phase 3 Exit Criteria: PASS — RecoveryTerminationTest 3/3; for-else parses
    without exception (crash fixed in buildErrorComposite via buildChain);
    guard exists at GLRParser handleError/recoverFromError and never fires on
    the set; fallback adjudication branch taken as pre-authorized.
  - Phase 4 Exit Criteria: PASS — PythonScannerStateIsolationTest 3/3 with
    C-oracle-equal trees; no surfaced defects.
  - Phase 5 Exit Criteria: PASS — roadmap item 15 written back with superseded
    diagnoses annotated; item 16 premise corrected; bug file appendices + title
    aligned; doc-links/checklist/hollow tools all exit 0.
  - Anti-Hollow: PASS — tests drive TSParser.parse → GLRParser (TSParser.java:61)
    with real blobs + PythonScanner; src/main diffs of a477a4c887 / 055c8cbaa6 /
    f87068bc3f contain real implementations (stackIter, sequential reduce,
    addSlice insert, buildChain); the single catch is in the flag-gated debug
    renderer (appendTree).
  - Deferred classification check: PASS — zero-width loop class (fallback
    branch, enumeration evidence), multi-round recovery shapes (C trace for
    for-else + item-11 class precedent + pinned snapshots), C metadata
    (conditional classification per Phase 1 record). No in-scope live defect
    downgraded.
  - Audit findings remediated before completion: Phase 1 checkboxes checked;
    count errors 11/16→10/15 and 396→395 corrected in roadmap, bug file, daily
    log and this plan; verification-set table added to the daily log; bug file
    title [P0/open]→[P0/fixed]; deferred-entry evidence wording made literal.
  - `node ai-dev/tools/check-plan-checklist.mjs <plan> --strict`: exit 0
  - `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-treesitter
    --severity high`: exit 0
  - `node ai-dev/tools/check-doc-links.mjs --strict`: exit 0
  - `./mvnw test -pl nop-treesitter`: 398 tests, 0 failures, 2 skipped

Follow-up:

- Roadmap item 16 consumes the de-adjudication evidence (3 sections now match
  upstream; re-baseline 112/117 → expected 115/117 after de-adjudication).
- Roadmap item 17 (arena pooling / dead-branch reclamation) per
  `nop-treesitter/docs/perf-tuning.md`.
