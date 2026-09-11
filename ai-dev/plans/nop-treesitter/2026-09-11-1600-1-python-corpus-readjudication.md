# 15 Python Corpus Re-Adjudication (roadmap item 16)

> Plan Status: active
> Last Reviewed: 2026-09-11
> Source: `ai-dev/backlog/nop-treesitter-roadmap.md` item 16; de-adjudication evidence from plan `2026-09-11-1500-1-recovery-hardening.md`
> Related: roadmap item 15 (done, unblocked this item), item 17

## Purpose

Close roadmap item 16: re-adjudicate `PyCorpusTest` after the item 15 GLR
fixes so the python corpus acceptance (≥ 95% of upstream sections passing)
holds with the minimal honest set of adjudications.

## Current Baseline

Verified against live repo 2026-09-11 (HEAD c2c3a3d65c):

- `PyCorpusTest` runs and passes: 112/117 sections, `ADJUDICATED` list has 5
  keys; acceptance assertion is ≥ 95%.
- Per-section status measured 2026-09-11 via `CorpusUtil` + `toSexpString(false)`
  (the same comparison the test performs): 'Print used as an identifier',
  'Assignments', 'Lists' now render **byte-identical to the upstream expected
  trees** (noFieldsEq=true) — item 15's variant-selection fixes resolved them.
- 'An error before a string literal' and 'Error detected at globally reserved
  keyword' still differ from upstream; both are multi-round recovery shape
  divergences, the class adjudicated watch-only in the error-recovery plan and
  re-confirmed by item 15's Phase 3 (C traces in `ai-dev/logs/2026/09-11.md`).
- Vendored python corpus size: 117 sections across errors/expressions/literals/
  pattern_matching/statements.

## Goals

- `ADJUDICATED` reduced to exactly the 2 error-recovery sections that still
  diverge, each with a recorded reason pointing at the adjudicated class.
- Pass rate 115/117 (98.3%) ≥ 95%, asserted by the test.
- The test's Javadoc documents the per-section adjudication reasons.

## Non-Goals

- Fixing the 2 remaining multi-round recovery shapes (adjudicated watch-only;
  successor only if a future grammar surfaces them beyond this class).
- Changes to GLRParser, the blob, or the extractor.

## Scope

### In Scope

- `nop-treesitter/src/test/java/io/nop/treesitter/corpus/PyCorpusTest.java`:
  ADJUDICATED list, Javadoc, pass-rate assertion unchanged (≥ 95%).
- Roadmap item 16 write-back.
- Docs bug fix: `docs-for-ai/03-modules/nop-treesitter.md` built-in grammar
  list omits python — add it (pre-existing drift, flagged in review).

### Out Of Scope

- Runtime changes; other grammar suites; new tests beyond the existing ones.

## Execution Plan

### Phase 1 - De-adjudicate and re-baseline

Status: planned
Targets: `PyCorpusTest.java`

- Item Types: `Fix`

- [ ] Remove the 3 resolved keys ('Print used as an identifier', 'Assignments',
      'Lists') from `ADJUDICATED`; update the Javadoc to state each remaining
      key's reason (multi-round recovery shape class; the C-traced for-else
      precedent in the 2026-09-11 daily log, and this section's C-vs-Java
      structural difference recorded there).
- [ ] Remove the leftover scratch measurement file
      (`nop-treesitter/src/test/java/io/nop/treesitter/corpus/ScratchAdjudCheckTest.java`)
      and clean the empty `/** */` Javadoc remnant in PyCorpusTest.
- [ ] Run the test: 115/117 pass ≥ 95%, 2 adjudicated; full module suite green.
      **Fallback**: if any de-adjudicated section unexpectedly fails (the test's
      equality assertion is a hard gate, not the 95% floor), keep that key with
      a dated reason instead — the roadmap's ≥ 114/117 expectation allows it.

Exit Criteria:

- [ ] `ADJUDICATED` contains exactly the 2 error-recovery sections (or the
      documented fallback subset).
- [ ] `./mvnw test -pl nop-treesitter -Dtest=PyCorpusTest` green with
      115 passing sections.
- [ ] `./mvnw test -pl nop-treesitter` green.
- [ ] No owner-doc update required (test-internal adjudication; roadmap
      write-back tracked in Phase 2).

### Phase 2 - Roadmap write-back and closure

Status: planned
Targets: roadmap, daily log, plan closure

- Item Types: `Follow-up`

- [ ] Roadmap item 16 written back (`todo` → `done`) with the measured numbers.
- [ ] Daily log entry with the verification command and numbers.
- [ ] Independent subagent closure audit; evidence in the Closure section.

Exit Criteria:

- [ ] Roadmap + plan textually consistent with live state; module doc lists
      python among the built-in grammars.
- [ ] Closure audit evidence present in this file.

## Closure Gates

- [ ] PyCorpusTest passes 115/117 with 2 adjudications (≥ 95% floor).
- [ ] Full module suite green.
- [ ] Roadmap item 16 done.
- [ ] Independent closure audit evidence recorded below.
- [ ] `./mvnw test -pl nop-treesitter` green.

## Deferred But Adjudicated

### 'An error before a string literal' + 'Error detected at globally reserved keyword'

- Classification: `watch-only residual`
- Why Not Blocking Closure: multi-round recovery tree shapes, the class
  adjudicated watch-only in the error-recovery plan and re-confirmed by item
  15's Phase 3 with C-trace evidence; valid-source parsing unaffected; pass
  rate 98.3% exceeds the 95% acceptance floor.
- Successor Required: `no`
- Successor Path: n/a

## Closure

Status Note: (pending)
Completed: (pending)

Closure Audit Evidence:

- Reviewer / Agent: (pending)
- Audit Session: (pending)
- Evidence: (pending)
