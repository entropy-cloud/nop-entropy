# Stage 8 — Delta StreamModel Entry Audit Evidence

> Status: produced by Stage 8 audit (plan `2026-08-08-0514-3-delta-streammodel-entry-audit.md`)
> Domain: manifest b/c/f/g (XDSL Delta overlay surface + `.stream.xml` Delta fixtures + Delta semantic test lane)
> Scope: Delta overlay entry path — every supported Delta overlay form must alter **only** the already-supported (Stage 7) StreamModel semantics, and every unsupported/coverage-gap combination must have an explicit fail-fast proof or non-goal/residual-risk adjudication. Non-Delta XDSL entry path is Stage 7 (done); this audit verifies the Stage 7 Support/Reject Matrix dispositions **still hold** when a node is introduced **via Delta merge**.
> Lane policy: only the `in-process` lane (single-JVM `.stream.xml` parse → `DslModelParser` Delta merge → `StreamModelDslBuilder.of(model).build()` → `env.execute()` → sink output) or stronger is credited for Delta-capability claims; `unit` lane is credited only for model-level properties (e.g. `computeFingerprint()`, which is a model computation not a pipeline execution); code-trace-only / no-evidence is `residual-risk` or `unverified`.
> Validator: `node ai-dev/tools/check-nop-stream-audit-manifest.mjs evidence --strict` (parses `@@EVIDENCE` rows from `*.evidence.md` direct children of this dir)

## Delta Overlay Support / Reject Matrix (frozen by this audit)

This matrix freezes the disposition of every Delta overlay form declared or conceivable for
`stream.xdef` (`nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/stream/stream.xdef`) with
respect to the **Delta-merged XDSL entry path**. It changes neither the 11 evidence-row fields nor
the 7-value disposition vocabulary. `stream.xdef:22` declares `xdef:support-extends="true"`, which
is the Delta enabler — the standard platform `DslModelParser` performs the merge (no custom parser).

| Delta overlay form | Supported? | Auto-activated? | environment_class | disposition | Evidence row |
| --- | --- | --- | --- | --- | --- |
| **Form A** — `x:extends` explicit base path (transform add/modify/delete + edge rewrite) | **SUPPORTED** | n/a (explicit) | `in-process` | `e2e-proved` | EVID-S8-001 |
| **Form B** — `_delta/default/` layered `x:extends="super"` | **SUPPORTED** | yes (`DeltaResourceStoreBuilder:73-77`) | `in-process` | `e2e-proved` | EVID-S8-002 |
| **custom-layer non-default** (`_delta/<custom>/`) | NON-GOAL | **NO** (silent unless `nop.core.vfs.delta-layer-ids`) | `none` | `residual-risk` | EVID-S8-003 |
| **`x:override`** merge instruction | NON-GOAL | n/a | `none` | `non-goal` (0 occurrences) | EVID-S8-004 |

**Fingerprint sensitivity (frozen by this audit):** `computeFingerprint()` hashes DAG topology
(sorted transform-id keys → `dagTopologyHash`) + `requirementsHash` + `checkpointParticipantsHash`
(`nop-stream-core/.../core/model/StreamModel.java:60-86`). It does **not** hash checkpoint
interval / parallelism. Therefore:
- **transform-level delta** (add/remove/rewrite a transform) → fingerprint **sensitive** (EVID-S8-005)
- **config-only delta** (checkpoint interval / parallelism only) → fingerprint **by-design invariant** (EVID-S8-006)

**Fail-fast under Delta (frozen by this audit):** the Stage 50 fail-fast in
`StreamModelDslBuilder.failFastOnUnsupportedRegistries():156-189` fires on the **merged** model, so
a delta-introduced unsupported registry still throws `UnsupportedOperationException` (not silently
bypassed). Of the 8 fail-fast registries, **only `<streams>` is re-tested under Delta**
(EVID-S8-007, `TestStreamModelDeltaFailFast`); the remaining 7 are `residual-risk` coverage gaps
(EVID-S8-008) assigned to Stage 17 successor.

---

## Evidence Rows

### Phase 1 — Delta Overlay Form Inventory & Transform-Level Evidence

@@EVIDENCE
inventory_id: EVID-S8-001
source_anchor: nop-stream/nop-stream-flow/src/test/resources/_vfs/nop/stream/test/test-delta-extends.stream.xml:11
declared_guarantee: Form A explicit-path Delta — x:extends="/nop/stream/test/test-delta-base.stream.xml" merges base+delta so the delta-added <filter> (deltaFilter) and rewritten edges (e1 redirected to deltaFilter, new e2) yield an executable topology whose sink output differs from the base (base ["A","B","C"] -> delta ["A","C"], dropping B)
implementation_anchor: nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/stream/stream.xdef:22
runtime_wiring: wired
positive_proof: TestStreamModelDeltaExtends#xExtendsDeltaProducesDifferentSinkOutput
rejection_proof: TestStreamModelDeltaExtends#xExtendsDeltaAddsFilterTransformNotPresentInBase
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S8-002
source_anchor: nop-stream/nop-stream-flow/src/test/resources/_vfs/_delta/default/nop/stream/test/test-delta-layered.stream.xml:13
declared_guarantee: Form B _delta/default/ layered Delta — x:extends="super" auto-activated by DeltaResourceStoreBuilder (:73-77 when delta-layer-ids empty and _delta/default exists) so the delta-added <map> (deltaLayerMap toUpperCase) and rewritten edges (e0 redirected, new e1) yield an executable topology whose sink output proves auto-activation (lowercase passthrough -> uppercase ["A","B","C"])
implementation_anchor: nop-kernel/nop-core/src/main/java/io/nop/core/resource/store/DeltaResourceStoreBuilder.java:72-77
runtime_wiring: wired
positive_proof: TestStreamModelDeltaExtends#layeredDefaultDeltaProducesUppercaseOutput
rejection_proof: TestStreamModelDeltaExtends#layeredDefaultDeltaAddsMapTransform
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S8-003
source_anchor: nop-kernel/nop-core/src/main/java/io/nop/core/resource/store/DeltaResourceStoreBuilder.java:72-77
declared_guarantee: custom-layer non-default non-activation — DeltaResourceStoreBuilder auto-activates ONLY the "default" layer (:73-77 when delta-layer-ids empty and _delta/default exists); a custom layer (_delta/<custom>/) is silently ignored unless nop.core.vfs.delta-layer-ids is set. There is NO fail-fast throw for a non-default layer — it is a silent no-op by design. Risk: a deployment expecting a custom layer to activate gets silent no-op with no error
implementation_anchor: nop-kernel/nop-core/src/main/java/io/nop/core/resource/store/DeltaResourceStoreBuilder.java:68-77
runtime_wiring: partial
positive_proof: manual-trace:nop-kernel/nop-core/src/main/java/io/nop/core/resource/store/DeltaResourceStoreBuilder.java:68-77
rejection_proof: none
environment_class: none
required_lane: in-process
finding_id: none
disposition: residual-risk
@@END

@@EVIDENCE
inventory_id: EVID-S8-004
source_anchor: nop-stream/nop-stream-flow/src/test/resources/_vfs/nop/stream/test/test-delta-extends.stream.xml
declared_guarantee: x:override merge instruction is unused — grep of all *.stream.xml under nop-stream for "x:override" returns 0 occurrences. The current supported Delta overlay forms are x:extends (explicit path) and x:extends="super" (layered). x:override is explicitly a non-goal for the current supported baseline (production plan Stage 51 non-goal)
implementation_anchor: none
runtime_wiring: unwired
positive_proof: none
rejection_proof: manual-trace:nop-stream/nop-stream-flow/src/test/resources/_vfs/nop/stream/test/
environment_class: none
required_lane: unit
finding_id: none
disposition: non-goal
@@END

### Phase 2 — Fingerprint Sensitivity & Fail-Fast Under Delta Evidence

@@EVIDENCE
inventory_id: EVID-S8-005
source_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/model/StreamModel.java:60-86
declared_guarantee: fingerprint transform-level sensitivity — computeFingerprint() hashes DAG topology (sorted transform-id keys -> dagTopologyHash :76-78) + requirementsHash + checkpointParticipantsHash; a transform-level delta (add/remove/rewrite a transform, e.g. delta adds deltaFilter) changes the transform-id set so dagTopologyHash differs and fingerprint differs. This is a model-level property proven by direct computeFingerprint() construction (unit lane), not by pipeline execute()
implementation_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/model/StreamModel.java:60-86
runtime_wiring: wired
positive_proof: TestStreamModelDeltaFingerprint#transformLevelDeltaProducesDifferentFingerprint
rejection_proof: none
environment_class: unit
required_lane: unit
finding_id: none
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S8-006
source_anchor: nop-stream/nop-stream-flow/src/test/resources/_vfs/nop/stream/test/test-delta-config-extends.stream.xml:15-18
declared_guarantee: fingerprint config-only invariance — a config-only delta (checkpoint interval 60000->30000, parallelism 2->1, identical transforms/edges) leaves the transform-id set unchanged so dagTopologyHash is unchanged and fingerprint is unchanged by design. computeFingerprint() does not hash checkpoint interval/parallelism (it hashes only transform-id topology + requirements + checkpointParticipants). Proven by direct computeFingerprint() construction (unit lane)
implementation_anchor: nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/model/StreamModel.java:60-86
runtime_wiring: wired
positive_proof: TestStreamModelDeltaFingerprint#configOnlyDeltaPreservesFingerprintByDesign
rejection_proof: none
environment_class: unit
required_lane: unit
finding_id: none
disposition: e2e-proved
@@END

@@EVIDENCE
inventory_id: EVID-S8-007
source_anchor: nop-stream/nop-stream-flow/src/test/resources/_vfs/nop/stream/test/test-delta-failfast-extends.stream.xml:10-15
declared_guarantee: <streams> fail-fast under Delta — a delta that introduces the unsupported <streams> registry still throws UnsupportedOperationException when built, because the Stage 50 fail-fast in StreamModelDslBuilder.failFastOnUnsupportedRegistries() fires on the MERGED model (delta merge does not bypass fail-fast). The merged model genuinely has <streams> (proves the delta was applied), then build() throws
implementation_anchor: nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/StreamModelDslBuilder.java:157-160
runtime_wiring: unwired
positive_proof: none
rejection_proof: TestStreamModelDeltaFailFast#deltaAddingStreamsRegistryStillFailsFast
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: fail-fast
@@END

@@EVIDENCE
inventory_id: EVID-S8-008
source_anchor: nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/StreamModelDslBuilder.java:161-188
declared_guarantee: 7 fail-fast registries NOT re-exercised under Delta — of the 8 fail-fast registries (streams/sideInputs/environments/schemas/coders/requirements/checkpointParticipants/onStart-onEnd-onError at :157-188), ONLY <streams> is re-tested under Delta (EVID-S8-007). The other 7 (sideInputs/environments/schemas/coders/requirements/checkpointParticipants/onStart-onEnd-onError) are proven fail-fast on the non-Delta path (Stage 7 EVID-S7-020..026) but have NO Delta-specific fixture. Delta-introduced unsupported node SHOULD still fail-fast (same builder runs on merged model), but only <streams> is PROVEN under Delta — the 7 are a coverage gap
implementation_anchor: nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/StreamModelDslBuilder.java:161-188
runtime_wiring: partial
positive_proof: none
rejection_proof: manual-trace:nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/StreamModelDslBuilder.java:156-189
environment_class: none
required_lane: in-process
finding_id: none
disposition: residual-risk
@@END

@@EVIDENCE
inventory_id: EVID-S8-009
source_anchor: nop-stream/nop-stream-flow/src/test/resources/_vfs/nop/stream/test/test-delta-extends.stream.xml:14-25
declared_guarantee: delta-introduced supported-node matrix — the Delta fixtures exercise only supported transforms (source/map/filter/sink), all of which build and execute correctly under Delta merge (Form A proves filter+sink+map via execution; Form B proves map+source+sink via execution). No Delta fixture exercises delta-introduced cep/window/aggregate (M7-2-P2-1 cross-ref: <cep> requires nop-stream-cep dep). Supported transforms proven under Delta: source/map/filter/sink = e2e-proved; cep/window/aggregate under Delta = unverified (no fixture)
implementation_anchor: nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/StreamModelDslBuilder.java:106-118
runtime_wiring: wired
positive_proof: TestStreamModelDeltaExtends#xExtendsDeltaProducesDifferentSinkOutput
rejection_proof: none
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: e2e-proved
@@END

### Phase 3 — Historical Finding Revalidation, Delta-vs-Non-Delta Equivalence & Coverage Gaps

@@EVIDENCE
inventory_id: EVID-S8-010
source_anchor: nop-stream/nop-stream-flow/pom.xml:22
declared_guarantee: M7-2-P2-1 revalidation — nop-stream-flow/pom.xml:22 declares dependency on nop-stream-cep (deps block :17 core, :22 cep, :27 xdefs). This is LOAD-BEARING for the XDSL <cep> transform (Stage 7 EVID-S7-010: AdvancedTransforms imports io.nop.stream.cep.* to implement <cep>), contradicting the architecture (flow -> core only) but live-functional. Delta path inherits this: no Delta fixture exercises delta-introduced <cep>. Successor: Stage 23 doc-contract
implementation_anchor: nop-stream/nop-stream-flow/pom.xml:17-27
runtime_wiring: partial
positive_proof: manual-trace:nop-stream/nop-stream-flow/pom.xml:17-27
rejection_proof: none
environment_class: unit
required_lane: unit
finding_id: M7-2-P2-1
disposition: residual-risk
@@END

@@EVIDENCE
inventory_id: EVID-S8-011
source_anchor: nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/model/
declared_guarantee: M7-2-P2-2 revalidation — nop-stream-flow/.../flow/model/ contains a duplicate source tree: 30 hand-authored classes + 30 generated classes under _gen/. The _gen/ tree is generated (manifest exclude rule) and does NOT inflate the denominator. NOTE corpus anchor typo: finding-corpus.md:159 records the path as "nop-stream/src/main/java/..." (missing the nop-stream-flow module segment); the correct path is nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/model/. Recorded for finding-corpus.md maintenance, not fixed in this audit-only plan
implementation_anchor: nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/model/_gen/
runtime_wiring: partial
positive_proof: manual-trace:nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/model/
rejection_proof: none
environment_class: unit
required_lane: unit
finding_id: M7-2-P2-2
disposition: residual-risk
@@END

@@EVIDENCE
inventory_id: EVID-S8-012
source_anchor: nop-stream/README.md:18
declared_guarantee: M7-2-P2-21 revalidation — nop-stream/README.md:18 now reads "nop-stream-flow | active | XDSL declarative stream orchestration, depends on core + cep (CepPatternModel) + nop-xdefs". The README doc-level claim is RESOLVED (no longer contradicts the pom). However the pom-level fact (flow deps cep+xdefs) remains TRUE. Successor: Stage 23 doc-contract to confirm closure of the pom/doc reconciliation
implementation_anchor: nop-stream/README.md:14-18
runtime_wiring: partial
positive_proof: manual-trace:nop-stream/README.md:14-18
rejection_proof: none
environment_class: unit
required_lane: unit
finding_id: M7-2-P2-21
disposition: residual-risk
@@END

@@EVIDENCE
inventory_id: EVID-S8-013
source_anchor: ai-dev/audits/nop-stream-independent-audit/stage-7-xdsl-streammodel-entry.evidence.md
declared_guarantee: Delta-vs-non-Delta equivalence — Stage 7 frozen Support/Reject Matrix dispositions hold for nodes proven under Delta merge: source/map/filter/sink are e2e-proved under Delta (EVID-S8-001/002/009 reuse the same builder dispatch as Stage 7 EVID-S7-001/002/003/013). Gap: cep/window/aggregate/flatMap have no Delta fixture (Stage 7 coverage gap EVID-S7-004/006/007/010 applies equally under Delta). Delta-introduced unsupported node still fails-fast (EVID-S8-007). Supported-under-Delta = e2e-proved; unsupported-under-Delta = fail-fast proven for <streams> only (EVID-S8-008 residual-risk for other 7)
implementation_anchor: nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/StreamModelDslBuilder.java:106-118
runtime_wiring: wired
positive_proof: TestStreamModelDeltaExtends#xExtendsDeltaProducesDifferentSinkOutput
rejection_proof: TestStreamModelDeltaFailFast#deltaAddingStreamsRegistryStillFailsFast
environment_class: in-process
required_lane: in-process
finding_id: none
disposition: residual-risk
@@END

@@EVIDENCE
inventory_id: EVID-S8-014
source_anchor: nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/StreamModelDslBuilder.java:94-100
declared_guarantee: Stage 7 EVID-S7-028 Delta impact — main/ has NO production loader/dispatcher/bean loading .stream.xml; the XDSL entry path is invokable only from test code. This applies EQUALLY to the Delta entry: a Delta-merged .stream.xml has no production loader either (the Delta entry is test-invokable only, identical to the non-Delta path). The Delta capability is proven in-process via tests but has no production wiring. Successor: production loader remediation plan
implementation_anchor: none
runtime_wiring: partial
positive_proof: none
rejection_proof: none
environment_class: none
required_lane: in-process
finding_id: none
disposition: residual-risk
@@END

---

## Cross-Reference Notes (corpus findings touched by this audit — final disposition owned by Stages 17/21/23)

- **M7-2-P2-1** (`nop-stream-flow/pom.xml` depends on `nop-stream-cep`): **residual-risk, LIVE.**
  Confirmed load-bearing for the XDSL `<cep>` transform (Stage 7 EVID-S7-010). The Delta path
  inherits this dependency (no Delta fixture exercises delta-introduced `<cep>`). Successor: Stage 23
  doc-contract. EVID-S8-010.
- **M7-2-P2-2** (`flow/model/` duplicate source tree): **residual-risk, LIVE + corpus anchor typo.**
  30 hand-authored + 30 `_gen/` generated classes. `_gen/` is excluded by manifest rule (does not
  inflate denominator). **Corpus anchor typo**: `finding-corpus.md:159` records
  `nop-stream/src/main/java/...` (missing `nop-stream-flow` module segment); correct path is
  `nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/model/`. Recorded for
  finding-corpus.md maintenance; not fixed in this audit-only plan. EVID-S8-011.
- **M7-2-P2-21** (README "flow depends only on core"): **residual-risk, doc-level RESOLVED /
  pom-fact LIVE.** `README.md:18` now correctly acknowledges `core + cep + nop-xdefs`. The
  pom-level fact remains TRUE. Successor: Stage 23 doc-contract to confirm closure. EVID-S8-012.

## Coverage Gaps Found (assigned to successor remediation per roadmap rule)

- **7 fail-fast registries not re-exercised under Delta** (sideInputs/environments/schemas/coders/
  requirements/checkpointParticipants/onStart-onEnd-onError). Proven fail-fast on the non-Delta path
  (Stage 7 EVID-S7-020..026) but no Delta-specific fixture. Delta-introduced unsupported node SHOULD
  still fail-fast (same builder runs on merged model), but only `<streams>` is PROVEN under Delta
  (EVID-S8-007/008). Coverage gap; successor: Stage 17 (test effectiveness).
- **No Delta fixture exercises delta-introduced `<cep>`/`<window>`/`<aggregate>`.** Current Delta
  fixtures use only source/map/filter/sink (all supported, EVID-S8-009). `<cep>` requires
  `nop-stream-cep` (M7-2-P2-1 cross-ref). Successor: test-coverage remediation plan.
- **No production loader for `.stream.xml`** (Stage 7 EVID-S7-028). Applies equally to the Delta
  entry (test-invokable only, EVID-S8-014). Successor: production loader remediation plan.
- **Custom-layer non-default silent no-op** (EVID-S8-003). `DeltaResourceStoreBuilder` silently
  ignores non-default layers unless `nop.core.vfs.delta-layer-ids` is set — no fail-fast throw.
  By-design, but a deployment misconfiguration risk. Recorded as residual-risk, not fixed in this
  audit-only plan.
