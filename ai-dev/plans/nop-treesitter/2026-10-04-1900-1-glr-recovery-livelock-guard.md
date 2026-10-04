# GLR error-recovery livelock guard: divergence from the C oracle on malformed input

> Plan Status: draft
> Last Reviewed: 2026-10-04
> Source: discovered while executing `ai-dev/plans/nop-treesitter/2026-10-04-1430-1-bash-grammar-and-shell-analysis.md` Phase 1 (`BashScannerIdiomsTest`), recorded in `ai-dev/analysis/nop-treesitter-bash-grammar-contracts.md` § 10
> Related: `docs-for-ai/03-modules/nop-treesitter.md` (Known Deviations)

## Purpose

`GLRParser`'s error recovery can livelock on a single byte and then abort the
whole parse with an exception, where the official C runtime recovers and returns
a tree containing an `ERROR` node. The repository's public contract says a
syntax error does **not** throw — it returns `(ERROR ...)` / `(MISSING ...)`
nodes — so this is a confirmed contract violation, not a fidelity nicety.

## Current Baseline

**The contract that is being violated** is documented in
`docs-for-ai/03-modules/nop-treesitter.md`:

> 输入有语法错误时**不抛异常**——返回的树包含 `(ERROR ...)` / `(MISSING "token")` 节点。

**The reproducer**, measured 2026-10-04:

| Input | Official tree-sitter CLI 0.25.10 | This runtime |
|---|---|---|
| `echo ${x!y}` | `(program (command name: (command_name (word))) (ERROR (variable_name) (word)))` | throws `TreeSitterException: error recovery made no progress at byte offset 8 (zero-width token skip loop)` |
| `echo ${x!y}z` | recovers | same throw at byte 8 |
| `echo ${x! y}` | recovers | same throw at byte 8 |
| `echo ${x!yy}` | recovers | same throw at byte 8 |

`echo ${x!y}` is malformed bash (`!` is not a valid operator after a variable
name), so this is an error-recovery path, not a valid-input path.

**Localization** (controlled experiment, `BashScanner` swapped for stubs):

| Scanner attached to the bash blob | Result on `echo ${x!y}` |
|---|---|
| none | `(program (command (command_name (word)) (expansion (variable_name) (MISSING "}")) (word) (word)))` |
| always-`false` stub | same as above |
| the faithful `BashScanner` | **throws** |

So the faithful scanner is what exposes it. The triggering region is
`expansion_word:`, which upstream `scanner.c` deliberately leaves **without** an
`in_error_recovery` guard, so the translation produces the same token in the same
parse state as C does. The scanner is therefore faithful; the divergence is in
`GLRParser`.

**Where the abort happens**: two guards in
`io.nop.treesitter.parser.glr.GLRParser`, in `handleError` and in the skip-merge
path, both of the form "same recovery position for more than 100 rounds → throw".
These are a defensive backstop, not a translation of any C construct. C's
`ts_parser__recover` always advances the position by skipping a token, so it
cannot hit this state.

**Scope of the defect**: it is shared runtime code. bash is simply the first
grammar whose external scanner produces a token the error state accepts but
cannot shift, which is why it surfaced here and not in the eight grammars
assembled earlier. Other grammars can reach it too; the backstop has simply not
been hit by their corpora.

## Goals

- `GLRParser`'s error recovery always terminates by making progress, as C's does
- every malformed input returns a tree with `ERROR` / `MISSING` nodes instead of
  throwing, restoring the documented contract
- the backstop, if it is kept at all, is a last-resort guard that a corpus-sized
  regression suite cannot reach

## Non-Goals

- Matching C's recovery *shape* for malformed input. Where the runtime already
  diverges in tree shape under equal error cost, that is accepted and documented;
  this plan is about not aborting
- Changing the external scanner contract
- Any grammar-specific workaround in `BashScanner`

## Scope

### In Scope

- `GLRParser.handleError` / the skip-merge path: make the no-progress case
  advance (skip a token, or wrap the remainder in an `ERROR` node) the way
  `ts_parser__recover` does, instead of throwing
- a regression test for `echo ${x!y}` and its `z` / space / doubled-suffix
  variants, asserting a tree with `ERROR` and no exception
- audit of the other grammars' malformed-input handling under the fixed recovery,
  to confirm the fix does not change any currently-correct tree

### Out Of Scope

- grammar-specific workarounds
- improving recovery tree *shape* fidelity
- shell semantic layer work

## Execution Plan

### Phase 1 - Reproduce and characterize

Status: planned
Targets: `src/test/java/io/nop/treesitter/parser/` (new regression test)

- Item Types: `Proof`

- [ ] Add a regression test covering the four `echo ${x!y*}` variants, asserting
      the current throw (so the fix has a red-to-green signal) and recording the
      CLI's expected trees as the target
- [ ] Enumerate every code path in `handleError` and the skip-merge path that can
      re-enter recovery at an unchanged position, and write down which C function
      each corresponds to and where C's position advance lives
- [ ] Determine empirically whether the position can be advanced by skipping the
      offending token (C's behaviour) or whether the version must be dropped
- [ ] `ai-dev/logs/2026/10-04.md` (or the then-current date) entry updated

Exit Criteria:

- [ ] The failing set is enumerated, not just the four known inputs
- [ ] The chosen advance strategy is justified against the C function it replaces
- [ ] `./mvnw test -pl nop-treesitter -am -T 1C` exits 0 with the new test red

### Phase 2 - Fix

Status: planned
Targets: `src/main/java/io/nop/treesitter/parser/glr/GLRParser.java`

- Item Types: `Fix`

- [ ] Replace the throw with the position-advancing behaviour
- [ ] Keep a backstop only if it cannot be reached by any corpus; if kept, make it
      recover rather than abort
- [ ] Confirm the nine existing grammars' corpus results are unchanged, and that
      `BashCorpusTest` stays at 100/100
- [ ] `docs-for-ai/03-modules/nop-treesitter.md` Known Deviations updated (the
      divergence is removed)

Exit Criteria:

- [ ] `./mvnw test -pl nop-treesitter -am -T 1C` exits 0
- [ ] The Phase 1 regression test is green and asserts a tree, not an exception
- [ ] All corpus tests unchanged (bash 100/100, the other eight as before)
- [ ] `docs-for-ai/03-modules/nop-treesitter.md` and
      `ai-dev/analysis/nop-treesitter-bash-grammar-contracts.md` § 10 updated
- [ ] `ai-dev/logs/` entry updated

## Closure Gates

- [ ] No malformed-input reproducer throws
- [ ] The documented "syntax errors do not throw" contract holds for every input in
      the new regression suite
- [ ] Zero regression across all nine grammars' corpus tests
- [ ] Owner doc Known Deviations section reflects reality
- [ ] `./mvnw test -pl nop-treesitter -am -T 1C` exits 0
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` exits 0
- [ ] Independent subagent closure audit recorded in this file's `Closure` section

## Deferred But Adjudicated

（起草时为空。）

## Non-Blocking Follow-ups

- **Recovery tree-shape fidelity under equal error cost**
  - Classification: `watch-only residual`
  - Why Not Blocking Closure: already documented as an accepted deviation; this
    plan targets termination, not shape
  - Successor Required: `no`
  - Successor Path: —

## Risks And Rollback

| Risk | Mitigation |
|---|---|
| Advancing the position produces different (worse) trees for inputs that currently recover correctly | Phase 2 requires the full nine-grammar corpus to be unchanged; a corpus regression blocks the fix |
| The livelock is entered from a path the fix does not cover | Phase 1 requires enumerating every no-progress entry, not just the known one |
| The backstop removal turns a bounded abort into an unbounded loop | Keep a round cap as a hard stop, but make the reached state recover first; assert the cap is unreachable in the regression suite |

## Closure

Status Note: （待填）
Completed: （待填）

Closure Audit Evidence:

- Reviewer / Agent: （待独立子 agent 填写）
- Audit Session: （待填）
- Evidence: （待填）

Follow-up:

- （待填；或明确写 no remaining plan-owned work）
