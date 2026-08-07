# 8 Delta StreamModel Entry Audit (nop-stream Independent Audit)

> Plan Status: active
> Last Reviewed: 2026-08-08
> Source: `ai-dev/backlog/nop-stream-independent-audit-roadmap.md` (Stage 8); frozen Stage-4 outputs (`source-manifest.md` domains b/c/f, `evidence-schema.md`, `finding-corpus.md`, `ai-dev/tools/check-nop-stream-audit-manifest.mjs`); frozen Stage-5 outputs (`environment-qualification.md` — T1 `qualified`/`in-process`); frozen Stage-6/7 evidence (`stage-7-xdsl-streammodel-entry.evidence.md`); production plan `nop-stream-production/2026-08-04-0900-3-delta-customization-stream-model.md` (Stage 51, completed); live repo baseline of `nop-stream-flow` model/builder + `.stream.xml` fixtures.
> Mission: nop-stream-independent-audit
> Work Item: 8. Delta StreamModel entry audit
> Related: Execution order `{3}` of this DRAFT_PLANS round. Roadmap deps: Stage 4 (evidence schema), Stage 7 (XDSL StreamModel entry audit) — both `done`. Direct successor of Stage 7. NOT on critical path. Hard prerequisite only for Stage 23 (docs/readiness decision). Absorbs Stage-7 deferral: "Delta overlay behavior itself is Stage 8" + the 7 untested-under-Delta fail-fast branches.

## Purpose

独立验证 nop-stream 的 **Delta StreamModel 入口**是否实现其设计目标：声明的 Delta overlay 形式（Form A `x:extends` 显式 base path；Form B `_delta/<layer>/` 分层 override 用 `x:extends="super"`，`_delta/default/` 零配置自动激活）只改变**已支持**的 StreamModel 语义，并在 intended 处保留 XDSL/Java 契约（transform-level delta 改变 fingerprint；config-only delta by-design 保持 fingerprint）。每个被支持的 Delta overlay 形式必须形成一条可复核的 evidence row；每个不支持/未实现的组合必须有 fail-fast 证明或显式 non-goal 裁定。

本审计验证核心 invariants：(a) Form A 显式-path Delta（transform add/modify/delete + edge rewrite）经 `DslModelParser` merge 后产出可执行的 supported topology；(b) Form B `_delta/default/` 分层 override（`x:extends="super"`）零配置自动激活；(c) delta-introduced unsupported node（如 `<streams>` registry）仍 fail-fast（Stage 7 已证 `<streams>`，本审计复核 + 覆盖其余 7 个 fail-fast registry 的 Delta 行为）；(d) fingerprint 敏感性规则（transform-level → sensitive；config-only → by-design invariant）。

本审计**发现**的任何 confirmed live defect 不在本计划内修复，而按 roadmap 规则指派给 active/successor remediation plan。

## Current Baseline

经 2026-08-08 live repo 核对（引用均与 frozen Stage-4 `source-manifest.md` 域 b/c/f + 实际源码一致；line anchors 经 explore agent 逐行复核）：

- **两个 StreamModel 类（勿混淆）**：(1) `io.nop.stream.flow.model.StreamModel` `nop-stream-flow/.../flow/model/StreamModel.java:5`（9-line thin subclass → generated base `_gen/_StreamModel.java:27` extends `AbstractComponentModel`，`_transforms` field `_gen/_StreamModel.java:139` KeyedList<StreamTransformModel>、`_edges` `:55`）——**XDSL 声明式 binding，Delta 审计目标**。(2) `io.nop.stream.core.model.StreamModel` `nop-stream-core/.../core/model/StreamModel.java:30`（runtime/executable model，`computeFingerprint():60`）。
- **XDSL schema**：`nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/stream/stream.xdef`（232 lines），root `<stream>` decl `:17-25`，**`:22` declares `xdef:support-extends="true"`（Delta enabler）**，header `:7-8` 列 Delta 为 entry #3，`:15` "可逆计算：支持 x:extends 继承和 Delta 定制"。Sibling xdefs：`resource-spec.xdef`、`pattern.xdef`（同目录）。
- **Parser/loader**：**无自定义 parser**——用标准 Nop 平台 `io.nop.xlang.xdsl.DslModelParser`。canonical reference（production plan cite）：`StreamModelSmokeTest.java:13/38-39`（`new DslModelParser().parseFromResource(resource)`），test helper 模式复用于 5 个 test class。Builder：`StreamModelDslBuilder` `nop-stream-flow/.../flow/builder/StreamModelDslBuilder.java:79`（class），`of()` factories `:94-100`，`build()` `:106-118`。**Stage 7 EVID-S7-028 finding 仍 live**：`main/` 无 production loader/dispatcher/bean 加载 `.stream.xml`——入口仅从 test code 可调。
- **Delta overlay fixtures**（manifest 域 c：12 个 `*.stream.xml`，expected_denominator 12 已核对）：1 production demo（`fraud-detection.stream.xml`，`x:extends` 指 xdef schema **非** Delta overlay）+ 3 non-Delta smoke/reduce + **6 Delta 相关**：
  - `test-delta-base.stream.xml` + `test-delta-extends.stream.xml`（`:11` `x:extends="/nop/stream/test/test-delta-base.stream.xml"`——adds `deltaFilter`、rewrites edge e1、adds edge e2）
  - `test-delta-config-base.stream.xml` + `test-delta-config-extends.stream.xml`（`:15` `x:extends=".../test-delta-config-base.stream.xml"`——config-only：interval 60000→30000）
  - `test-delta-failfast-base.stream.xml` + `test-delta-failfast-extends.stream.xml`（`:10` `x:extends`——introduces `<streams>` registry）
  - `test-delta-layered.stream.xml`（base）+ **`_vfs/_delta/default/nop/stream/test/test-delta-layered.stream.xml`**（`:13` `x:extends="super"`——adds `deltaLayerMap`、overrides edge e0、adds edge e1；**Form B `_delta/default/` 自动激活** by `DeltaResourceStoreBuilder`）
- **`_delta/` 目录**：恰好 1 个——`nop-stream-flow/src/test/resources/_vfs/_delta/default/`。
- **`x:override` 用法**：所有 `.stream.xml` 中 **0 occurrences**（仅 `x:extends`）。
- **Delta 语义测试**（manifest 域 g；3 个 Delta-specific test class，8 @Test 总计）：
  - `TestStreamModelDeltaExtends.java:58`（5 @Test，Stage 51 Anti-Hollow gate）：`xExtendsDeltaAddsFilterTransformNotPresentInBase()` `:75`、`xExtendsDeltaRedirectsEdgeToUpperFilterOut()` `:91`、**`xExtendsDeltaProducesDifferentSinkOutput()` `:104`**（execution：base `["A","B","C"]` → delta `["A","C"]` drop B）、`layeredDefaultDeltaAddsMapTransform()` `:125`、**`layeredDefaultDeltaProducesUppercaseOutput()` `:136`**（execution：merged `["A","B","C"]` uppercase vs lowercase passthrough）。
  - `TestStreamModelDeltaFingerprint.java:68`（2 @Test）：**`transformLevelDeltaProducesDifferentFingerprint()` `:81`**（`assertNotEquals` + dagTopologyHash differs）、**`configOnlyDeltaPreservesFingerprintByDesign()` `:97`**（`assertEquals` by-design invariance）。
  - `TestStreamModelDeltaFailFast.java:31`（1 @Test）：**`deltaAddingStreamsRegistryStillFailsFast()` `:44`**（delta-introduced `<streams>` 仍抛 `UnsupportedOperationException`）。
- **Production plan**：`nop-stream-production/2026-08-04-0900-3-delta-customization-stream-model.md`（Stage 51，`completed`）。实现的 2 个 supported overlay form：Form A `x:extends` 显式 base path（transform add/modify/delete）、Form B `_delta/<layer>/` `x:extends="super"`（`_delta/default/` auto-activated by `DeltaResourceStoreBuilder.java:72-77`）。关键设计：无新 parser（Delta 由 `DslModelParser` 支持，因 `stream.xdef:22` `xdef:support-extends="true"`）；fingerprint 敏感性 split（transform-level → sensitive；config-only → by-design invariant，因 `computeFingerprint()` hash DAG topology/requirements/checkpointParticipants 非 checkpoint interval/parallelism）；silent-no-op 风险缓解（custom layer names 非 `default` 不自动激活除非设 `nop.core.vfs.delta-layer-ids`）。Non-Goals：exact fingerprint equality（XDSL vs Java API）、StreamComponents-registry delta、`<union>`/`<sideOutput>` delta、core `computeFingerprint()` 改动。
- **Stage 7 deferral → 本计划吸收**：Stage 7 evidence `:5` 显式声明 "Delta overlay behavior itself is Stage 8"。Stage 7 冻结的 **Support/Reject XDSL Node Matrix**（`:9-50`）是 non-Delta path 的；Stage 8 须验证这些 disposition 在 node 经 Delta merge 引入时仍成立。8 个 fail-fast registry 中（streams/sideInputs/environments/schemas/coders/requirements/checkpointParticipants/onStart-onEnd/onError，EVID-S7-019..026），**仅 `<streams>` 在 Delta 下 re-tested**（`TestStreamModelDeltaFailFast`）；其余 7 个 fail-fast branch **未在 Delta 下 re-exercise**——候选 Stage 8 coverage gap。Stage 7 EVID-S7-010（`<cep>` transform）flag `M7-2-P2-1`（`<cep>` 需 `nop-stream-cep`）；当前**无 Delta fixture exercise delta-introduced `<cep>`**（delta fixture 只用 source/map/filter/sink）。
- **Corpus 交叉**（finding-corpus.md）：Delta/flow 相关 3 个 finding。**M7-2-P2-1**（flow pom depends on cep，contradicting architecture）：**LIVE**——`nop-stream-flow/pom.xml:22` `<artifactId>nop-stream-cep</artifactId>`（deps block `:17` core、`:22` cep、`:27` xdefs），Stage 7 EVID-S7-010 确认 load-bearing for XDSL `<cep>`。**M7-2-P2-2**（flow/model duplicate source tree）：**LIVE**——`nop-stream-flow/.../flow/model/` 含 ~30 hand-authored + `_gen/` ~30 generated；**corpus anchor typo**：finding-corpus `:159` 记 `nop-stream/src/main/java/...`（缺 `nop-stream-flow` module segment），correct path 是 `nop-stream/nop-stream-flow/src/main/java/...`。**M7-2-P2-21**（README says flow depends only on core）：**doc-level RESOLVED**——`nop-stream/README.md:18` 现读 "nop-stream-flow | 活跃 | XDSL 声明式流编排，依赖 core + cep（CepPatternModel）+ nop-xdefs"（README 不再矛盾），但 pom-level fact（flow deps cep+xdefs）仍 TRUE——Stage 23 doc-contract 应确认 closure。
- **真实 gap**：(1) Form A / Form B Delta overlay 的 entry-to-effect evidence row 缺冻结（虽有 `TestStreamModelDeltaExtends` 5 个 @Test）；(2) 7 个 fail-fast registry（sideInputs/environments/schemas/coders/requirements/checkpointParticipants/onStart-onEnd-onError）在 Delta 下未 re-exercise（仅 `<streams>` re-tested）——缺 evidence row 覆盖；(3) fingerprint 敏感性规则的 evidence row 缺冻结（虽有 `TestStreamModelDeltaFingerprint` 2 个 @Test）；(4) delta-introduced supported node 仍 build/execute、delta-introduced unsupported node 仍 fail-fast 的矩阵 evidence row 缺冻结；(5) Stage 7 EVID-S7-028（main/ 无 production loader）对 Delta 入口的影响缺 disposition；(6) corpus anchor typo（M7-2-P2-2）需标注。

## Goals

- 产出一份 **Delta overlay 支持/拒绝矩阵**（Form A 显式-path、Form B `_delta/default/` 分层、custom-layer non-default、`x:override`），每形式一条 evidence row，`environment_class` 据 frozen lane 词表裁定（Delta 全部 in-process → `in-process`）。
- 为**每条 supported Delta form**产出 entry-to-effect evidence row：Form A `positive_proof` 引用 `TestStreamModelDeltaExtends#xExtendsDeltaProducesDifferentSinkOutput`（execution proves merge changes output）；Form B `positive_proof` 引用 `TestStreamModelDeltaExtends#layeredDefaultDeltaProducesUppercaseOutput`（execution proves auto-activation changes output）；`runtime_wiring: wired`。
- 产出 **fingerprint 敏感性** evidence row：`positive_proof` 引用 `TestStreamModelDeltaFingerprint#transformLevelDeltaProducesDifferentFingerprint`（transform-level sensitive）+ `#configOnlyDeltaPreservesFingerprintByDesign`（config-only invariant）；`disposition` 据 in-process lane 裁定。
- 产出 **fail-fast under Delta** evidence row：`<streams>` registry `positive_proof` 引用 `TestStreamModelDeltaFailFast#deltaAddingStreamsRegistryStillFailsFast`（`disposition: fail-fast`）；对其余 7 个 fail-fast registry（sideInputs/environments/schemas/coders/requirements/checkpointParticipants/onStart-onEnd-onError）据 live 行为裁定——若 Delta 下未 re-exercise，标 `residual-risk`/`unverified` + 注明 gap + Stage 17 successor。
- 产出 **delta-introduced supported node 仍 build/execute + delta-introduced unsupported node 仍 fail-fast** 矩阵 evidence row（据 Stage 7 Support/Reject Matrix + live Delta fixture 范围——当前 delta fixture 只用 source/map/filter/sink）。
- 产出 **custom-layer non-default non-activation** evidence row（silent-no-op 风险缓解）：`disposition: non-goal` 或 `residual-risk`——注明 custom layer names 非 `default` 不自动激活除非设 `nop.core.vfs.delta-layer-ids`（production plan silent-no-op mitigation；`DeltaResourceStoreBuilder` 静默忽略非-default layer，无 fail-fast 抛异常，故**不得标 `fail-fast`**）。
- 对**关键历史 finding** 做 live 复验标注：M7-2-P2-1（flow pom deps cep，LIVE）、M7-2-P2-2（duplicate tree，LIVE + corpus anchor typo 标注）、M7-2-P2-21（README doc-resolved/pom-fact-live，→ Stage 23）——据 live 行为标 `finding_id` + `disposition`。
- 产出 **Stage 7 EVID-S7-028 Delta 影响** evidence row：`disposition: residual-risk`——注明 main/ 无 production loader 对 Delta 入口的影响（Delta 入口同样仅 test-invokable）。
- 所有 evidence row 经 `check-nop-stream-audit-manifest.mjs evidence --strict` 校验通过且非空过；corpus finding_id 交叉标注合法。

## Non-Goals

- 非-Delta XDSL 入口行为——属 Stage 7（已 done；本计划引用其 Support/Reject Matrix，验证 Delta 下仍成立）。
- Delta 新 feature（`<union>`/`<sideOutput>` delta、StreamComponents-registry delta、exact fingerprint equality XDSL vs Java API）——production plan 已声明 non-goal。
- core `computeFingerprint()` 改动。
- Java API 入口 / graph / LOCAL execution——属 Stage 6。
- 修复本审计发现的 confirmed live defect（按 roadmap 规则指派 remediation plan）。

## Scope

### In Scope

- `ai-dev/audits/nop-stream-independent-audit/stage-8-delta-streammodel-entry.evidence.md`（domain evidence rows，manifest 域 b/c/f/g 范围内的 flow model/builder + `.stream.xml` fixture source anchor + test lane）。**文件名必须是 `*.evidence.md` 且为 audit dir 直系子文件。**
- 支持/拒绝矩阵文本（写入证据文件头部，仅矩阵/判据不改 frozen 字段/词表）。

### Out Of Scope

- 修复 confirmed live defect（指派 remediation plan）。
- 非-Delta XDSL 入口（Stage 7）。
- Java API/graph/LOCAL execution（Stage 6）。
- Delta 新 feature 开发（production plan non-goal）。
- 修改 frozen evidence-row 11 字段定义或 7 分类词表。

## Execution Plan

### Phase 1 - Delta Overlay Form Inventory & Transform-Level Evidence

Status: planned
Targets: `ai-dev/audits/nop-stream-independent-audit/stage-8-delta-streammodel-entry.evidence.md`

- Item Types: `Proof`

- [ ] 产出 Form A 显式-path Delta evidence row：`source_anchor` 指向 `test-delta-extends.stream.xml:11`（`x:extends="/nop/stream/test/test-delta-base.stream.xml"`）+ `stream.xdef:22`（`xdef:support-extends="true"`）；`implementation_anchor` 指向 `DslModelParser`（标准平台 merge）；`positive_proof` 引用 `TestStreamModelDeltaExtends#xExtendsDeltaAddsFilterTransformNotPresentInBase` + `#xExtendsDeltaRedirectsEdgeToUpperFilterOut` + **`#xExtendsDeltaProducesDifferentSinkOutput`**（execution：base `["A","B","C"]` → delta `["A","C"]`）；`runtime_wiring: wired`；`environment_class: in-process`；`disposition: e2e-proved`。
- [ ] 产出 Form B `_delta/default/` 分层 evidence row：`source_anchor` 指向 `_vfs/_delta/default/nop/stream/test/test-delta-layered.stream.xml:13`（`x:extends="super"`）+ `DeltaResourceStoreBuilder`（auto-activation for `_delta/default/`）；`positive_proof` 引用 `TestStreamModelDeltaExtends#layeredDefaultDeltaAddsMapTransform` + **`#layeredDefaultDeltaProducesUppercaseOutput`**（execution proves auto-activation）；`runtime_wiring: wired`；`environment_class: in-process`；`disposition: e2e-proved`。
- [ ] 产出 custom-layer non-default non-activation evidence row（silent-no-op 风险缓解）：`source_anchor` 指向 production plan silent-no-op mitigation（custom layer names 非 `default` 不自动激活除非 `nop.core.vfs.delta-layer-ids`）；`disposition: non-goal` 或 `residual-risk`——注明 custom layer 不自动激活是 by-design，但 `DeltaResourceStoreBuilder` **静默忽略**非-default layer（无 fail-fast 抛异常），故不得标 `fail-fast`；若 deployment 误设 custom layer 期望激活会 silent no-op，注明风险。
- [ ] 产出 `x:override` absent evidence row：`source_anchor` 指向 grep evidence（所有 `.stream.xml` 中 0 occurrences of `x:override`）；`disposition: non-goal`——注明当前 supported Delta 仅 `x:extends`（显式 + `super`），`x:override` 未使用。
- [ ] 冻结 **Delta overlay 支持/拒绝矩阵**文本（写入证据文件头部）：Form A（SUPPORTED, in-process）、Form B `_delta/default/`（SUPPORTED, in-process）、custom-layer non-default（non-goal/residual-risk，silent by design——`DeltaResourceStoreBuilder` 静默忽略非-default layer，无 fail-fast）、`x:override`（non-goal, unused）。

Exit Criteria:

- [ ] ≥4 条 Delta overlay form evidence row（Form A/Form B/custom-layer/x:override），格式经 `check-nop-stream-audit-manifest.mjs evidence --strict` 校验 exit 0，且校验器实际解析到行（非 "0 evidence rows yet" 空过）
- [ ] **端到端验证（Rule #22）**：Form A / Form B row 的 `positive_proof` 引用真实 in-process 实跑测试（execution 证明 merge 改变 output），`environment_class >= in-process`，`disposition: e2e-proved`；不得用 metadata-only 断言充数
- [ ] **接线验证（Rule #23）**：Form A/B row 的 `runtime_wiring: wired` 据 in-process 实跑裁定（`x:extends` → `DslModelParser` merge → 可执行 topology 确实连通），非仅 parse 成功
- [ ] **无静默跳过（Rule #24）**：custom-layer non-activation 若为 silent（无 fail-fast），须标 `residual-risk` + 注明 silent-no-op 风险（不得静默当 supported）；`x:override` 标 `non-goal`
- [ ] `No owner-doc update required`（证据文件是审计产出；不改 `docs-for-ai/`）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - Fingerprint Sensitivity & Fail-Fast Under Delta Evidence

Status: planned
Targets: `ai-dev/audits/nop-stream-independent-audit/stage-8-delta-streammodel-entry.evidence.md`

- Item Types: `Proof | Decision`

- [ ] 产出 fingerprint transform-level sensitive evidence row：`source_anchor` 指向 `StreamModel.computeFingerprint()`（core `StreamModel.java:60`，hash DAG topology/requirements/checkpointParticipants）+ `test-delta-extends.stream.xml`（transform-level delta）；`positive_proof` 引用 `TestStreamModelDeltaFingerprint#transformLevelDeltaProducesDifferentFingerprint`（`assertNotEquals`）；**注：fingerprint 是 model-level 属性，测试为 unit-level（直接构造 StreamModel + 调 `computeFingerprint()`，不调 `build()`/`execute()`），故 `environment_class: unit`、`required_lane: unit`、`disposition: e2e-proved`（unit 属性在 unit lane 证明，`unit >= unit` 成立）**。
- [ ] 产出 fingerprint config-only invariant evidence row：`source_anchor` 指向 `test-delta-config-extends.stream.xml:15`（config-only：interval 60000→30000）；`positive_proof` 引用 `TestStreamModelDeltaFingerprint#configOnlyDeltaPreservesFingerprintByDesign`（`assertEquals` by-design invariance）；`environment_class: unit`；`required_lane: unit`；`disposition: e2e-proved`（unit-level property）。
- [ ] 产出 `<streams>` fail-fast under Delta evidence row：`source_anchor` 指向 `test-delta-failfast-extends.stream.xml:10`（introduces `<streams>`）；`positive_proof` 引用 `TestStreamModelDeltaFailFast#deltaAddingStreamsRegistryStillFailsFast`（throws `UnsupportedOperationException`）；`disposition: fail-fast`。
- [ ] 产出 7 untested-under-Delta registry coverage-gap evidence row：`source_anchor` 指向 Stage 7 EVID-S7-019..026（其中 5 个 fail-fast：streams/sideInputs/environments/schemas/coders；3 个 unverified：requirements/checkpointParticipants/onStart-onEnd-onError）；`disposition: residual-risk` 或 `unverified`——注明这 7 个 registry 在 Delta 下**未 re-exercise**（仅 `<streams>` 在 Delta 下 re-tested），coverage gap 指派 Stage 17 successor；注明 "delta-introduced unsupported node should still fail-fast, but only `<streams>` proven under Delta"。
- [ ] 产出 delta-introduced supported-node matrix evidence row：据 Stage 7 Support/Reject Matrix + live Delta fixture 范围（当前 delta fixture 只用 source/map/filter/sink，均为 supported）；`disposition: e2e-proved` for source/map/filter/sink under Delta；注明无 delta-introduced `<cep>`/`<window>`/`<aggregate>` fixture（M7-2-P2-1 cross-ref：`<cep>` 需 cep dep）。

Exit Criteria:

- [ ] ≥5 条 fingerprint/fail-fast evidence row，格式校验 exit 0，且校验器实际解析到行（非空过）
- [ ] **端到端验证（Rule #22）**：Form A/B execution row 的 `positive_proof` 引用真实 in-process 实跑测试（execution 证明 merge 改变 output，见 Phase 1）；fingerprint row 为 model-level unit 属性，`environment_class: unit`（不要求 in-process，因 `computeFingerprint()` 是模型计算非管线执行）——`positive_proof` 引用真实 unit 实跑测试（transform-level `assertNotEquals` + config-only `assertEquals`）
- [ ] **无静默跳过（Rule #24）**：7 untested-under-Delta registry gap 不得静默当 `e2e-proved`——须标 `residual-risk`/`unverified` + 注明 gap + Stage 17 successor；delta-introduced unsupported node 须证明 fail-fast 或标 `unverified`
- [ ] **fail-fast 验证**：`<streams>` fail-fast row 证明 delta-introduced unsupported node 抛 `UnsupportedOperationException`（Rule #24：缺失功能显式失败非静默跳过）
- [ ] `node ai-dev/tools/check-nop-stream-audit-manifest.mjs evidence --strict` exit 0，且校验器实际解析到行（非空过）
- [ ] `No owner-doc update required`
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - Historical Finding Revalidation, Delta-vs-Non-Delta Equivalence & Coverage Gaps

Status: planned
Targets: `ai-dev/audits/nop-stream-independent-audit/stage-8-delta-streammodel-entry.evidence.md`

- Item Types: `Proof | Decision`

- [ ] 对 M7-2-P2-1（flow pom deps cep）做 live 复验 evidence row：`source_anchor` 指向 `nop-stream-flow/pom.xml:22`；`finding_id: M7-2-P2-1`；`disposition: residual-risk`——注明 flow deps cep 是 load-bearing for `<cep>`（Stage 7 EVID-S7-010），contradicts architecture 但 live functional；successor → Stage 23 doc-contract。
- [ ] 对 M7-2-P2-2（flow/model duplicate tree）做 live 复验 evidence row：`source_anchor` 指向 `nop-stream-flow/.../flow/model/`（hand-authored + `_gen/`）；`finding_id: M7-2-P2-2`；`disposition: residual-risk`——注明 corpus anchor typo（缺 `nop-stream-flow` module segment），correct path 标注；`_gen/` 是 generated（manifest exclude rule），不 inflate denominator。
- [ ] 对 M7-2-P2-21（README doc-resolved）做 live 复验 evidence row：`source_anchor` 指向 `nop-stream/README.md:18`（现正确 acknowledge cep+xdefs deps）；`finding_id: M7-2-P2-21`；`disposition: residual-risk`——注明 README doc-level RESOLVED 但 pom-fact 仍 TRUE，→ Stage 23 doc-contract 确认 closure。
- [ ] 产出 Delta-vs-non-Delta equivalence evidence row：`source_anchor` 指向 Stage 7 Support/Reject Matrix（frozen non-Delta disposition）；`disposition: e2e-proved` for supported nodes proven under Delta（source/map/filter/sink），`residual-risk` for nodes with no Delta fixture（cep/window/aggregate/flatMap 等 Stage 7 coverage gap 在 Delta 下同样未覆盖）。
- [ ] 产出 Stage 7 EVID-S7-028 Delta 影响 evidence row：`disposition: residual-risk`——注明 main/ 无 production loader/dispatcher/bean 对 Delta 入口同样适用（Delta 入口仅 test-invokable，与 non-Delta 一致），successor → production loader remediation。
- [ ] 全 evidence 文件回归校验 + corpus 交叉标注核对（含 M7-2-P2-2 anchor typo 标注）。
- [ ] 冻结 **Delta 支持/拒绝矩阵**最终文本（写入证据文件头部）。

Exit Criteria:

- [ ] ≥5 条 historical finding/equivalence/coverage evidence row，格式校验 exit 0，且校验器实际解析到行（非空过）
- [ ] **无静默跳过（Rule #24）**：corpus anchor typo（M7-2-P2-2）须显式标注（不得静默忽略）；no-production-loader gap（EVID-S7-028）须标 `residual-risk`；Delta-vs-non-Delta gap 须如实标 `residual-risk`
- [ ] Delta 支持/拒绝矩阵在证据文件头部有显式文本
- [ ] `node ai-dev/tools/check-nop-stream-audit-manifest.mjs evidence --strict` exit 0，且校验器实际解析到行（非空过）；finding_id 全部合法（M7-2-P2-1/2/21 在 frozen corpus 内或 `none`）
- [ ] `No owner-doc update required`
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **审计计划（无生产代码变更）**：本计划产出为 evidence rows + 矩阵文本，不改 nop-stream 生产代码。`./mvnw test`/`compile` 不强制；改为以 evidence 校验器退出码 + in-process 实跑证据引用为 closure 依据。但若审计中发现 confirmed live defect，按 roadmap 规则指派 remediation plan（不在本计划内修复）。

- [ ] Form A / Form B Delta overlay 各有 entry-to-effect evidence row（in-process execution 实跑证明 merge 改变 output）
- [ ] fingerprint 敏感性规则（transform-level sensitive / config-only invariant）有 evidence row
- [ ] fail-fast under Delta（`<streams>`）有 evidence row；7 个未 re-exercise fail-fast registry 如实标 `residual-risk` + Stage 17 successor
- [ ] 支持/拒绝矩阵显式成文（Form A/B supported、custom-layer non-goal、x:override non-goal）
- [ ] 关键历史 finding（M7-2-P2-1/2/21）有 live 复验 evidence row，含 corpus anchor typo 标注
- [ ] 所有 evidence row 经 `check-nop-stream-audit-manifest.mjs evidence --strict` exit 0，且**非空过**
- [ ] 不存在被静默降级到 deferred 的 in-scope 审计项（fail-fast gap 标 `residual-risk` + Stage 17 successor；no-production-loader 标 `residual-risk`；doc-contract finding → Stage 23——均为合法终态）
- [ ] 审计发现的任何 confirmed live defect 已指派 active/successor remediation plan
- [ ] `No owner-doc update required`（不改 `docs-for-ai/`）
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：closure audit 验证（a）Form A/B row 的 `positive_proof` 确为 execution-level in-process 实跑测试（非仅 parse 成功/metadata 断言），（b）`runtime_wiring=wired` 确经接线验证，（c）7 fail-fast gap 无静默放行（标 `residual-risk`），（d）corpus anchor typo 无静默忽略

## Deferred But Adjudicated

（执行中如出现延期项，须写明 Classification / Why Not Blocking Closure / Successor Required。预期场景：7 个 fail-fast registry 在 Delta 下未 re-exercise——标 `residual-risk` + Stage 17 successor（test effectiveness）。no-production-loader（EVID-S7-028）——标 `residual-risk` + production loader successor。Delta-vs-non-Delta gap for cep/window/aggregate（无 Delta fixture）——标 `residual-risk`。doc-contract finding（M7-2-P2-21）——`residual-risk` + Stage 23 successor。confirmed still-live defect 不得 deferred——须指派 remediation plan。）

## Non-Blocking Follow-ups

- 7 fail-fast registry Delta re-exercise（sideInputs/environments/schemas/coders/requirements/checkpointParticipants/onStart-onEnd-onError）→ Stage 17（test effectiveness）。
- no-production-loader for `.stream.xml`（EVID-S7-028，Delta 入口同样仅 test-invokable）→ production loader remediation plan。
- delta-introduced `<cep>`/`<window>`/`<aggregate>` fixture（M7-2-P2-1 cross-ref）→ successor test-coverage plan。
- M7-2-P2-1/M7-2-P2-21 doc-contract → Stage 23（documentation contract and readiness decision）。
- M7-2-P2-2 corpus anchor typo 修正 → finding-corpus.md 维护（recorded, not fixed in this audit-only plan）。

## Closure

Status Note: <<待 closure 时填写>>
Completed: <<待 closure>>

Closure Audit Evidence:

- Reviewer / Agent: <<待 closure 时填写（独立 closure-audit subagent, fresh session）>>
- Evidence: <<待 closure 时填写：每条 Exit Criterion / Closure Gate 验证结果 + validator 退出码 + Anti-Hollow 检查>>

Follow-up:

- <<待 closure 时填写；confirmed live defect 不得出现在这里>>
