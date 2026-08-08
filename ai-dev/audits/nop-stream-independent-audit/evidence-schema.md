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

## Stage 18 Supplement — Finding-Disposition Schema

> Status: frozen supplement (Stage 18). This section is an **additive** rules block: it changes **neither** the 11 evidence-row fields (Field Specification table) **nor** the 7-value Disposition Vocabulary above. It introduces a **separate** `@@DISPOSITION` block format and a **separate** 5-value finding-disposition vocabulary used by Stages 18–22 to adjudicate the **terminal state** of each frozen-corpus finding. Authoritative text lives here; the `disposition` subcommand of `check-nop-stream-audit-manifest.mjs` enforces it.

### Relationship Between the Two Vocabularies (must not be conflated)

The audit uses **two distinct disposition vocabularies** that operate at different semantic layers:

| Layer | Vocabulary | Values | What it adjudicates | Block type |
| --- | --- | --- | --- | --- |
| **Capability** (evidence-row) | 7-value | `e2e-proved \| component-only \| unverified \| fail-fast \| non-goal \| residual-risk \| blocked` | Whether a **capability** is proven to the required lane strength | `@@EVIDENCE` |
| **Finding** (finding-disposition) | 5-value | `revalidated \| stale \| active/successor owner \| residual-risk \| blocked` | The **terminal state** of a specific frozen-corpus finding (was the defect fixed? did the anchor disappear? is it still live with an owner?) | `@@DISPOSITION` |

The two vocabularies **share** the value names `residual-risk` and `blocked`, but their **semantics are different** and they **must not be conflated**:

- An evidence-row `disposition: residual-risk` means a **capability** has a known limitation accepted as non-blocking (e.g., "cross-JVM distributed mutex cannot be proven in the in-process lane").
- A finding-disposition `residual-risk` means a **finding** (a specific defect) has been accepted as a non-blocking residual (e.g., "the bare-exception two-tier violation is non-blocking because the behavior is correct, only the exception type drifts").

A single finding may have multiple evidence rows (each adjudicating a different capability angle) with various 7-value dispositions, but receives **exactly one** 5-value finding-disposition.

### Finding-Disposition — 5-Value Vocabulary (frozen)

| Value | Meaning | Required conditional field |
| --- | --- | --- |
| `revalidated` | The defect described in the finding has been fixed or no longer holds against live code. The finding is resolved. | `revalidation_evidence` (test name / manual-trace `file:line` / cross-ref evidence row `inventory_id`) |
| `stale` | The anchor or context described in the finding has disappeared (file deleted, code refactored, premise no longer holds). The finding cannot be revalidated because its target no longer exists. | `stale_rationale` (what disappeared / why the premise is false) |
| `active/successor owner` | The finding is a confirmed still-live defect and has an owner: either a plan file in the repo, or a `roadmap-stage-<N>` sentinel pointing to a non-`done` roadmap stage. | `owner_plan` (repo plan path **or** `roadmap-stage-<N>` sentinel; validator verifies path exists or sentinel points to a non-`done` stage) |
| `residual-risk` | The finding describes a known limitation accepted as a non-blocking residual. Must include explicit non-blocking rationale. | `residual_rationale` (why this is non-blocking for the supported baseline) |
| `blocked` | The finding cannot be fully adjudicated because a required lane is not qualified (e.g., multi-jvm fencing test has a defect). | `blocked_lane` (a registered lane_id from `environment-qualification.md`) |

### Cross-Cutting Adjudication Rules

1. **P0/P1 still-live must have an owner**: a confirmed still-live P0 or P1 finding **must** fall to `active/successor owner` (with a valid plan path or sentinel). A P0/P1 still-live defect **must not** be silently downgraded to `residual-risk`. (Exception: if the defect has been fixed, it is `revalidated`, not "still-live".)
2. **P2 residual-risk requires explicit rationale**: every P2 `residual-risk` must include a non-blocking rationale before residual acceptance.
3. **Exactly one disposition per finding**: each frozen-corpus finding receives exactly one `@@DISPOSITION` block (completeness + no-dup).
4. **finding_id / severity / source_anchor consistency**: the `finding_id`, `severity`, and `source_anchor` in a `@@DISPOSITION` block must match the frozen corpus entry for that ID.

### `@@DISPOSITION` Block — Encoding Format

Each disposition is a `@@DISPOSITION ... @@END` record with flat `key: value` lines (same convention as `@@EVIDENCE` / `@@ENTRY` blocks). Example (illustrative only):

```
@@DISPOSITION
finding_id: M8-2-P0-1
severity: P0
source_anchor: nop-stream/nop-stream-runtime/.../JobCoordinator.java:889-1024
disposition: revalidated
revalidation_evidence: TestJobCoordinatorRecoveryConcurrency#concurrentGlobalRecovery_serializesToOneRotation; cross-ref EVID-S9-016
@@END
```

**Required fields** (all blocks): `finding_id`, `severity`, `source_anchor`, `disposition`.

**Conditional fields** (required when `disposition` value triggers them, see table above): `revalidation_evidence`, `stale_rationale`, `owner_plan`, `residual_rationale`, `blocked_lane`.

**Optional fields**: `note`, `successor_note`.

Disposition files live at `ai-dev/audits/nop-stream-independent-audit/stage-*-disposition.md`. The `disposition` subcommand scans these files (same pattern as the `evidence` subcommand scanning `*.evidence.md`).
