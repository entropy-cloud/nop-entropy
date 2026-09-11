# 15 Python Corpus Re-Adjudication (roadmap item 16)

> Plan Status: completed
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

Status: completed
Targets: `PyCorpusTest.java`

- Item Types: `Fix`

- [x] Remove the 3 resolved keys ('Print used as an identifier', 'Assignments',
      'Lists') from `ADJUDICATED`; update the Javadoc to state each remaining
      key's reason (multi-round recovery shape class; the C-traced for-else
      precedent in the 2026-09-11 daily log, and this section's C-vs-Java
      structural difference recorded there).
- [x] Remove the leftover scratch measurement file (the untracked
      ScratchAdjudCheckTest scratch class) and clean the empty `/** */`
      Javadoc remnant in PyCorpusTest.
- [x] Run the test: 115/117 pass ≥ 95%, 2 adjudicated; full module suite green.
      **Fallback**: if any de-adjudicated section unexpectedly fails (the test's
      equality assertion is a hard gate, not the 95% floor), keep that key with
      a dated reason instead — the roadmap's ≥ 114/117 expectation allows it.

Exit Criteria:

- [x] `ADJUDICATED` contains exactly the 2 error-recovery sections (or the
      documented fallback subset).
- [x] `./mvnw test -pl nop-treesitter -Dtest=PyCorpusTest` green with
      115 passing sections.
- [x] `./mvnw test -pl nop-treesitter` green.
- [x] No owner-doc update required (test-internal adjudication; roadmap
      write-back tracked in Phase 2).

### Phase 2 - Roadmap write-back and closure

Status: completed
Targets: roadmap, daily log, plan closure

- Item Types: `Follow-up`

- [x] Roadmap item 16 written back (`todo` → `done`) with the measured numbers.
- [x] Daily log entry with the verification command and numbers.
- [x] Independent subagent closure audit; evidence in the Closure section.

Exit Criteria:

- [x] Roadmap + plan textually consistent with live state; module doc lists
      python among the built-in grammars.
- [x] Closure audit evidence present in this file.

## Closure Gates

- [x] PyCorpusTest passes 115/117 with 2 adjudications (≥ 95% floor).
- [x] Full module suite green.
- [x] Roadmap item 16 done.
- [x] Independent closure audit evidence recorded below.
- [x] `./mvnw test -pl nop-treesitter` green.

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

Status Note: Python corpus acceptance holds at 115/117 = 98.3% with the two
remaining adjudications honestly scoped to the multi-round recovery shape
class. The independent auditor re-ran the focused and full suites, verified
the ADJUDICATED list contents, the module-doc python entry, the roadmap
write-back, and both deferred classifications.
Completed: 2026-09-11

Closure Audit Evidence:

- Reviewer / Agent: independent subagent closure auditor (fresh session)
- Audit Session: agent_cde8751a-8c4f-4486-a58f-e3dd7a1f750b
- Evidence:
  - P1 EC1 PASS: ADJUDICATED = exactly the 2 errors.txt sections
    (PyCorpusTest.java:43-45); empty Javadoc remnant removed.
  - P1 EC2 PASS: focused run prints "Python corpus: 115/117 sections pass
    (2 adjudicated)".
  - P1 EC3 PASS: full module run 398 tests / 0 failures / 2 skipped.
  - P1 EC4 PASS: adjudication is test-internal; the flagged docs drift
    (module doc missing python) was fixed in the same commit.
  - P2 EC PASS: roadmap item 16 done with correct numbers; module doc lists
    python with 115/117; doc-links exit 0.
  - Deferred check PASS: 2 remaining adjudications = watch-only residual,
    same class as the error-recovery plan's adjudication, evidence linked.
  - `check-plan-checklist.mjs --strict` exit 0 (re-run after write-back).
  - Hollow scan exit 0.

Follow-up:

- no remaining plan-owned work
