# 14 nop-treesitter Recovery Hardening (roadmap item 15)

> Plan Status: active
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

- [ ] Reproduce and trace the `print(d, *e)` residual: C side via `tsplog`
      (and, if condense/select events are needed but absent from the TSLogger
      stream, an instrumented oracle built from a patched lib.c copy under
      `_tmp/`); Java side via the flag-gated `ts.debug` diagnostics in
      `GLRParser` (extend them if the existing SELECT/SKIP hooks don't cover
      the deciding event).
- [ ] Identify the first divergent decision with evidence from both sides and
      record it in the daily log: which version/link disappears, at which
      lookahead, through which code path.
- [ ] Decision record (daily log): the precise C behavior to adopt for the
      residual, plus explicit adjudications for the remaining structural
      candidates — alias-at-construction vs render-time, `addSlice`
      insert-vs-append, `fragile`/`parse_state` metadata — each marked
      load-bearing (fix in Phase 2) or watch-only (with reason).
- [ ] If the decision record requires no code change beyond Phase 2's
      documented scope, note that explicitly.

Exit Criteria:

- [ ] Divergence point for `print(d, *e)` identified with concrete trace
      evidence from both C and Java.
- [ ] Decision record written covering all four candidate divergences (splat
      residual + three structural candidates) with load-bearing/watch-only
      verdicts and reasons.
- [ ] `ai-dev/logs/2026/09-11.md` entry records the findings.
- [ ] No owner-doc update required: temporary diagnostics only (diagnostics
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

Status: planned
Targets: `GLRParser.java` recovery path, `SubtreeArena.java` if implicated, focused tests

- Item Types: `Fix`

- [ ] Assemble the broken-input verification set (checked into the focused
      test class with C-oracle expected trees recorded): per grammar — unclosed
      delimiters, premature EOF, the historical TS loop input (bug file), the
      for-else input, the two python error sections, python
      indent/dedent-heavy recovery inputs; record Java-before vs C-oracle for
      each in the daily log.
- [ ] Fix the for-else child-count crash (parse to the C oracle's tree, no
      exception).
- [ ] Fix remaining members of the zero-width/termination class found in the
      set so each terminates with the C oracle's tree. **Fallback branch**: if
      no live member of the zero-width loop class exists in the set, close this
      item by explicit adjudication — guard reclassified as safety-net-only
      (Proof), recorded in Deferred But Adjudicated with the enumeration
      evidence.
- [ ] Fix the 'globally reserved keyword' ERROR-placement divergence if its
      root cause is in our recovery (vs an upstream-grammar quirk); otherwise
      adjudicate with C-log evidence.
- [ ] New focused test(s): every verification-set input parses to its recorded
      C-oracle tree (or recorded adjudicated shape with reason), with no
      exception; the guard throws `TreeSitterException` (GLRParser handleError
      and recoverFromError), so assert no parse of the set throws one.
- [ ] Full module suite green; no corpus regression.

Exit Criteria:

- [ ] Verification-set table in the daily log (input → C tree → Java
      before/after).
- [ ] For-else input produces the C oracle tree in an automated test.
- [ ] Guard not triggered by any verification-set input (or item explicitly
      closed via the fallback adjudication branch).
- [ ] `./mvnw test -pl nop-treesitter` green.
- [ ] Bug file updated: appendix statuses aligned with the definitive outcome.
- [ ] If the crash fix changes user-visible behavior beyond the recovery tree
      (e.g. a new exception path), `docs-for-ai/03-modules/nop-treesitter.md`
      updated; otherwise `No owner-doc update required: recovery trees now
      match the C oracle, no API change`.
- [ ] `ai-dev/logs/2026/09-11.md` entry updated.

### Phase 4 - 15(b) proof: per-version scanner state isolation

Status: planned
Targets: focused tests under `nop-treesitter/src/test/java`

- Item Types: `Proof` (escalates to `Fix` if a defect surfaces)

- [ ] Focused test on python: two GLR versions forked at the same position
      must hold different serialized indentation-scanner states and each
      version's subsequent tokens must reflect its own state; observable via
      the final tree shape(s) (extend `ts.debug` hooks if a stronger
      observation point is needed).
- [ ] Focused test on python: for a broken input, the subtrees produced after
      the recovery point (missing-token insertion / skip strategy) equal the
      corresponding subtrees of a clean reparse of the repaired source —
      observable via tree-shape comparison of the post-error region, not a
      same-source reparse (which would be trivially identical).
- [ ] If either test exposes a defect: fix and record in the daily log.

Exit Criteria:

- [ ] Both focused tests exist and pass (or surfaced defects are fixed and
      covered).
- [ ] `./mvnw test -pl nop-treesitter` green.
- [ ] `ai-dev/logs/2026/09-11.md` entry updated.
- [ ] No owner-doc update required (tests only), unless a Phase 4 fix changes
      documented behavior — then module doc updated in the same phase.

### Phase 5 - Closure: docs, roadmap write-back, audit

Status: planned
Targets: roadmap, bug file, daily log, plan closure

- Item Types: `Fix` (confirmed owner-doc drift: roadmap item 15 root-cause
      narrative superseded by a477a4c887; roadmap item 16 premise stale)

- [ ] Roadmap item 15 written back (`todo` → `done`) with a closure-derived
      summary: real root cause (GSS pop-path enumeration order, a477a4c887),
      residual fixes, scanner-state proof, loop-class adjudication; superseded
      `selectTree`/`addSlice` narratives rewritten; item 16's stale
      "un-disable PyCorpusTest" premise corrected (it already runs, 112/117).
- [ ] Bug file header + appendices updated to final statuses.
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0.
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <this file> --strict` exit 0.
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-treesitter --severity high` exit 0.
- [ ] Independent subagent closure audit completed; evidence written into the
      Closure section below.

Exit Criteria:

- [ ] Roadmap + bug file textually consistent with live code/test state.
- [ ] Both tools above exit 0.
- [ ] Closure audit evidence present in this file.

## Closure Gates

- [ ] All in-scope confirmed live defects fixed: `print(d, *e)` residual
      (15a), for-else recovery crash + termination class (15c), plus any
      defect surfaced by Phase 4 proofs.
- [ ] 15(b) verified by focused tests (or surfaced defects fixed).
- [ ] No corpus regression on any of the six grammars (python baseline
      recorded: 112/117, 5 adjudicated).
- [ ] Full module test suite green (baseline: 390 tests, 2 skipped).
- [ ] No in-scope live defect silently downgraded to deferred/follow-up.
- [ ] Owner docs synced (roadmap item 15 + item 16 premise, bug file; module
      doc only if user-visible behavior changed) — per-phase adjudications
      recorded.
- [ ] Independent subagent closure audit completed with evidence recorded
      below (Reviewer/Agent, per-criterion PASS/FAIL, tool exit codes).
- [ ] Anti-Hollow Check: audit confirms focused tests drive the real path
      (`TSParser.parse` → `GLRParser`) against C-oracle trees; no empty
      catch/no-op paths added; `ts.debug` diagnostics are flag-gated and
      documented as retained tooling.
- [ ] `./mvnw test -pl nop-treesitter` green at closure.
- [ ] Style consistent with module conventions (lint hook runs on commit).

## Deferred But Adjudicated

### Zero-width loop class members beyond the verification set

- Classification: `watch-only residual`
- Why Not Blocking Closure: the guard remains active as a safety net; any
  future member fails loudly with a diagnostic exception instead of hanging,
  and Phase 3 records the enumeration method used.
- Successor Required: `no`
- Successor Path: n/a (re-open scope via a new bug note if a member appears)

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

Status Note: (pending)
Completed: (pending)

Closure Audit Evidence:

- Reviewer / Agent: (pending)
- Audit Session: (pending)
- Evidence: (pending — per-criterion PASS/FAIL with live code paths / test
  names; checklist + hollow-scan tool exit codes; deferred classification
  check)

Follow-up:

- (pending closure)
