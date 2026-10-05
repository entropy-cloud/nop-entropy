# GLR error-recovery livelock guard: divergence from the C oracle on malformed input

> Plan Status: in progress
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
`io.nop.treesitter.parser.glr.GLRParser:959` (`handleError`) and
`GLRParser:1279` (skip-merge, inside `recoverFromError`), both of the form "same
recovery position for more than 100 rounds → throw". These are a defensive
backstop, not a translation of any C construct. C's `ts_parser__recover` always
advances the position by skipping a token, so it cannot hit this state.

**Corrected during Phase 1**: the two guards share one counter pair
(`zeroProgressRecoveryRounds` / `lastRecoveryPosition`) across two different code
paths, so interleaving them corrupts each other's count. Both must be addressed
together or not at all.

There are **four** entry points into recovery, and only the two guarded paths are
covered — so "enumerate every no-progress entry" is not a formality:

| Entry | Site | Guarded? |
|---|---|---|
| lexed token while already in `ERROR_STATE` | `GLRParser:204` | via `recoverFromError`'s guard |
| `Language.Action.RECOVER` action | `GLRParser:245` | via `recoverFromError`'s guard |
| end of `handleError` (C's unconditional `ts_parser__recover`) | `GLRParser:1012` | **both** guards |
| parse loop's error branch | `GLRParser:1716` | via `handleError`'s guard |

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

Status: completed
Targets: `src/test/java/io/nop/treesitter/parser/` (new regression test)

- Item Types: `Proof`

- [x] Add a regression test covering the four `echo ${x!y*}` variants, asserting
      the current throw (so the fix has a red-to-green signal) and recording the
      CLI's expected trees as the target →
      `nop-treesitter/src/test/java/io/nop/treesitter/parser/BashGlrNoProgressRecoveryTest.java`
      (3 tests, green; asserts only *that* a `TreeSitterException` is thrown and
      never binds its message, so Phase 2 can invert it without rewriting)
- [x] Enumerate every code path in `handleError` and the skip-merge path that can
      re-enter recovery at an unchanged position, and write down which C function
      each corresponds to and where C's position advance lives → the four-entry
      table in `Current Baseline`; **finding: only two of the four are guarded**
- [x] Determine empirically whether the position can be advanced by skipping the
      offending token (C's behaviour) or whether the version must be dropped →
      **both were tried; neither works. See "Phase 1 adjudication" below.**
- [x] `ai-dev/logs/2026/10-04.md` entry updated

Exit Criteria:

- [x] The failing set is enumerated, not just the four known inputs → four recovery
      entry points mapped; two carry the guard, and the shared-counter defect found
- [x] The chosen advance strategy is justified against the C function it replaces →
      **Phase 2's drafted strategy is disproved and replaced by a recorded decision
      point** (below). No strategy is silently adopted.
- [x] `./mvnw test -pl nop-treesitter -am` exits 0; the new test is green and pins
      the defect without asserting the throw's message

## Phase 1 adjudication — the drafted fix premise is wrong

The draft assumed recovery fails to advance because the offending token is
zero-width, and that advancing past it (C's behaviour) would therefore fix it.
Instrumented tracing of `echo ${x!y}` shows the opposite, and both candidate fixes
were implemented and measured before this conclusion was recorded.

**What actually happens.** At the backstop, for all four inputs:

```
skip-guard pos=8 rounds=101 lookaheadSym=165 lookaheadSpan=[9,1] state=0 versions=1
```

Instrumenting `push` on either side of the skip shows the position *does* move
within a round — `8 → 9` (or `8 → 10` for the first input) — and is back at `8`
on the next round. So this is **not** a zero-width token loop despite the
exception text; it is a bounded **oscillation** between two positions:

```
recover at 8 → skip → position 9 → next error → pop back to 8 → recover at 8 → …
```

**Why the two drafted strategies both fail.**

| Strategy | Result (measured) |
|---|---|
| Advance past the offending token (C's behaviour) | Already happens inside a round (`8 → 9`); the oscillation returns to `8`, so the guard still fires |
| Drop the version (`halt` + return) instead of throwing | Converts the abort into a *different* abort: `parse error: no stack version reached a complete tree` |

Dropping the version fails because the oscillator is the **only** live version
(`versions=1`), so halting it leaves nothing to complete a tree.

**Consequence for Phase 2.** The fix cannot be "make the skip advance" nor "drop
the version". It needs a **net-progress** mechanism: when the recovery position
repeats, force the parse past the oscillating byte and rebuild — reproducing the
property that makes C's `ts_parser__recover` unable to reach this state at all.
That mechanism does not exist in the runtime today, and where it belongs
(lexer skip primitive vs. a recovery-level forced advance vs. synthesizing an
ERROR-wrapped tree from the best partial stack) is a **design decision with
different behaviour for all nine grammars**, not a mechanical repair.

**Phase 2 is therefore blocked pending that decision**, and this plan is left
`in progress` rather than closed with an unproven fix. See `Closure` for the
decision that has to be made.

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

Status Note: **Not closed.** Phase 1 is complete and delivered; Phase 2 is blocked
on a design decision, not on effort. The draft's Phase 2 premise ("the offending
token is zero-width, so advancing past it fixes recovery") was tested and
**disproved**: the position already advances within a round and recovery
oscillates 8→9→8 instead. Both drafted strategies were implemented and measured —
advance-past-token still trips the backstop, and drop-the-version converts the
abort into `no stack version reached a complete tree` because the oscillator is
the only live version.

Closing this plan now would mean shipping an unproven change to shared runtime
code, so the honest state is `in progress` with a recorded decision point.

Completed: （未完成 —— Phase 2 待设计裁定）

### Open decision (blocks Phase 2)

Where should net progress be forced when the recovery position repeats?

| Option | Shape | Trade-off |
|---|---|---|
| A. Lexer skip primitive | teach the lexer / `GLRParser` to unconditionally consume one byte on a repeated recovery position, then resume | closest to C's invariant; touches the lex/scan path, so every grammar is affected |
| B. Recovery-level forced advance | on a repeated position, rebuild the stack at `position + 1` with the prefix wrapped in `ERROR` | contained to `recoverFromError`; recovery tree shape becomes ours, not C's |
| C. Synthesize from the best partial stack | when all versions halt without completing, wrap the unconsumed remainder in `ERROR` and return that tree | smallest behavioural blast radius and always satisfies the documented contract; furthest from C's shape |

All three change behaviour for all nine grammars, so `parser/glr` stays a
protected area per `AGENTS.md` and the choice needs an explicit decision before
implementation.

Closure Audit Evidence:

- Reviewer / Agent: （Phase 2 实施并收口时由独立子 agent 填写）
- Audit Session: （待填）
- Evidence: Phase 1 evidence is in the daily log entry for 2026-10-04; Phase 2 has
  not started, so there is nothing to audit for closure yet.

Follow-up:

- 决定 A / B / C 之后，本 plan 的 Phase 2 需要按选定方案重写——现有 Phase 2 的
  checklist 描述的是已被证伪的策略，不能照做。
