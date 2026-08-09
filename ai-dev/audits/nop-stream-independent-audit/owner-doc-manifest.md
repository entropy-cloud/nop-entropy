# nop-stream Independent Audit — Owner-Document Manifest (Stage 23)

> Status: frozen
> Frozen at: HEAD 2026-08-09
> Owner: nop-stream-independent-audit mission (Stage 23)
> Validator: `ai-dev/tools/check-nop-stream-audit-manifest.mjs docs-coverage [--strict]`

This manifest freezes the **finite owner-document surface** that Stage 23 (Documentation Contract and Readiness Decision) reviews against the frozen evidence corpus (Stages 6–16) and finding dispositions (Stages 18–22). Every document in this manifest MUST receive exactly one `@@DOC_REVIEW` block before Stage 23 closure. The review is bound by the frozen evidence; Stage 23 applies minimal corrections only for proven contract drift — it does not rewrite owner docs wholesale.

## Scope Boundaries

- **In scope**: the 19 documents listed below (3 `docs-for-ai/` + 16 `ai-dev/design/nop-stream/*.md`).
- **Out of scope**: every other document in the repo. Documents outside this manifest are not audited by Stage 23 and receive no `@@DOC_REVIEW`.
- **Review rule**: a `@@DOC_REVIEW` must classify each document as `reviewed-no-change`, `corrected`, or `out-of-scope`. A confirmed contract drift MUST be `corrected` (with `correction_summary` + `drift_ids`); it may not be silently skipped or downgraded to `reviewed-no-change`.

## Known Drift Inventory (consumed by this manifest)

These drift IDs are referenced by `@@DOC_REVIEW` blocks below. They were surfaced by the frozen evidence corpus (Stages 6–16) and finding dispositions (Stages 18–22):

| Drift ID | Source | Summary |
| --- | --- | --- |
| `module-groups-nop-stream-submodules` | Stage 4 manifest baseline | `docs-for-ai/01-repo-map/module-groups.md` listed 6 nop-stream submodules; live reactor has 10 |
| `EVID-S11-020` | Stage 11 evidence | `window-design.md` named session assigner `SessionEventTimeWindows`; live class is `EventTimeSessionWindows` |
| `M7-2-P1-16` | Stage 18 disposition (active/successor owner → roadmap-stage-23) | `TimestampsAndWatermarksOperator` placement drift (core/operators); partial mitigation existed, full sweep owned by Stage 23 |
| `EVID-S8-012` | Stage 8 evidence | `nop-stream-flow` deps cep+xdefs (pom-level fact); README/architecture doc-level claim convergence to confirm |

## Document Registry

The registry below (`@@DOC_ENTRY` blocks) freezes the 19 in-scope documents. Each entry declares the document path, its surface category, the expected review items, and any known drift IDs. The `@@DOC_REVIEW` blocks in the next section record the review result for each registered path.

@@DOC_ENTRY
doc_path: docs-for-ai/INDEX.md
surface: navigation
expected_review: nop-stream routing entries (module-groups, source-anchors, design README)
known_drift_ids: none
@@END

@@DOC_ENTRY
doc_path: docs-for-ai/01-repo-map/module-groups.md
surface: module-map
expected_review: nop-stream submodule list vs live reactor
known_drift_ids: module-groups-nop-stream-submodules
@@END

@@DOC_ENTRY
doc_path: docs-for-ai/04-reference/source-anchors.md
surface: source-anchors
expected_review: STRM-001+ nop-stream anchors vs live code paths
known_drift_ids: none
@@END

@@DOC_ENTRY
doc_path: ai-dev/design/nop-stream/00-vision.md
surface: design
expected_review: vision-level scope/constraints vs proven capabilities
known_drift_ids: none
@@END

@@DOC_ENTRY
doc_path: ai-dev/design/nop-stream/01-architecture-baseline.md
surface: design
expected_review: module dependency declarations vs live poms (flow deps cep+xdefs)
known_drift_ids: EVID-S8-012
@@END

@@DOC_ENTRY
doc_path: ai-dev/design/nop-stream/README.md
surface: design
expected_review: module overview, dependency declarations, operator placement
known_drift_ids: EVID-S8-012, M7-2-P1-16
@@END

@@DOC_ENTRY
doc_path: ai-dev/design/nop-stream/cep-design.md
surface: design
expected_review: CEP NFA/SharedBuffer/Pattern design vs Stage 12 evidence
known_drift_ids: none
@@END

@@DOC_ENTRY
doc_path: ai-dev/design/nop-stream/checkpoint-design.md
surface: design
expected_review: checkpoint lifecycle design vs Stage 9 evidence
known_drift_ids: none
@@END

@@DOC_ENTRY
doc_path: ai-dev/design/nop-stream/comparison.md
surface: design
expected_review: Flink-comparison claims, TimestampsAndWatermarksOperator references
known_drift_ids: M7-2-P1-16
@@END

@@DOC_ENTRY
doc_path: ai-dev/design/nop-stream/component-roadmap.md
surface: design
expected_review: component roadmap claims, operator auto-insertion
known_drift_ids: M7-2-P1-16
@@END

@@DOC_ENTRY
doc_path: ai-dev/design/nop-stream/connector-design.md
surface: design
expected_review: connector source/sink design vs Stages 15-16 evidence
known_drift_ids: none
@@END

@@DOC_ENTRY
doc_path: ai-dev/design/nop-stream/core-design.md
surface: design
expected_review: core DataStream/Transformation/graph design vs Stage 6 evidence
known_drift_ids: none
@@END

@@DOC_ENTRY
doc_path: ai-dev/design/nop-stream/failover-design.md
surface: design
expected_review: failover/recovery design vs Stages 13-14 evidence
known_drift_ids: none
@@END

@@DOC_ENTRY
doc_path: ai-dev/design/nop-stream/graph-model-design.md
surface: design
expected_review: graph model design, TimestampsAndWatermarksOperator integration
known_drift_ids: M7-2-P1-16
@@END

@@DOC_ENTRY
doc_path: ai-dev/design/nop-stream/mailbox-design.md
surface: design
expected_review: mailbox executor design vs Stage 6/13 evidence
known_drift_ids: none
@@END

@@DOC_ENTRY
doc_path: ai-dev/design/nop-stream/state-management-design.md
surface: design
expected_review: state backend design vs Stage 10 evidence
known_drift_ids: none
@@END

@@DOC_ENTRY
doc_path: ai-dev/design/nop-stream/stream-dsl-design.md
surface: design
expected_review: XDSL StreamModel design vs Stages 7-8 evidence
known_drift_ids: none
@@END

@@DOC_ENTRY
doc_path: ai-dev/design/nop-stream/time-model-design.md
surface: design
expected_review: watermark/TimestampsAndWatermarksOperator placement (core/operators)
known_drift_ids: M7-2-P1-16
@@END

@@DOC_ENTRY
doc_path: ai-dev/design/nop-stream/window-design.md
surface: design
expected_review: window assigner naming (session assigner), window semantics vs Stage 11 evidence
known_drift_ids: EVID-S11-020
@@END

## Document Reviews

Each `@@DOC_REVIEW` block records the review result for one registered `doc_path`. `@@DOC_REVIEW` blocks live in this manifest file (centralized), not scattered across the 19 owner docs.

@@DOC_REVIEW
doc_path: docs-for-ai/INDEX.md
review_status: reviewed-no-change
drift_ids: none
correction_summary: none
reviewer_evidence: nop-stream routing anchors (module-groups, source-anchors, design README) already registered in INDEX.md; no routing/anchor change required by Stage 23 drift corrections (module-groups submodule list and source-anchors paths remain valid after Phase 2 fixes)
@@END

@@DOC_REVIEW
doc_path: docs-for-ai/01-repo-map/module-groups.md
review_status: corrected
drift_ids: module-groups-nop-stream-submodules
correction_summary: Updated nop-stream submodule list from 6 to 10 to match live reactor (nop-stream/pom.xml <modules>). Added missing modules: nop-stream-connector-batch, nop-stream-connector-jdbc, nop-stream-connector-debezium, nop-stream-rocksdb; fixed stream-flow description to reflect XDSL StreamModel responsibility; removed stale "6 个" count
reviewer_evidence: cross-checked nop-stream/pom.xml <modules> (10 entries) + live directory enumeration; Stage 4 source-manifest.md uses live 10-module layout as fact baseline
@@END

@@DOC_REVIEW
doc_path: docs-for-ai/04-reference/source-anchors.md
review_status: reviewed-no-change
drift_ids: none
correction_summary: none
reviewer_evidence: All STRM-001..STRM-037 nop-stream anchor paths verified against live code (TimestampsAndWatermarksOperator @ STRM-031 = core/operators — correct; CepOperator, JdbcTwoPhaseCommitSink, DebeziumCdcSourceFunction, FileTwoPhaseCommitSink, SharedBuffer paths all exist). No refactor drift found
@@END

@@DOC_REVIEW
doc_path: ai-dev/design/nop-stream/00-vision.md
review_status: reviewed-no-change
drift_ids: none
correction_summary: none
reviewer_evidence: Vision-level scope/constraints (no self-built Netty stack, platform message infra delegation) consistent with proven evidence (Stage 14 data-plane); no capability claim drift
@@END

@@DOC_REVIEW
doc_path: ai-dev/design/nop-stream/01-architecture-baseline.md
review_status: reviewed-no-change
drift_ids: EVID-S8-012
correction_summary: none
reviewer_evidence: flow module dependency declaration (→ core, cep, xdefs at line 36/43) matches live nop-stream-flow/pom.xml (deps: nop-stream-core, nop-stream-cep, nop-xdefs). Doc-level claim and pom-level fact are consistent — no contradiction. EVID-S8-012 convergence confirmed
@@END

@@DOC_REVIEW
doc_path: ai-dev/design/nop-stream/README.md
review_status: reviewed-no-change
drift_ids: EVID-S8-012
correction_summary: none
reviewer_evidence: README line 96/132 declares nop-stream-flow → core, cep, xdefs; matches live pom.xml. TimestampsAndWatermarksOperator listed under core/operators (line 80) — correct placement. EVID-S8-012 convergence confirmed (doc claim = pom fact)
@@END

@@DOC_REVIEW
doc_path: ai-dev/design/nop-stream/cep-design.md
review_status: reviewed-no-change
drift_ids: none
correction_summary: none
reviewer_evidence: CEP design (NFA/SharedBuffer/Pattern) consistent with Stage 12 proven evidence (linear + branching pattern, timeout, skip strategies, checkpoint continuation); no contract drift
@@END

@@DOC_REVIEW
doc_path: ai-dev/design/nop-stream/checkpoint-design.md
review_status: reviewed-no-change
drift_ids: none
correction_summary: none
reviewer_evidence: Checkpoint lifecycle (aligned/unaligned/multi-epoch) consistent with Stage 9 proven evidence; fail-fast for unsupported combinations documented and evidenced
@@END

@@DOC_REVIEW
doc_path: ai-dev/design/nop-stream/comparison.md
review_status: reviewed-no-change
drift_ids: M7-2-P1-16
correction_summary: none
reviewer_evidence: Comparison doc references TimestampsAndWatermarksOperator in Flink-comparison context (lines 437/452); not a placement claim — live operator location (core/operators) is not contradicted. M7-2-P1-16 sweep confirms no residual placement drift in this doc
@@END

@@DOC_REVIEW
doc_path: ai-dev/design/nop-stream/component-roadmap.md
review_status: reviewed-no-change
drift_ids: M7-2-P1-16
correction_summary: none
reviewer_evidence: Line 133 states TimestampsAndWatermarksOperator auto-inserted in execute() via StreamGraph path — consistent with live behavior and Stage 6/11 evidence; no placement drift
@@END

@@DOC_REVIEW
doc_path: ai-dev/design/nop-stream/connector-design.md
review_status: reviewed-no-change
drift_ids: none
correction_summary: none
reviewer_evidence: Connector design (batch/jdbc/file/cdc source/sink) consistent with Stages 15-16 proven evidence; WatermarkEstimator deferred (non-goal) documented; no contract drift
@@END

@@DOC_REVIEW
doc_path: ai-dev/design/nop-stream/core-design.md
review_status: reviewed-no-change
drift_ids: none
correction_summary: none
reviewer_evidence: Core design (DataStream API, Transformation, StreamGraph/JobGraph) consistent with Stage 6 proven evidence; no contract drift
@@END

@@DOC_REVIEW
doc_path: ai-dev/design/nop-stream/failover-design.md
review_status: reviewed-no-change
drift_ids: none
correction_summary: none
reviewer_evidence: Failover design (global recovery, region-based failover, fencing) consistent with Stages 13-14 evidence; T2 lane capability-level gaps (EVID-S13-015/016, S14-013/014) are honestly classified as blocked in evidence, not contradicted by design claims
@@END

@@DOC_REVIEW
doc_path: ai-dev/design/nop-stream/graph-model-design.md
review_status: reviewed-no-change
drift_ids: M7-2-P1-16
correction_summary: none
reviewer_evidence: Line 258 references TimestampsAndWatermarksOperator as integrated into operator chain — consistent with live placement (core/operators) and Stage 6/11 evidence; no placement drift
@@END

@@DOC_REVIEW
doc_path: ai-dev/design/nop-stream/mailbox-design.md
review_status: reviewed-no-change
drift_ids: none
correction_summary: none
reviewer_evidence: Mailbox design (MailboxExecutor, block-on-empty, task thread model) consistent with Stage 6/13 proven evidence; no contract drift
@@END

@@DOC_REVIEW
doc_path: ai-dev/design/nop-stream/state-management-design.md
review_status: reviewed-no-change
drift_ids: none
correction_summary: none
reviewer_evidence: State backend design (memory/RocksDB, keyed/operator state, savepoint, rescale) consistent with Stage 10 proven evidence; no contract drift
@@END

@@DOC_REVIEW
doc_path: ai-dev/design/nop-stream/stream-dsl-design.md
review_status: reviewed-no-change
drift_ids: none
correction_summary: none
reviewer_evidence: XDSL StreamModel design consistent with Stages 7-8 proven evidence (XDSL + Delta entry paths); no contract drift
@@END

@@DOC_REVIEW
doc_path: ai-dev/design/nop-stream/time-model-design.md
review_status: reviewed-no-change
drift_ids: M7-2-P1-16
correction_summary: none
reviewer_evidence: TimestampsAndWatermarksOperator placement explicitly documented as core/operators (line 174: "core 模块 .../operators/") — correct. Watermark chapter (lines 172-228) and source-anchors.md:215 already mitigated in prior partial sweep. M7-2-P1-16 full sweep confirms no residual placement drift
@@END

@@DOC_REVIEW
doc_path: ai-dev/design/nop-stream/window-design.md
review_status: corrected
drift_ids: EVID-S11-020
correction_summary: Fixed session window assigner naming drift — renamed SessionEventTimeWindows to EventTimeSessionWindows to match live class (nop-stream-core/.../windowing/assigners/EventTimeSessionWindows.java). Live code has no SessionEventTimeWindows class
reviewer_evidence: cross-checked live class EventTimeSessionWindows in nop-stream-core/src/main/java/io/nop/stream/core/windowing/assigners/; Stage 11 evidence EVID-S11-020 confirmed the drift
@@END

## Completeness Note

This manifest contains exactly 19 `@@DOC_REVIEW` blocks (3 docs-for-ai + 16 design docs). The `docs-coverage --strict` validator confirms every registered `doc_path` exists in the repo and has exactly one review block with a legal vocabulary value and satisfied conditional fields.
