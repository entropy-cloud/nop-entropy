> Audit Status: closed
> Audit Type: multi-dimensional
> Mission: nop-deep-analysis
> Routed to: `ai-dev/plans/nop-deep-analysis/2026-07-26-0816-1-doc-contract-drift-remediation.md`（3 个 P1 全部纳入，plan Status: completed，closure 见 plan `## Closure` 段）。12 个 P2 已转 follow-up backlog：`ai-dev/audits/nop-deep-analysis-audit-followups.md`。

# Multi-Dimensional Audit — mission `nop-deep-analysis`

- **Audit date**: 2026-07-26
- **Scope**: `./` — mission config (`missions/nop-deep-analysis.json`), roadmap (`ai-dev/design/nop-deep-analysis/nop-deep-analysis-roadmap.md`), 7 analysis deliverables under `ai-dev/analysis/2026-07/`, plan files under `ai-dev/plans/nop-deep-analysis/`, and the public contracts those deliverables expose (factual claims + `file:line` anchors cross-referenced against actual platform code and `docs-for-ai/`).
- **Mission nature**: analysis/documentation mission (no build/lint/typecheck; quality gate = closure audit). Audit is therefore adapted to: (1) config/roadmap correctness, (2) deliverable completeness, (3) reference-anchor validity, (4) doc–code consistency / contract drift, (5) capstone–source consistency, (6) numeric/statistical accuracy. Java-module dimensions (01/04/05/07…) that assume ORM/codegen/BizModel products are N/A — there is no generated code under audit.
- **Baseline run**: `node ai-dev/tools/check-doc-links.mjs --strict` → 40 BROKEN_LINK issues, **none** inside `nop-deep-analysis` deliverables or plans (all in other missions' files). Deliverables are link-clean.

## Methodology

Followed `ai-dev/skills/deep-audit-prompts.md` shared prefix (live-code baseline, false-positive calibration, ≥9-line finding format). Four parallel explore subagents verified the 7 deliverables' anchors and load-bearing claims against actual platform source; the main agent independently confirmed every P1 candidate and cross-referenced against the authoritative architecture docs (`module-groups.md`, `source-anchors.md`, `INDEX.md`) to locate **documented contract drift** at the root.

## Finding priority summary

| Priority | Count | Drives remediation plan? |
|----------|-------|--------------------------|
| P0 | 0 | — |
| P1 | 3 | yes |
| P2 | 12 | backlog only |

**No P0.** No claim asserts incorrect platform *behavior at runtime*, no data-loss/security issue, and the analysis mission has no executable test to be absent. The P1s are (a) one definitively wrong behavioral assertion in a deliverable, and (b) two cases where deliverables faithfully **propagate contract drift already present in the authoritative architecture docs** — the root-cause fix belongs in `docs-for-ai/`.

---

## P1 findings

### [P1-A4-01] `findCount` GraphQL return type stated as `Int`; actual is `Long`
- **Priority justification**: Definitively wrong assertion about generated GraphQL schema behavior that will mislead any reader implementing `graphql:queryMethod="findCount"`.
- **Doc location**: `ai-dev/analysis/2026-07/2026-07-24-nop-graphql-service-frontend.md:113`
- **Claimed**: `graphql:queryMethod` return-type mapping — "`findCount`→Int、`findFirst`→单对象、`findList`→`[对象]`、`findPage`→`PageBean`、`findConnection`→`Connection`".
- **Evidence (actual)**: `nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/utils/GraphQLObjMetaHelper.java:27-28`
  ```java
  if (queryMethod == GraphQLQueryMethod.findCount)
      return GraphQLScalarType.Long.name();
  ```
  Corroborated by `GraphQLScalarType.java:25` (`Long(StdDataType.LONG)`) and `ICrudBiz.java:38-39` (`@BizQuery long findCount(...)`). The other four mappings in the same sentence are verified correct, isolating this as a factual error, not a paraphrase.
- **Impact**: Strict GraphQL clients consuming a `findCount` field will type-mismatch; readers will write frontend code against the wrong scalar.
- **Why not false positive**: A scalar type name is a factual reference directly contradicted by the authoritative type-mapping code.

### [P1-A5-02 / DRIFT-1] `nop-stream-flink` and `nop-stream-checkpoint` cited as real nop-stream submodules — they do not exist (root cause: architecture doc `module-groups.md` is itself drift)
- **Priority justification**: Material contract drift in the authoritative module map, propagated verbatim into the analysis deliverable. Module existence is the primary structural contract.
- **Doc location**: `ai-dev/analysis/2026-07/2026-07-24-nop-module-matrix.md:207-208` (§5.3 lists `nop-stream-flink` as a nop-stream component and as evidence of "API 对齐 Flink、实现自研" intent).
- **Claimed**: `nop-stream-flink`（Flink API 兼容层）and (in the source doc) `nop-stream-checkpoint`（检查点存储抽象）are nop-stream submodules.
- **Evidence (actual)**: `nop-stream/pom.xml:17-22` declares only 6 submodules:
  ```xml
  <module>nop-stream-core</module>
  <module>nop-stream-cep</module>
  <module>nop-stream-connector</module>
  <module>nop-stream-runtime</module>
  <module>nop-stream-flow</module>
  <module>nop-stream-fraud-example</module>
  ```
  No `nop-stream-flink` / `nop-stream-checkpoint` dir or pom anywhere under `nop-stream/`. The Flink-API-compatible code actually lives **inside** `nop-stream-core` (`DataStreamSource.java`, `Transformation.java`, `StreamGraph.java`, `JobGraph.java`).
- **Root-cause (cross-reference against architecture docs)**: `docs-for-ai/01-repo-map/module-groups.md:23` invents both phantom submodules:
  ```
  …`nop-stream-checkpoint`（检查点存储抽象）、`nop-stream-flow`（流控）、`nop-stream-flink`（Flink API 兼容层）…
  ```
  A5 copied the authoritative doc without verification. **The fix must land in `module-groups.md` first**, else every downstream consumer re-inherits the drift.
- **Impact**: Any reader/module-matrix consumer will search for non-existent submodules; the "self-built Flink-compat layer as a separate module" narrative is structurally false.
- **Why not false positive**: `pom.xml <module>` is the source of truth for module existence; both the deliverable and the architecture doc directly contradict it.

### [P1-A2-01 / DRIFT-2] Anchor ID `GQL-008` misused for `GraphQLWebService`; root cause: `source-anchors.md:81` reuses label `GQL-002` (duplicate ID)
- **Priority justification**: Wrong anchor reference in the deliverable, caused by a duplicate-ID defect in the authoritative anchor registry. The deliverable also contradicts itself on what `GQL-008` means.
- **Doc location**: `ai-dev/analysis/2026-07/2026-07-24-nop-core-engine-deep-dive.md:43, 59, 203` (cite `GQL-008` for `GraphQLWebService`) vs `:226` (uses `GQL-008` correctly for `GraphQLConstants`).
- **Claimed**: `[GQL-008: GraphQLWebService.java:229]`, `GraphQLWebService（抽象类，GQL-008）`, etc.
- **Evidence (actual)**: `docs-for-ai/04-reference/source-anchors.md:80` defines `GQL-008` = `GraphQLConstants.java`. Line 81 — the `GraphQLWebService.java` entry — is mislabeled `GQL-002`, duplicating line 74's `GQL-002` (`obj-schema.xdef`):
  ```
  74: | GQL-002 | …/obj-schema.xdef … | graphql:* 属性的 schema 定义完整集合 …
  81: | GQL-002 | …/web/GraphQLWebService.java (runGraphQL / runRest / …) | …所有 HTTP 入口… |   ← duplicate ID
  ```
  So there is no valid unique GQL ID for `GraphQLWebService`; A2 grabbed the nearest plausible one (`GQL-008`), then used the same ID for the genuinely-correct `GraphQLConstants` row at line 226 — an internal inconsistency.
- **Root-cause (cross-reference against architecture docs)**: `source-anchors.md:81` must be relabeled (e.g. `GQL-009`); then A2's three wrong `GQL-008`→`GraphQLWebService` citations must be repointed.
- **Impact**: ID-based lookup of `GQL-008` lands on the wrong file; capstone A7 inherits the same anchor vocabulary.
- **Why not false positive**: The `file:line` part (`GraphQLWebService.java:229`) is itself correct, so content is still locatable — hence P1, not P0.

---

## P2 findings (recorded; backlog, no standalone remediation plan)

### [P2-A2-02] `CoreConstants` line cite off-by-one for `.annotations` suffix
- **Priority justification**: Line-number rot; content on the immediately adjacent line.
- **Doc location**: `ai-dev/analysis/2026-07/2026-07-24-nop-core-engine-deep-dive.md:280`
- **Claimed**: Both `/nop/aop` path and `.annotations` suffix attributed to `CoreConstants.java:26`.
- **Evidence (actual)**: `nop-kernel/nop-core/.../CoreConstants.java` — L26 `ANNOTATION_REGISTRY_PATH = "/nop/aop";`, L27 `FILE_POSTFIX_ANNOTATIONS = ".annotations";`.
- **Impact**: Trivial.
- **Why not false positive**: Doc pins two facts to one line that holds only one.

### [P2-A2-03] `graphql:*` constants "全集" range `L26-43` is incomplete
- **Priority justification**: Range too narrow for a "complete set" claim; constants still locatable in-file.
- **Doc location**: `ai-dev/analysis/2026-07/2026-07-24-nop-core-engine-deep-dive.md:226`
- **Claimed**: "`graphql:*` 属性名常量全集定义于 `GraphQLConstants.java`(GQL-008, L26-43)".
- **Evidence (actual)**: full set spans L23-45 (`ATTR_GRAPHQL_DICT_VALUE_PROP` L23, `ATTR_GRAPHQL_DICT_NAME` L24, `ATTR_GRAPHQL_PROP` L25 before; `TAG_GRAPHQL_TRANS_FILTER` L45 after).
- **Impact**: Minor.
- **Why not false positive**: "全集" explicitly asserts completeness; 4 edge constants omitted.

### [P2-A2-04] `AppBeanContainerLoader` L170-185 conflates two distinct code locations
- **Priority justification**: Two mechanisms joined under one range that matches only the first.
- **Doc location**: `ai-dev/analysis/2026-07/2026-07-24-nop-core-engine-deep-dive.md:240`
- **Claimed**: L170-185 covers both `/nop/autoconfig/*.beans` resources and the `nop.ioc.app-beans.files` config supplement.
- **Evidence (actual)**: L170-185 = `getAutoConfigResources()` only; the `nop.ioc.app-beans.files` handling is at L144-149 inside `loadBeansFile()`.
- **Impact**: Reader looks for the config-supplement logic at the wrong range; same file, still locatable.
- **Why not false positive**: Two distinct mechanisms named under one range.

### [P2-A4-02] `INopJobScheduleBiz` declares **6** extra `@BizMutation` methods, doc says **5**
- **Priority justification**: Numeric inaccuracy; the doc lists all six names immediately after, so self-correctable.
- **Doc location**: `ai-dev/analysis/2026-07/2026-07-24-nop-graphql-service-frontend.md:50`
- **Claimed**: "声明 **5 个额外** `@BizMutation`（…`enableSchedule`/`disableSchedule`/`pauseSchedule`/`resumeSchedule`/`triggerNow`/`archiveSchedule`）".
- **Evidence (actual)**: `nop-job/nop-job-dao/.../INopJobScheduleBiz.java:14-31` has six `@BizMutation` annotations; the enumerated list also has 6 items.
- **Impact**: Internal contradiction within the same sentence; minor.
- **Why not false positive**: Count and enumeration disagree with each other and with the source.

### [P2-A5-01] `nop-plugin` classified as a top-level infrastructure module; actually nested under `nop-core-framework`
- **Priority justification**: Module-nesting classification error; description is otherwise accurate.
- **Doc location**: `ai-dev/analysis/2026-07/2026-07-24-nop-module-matrix.md:5, 52` (§1.2 "基础设施模块" row lists `nop-plugin` as peer of `nop-network`/`nop-search`/`nop-cluster`/`nop-message`).
- **Evidence (actual)**: no `nop-plugin*` at repo root; actual location `nop-core-framework/nop-plugin/{nop-plugin-api,nop-plugin-manager,nop-plugin-support}/`. Root `pom.xml` lists `nop-core-framework`, not `nop-plugin`. `module-groups.md` does not mention `nop-plugin` at all.
- **Impact**: Reader looks for a non-existent top-level dir.
- **Why not false positive**: Presented as a top-level peer in an infra-module matrix without a nesting caveat.

### [P2-A6-01] "`ai-dev/` 七层知识层" miscount — A6's own table has 8 rows
- **Priority justification**: Numeric inconsistency with the table immediately below; cosmetic.
- **Doc location**: `ai-dev/analysis/2026-07/2026-07-24-nop-engineering-dx-ai-dev.md:5,6,13,29,67,69` ("七层"); table at `:71-80`.
- **Evidence (actual)**: §2.2 table enumerates 8 roles (`logs/plans/design/analysis/discussions/bugs/audits/skills`); `AGENTS.md` itself lists 9 (adds `lessons/`).
- **Impact**: Minor reader confusion.
- **Why not false positive**: Heading count directly contradicts the table right under it.

### [P2-A6-02] "`docs-for-ai/` 七区结构" miscount — INDEX.md has 9 entries, A6's own table has 8
- **Priority justification**: Numeric miscount of a structural inventory.
- **Doc location**: `ai-dev/analysis/2026-07/2026-07-24-nop-engineering-dx-ai-dev.md:158` (§4.2).
- **Evidence (actual)**: `docs-for-ai/INDEX.md:191-202` "目录角色" table has 9 entries (`00-start-here / 01-repo-map / 02-core-guides / 03-modules / 03-runbooks / 04-reference / 06-extensibility / 90-maintenance / 05-examples`); A6's replicated table (L160-169) lists 8 (omits `90-maintenance/`).
- **Impact**: Minor.
- **Why not false positive**: "七" is wrong by either count.

### [P2-A6-03] `source-anchors` "~90 个锚点" undercounts (actual ≈180)
- **Priority justification**: Material undercount, but it is a single statistic, not a structural claim.
- **Doc location**: `ai-dev/analysis/2026-07/2026-07-24-nop-engineering-dx-ai-dev.md:200` (§4.4).
- **Evidence (actual)**: `source-anchors.md` defines 180 unique anchor IDs across 35 series (`AIREL…XLANG`); "~90" is a 2× undercount.
- **Impact**: Reader underestimates anchor coverage.
- **Why not false positive**: 180 vs ~90 is not a rounding difference.

### [P2-A6-04] `events.jsonl` step-counts in §2.3 are stale (3 of 8 figures wrong)
- **Priority justification**: Numbers verifiably off against the cited file; qualitative claim still valid.
- **Doc location**: `ai-dev/analysis/2026-07/2026-07-24-nop-engineering-dx-ai-dev.md:91` (§2.3).
- **Evidence (actual)**: re-count of the cited `_tmp/2026-07-24-190404-mission-driver/events.jsonl` yields `EXECUTE=12` (doc: 11), `CLOSURE_SCRIPT_CHECK=12` (doc: 10), `pass=39` (doc: 37), plus unmentioned `fail=2`. `run-state.json` shows `status=aborted` — A6 captured an intermediate snapshot.
- **Impact**: Three quoted stats are concretely wrong; narrative holds.
- **Why not false positive**: Counts disagree with the very file cited.

### [P2-A7-01] §8.5 deferred-items tally is arithmetically inconsistent (22/27 stated; actual 24/29)
- **Priority justification**: Summary-stat arithmetic drift; §8.1–§8.4 themselves adjudicate every item correctly.
- **Doc location**: `ai-dev/analysis/2026-07/2026-07-26-nop-platform-deep-introduction.md:335-337`.
- **Evidence (actual)**: §8.1(1)+§8.2(14)+§8.3(8) = 23 entries ✓. But A7's parenthetical "5+5+4+5+4+5+1" = 29, not 27; working draft `_tmp/a7-phase1-working-draft.md:91-114` lists 24 included rows (1+5+4+2+4+4+4) while its own text says "22 项".
- **Impact**: Verification confusion; per-item content is correct.
- **Why not false positive**: Stated totals match neither the row count nor the parenthetical sum.

### [P2-A7-02] §2 mermaid coverage "23 rows cover all 25 nodes" overstates — `API` and `OPS` nodes unmapped
- **Priority justification**: Coverage claim slightly overreaches; most nodes are mapped.
- **Doc location**: `ai-dev/analysis/2026-07/2026-07-26-nop-platform-deep-introduction.md:102`.
- **Evidence (actual)**: mermaid graph defines 25 nodes; evidence table (L104-128) has 23 rows. `API` node (L68) has no row; `OPS` node (L58) is only indirectly covered by the `BIZ` row (ReflectionBizModelBuilder).
- **Impact**: 1–2 nodes lack explicit provenance rows.
- **Why not false positive**: Enumerated every node label vs every table row label.

### [P2-A7-03] Label `A2 §8(c)` used for two different items (L367 `@BizAction` vs L368 启动性能)
- **Priority justification**: Ambiguous cross-reference label; cosmetic.
- **Doc location**: `ai-dev/analysis/2026-07/2026-07-26-nop-platform-deep-introduction.md:167` (§4.2 G1) and `:316` (§8.3 row 3c) use "A2 §8(c)"→L368; `:329` (§8.4) uses "A2 §8(c)"→L367.
- **Evidence (actual)**: in A2 source `nop-core-engine-deep-dive.md:365-369`, §8(c) = L367 (`@BizAction`). The working draft renumbered L368 as "3c" (skipping excluded L367) but did not propagate consistently.
- **Impact**: Reader tracing G1/3c finds `@BizAction`, not 启动性能.
- **Why not false positive**: Same label points at two different file:line anchors within one capstone.

---

## Dimensions verified clean (no findings)

- **Config correctness** — `missions/nop-deep-analysis.json`: every path resolves (`roadmapPath`, `plansDir`, `contextDir` `docs-for-ai/00-start-here`, `prompts.multiAudit`, `prompts.openAudit`); `commands` are intentional no-ops for an analysis mission. ✓
- **Roadmap internal consistency** — `nop-deep-analysis-roadmap.md`: all 7 work items (A1–A7) marked `done`; every Work-Item deliverable path matches an existing file under `ai-dev/analysis/2026-07/`; dependency graph consistent with status. ✓
- **Deliverable completeness** — all 7 deliverables exist; roadmap hard-requirement "每章必须含联网对标小节并附来源链接" satisfied (A1:12, A2:17, A3:17, A4:15, A5:8, A6:11, A7:33 external http links). ✓
- **Plan→deliverable mapping** — every plan file in `ai-dev/plans/nop-deep-analysis/` has its declared analysis output present. ✓
- **Link integrity** — doc-links checker reports 0 broken links inside any `nop-deep-analysis` deliverable or plan. ✓
- **A1 (`nop-theory-foundation.md`)** — every cited `file:line` anchor verified accurate (XDefOverride, xdsl.xdef, XDslKeys, XDslExtender, DeltaResourceStore, DslModelParser, theory-paper line refs); all EXT-001~006 / XLANG-001~008 IDs map correctly to `source-anchors.md`. 0 findings. ✓
- **A3 (`nop-model-driven-and-codegen.md`)** — model-first mechanics, `_gen/`/`_*.xml` generation, Delta `x:extends`/`x:override`, "generated files must not be hand-edited" rule all verified correct. 0 findings. ✓

## Root-cause note (architecture-doc contract drift)

Two of the three P1s are **not** original errors introduced by the analysis deliverables — they are faithful propagation of pre-existing contract drift in the authoritative `docs-for-ai/` docs:
1. `docs-for-ai/01-repo-map/module-groups.md:23` — invents `nop-stream-checkpoint` + `nop-stream-flink` submodules → A5 P1.
2. `docs-for-ai/04-reference/source-anchors.md:81` — duplicate `GQL-002` label → A2 P1.

The remediation plan should fix the architecture docs **first** (they are the source of truth for many downstream consumers, not just this mission), then repoint the two analysis deliverables.

## Audit blind spots (self-assessment)

- **External/web-research comparison claims** (vs Spring/Quarkus/Flowable/Flink/LangGraph/etc.) were explicitly out of scope per the shared-prefix calibration; only platform-internal factual claims were verified.
- Spot-checks of "load-bearing" claims were prioritized; exhaustive verification of every secondary citation across ~280 KB of analysis prose was not performed.
- No verification of whether A7's recommendation branches ("update docs-for-ai?" / "use as roadmap input?") will be *acted on* — only that they are backed by evidence in A1–A6.

<AI_STEP_RESULT>issues</AI_STEP_RESULT>
