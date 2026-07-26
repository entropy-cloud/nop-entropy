> Audit Status: triaged
> Audit Type: open-ended
> Mission: nop-deep-analysis
> P2-only audit（0 P0 / 0 P1）→ 不起草 plan。4 个 P2 已转 follow-up backlog：`ai-dev/audits/nop-deep-analysis-audit-followups.md`。

# Open-Ended Adversarial Audit — mission `nop-deep-analysis`

- **Audit date**: 2026-07-26
- **Scope**: `./` — mission config (`missions/nop-deep-analysis.json`), roadmap (`ai-dev/design/nop-deep-analysis/nop-deep-analysis-roadmap.md`), 7 analysis deliverables under `ai-dev/analysis/2026-07/` (A1–A7), 7 plan files under `ai-dev/plans/nop-deep-analysis/`, and the public contracts those deliverables expose (factual claims + `file:line` anchors cross-referenced against actual platform source and `docs-for-ai/`).
- **Methodology**: Followed `ai-dev/skills/open-ended-adversarial-review-prompt.md` (discovery-oriented, code-signal-driven, false-positive calibration). One parallel explore subagent independently verified A2/A6 mechanism claims against live source; the main agent independently re-verified every distinctive claim it relied on (8 x:override modes, 5 HTTP endpoints, AOP codegen chain, xlib tag-count parity, nop-ai submodule count, batch.xdef path, dependency-matrix consumer counts) and cross-checked `ai-dev/audits/2026-07-26-0702-multi-audit-nop-deep-analysis.md` for dedup.
- **Dedup baseline**: The multi-dimensional audit (`2026-07-26-0702-multi-audit-nop-deep-analysis.md`) already recorded 3 P1 + 12 P2. This open-ended pass **does not repeat** those. Findings below are **new** relative to that audit. Where a multi-audit finding is referenced for context, it is cited as `(known: multi-audit Px-...)`.

## Calibration — distinctive claims independently verified CORRECT

To avoid false positives, the following load-bearing claims were checked against source and **passed**. These set the confidence floor (the deliverables are accurate on the claims that were tested):

| Claim (doc) | Verified against | Result |
|---|---|---|
| `XDefOverride` defines **8** modes (A1/A3/capstone S1) | `nop-kernel/nop-xlang/.../XDefOverride.java:19-50` (REMOVE/REPLACE/PREPEND/APPEND/MERGE/MERGE_REPLACE/BOUNDED_MERGE/MERGE_SUPER) | ✓ exactly 8 |
| **5 HTTP entries** `/graphql` `/r/` `/p/` `/px/` `/jsonrpc` converge to `IGraphQLEngine` (A2/A4/capstone S5) | `SpringGraphQLWebService.java` + `QuarkusGraphQLWebService.java` (`@PostMapping/@Path` for all five) | ✓ all 5 exist |
| AOP is **build-time source generation**: `GenAopProxy`→`__aop.java` + runtime `IAopProxy` interceptor array (A2 §6.4 / capstone S6) | `GenAopProxy.java`, `IAopProxy.java:16` (`$$aop_interceptors`), generated `*__aop.java` artifacts | ✓ correct, not CGLIB/ASM |
| NopIoC: file-based `beans.xml`, no annotation scan, **private fields not injectable** (A2/A6) | `AppBeanContainerLoader.loadBeansFile`, `ClassModelBuilder.java:404` (`if (Modifier.isPrivate(...)) continue;`) | ✓ correct |
| `ReflectionBizModelBuilder` reflective registration (A2 §5.2) | `ReflectionBizModelBuilder.java` exists, `build()` scans `@BizQuery/@BizMutation/@BizAction/@BizLoader` | ✓ correct |
| AMIS/Flux xlib **75/75 + 37/37 tag parity** (A4 §4.4) | `control.xlib`=75, `flux-control.xlib`=75, `web.xlib`=37, `flux-web.xlib`=37 (top-level `<tags>` children counted) | ✓ exact parity |
| `nop-ai` has **21 submodules** (A5 §1.1) | `nop-ai/pom.xml` `<module>` count = 21 | ✓ exactly 21 |
| `nop-graph` algorithm classes (A5 §1.1) | `Bfs/BetweennessCentrality/PageRank/ImpactPropagator/LeidenDetector/TarjanSCC` all exist under `nop-graph/nop-graph-core/.../algorithm/` | ✓ all 6 exist |
| `batch.xdef` path `nop/schema/task/batch.xdef` (A5 §6) | `_vfs/nop/schema/task/batch.xdef` exists | ✓ correct |
| A5 §2.2 dependency matrix consumer counts | re-derived `*-api` consumers from `pom.xml` (`nop-auth-api`=0, `nop-code-api`=1, `nop-graph-api`=2 …) | ✓ internally consistent (self-consumption counted uniformly) |
| AOP `__aop.java` naming (capstone S6) | generated artifacts `AuditServiceImpl__aop.java` et al. | ✓ correct |
| `check-plan-checklist.mjs` / `check-doc-links.mjs` exist (A7 plan Closure Gates) | both present under `ai-dev/tools/` | ✓ exist |

**Conclusion from calibration**: the deliverables are overwhelmingly accurate on platform-internal mechanism claims. The defects found by the multi-audit (findCount scalar, phantom `nop-stream-flink`, duplicate `GQL-002` id) are genuinely isolated, not symptoms of a broader accuracy problem. The findings below are correspondingly minor.

---

## Findings (new, not in multi-audit)

### [OA-1] `[P2]` Capstone (a `resolved`, closure-audited doc) cites `_tmp/` scratch files as provenance for a load-bearing completeness count
- **Priority justification**: Real traceability fragility in a permanent artifact, but non-blocking — the conclusions themselves are in the doc body and do not depend on `_tmp/` presence; only the audit-completeness proof points at ephemeral storage.
- **File**: `ai-dev/analysis/2026-07/2026-07-26-nop-platform-deep-introduction.md:279, 337, 365`
- **Evidence**:
  ```
  :279  本节是 Phase 4 产出。逐项裁定 Phase 1 登记的 22 项 deferred / open-question（详见 `_tmp/a7-phase1-working-draft.md` §3 全量登记）。
  :337  - **原始登记总数**：22 项纳入（来自 `_tmp/a7-phase1-working-draft.md` §3）+ 5 项排除 + 跨章节合并 ... = 27 个独立 open-question 源
  :365  - 工作草稿（Phase 1 + Phase 2 产出）：`_tmp/a7-phase1-working-draft.md`、`_tmp/a7-phase2-working-draft.md`
  ```
- **Status**: `AGENTS.md` ("Temporary files: Use `_tmp/` ... for ALL temporary files, scratch data, and intermediate outputs") defines `_tmp/` as scratch. The capstone is `Status: resolved` and its §8.5 "裁定完整性核对" is the closure-evidence block — yet it derives its headline "22 项纳入" count from `_tmp/a7-phase1-working-draft.md §3`. The A7 plan's Phase-1 Exit Criteria (`ai-dev/plans/nop-deep-analysis/2026-07-26-0703-1-a7-capstone-deep-introduction.md:117-121`) similarly point all evidence to `_tmp/`.
- **Risk**: If `_tmp/` is cleaned (it is the designated scratch area; nothing pins these files into version-controlled permanence), the §8.5 completeness proof becomes non-reproducible — a future auditor re-checking "are all 22 items really accounted for?" cannot follow the citation chain. The `_tmp` files do exist today, so this is latent, not yet broken.
- **Recommendation**: Either (a) inline the 22-item registry into the capstone's §8 (or an `ai-dev/` sibling) so the count is self-proving, or (b) if `_tmp` working drafts are to be cited as evidence by a `resolved` doc, promote them out of `_tmp/`.
- **Confidence**: certain.
- **Source perspective**: 异常路径侦探 / future-auditor continuity.

### [OA-2] `[P2]` A6 misquotes the canonical module skeleton, dropping the `codegen` step it cites
- **Priority justification**: Verbatim misquote of the cited source line; conceptually significant (the dropped step is the subject of the entire A3 deliverable) but locally minor since codegen is documented everywhere else.
- **File**: `ai-dev/analysis/2026-07/2026-07-24-nop-engineering-dx-ai-dev.md:150`
- **Evidence**:
  ```
  A6:150    每个业务模块都遵循 `model → dao → meta → service → web → app → api` 骨架（`docs-for-ai/INDEX.md:207`）。
  INDEX.md:207  业务模块普遍遵循 `model -> codegen -> dao -> meta -> service -> web -> app -> api` 骨架。
  ```
- **Status**: A6 quotes INDEX.md:207 but omits the `codegen` hop between `model` and `dao`. The citation is to the exact line, so this is a quoting error, not a paraphrase.
- **Risk**: A reader taking the skeleton at face value misses that codegen is a first-class pipeline stage (the core thesis of A3); undermines the "生成即一等公民" point A6 itself makes two lines later (`:151`).
- **Recommendation**: Restore the `codegen` step in the quoted skeleton, or drop the precise `:207` citation if shortening intentionally.
- **Confidence**: certain.

### [OA-3] `[P2]` A2 Conclusion self-contradicts: label says "六大引擎模块" but the parenthetical lists 7
- **Priority justification**: Internal count contradiction in a load-bearing summary statement; narrative grouping only, no behavioral claim, hence P2 not P1.
- **File**: `ai-dev/analysis/2026-07/2026-07-24-nop-core-engine-deep-dive.md:373`
- **Evidence**:
  ```
  本分析建立了六大引擎模块（nop-core / nop-xlang / nop-xdef / nop-dao / nop-orm / nop-graphql / NopIoC）的职责矩阵 ...
  ```
- **Status**: The label "六大" (six) is immediately followed by 7 enumerated modules (core, xlang, xdef, dao, orm, graphql, ioc). The doc title treats persistence as one (`nop-dao`/`nop-graphql`/`NopIoC`), but the Conclusion splits `nop-orm` out separately, creating the off-by-one. Independently confirmed by explore subagent.
- **Risk**: Reader counts the parenthetical and gets 7 vs the claimed 6; minor confusion in the summary that A7 capstone inherits (capstone §2 architecture graph also references the A2 engine layer).
- **Recommendation**: Either say "七大" or fold `nop-orm` back under `nop-dao` in the parenthetical to match the six-module framing used elsewhere in A2.
- **Confidence**: certain.

### [OA-4] `[P2]` Mission `commands.test` is structurally a no-op: `; echo` masks the doc-links exit code
- **Priority justification**: The mission's only configured quality gate cannot fail by construction; by-design for an analysis mission (explicit "non-blocking" comment) and the closure audit ran the real check manually, so impact is near-zero. Recorded for completeness.
- **File**: `missions/nop-deep-analysis.json:15`
- **Evidence**:
  ```
  "test": "node ai-dev/tools/check-doc-links.mjs --strict; echo \"doc-links check (non-blocking, analysis mission)\""
  ```
- **Status**: The `;` (not `&&`) followed by `echo` means the compound command always exits 0 (`echo`'s exit code), regardless of whether `check-doc-links.mjs` reports broken links. So the mission-driver CHECK step's "test" is vacuous for this mission. The `echo` label even says "non-blocking", so this is intentional — but it means a future regression (a broken link introduced into these deliverables) would never block the loop.
- **Risk**: Near-zero today (mission is `done`; closure audit already verified links manually at A7 plan `:302` — "退出码 1（2 errors … 与本 mission 无关）"). The only latent risk is if the mission is re-opened: the automated gate would not catch doc-link regressions, relying again on manual closure audit.
- **Recommendation**: Acceptable as-is for an analysis mission; if a real gate is ever wanted, drop the `; echo` (or gate on the node exit code explicitly) and scope the checker to mission files only.
- **Confidence**: certain.
- **Note**: borderline trivial / by-design — recorded so it is not "discovered" afresh next audit.

---

## Dimensions probed and found clean (no new findings)

- **A1 (`nop-theory-foundation.md`)** — independently re-read §1–§1.4; the precision notes (e.g. §1.4 explicitly states "生成即逆元" is **not** a literal quote of the three core theory docs) show careful scholarship. Anchor-driven claims spot-checked correct. Confirms multi-audit's 0-findings on A1. ✓
- **A3 (`nop-model-driven-and-codegen.md`)** — model-first / `_gen` / Delta `x:extends`/`x:override` / "generated files must not be hand-edited" all consistent with `AGENTS.md` Hard Stop and verified source mechanics. Confirms multi-audit's 0-findings on A3. ✓
- **A4 (`nop-graphql-service-frontend.md`)** — beyond the known `(known: multi-audit P1-A4-01 findCount)` and `(known: P2-A4-02 INopJobSchedule count)`, the frontend-rendering chain (`gen-page.xgen` → `main.page.yaml` → `web:GenPage` → `impl_GenPage.xpl` → page-type dispatch) and the AMIS/Flux parity claims (75/75, 37/37 xlib tags) verified exact. dict `_label` mechanism and `relKind` control-matching layer are honestly flagged as A4 Open Questions, not hidden. ✓
- **A5 (`nop-module-matrix.md`)** — beyond `(known: P1-A5-02 phantom nop-stream-flink)` and `(known: P2-A5-01 nop-plugin nesting)`, every other module-existence claim (`nop-tcc`, `nop-retry`, `nop-dyn`, `nop-graph` algorithms, `nop-ai`=21 submodules) and the §2.2 `*-api` consumer counts re-derived from `pom.xml` are sound. ✓
- **A7 §8 adjudication completeness** — cross-walked the plan's Current-Baseline item list (item 1 + A1§6 ×5 + A2§8 ×4 + A3§7 ×2 + A4 OpenQ ×4 + A5 OpenQ ×4 + A6§7 ×4) against capstone §8.1–§8.4; every in-scope item resolves to a row, every excluded item is listed with reason. No silently-dropped in-scope item. (The numeric drift in §8.5 is `(known: multi-audit P2-A7-01)`.) ✓
- **Capstone mermaid ↔ evidence table** — `(known: multi-audit P2-A7-02)` covers API/OPS node mapping; nothing else surfaced. ✓
- **External/web-research comparison claims** — out of scope per the open-ended prompt's false-positive calibration (only platform-internal factual claims were verified against source; URLs were checked for format/plausibility only, not fetched).

## Dedup note vs multi-audit

All three multi-audit P1s (`findCount` Int/Long, phantom `nop-stream-flink`/`nop-stream-checkpoint`, duplicate `GQL-002`→`GQL-008` anchor) and all 12 multi-audit P2s were reviewed. **None are re-reported here.** Two of the three P1s are root-caused in `docs-for-ai/` (`module-groups.md:23`, `source-anchors.md:81`) — that root-cause characterization is accurate and is not duplicated.

## Overall assessment

The nop-deep-analysis deliverables are, on the claims an independent auditor can verify against source, **highly accurate**. The four new findings here are all P2: two are quoting/counting slips inside otherwise-correct prose (OA-2 skeleton misquote, OA-3 "六大" vs 7), one is a doc-lifecycle hygiene issue (OA-1 `_tmp` provenance in a resolved doc), and one is a by-design no-op gate (OA-4). The most attention-worthy directions for whoever triages this mission next remain the ones the multi-audit already flagged — the two `docs-for-ai/` root-cause drifts (`module-groups.md` phantom submodules, `source-anchors.md` duplicate ID) — because every downstream consumer of those authoritative docs re-inherits the drift. My open-ended pass did **not** uncover a new P0/P1 beyond those.

## Audit blind spots (self-assessment)

- **External/web-research claims were not fetched/verified.** The multi-audit explicitly excluded these too, so this remains an open verification gap across both audits: ~80 external URLs across A1–A7 (Spring/Quarkus/Hasura/Federation/Formily/LangGraph/Drools/JasperReports/SDD/etc.) cite specific feature claims and dates (e.g. "2025-08 GitHub Copilot AGENTS.md support", "Java 25 framework startup", "~41% code AI-generated") that were not checked against their sources.
- **Exhaustive per-line verification of ~280 KB of analysis prose was not performed**; "load-bearing / distinctive" claims were prioritized. Secondary citations in dense tables (e.g. individual source-anchor `file:line` pins beyond those spot-checked) may still harbor line-number rot of the kind the multi-audit sampled.
- **A2/A4/A5/A6 deliverables' references into `ai-dev/analysis/` prior reports** (e.g. "复用 `…-comparison.md` 结论") were not followed — i.e. whether the *prior* analyses actually support the claims A1–A6 attribute to them was assumed, not re-verified.
- The `_tmp` working drafts themselves were not audited for internal accuracy (they are scratch); only their *citation by a resolved doc* is reported (OA-1).

## Priority distribution

| Priority | Count | Drives remediation plan? | Main categories |
|----------|-------|--------------------------|-----------------|
| P0 | 0 | — | — |
| P1 | 0 | — | — (all material P1s already in multi-audit) |
| P2 | 4 | no (backlog) | doc-lifecycle/traceability (OA-1), quoting accuracy (OA-2), internal count contradiction (OA-3), by-design no-op gate (OA-4) |

Per the audit triage rule, this is a **P2-only** audit → triaged to follow-up backlog without a standalone remediation plan. The two `docs-for-ai/` root-cause drifts already documented in the multi-audit remain the actionable P1 track.

<AI_STEP_RESULT>issues</AI_STEP_RESULT>
