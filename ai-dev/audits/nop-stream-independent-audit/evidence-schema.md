# nop-stream Independent Audit — Evidence Schema (Frozen)

> Status: frozen
> Frozen at: HEAD 2026-08-07
> Owner: nop-stream-independent-audit mission (Stage 4)
> Validator: `ai-dev/tools/check-nop-stream-audit-manifest.mjs evidence`

This schema freezes the **evidence row** that every later domain audit (Stages 6–22) must produce before making any capability claim. It also freezes the **disposition vocabulary** and the **include/exclude rules**. Stages 5–23 may only take evidence and classify within this frozen schema; they may not invent new formats or vocabularies.

## Evidence Row — Field Specification

Every evidence row is a single record. Fields are declared below with **required-ness** (`REQUIRED` / `OPTIONAL`) and **allowed values**. The validator rejects any row that: omits a REQUIRED field, carries an unknown field, or uses a value outside the declared vocabulary.

| # | Field | Required | Allowed values / format | Meaning |
| --- | --- | --- | --- | --- |
| 1 | `inventory_id` | REQUIRED | non-empty string; must be unique within an evidence file | Stable ID for this evidence row (e.g. `EVID-CORE-001`) |
| 2 | `source_anchor` | REQUIRED | `file:line` or `file:lines` path under the repo (e.g. `nop-stream-core/.../X.java:120-140`) | The exact code/model location the capability claim is about |
| 3 | `declared_guarantee` | REQUIRED | non-empty string (≤200 chars) | The guarantee this location claims to provide (quote design/contract or summarize the capability) |
| 4 | `implementation_anchor` | REQUIRED | `file:line` or `none` | Where the guarantee is implemented; `none` if no implementation exists (hollow) |
| 5 | `runtime_wiring` | REQUIRED | `wired` \| `unwired` \| `partial` | Whether the component is actually called on the runtime path from entry point to output (`wired`=fully connected; `unwired`=never invoked; `partial`=invoked but path incomplete) |
| 6 | `positive_proof` | REQUIRED | test name (`ClassName#method`), `none`, or `manual-trace:<file:line>` | Evidence that the guarantee holds in the positive case |
| 7 | `rejection_proof` | REQUIRED | test name, `none`, or `manual-trace:<file:line>` | Evidence that the system fails fast when the guarantee is violated (negative/regression case) |
| 8 | `environment_class` | REQUIRED | `unit` \| `in-process` \| `multi-jvm` \| `none` | The strongest evidence lane actually exercised for this row |
| 9 | `required_lane` | REQUIRED | `unit` \| `in-process` \| `multi-jvm` | The MINIMUM lane a claim of this capability requires to be credible (see Lane table in source-manifest.md) |
| 10 | `finding_id` | REQUIRED | a corpus finding ID (see finding-corpus.md) OR `none` | The frozen corpus finding this row adjudicates; `none` if the capability has no open finding |
| 11 | `disposition` | REQUIRED | one of the Disposition Vocabulary below | The classification result for this row |

### Disposition Vocabulary (frozen — `disposition` field allowed values only)

| Value | Meaning |
| --- | --- |
| `e2e-proved` | Capability is proven end-to-end on the required lane with both positive + rejection evidence |
| `component-only` | Only component-level (unit) evidence exists; wiring/e2e not proven (cannot justify a system capability claim alone) |
| `unverified` | No evidence exists; the guarantee is asserted but not demonstrated |
| `fail-fast` | The missing/broken path fails fast (throws) — acceptable for not-yet-implemented features per Rule #24 |
| `non-goal` | Explicitly out of scope for the current supported baseline (must cite the non-goal) |
| `residual-risk` | Known limitation accepted as a residual risk (must cite the risk + why non-blocking) |
| `blocked` | Cannot be adjudicated because a required lane/environment is not qualified (see Stage 5) |

> Any `disposition` value outside this set is rejected by the validator. A row may not silently leave `disposition` empty.

## Lane Semantics (cross-reference — defined in source-manifest.md)

| Lane | Credible for |
| --- | --- |
| `unit` | Component-internal invariants only. NEVER sufficient for a wiring, data-plane, or recovery claim. |
| `in-process` | Data-plane semantics + operator-chain wiring within one JVM. NOT sufficient for cross-JVM control-plane / HA / fencing claims. |
| `multi-jvm` | Cross-JVM control-plane, HA, fencing, real recovery. The ONLY lane that can justify `required_lane: multi-jvm`. |

A row's `environment_class` must be ≥ its `required_lane` in strength (`none < unit < in-process < multi-jvm`) for the disposition to be `e2e-proved`; otherwise the disposition must be `component-only`, `unverified`, or `blocked`.

## Include / Exclude Rules (for evidence enumeration — bound by source-manifest.md)

- Evidence rows may only reference `source_anchor` / `implementation_anchor` paths that fall **inside** a manifest domain's scope. A row anchored to an excluded path (generated `_`-prefixed source, or `target/` output) is rejected.
- `@Internal`-annotated symbols ARE eligible anchors (they are counted in the public surface; see manifest domain `internal-spi-markers`).
- Example module (`nop-stream-fraud-example`) anchors are eligible ONLY for fail-fast/semantic rows, never for production-capability claims.
- A `finding_id` value MUST match a registered ID in finding-corpus.md, or be `none`. The validator cross-checks this when a corpus is available.

## Evidence Row — Encoding Format

Evidence rows live in machine-parsable blocks. Each row is a `@@EVIDENCE ... @@END` record with flat `key: value` lines (same convention as source-manifest.md entries). Example (NOT a real row — illustrative only):

```
@@EVIDENCE
inventory_id: EVID-EXAMPLE-000
source_anchor: nop-stream-core/.../ExampleOperator.java:10-20
declared_guarantee: ExampleOperator processes exactly-once under checkpoint
implementation_anchor: nop-stream-core/.../ExampleOperator.java:30-50
runtime_wiring: wired
positive_proof: TestExampleOperator#testExactlyOnce
rejection_proof: TestExampleOperator#testFailsOnDup
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END
```

This is illustrative; real evidence rows are produced by Stages 6–22. The validator's `evidence` subcommand validates any such file (or reports "no evidence rows yet" if none exist, which is the current state at Stage 4).

## Stage 5 Supplement — Gated-Evidence, Required-Lane/Blocked-Gate, and Lane-Mapping Rules

> Status: frozen supplement (Stage 5). This section is an **additive** rules block: it changes **neither** the 11 evidence-row fields (Field Specification table) **nor** the 7-value Disposition Vocabulary above. It binds how a later domain audit may consume a gated test's result and how a `blocked` row propagates. Authoritative text lives here; `environment-qualification.md` holds the per-lane qualification registry the rules refer to.

### Rule S5-1 — Gated-Evidence Rule (a gated test is evidence only when actually executed in its qualified lane)

A gated test (one guarded by `@EnabledIfSystemProperty` or any equivalent skip-by-default mechanism) is **not** evidence by default. Its result may be cited as `positive_proof` / `rejection_proof` by a later domain audit (Stages 6–22) **only when all three** hold:

1. The lane it requires is `qualified` in `environment-qualification.md` (a `@@LANE` block with `status: qualified` whose `frozen_strength` is ≥ the row's `required_lane`).
2. The test was **actually executed** in that lane during the audit window — producing a real surefire report / run log / assertion trace, not a default skip.
3. The citation references that concrete run artifact.

A default skip (the gated test was not enabled and therefore reported `Skipped`) is **never** evidence: it cannot justify `e2e-proved`, nor `positive_proof`, nor `rejection_proof`. A skipped gated test that a capability claim depends on forces the row's `disposition` to `blocked` (if its lane is unqualified) or `unverified`/`component-only` (if only weaker-lane evidence exists).

### Rule S5-2 — Required-Lane / Blocked-Gate Rule (a required-lane blocked row blocks readiness)

- A row's `required_lane` is **derived** from the declared capability/guarantee in the Stage-4 `source-manifest.md` (what lane strength the claim needs to be credible), never invented ad hoc.
- A domain audit (Stages 9–16) **may** close with `blocked` rows, but **only** when those rows set `disposition: blocked` (a `blocked` row MUST NOT be classified `e2e-proved`). A `blocked` row MUST name, in its `positive_proof`/`rejection_proof` or an adjacent note, the lane that is unqualified (cross-referencing a `@@LANE` block with `status: blocked`).
- **Readiness gate**: any evidence row whose `required_lane` corresponds to a lane that is `blocked` in `environment-qualification.md` blocks the Stage 23 `ready` verdict and blocks the production-readiness milestone. Stage 23 may only return `ready only for enumerated e2e-proved capability/environment pairs`; a `ready` verdict is **forbidden** while any required-lane row remains `blocked`.

### Rule S5-3 — Lane-Mapping Rule (environment_class cannot exceed a qualified lane's frozen_strength)

A domain audit's evidence row sets `environment_class` to the strongest lane actually exercised for that row. That value:

- MUST be one of the frozen vocabulary values `unit | in-process | multi-jvm | none`.
- MUST be ≤ the `frozen_strength` declared by some `qualified` `@@LANE` block in `environment-qualification.md` — i.e. a row may claim at most the strength of a lane that is actually qualified, or a weaker strength. A row may **not** silently upgrade its `environment_class` beyond what any qualified lane provides.
- Consequently, if a row needs `required_lane: multi-jvm` but no `multi-jvm` lane is `qualified`, the row cannot be `e2e-proved` (consistent with the Lane Semantics invariant already in this schema); it must be `blocked` or weaker.

These three rules are enforced structurally: the `qualification` validator subcommand guards the lane registry, and the `evidence` subcommand already enforces the `environment_class ≥ required_lane` invariant for `e2e-proved`.
