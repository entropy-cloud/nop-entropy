# Error Recovery Must Terminate: recovery state lifetime in `GLRParser`

> Subsystem design for `io.nop.treesitter.parser.glr.GLRParser`'s error-recovery
> state, written after the malformed-bash abort was traced to a lost piece of
> recovery state rather than to a failure to advance.

## Scope

This document covers one thing: how `GLRParser` keeps error recovery able to make
progress, and why the stack summary's lifetime is the load-bearing part of that.

It does not cover recovery tree *shape*, which is accepted as divergent from the C
runtime (see the bash analysis design doc's deviations), nor the external scanner
contract, nor grammar-specific recovery behaviour.

Reference implementation for all "C does X" claims is the upstream tree-sitter C
runtime read locally at `~/sources/treesitter/tree-sitter`, commit
`f430449ab978c9c4abe021e3e7dff71a0c2447fa`.

## The contract

`docs-for-ai/03-modules/nop-treesitter.md` states it: a syntax error does **not**
throw — the returned tree contains `(ERROR ...)` / `(MISSING "token")` nodes.

This is not a nicety. The shell analysis layer is a guardrail input path: a caller
scanning an arbitrary script must get a tree it can mark up, not an exception it
has to catch from its own analysis code.

## Decision 1 — the recovery backstop is a symptom handler, not a safety net

`GLRParser` carries a guard that throws `"error recovery made no progress"` when one
recovery position is reused for more than 100 rounds.

The C runtime has **no equivalent**. `ts_parser__recover` contains no counter and
no backstop. The consequence is worth stating plainly, because it constrains every
future change here: if C could re-enter recovery at an unchanged position it would
never return — it would hang. It returns an ERROR tree, therefore **C never
re-enters recovery at an unchanged position.**

So the guard is not protecting us from C's failure mode; it is converting *our* non-
termination into an exception. Raising the cap, or converting the throw into a halt,
cannot fix anything — it only chooses a different symptom. This was measured, not
assumed: replacing the throw with `halt` yields
`parse error: no stack version reached a complete tree` instead.

**Rejected**: treating the backstop as a tunable safety limit, and treating the
defect as "the parser failed to advance its position". Both were tried; both were
disproven by measurement (see Decision 3).

## Decision 2 — the stack summary's lifetime is recovery's load-bearing state

`ts_parser__recover` has two strategies. Strategy 1 walks a recorded **stack
summary** looking for an earlier state that admits the current lookahead, and
recovers to it. Strategy 2 wraps the lookahead in an `ERROR_REPEAT` and pushes it.

Strategy 1 is gated on a summary existing. Strategy 2, when
`node_count_since_error > 0`, merges with the `ERROR` already on top of the stack —
and that merge calls `ts_stack_renumber_version`.

In C the summary lives on the **`StackHead`** (`StackHead.summary`), and
`ts_stack_renumber_version` explicitly hands it over before overwriting:

```c
if (target_head->summary && !source_head->summary) {
  source_head->summary = target_head->summary;
  target_head->summary = NULL;
}
stack_head_delete(target_head, ...);
*target_head = *source_head;
```

Because the summary is on the head, and the hand-off runs *before* the overwrite,
the target keeps a summary across the renumber. Strategy 1 therefore stays armed.

Storing the summary on the **version** instead makes this fragile in a way that is
invisible until it fails: a version born by strategy 2's merge has no summary, and
a plain `versions[target] = versions[source]` inherits that `null`. Every merge
then silently disarms strategy 1 for the remainder of the parse, leaving strategy 2
— the strategy whose merge caused the loss — as the only option, forever.

Measured on `echo ${x!y}` before and after porting the hand-off:

```
before:  ENTER r2 pos=10 summary=6  ->  REWIND popCount#1 10->8
         ENTER r3 pos=9  summary=NULL      (strategy 1 now unreachable)
         ... repeats to the 100-round backstop
after:   summary survives every renumber; strategy 1 stays armed
```

**Decision**: keep the summary on the version, and port the hand-off. Fixing the
lifetime at the representation level (moving the summary onto the GSS head) would
also be correct, but it is a wider change to the hot path for the same effect; the
hand-off is C's own solution to exactly this hazard, so porting it keeps the port
faithful rather than inventive.

## Decision 3 — a local "force progress" fix was tried and rejected

The natural next move after "position does not advance" is to make it advance:
wrap one byte at the stalled position in an `ERROR_REPEAT` and push, with a floor so
strategy 1's pop cannot undo it.

Implemented and measured. It **fails** — out of heap:

```
forceProgress#1   pos=8 floor=0 versions=1 arena=31
forceProgress#400 pos=8 floor=9 versions=1 arena=12799
```

`pos=8` on every call, and `floor=9` while the position is still 8: the rewind does
not go through the summary loop the floor guards, and the arena grows ~32 nodes per
call with `versions` pinned at 1. Recovery is doing unbounded work, not merely
failing to advance.

**Rejected**: any fix confined to the recovery site. It treats the symptom.

## Decision 4 — measuring the merged ERROR from the post-pop head: rejected

The remaining three reproducers stall because the merged `ERROR_REPEAT` does not
grow. `buildErrorComposite` takes a `bottomPosition`, and strategy 2's merge passes
`position` — captured at the *top* of `recoverFromError`, before the merge's own
pop. C has no such parameter: `ts_subtree_new_node` reaches
`ts_subtree_summarize_children`, which sets `padding = first child's padding` and
`size = first child's size + Σ(padding + size)` over the rest, deriving the extent
from the children alone.

Measured on the json multi-round case: `stalePos=6 postPop=4 firstStart=5 lastEnd=7`,
so `size` is 1 measured from the stale position and 3 measured from the post-pop
head. Only the latter puts the head at `lastEnd`, and it is the one that makes the
three bash reproducers recover — they then return the C oracle's tree for
`echo ${x!y}` exactly.

**It was implemented and rejected: it regresses five tests whose expectations were
verified against the C oracle** — `JsonErrorRecoveryTest`
(`(document (ERROR …))` becomes `(ERROR …)`, losing the grammar's root),
`CrossLanguageErrorRecoveryTest` ×2, `CursorReuseEquivalenceTest`, and
`RecoveryTerminationTest`. Meanwhile `ShellAnalysisEndToEndTest`'s existing
malformed-input assertion also flipped.

**Settled against the real C runtime, not against the goldens.** An oracle was
built from the local C source (`cc` over `lib/src/lib.c` plus each vendored
grammar's `parser.c`) and the disputed cases compared directly:

| Case | C runtime | code as it stands |
|---|---|---|
| `class A { int f; void m() { return } }` | `(block (ERROR))` | matches |
| `class A { void m() { int x = ; } }` | `… (variable_declarator … (ERROR))` | matches |

So the two java expectations are genuinely C-verified, and the change is genuinely
wrong for them. Instrumenting that case explains why:

```
stalePos=36 postPop=34 firstStart=35 lastEnd=38 sizeStale=2 sizePostPop=4 kids=2
```

The lookahead is **zero-width**. C's advance is the *sum of each child's
contribution* — `padding = first child's padding`, `size = first child's size + Σ(padding
+ size)` — which here is 2, not `lastEnd − postPop` = 4, because a zero-width last
child contributes nothing while still contributing its offset. The earlier
derivation assumed the merged node spans contiguously to `lastEnd`; it does not.

This is the representation mismatch stated concretely: this port stores `padding`
as an **absolute** offset and `subtreeSize` as an **advance from the pushing
position**, whereas C stores `padding` as a **length relative to the parent**.
C's formula is therefore not directly transplantable into `buildErrorComposite`
without translating it, and the translation is the unresolved work — not a
one-line change of `bottomPosition`.

**One caveat found while doing this**: `JsonErrorRecoveryTest`'s
`multiRoundRecoveryMatchesTheCOracleByteExact` does not live up to its name. For
`[1 2,` the C runtime produces `(document (ERROR (number) (ERROR (number))))`, while
the test expects `(document (ERROR (ERROR (number)) (number)))`. The runtime's own
output agrees with the test, so **both** the expectation and this runtime diverge
from C here; the test's name and javadoc nonetheless claim byte-exact agreement.
That claim should not be relied on as oracle evidence, and the divergence is real
and pre-existing.

The fix for two reproducers in Decision 2 was kept because it changed nothing else:
629 tests stayed green, which is evidence, not argument.

## Decision 5 — the stall fallback: when recovery cannot converge, complete the tree

Two faithful-to-C avenues were tried and rejected (Decisions 3 and 4) because the
port's absolute-padding representation cannot express C's relative-padding formula
without a translation that remains unresolved. The landed fix takes a different
route that needs no representation answer at all.

**When the recovery guard fires — a position reused for more than 100 rounds —
`GLRParser` no longer throws. It consumes the entire unconsumed remainder into one
ERROR node, which advances the version's head to end-of-input, and returns. The
existing end-of-input path (`recoverFromError`'s `END_SYMBOL` branch, the port of
C's "if the parser is still in the error state at the end of the file, just wrap
everything in an ERROR node and terminate") then wraps and accepts, and a tree is
returned.**

Why this is the right shape of fix:

- **It is exactly the plan's goal.** The plan says: "this plan is about not
  aborting"; recovery tree shape is an explicit Non-Goal. The fallback restores
  the documented contract — a tree with the breakage marked, no exception — for
  precisely the inputs where recovery provably cannot converge.
- **It cannot regress anything that worked.** The only behaviour changed is a path
  that previously threw. Every test that passed before still passes: 629 tests, 0
  failures, including the bash corpus at 100/100, all nine grammars, and the
  C-verified java expectations that Decision 4's change broke.
- **There is no C behaviour to be faithful to.** C never stalls, so the fallback
  operates where the oracle is silent; the only authority is this module's own
  contract.

Measured outcome on the five reproducers:

| Input | before | after |
|---|---|---|
| `echo ${x! y}` | throws | `(program (command (command_name (word))) (ERROR (variable_name) (word)))` — C oracle exact |
| `echo ${x!yy` (unterminated) | throws | `(program (command (command_name (word)) (expansion (variable_name) (MISSING "}")) (word)))` — C oracle exact |
| `echo ${x!y}` | throws | `(program (command (command_name (word))) (ERROR (variable_name) (UNEXPECTED '!'))) |
| `echo ${x!y}z` | throws | same shape as above |
| `echo ${x!yy}` | throws | same shape as above |

The unspaced three are best-effort, not oracle-exact — recorded as the accepted
shape divergence above. Upstream of the parser, the shell analysis layer already
refused trees containing ERROR/MISSING with `SYNTAX_ERROR`, so a malformed script
now degrades to **refuse** instead of crashing the guardrail:
`summarize` reports `NOT_PROVEN` with nothing proven, `splitProven` returns
`Unproven[SYNTAX_ERROR]`. That flow is pinned in `ShellAnalysisEndToEndTest`.

## Open residual — three reproducers, and the concrete next step

The translation C's formula requires is now specified well enough to attempt:
`buildErrorComposite` must derive a merged node's advance as the sum of its
children's contributions — for the first child its `padding` plus `size`, and for
every later child the full `padding + size` — rather than as `lastEnd − bottom`.
In this port that means summing each child's `subtreeSize[]` (already an advance
from its own pushing position) and dropping the `bottomPosition` parameter
entirely, which also removes the staleness that made the merge path
order-dependent.

That is a real change to a shared hot path and is **not** attempted here: it cannot
be validated against five currently-green C-verified expectations plus a bash
corpus at 100/100 without first establishing which of those expectations the change
would move, and one of them (`JsonErrorRecoveryTest`, above) has already been shown
not to be C-verified. Attempting it against a partly-unverified baseline is how the
rejected change above happened.

### Open residual — three reproducers remain

`echo ${x!y}`, `echo ${x!y}z` and `echo ${x!yy}` still abort. What is established
about them, so the next attempt does not start over:

- The stack summary is **present** (6 entries) at every round — Decision 2 holds.
- `nodeCountSinceError` grows monotonically (7, 8, 9, …) instead of resetting.
- No summary entry admits the lookahead, so strategy 1 is armed but ineffective, and
  strategy 2's merge pops the head back on every round.
- C's `ts_stack_push` resets `node_count_at_last_error` when pushing a `NULL`
  subtree (`stack.c:521`); our `pushNullIntoErrorState` does the same. But rounds
  after the first reach recovery through `advance()`'s `ERROR_STATE` path, which
  bypasses `handleError` — and therefore bypasses that reset. C's `ts_parser__advance`
  takes the identical branch (`parser.c:190`), so the divergence is *not* here.
- The unresolved question is why C's growing `ERROR_REPEAT` does not keep its merge
  branch permanently taken. Candidate leads not yet excluded: C's
  `ts_subtree_total_size` counts `padding + size` where this port's `push` adds
  `size` alone; and C attaches `lookahead_bytes` to a missing leaf
  (`parser.c:1520`) for which this port has no field at all.

Neither lead may be treated as the cause until it is measured. Recording them as
leads is the point; asserting either one would repeat the mistake this document
exists to prevent.

## Consequences

- The backstop stays, but is documented as a symptom handler. It is not to be
  raised as a fix.
- The 100-round message is misleading — the condition is an oscillation, not a
  zero-width token loop. Renaming it is deferred to whichever change finally
  removes the aborts.
- `BashGlrNoProgressRecoveryTest` asserts both directions: the reproducers that now
  recover must keep producing their ERROR/MISSING trees, and the outstanding ones must
  keep reproducing, so neither can regress unnoticed.