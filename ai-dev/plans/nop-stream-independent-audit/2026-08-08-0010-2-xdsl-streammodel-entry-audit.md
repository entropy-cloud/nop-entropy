# 5 XDSL StreamModel Entry Audit (nop-stream Independent Audit)

> Plan Status: completed
> Last Reviewed: 2026-08-08
> Draft Review: round 1 independent sub-agent review — Consensus YES with 2 Major recommendations (Major 1: Phase 2 topology/stable-identity 行高估可用测试证据，TestDagTopologyConsistency 为 build-only 非 execute() 需禁止 e2e-proved; Major 2: demo dangling transforms 应为 build-time fail-fast 而非"执行时不生效"). Round 2 fixes: Phase 2 三 item 均显式写出传递性论证 + 强制 component-only/manual-trace + 禁止 e2e-proved; Phase 4 demo item 改述为 build-time fail-fast (latent) + residual-risk disposition. Round-2 re-review verdict: **Consensus YES** (both Majors RESOLVED, no new issues).
> Source: `ai-dev/backlog/nop-stream-independent-audit-roadmap.md` (Stage 7); frozen Stage-4 outputs (`source-manifest.md`, `evidence-schema.md`, `finding-corpus.md`, `ai-dev/tools/check-nop-stream-audit-manifest.mjs`); frozen Stage-6 outputs (`stage-6-java-api-graph-local.evidence.md`); live repo baseline of `nop-stream-flow` XDSL/builder surfaces + `nop-stream-core` graph surfaces.
> Mission: nop-stream-independent-audit
> Work Item: 7. XDSL StreamModel entry audit
> Related: Execution order `{2}` of this DRAFT_PLANS round. Roadmap deps: Stage 4 (evidence schema), Stage 6 (Java/local audit) — both `done`. Direct successor of Stage 6. Hard prerequisite for Stage 8 (Delta StreamModel). NOT on critical path (can run in parallel with Stage 9).

## Purpose

独立验证 nop-stream 的 **XDSL StreamModel 入口路径**是否实现其设计目标：每个被声明的 `.stream.xml` 构造能通过声明的 StreamModel 路径（`DslModelParser → flow.model.StreamModel → StreamModelDslBuilder → StreamExecutionEnvironment → DataStream API → StreamGraphGenerator`）编译，并保持与 Stage 6 已验证的 Java 执行语义等价。本审计验证：XDSL 节点清单 disposition（supported/unsupported/fail-fast）、XDSL-to-model-to-Java/graph trace、supported topology 和 stable-identity 等价性、以及 unsupported XDSL 节点的 fail-fast 覆盖。

本审计**发现**的任何 confirmed live defect 不在本计划内修复，而按 roadmap 规则指派给 active/successor remediation plan。

## Current Baseline

经 2026-08-08 live repo 核对（引用均与 frozen Stage-4 `source-manifest.md` 域 b/c/f/g + 实际源码一致；line anchors 经 explore agent 逐行复核）：

- **XDEF schema**：`nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/stream/stream.xdef`（232 行）。`xdef:name="StreamModel"`，`xdef:bean-package="io.nop.stream.flow.model"`，`xdef:support-extends="true"`（Delta/x:extends enabled）。Root `<stream>` 属性：`name`、`version`、`parallelism`、`watermarkInterval`。Schema header（`:9-10`）声明五层管线：`StreamModel → StreamGraph → JobGraph → PartitionedPlan → DeploymentPlan → GraphExecutionPlan`。
- **XDSL transform 节点（15 subtypes in `<transforms>`）**：source（`:112`）、timestampsAndWatermarks（`:124`）、map（`:132`）、flatMap（`:137`）、filter（`:142`）、keyBy（`:147`）、window（`:150`）、aggregate（`:156`）、reduce（`:159`）、process（`:164`）、cep（`:167`）、sink（`:170`）、union（`:181`）、sideOutput（`:184`）、custom（`:188`）。`bean-sub-type-prop="type"` 多态反序列化。
- **其他 XDSL 节点**：`<checkpoint>`（CheckpointConfigModel，`:30`）、`<windowingStrategies>`（`:46`）、`<coders>`（`:56`）、`<schemas>`（`:64`）、`<environments>`（`:74`）、`<streams>`（`:83`）、`<requirements>`（`:91`）、`<checkpointParticipants>`（`:96`）、`<edges>`（`:199`，StreamEdgeModel）、`<sideInputs>`（`:212`）、`<patterns>`（`:222`，CepPatternModel ref `pattern.xdef`）、`<onStart>`/`<onEnd>`/`<onError>`（`:228-230`）。
- **StreamModel Java binding（XDSL）**：`nop-stream-flow/src/main/java/io/nop/stream/flow/model/`。28 手写 thin subclass（each `extends _XxxModel {}`）+ 28 generated bases under `_gen/`。Root class `StreamModel extends _StreamModel extends AbstractComponentModel`。所有 transform subtype extends `StreamTransformModel`。由 `nop-stream-flow/precompile/gen-stream-xdsl.xgen` 从 `stream.xdef` 生成。
- **Canonical runtime model（独立类）**：`nop-stream-core/src/main/java/io/nop/stream/core/model/StreamModel.java`——`@DataBean`（NOT XDSL model），holds `StreamComponents` + `Map<String, Transformation<?>>`。由 `StreamGraphGenerator.populateStreamModel()` `:126` 填充。**两个 StreamModel 类仅通过 `StreamModelDslBuilder.build()` 内的 DataStream API 调用链连接。**
- **StreamModel 编译桥接**：`nop-stream-flow/src/main/java/io/nop/stream/flow/builder/StreamModelDslBuilder.java`（435 行）——**唯一**将 `flow.model.StreamModel`（XDSL）转换为 `StreamExecutionEnvironment` 的类。`of(StreamModel[, BeanFunctionResolver])`（`:94/98`）→ `build()`（`:106-118`）→ creates `StreamExecutionEnvironment.createTestEnvironment()`，applies parallelism/watermarkInterval，`applyCheckpointConfig(env)`（`:124`），`failFastOnUnsupportedRegistries()`（`:115/156-189`），`buildTransforms(env)`（`:116/195-264`，topological sort over `<transforms>` using `<edges>`）。`buildTransform()`（`:266-290`）instanceof dispatch on source/map/filter/flatMap/keyBy/sink → delegate to `AdvancedTransforms.build(...)` for window/aggregate/reduce/process/cep/custom/timestampsAndWatermarks。
- **AdvancedTransforms**：`nop-stream-flow/src/main/java/io/nop/stream/flow/builder/AdvancedTransforms.java`（443 行，package-private final）。`build()` `:69-102` dispatches window/aggregate/reduce/process/cep/custom/timestampsAndWatermarks + **union（fail-fast `:244-250`）** + **sideOutput（fail-fast `:322-329`）**。Unknown transform type → throw `UnsupportedOperationException` `:100-101`。
- **Fail-fast 覆盖（8 top-level registries + 2 transforms）**：`StreamModelDslBuilder.failFastOnUnsupportedRegistries()` `:156-189` 对 `<streams>`/`<sideInputs>`/`<environments>`/`<requirements>`/`<checkpointParticipants>`/`<onStart>`/`<onEnd>`/`<onError>`/`<schemas>`/`<coders>` 抛 `UnsupportedOperationException`。`AdvancedTransforms` 对 `<union>` `:244-250` 和 `<sideOutput>` `:322-329` 抛 `UnsupportedOperationException`。
- **XDSL 入口路径（TEST-ONLY）**：`StreamModelDslBuilder.of()` 与 `new DslModelParser().parseFromResource()` **仅被 test 代码调用**——grep `nop-stream/**/src/main/**/*.java` 对 `DslModelParser`、`parseFromResource`、`StreamModelDslBuilder.of` 零命中。**无生产 loader/dispatcher/bean** 在运行时加载 `.stream.xml`。demo `fraud-detection.stream.xml` 无 Java driver class。这是一个关键审计发现：XDSL 入口路径目前**无生产接线**（test-only invocation）。
- **Stage 6 等价性判据**（`stage-6-java-api-graph-local.evidence.md`）：topology 等价（Java 入口 vs StreamGraph/JobGraph 节点/边一致）、stable identity（Transformation→StreamNode→JobVertex id 传播）、recovery inputs 判据。本审计须验证 XDSL 入口与 Java 入口的 **topology 等价 + stable-identity 等价**。
- **stream.xml 文件清单（12 total）**：1 demo（`fraud-detection.stream.xml`，使用 checkpoint/windowingStrategies/source/timestampsAndWatermarks/filter/map/flatMap/cep/sink/reduce/edges/patterns，**有 dangling transforms** split-map/sum-reduce 未被 edge 连接）；11 test fixtures（test-smoke、test-smoke-collecting、test-reduce-pipeline、test-delta-{base,extends,layered,config-base,config-extends,failfast-base,failfast-extends}）。1 `_delta/default/` layered overlay（test-delta-layered）。
- **Corpus 交叉**：finding-corpus.md 97 个 finding 中**无**直接 target XDSL 入口路径的 finding。最近相关：M7-2-P2-2（`flow/model/` duplicate source tree）、M7-2-P2-1（flow pom 依赖 cep，矛盾 architecture）、M8-2-P2-17（README 说五层 vs architecture 说六阶段 pipeline-count drift）、M7-2-P2-21（README 说 flow 只依赖 core，实际还依赖 cep/xdefs）。这些属 Stage 8（Delta）或 Stage 23（文档契约）边界，本审计只在 evidence row 的 `finding_id` 交叉中标注。
- **测试语料**（manifest 域 g）：`StreamModelSmokeTest`（纯 parse，3 test）、`TestStreamModelDslBuilderE2E`（parse + build + execute + sink 断言）、`TestAdvancedPipelineE2E`（reduce 端到端）、`TestAdvancedTransforms`（16 builder call sites 覆盖 window/aggregate/reduce/process/cep/custom/timestampsAndWatermarks）、`TestStreamModelDslBuilderFailFast`（验证 union/sideOutput/streams/sideInputs/environments/schemas/coders fail-fast）、`TestDagTopologyConsistency`（DAG topology 一致性）。
- **真实 gap**：(1) 没有覆盖"每个 supported XDSL transform 节点 → parse → build → execute → sink 输出"的成套 evidence row；(2) XDSL 入口与 Java 入口的 topology/stable-identity 等价性无独立 evidence；(3) unsupported XDSL 节点（8 registries + union + sideOutput + unknown type）的 fail-fast 无统一 evidence row 覆盖矩阵；(4) demo `fraud-detection.stream.xml` 的 dangling transforms 无 disposition；(5) XDSL 入口路径无生产接线这一事实无显式 `non-goal`/`residual-risk` 裁定；(6) checkpoint config 的部分字段（storageConfig/storageType/barrierAlignmentTimeout/maxConsecutiveCheckpointFailures/jobId/pipelineId）被 parse 但 unused 无 disposition。

## Goals

- 为**每个 supported XDSL transform 节点**（source/map/filter/flatMap/keyBy/window/aggregate/reduce/process/cep/custom/timestampsAndWatermarks/sink）产出一条 XDSL-to-sink evidence row，`positive_proof` 为真实 in-process 实跑测试名（parse `.stream.xml` → `StreamModelDslBuilder.of(model).build()` → `env.execute()` → sink 输出断言），`environment_class >= in-process`。
- 产出 **XDSL → Java/graph trace** evidence row：证明 `DslModelParser → flow.model.StreamModel → StreamModelDslBuilder → StreamExecutionEnvironment → StreamGraphGenerator` 路径确实连通（接线验证），且 XDSL `<transforms>`/`<edges>` 到 StreamGraph 节点/边有 topology 等价性。
- 产出 **stable-identity 等价性** evidence row：XDSL transform id 在 parse → build → StreamGraph → JobGraph 路径传播不失真（引用 Stage 6 frozen 判据）。
- 为**每个 unsupported XDSL 节点**（union、sideOutput、streams、sideInputs、environments、requirements、checkpointParticipants、schemas、coders、onStart/onEnd/onError、unknown transform type）产出一条 evidence row，`disposition: fail-fast`（有 rejection_proof 验证抛异常）或 `disposition: non-goal`（注明 out-of-scope for current supported baseline）。
- 对 **XDSL 入口无生产接线**这一事实产出显式 evidence row 裁定：`disposition: residual-risk`（XDSL 入口路径当前仅 test-only invocation，无 runtime loader/dispatcher/bean 在 `main/` 代码中加载 `.stream.xml`）——`runtime_wiring: partial`（test wired, production unwired），`required_lane: in-process`，`positive_proof: none`（无生产实跑），`rejection_proof: none`。
- 对 **demo `fraud-detection.stream.xml` 的 dangling transforms** 产出 disposition evidence row（`residual-risk` 或 `non-goal`）。
- 对 **checkpoint config unused 字段**（storageConfig/storageType/barrierAlignmentTimeout/maxConsecutiveCheckpointFailures/jobId/pipelineId）产出 disposition evidence row（`residual-risk`：parsed but not applied to `StreamExecutionEnvironment`）。
- 所有 evidence row 经 `check-nop-stream-audit-manifest.mjs evidence --strict` 校验通过且非空过；corpus finding_id 交叉标注合法。

## Non-Goals

- Delta overlay 行为验证（Stage 8）——本计划只验证非 Delta 的 XDSL 入口路径；`x:extends` 在 Delta 场景的效果属 Stage 8。
- Checkpoint/recovery 语义验证（Stage 9）——本计划只验证 XDSL `<checkpoint>` config 字段是否被 `StreamModelDslBuilder.applyCheckpointConfig()` 应用到 `env`，不验证 checkpoint 正确性。
- 远程/DISTRIBUTED 执行（Stage 13/14）。
- Connector source/sink 保证（Stage 15/16）。
- Window/watermark/timer 结果语义（Stage 11）——本计划验证 XDSL `<window>`/`<timestampsAndWatermarks>` 构造连通与编译，不验证窗口结果正确性。
- CEP 匹配语义（Stage 12）——本计划验证 XDSL `<cep>` 构造连通，不验证 NFA 匹配正确性。
- 修复本审计发现的 confirmed live defect（按 roadmap 规则指派 remediation plan）。

## Scope

### In Scope

- `ai-dev/audits/nop-stream-independent-audit/stage-7-xdsl-streammodel-entry.evidence.md`（domain evidence rows，manifest 域 b/c/f/g 范围内的 XDSL/flow-model source anchor + test lane）。**文件名必须是 `*.evidence.md` 且为 audit dir 直系子文件。**
- 支持/拒绝 XDSL 节点矩阵文本（写入证据文件头部，仅矩阵/判据不改 frozen 字段/词表）。

### Out Of Scope

- Delta overlay 行为（Stage 8）。
- 修复 confirmed live defect（指派 remediation plan）。
- DISTRIBUTED/远程执行路径（Stage 13/14）。
- Checkpoint/recovery/window/CEP/connector 语义（Stages 9/11/12/15/16）。
- 修改 frozen evidence-row 11 字段定义或 7 分类词表。

## Execution Plan

### Phase 1 - Supported XDSL Transform Node Inventory & Source-to-Sink Evidence

Status: completed
Targets: `ai-dev/audits/nop-stream-independent-audit/stage-7-xdsl-streammodel-entry.evidence.md`

- Item Types: `Proof`

- [x] 枚举 supported XDSL transform 节点并各产一条 evidence row（`source_anchor` 指向 `stream.xdef` 对应节点 line + `StreamModelDslBuilder.buildTransform():266` 或 `AdvancedTransforms.build():69` 的对应 dispatch 分支；`implementation_anchor` 指向对应的 `buildSource():313`/`buildMap():329`/`buildFilter():344`/`buildFlatMap():359`/`buildKeyBy():374`/`buildSink():384` 或 AdvancedTransforms 内的对应 build 方法）。覆盖：source、map、filter、flatMap、keyBy、window、aggregate、reduce、process、cep、custom、timestampsAndWatermarks、sink。
- [x] 每条 source-to-sink evidence row 的 `positive_proof` 来自一条 **in-process lane 实跑**（`.stream.xml` → `DslModelParser` → `StreamModelDslBuilder.of(model).build()` → `env.execute()` → sink 输出断言）；`environment_class >= in-process`。
- [x] 每条 row 标注 `required_lane`（连通/wiring 类构造最低 `in-process`；纯 parse 元数据可 `unit`）与 `finding_id`（交叉 corpus，如 M7-2-P2-2 flow/model duplicate tree）。

Exit Criteria:

- [x] evidence 文件存在，含 ≥10 条 supported-transform evidence row（覆盖 source/map/filter/flatMap/keyBy/window/aggregate/reduce/process/cep/custom/timestampsAndWatermarks/sink 的 supported 子集），格式经 `check-nop-stream-audit-manifest.mjs evidence --strict` 校验 exit 0
- [x] **端到端验证（Rule #22）**：**每条** supported-transform evidence row 的 `positive_proof` 是从 `.stream.xml` parse 到 `env.execute()` 到 sink 输出的 in-process 实跑测试名（`ClassName#method`），或该 row `disposition` 非 `e2e-proved`（`unverified`/`component-only`）并注明缺覆盖——不得"仅 1 条真 e2e + 其余用 component/unit 充数"
- [x] **接线验证（Rule #23）**：row 的 `runtime_wiring` 据 in-process 实跑裁定（`DslModelParser → StreamModelDslBuilder → StreamExecutionEnvironment` 确实连通），不得仅凭类存在标 `wired`
- [x] **无静默跳过**：任一构造无法在 in-process 实跑的，row `disposition` 标 `unverified`/`component-only`（Rule #24）
- [x] `No owner-doc update required`（证据文件是审计产出；不改 `docs-for-ai/`）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - XDSL → Java/Graph Trace & Topology/Stable-Identity Equivalence Evidence

Status: completed
Targets: `ai-dev/audits/nop-stream-independent-audit/stage-7-xdsl-streammodel-entry.evidence.md`

- Item Types: `Proof`

- [x] 产出 XDSL → Java/graph trace evidence row：`source_anchor` 指向 `StreamModelDslBuilder.build():106-118` + `buildTransforms():195-264`（topological sort）+ `StreamGraphGenerator.generate():110`（`:217-231` instanceof dispatch + `populateStreamModel():126`）。**等价性传递性论证**：topology 等价靠两段传递成立——(a) `TestDagTopologyConsistency`（**build-only，不调 `execute()`**）证明 XDSL `<transforms>`/`<edges>` 与 Java 入口产出的 `Transformation` DAG 在 Transformation 层一一对应；(b) Stage 6 frozen 判据已证 Java `Transformation` → `StreamGraph` → `JobGraph` 经 `execute()` 等价。故 XDSL ≡ graph 传递成立。**但 repo 无直接 XDSL → `execute()` → graph topology 断言测试**，故本 row `disposition` 须标 `component-only` + `positive_proof: manual-trace:<anchor>`（引用传递性论证），**不得**标 `e2e-proved`（因 `TestDagTopologyConsistency` 不调 `execute()` 且只比较 Transformation 层非 StreamGraph/JobGraph 节点/边）。
- [x] 产出 stable-identity 等价性 evidence row：`source_anchor` 指向 `StreamTransformModel.id`（`stream.xdef:104` 字段定义，`:108` 为 `<transforms>` 的 `xdef:key-attr="id"`）→ `StreamModelDslBuilder.buildTransform():266` → `Transformation` → `StreamNode`/`JobVertex` id 传播路径。**repo 无测试把 XDSL transform id 追溯到 StreamNode/JobVertex**，故 `disposition` 须标 `component-only` + `positive_proof: manual-trace:<anchor>`，引用 Stage 6 stable-identity 判据做传递性论证，**不得**标 `e2e-proved`。
- [x] 产出 checkpoint config 应用 evidence row：`source_anchor` 指向 `StreamModelDslBuilder.applyCheckpointConfig():124` + `CheckpointConfigModel` 字段。**repo 无测试断言 XDSL `<checkpoint>` 字段经 `applyCheckpointConfig()` 应用到 `env`**，故 `disposition` 须标 `component-only` 或 `unverified` + `positive_proof: manual-trace:<anchor>`（代码追踪 `applyCheckpointConfig` 读取哪些字段并调 `env.setCheckpointConfig(...)`），**不得**标 `e2e-proved`。supported 字段（enabled/interval/processingGuarantee/timeout/maxConcurrentCheckpoints/minPause/maxRetainedCheckpoints/jobTerminationMode）据代码追踪裁定；unused 字段在 Phase 4 裁定。

Exit Criteria:

- [x] ≥3 条 trace/equivalence evidence row，格式校验 exit 0
- [x] **接线验证（Rule #23）**：trace row 的 `runtime_wiring` 经实跑/manual-trace 证明 `DslModelParser → StreamModelDslBuilder → StreamExecutionEnvironment → StreamGraphGenerator` 路径确实连通（非仅类存在）
- [x] **端到端验证**：topology 等价 row 的 `positive_proof` 引用一条 in-process 实跑测试（XDSL → execute → graph topology 断言），或 `disposition` 非 `e2e-proved` 并注明
- [x] **无静默跳过**：checkpoint config 的 unused 字段不得被静默当作 applied——须有独立 evidence row 裁定（Phase 4 覆盖）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - Unsupported XDSL Node Fail-Fast Coverage Matrix

Status: completed
Targets: `ai-dev/audits/nop-stream-independent-audit/stage-7-xdsl-streammodel-entry.evidence.md`

- Item Types: `Decision | Proof`

- [x] 为每个 unsupported XDSL 节点各产一条 evidence row：`disposition` 为 `fail-fast`（有 `rejection_proof` 验证抛 `UnsupportedOperationException`）或 `non-goal`（注明 out-of-scope for current supported baseline）。覆盖：union（`AdvancedTransforms.buildUnion():244-250`）、sideOutput（`AdvancedTransforms.buildSideOutput():322-329`）、streams（`StreamModelDslBuilder.failFastOnUnsupportedRegistries():157`）、sideInputs（`:161`）、environments（`:165`）、requirements（`:169`）、checkpointParticipants（`:173`）、onStart/onEnd/onError（`:177`）、schemas（`:181`）、coders（`:185`）、unknown transform type（`AdvancedTransforms.build():100-101`）。
- [x] `rejection_proof` 引用 `TestStreamModelDslBuilderFailFast#<method>` 或 inline XML 测试（若有）；若某节点无 rejection 测试，`disposition` 标 `unverified` 而非 `fail-fast`。
- [x] 冻结**支持/拒绝 XDSL 节点矩阵**文本（写入证据文件头部）：supported transforms（source/map/filter/flatMap/keyBy/window/aggregate/reduce/process/cep/custom/timestampsAndWatermarks/sink）、fail-fast transforms（union/sideOutput/unknown）、fail-fast registries（streams/sideInputs/environments/requirements/checkpointParticipants/onStart/onEnd/onError/schemas/coders）。

Exit Criteria:

- [x] ≥11 条 unsupported-node evidence row（union/sideOutput/streams/sideInputs/environments/requirements/checkpointParticipants/onStart/onEnd/onError/schemas/coders/unknown），`disposition` 为 `fail-fast` 或 `non-goal` 或 `unverified`，格式校验 exit 0
- [x] 支持/拒绝节点矩阵在证据文件头部有显式文本
- [x] **无静默跳过（Rule #24）**：unsupported 节点不得被静默当作 supported；每个要么 `fail-fast`（有 rejection_proof 验证抛异常）要么 `non-goal`（注明 out-of-scope）要么 `unverified`（注明缺 rejection 测试）
- [x] `node ai-dev/tools/check-nop-stream-audit-manifest.mjs evidence --strict` exit 0，且校验器实际解析到本 stage 证据行（非空过）
- [x] `No owner-doc update required`
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - Production-Wiring Gap, Demo Dangling Transforms & Checkpoint Config Unused Fields Disposition

Status: completed
Targets: `ai-dev/audits/nop-stream-independent-audit/stage-7-xdsl-streammodel-entry.evidence.md`

- Item Types: `Decision | Proof`

- [x] 产出 **XDSL 入口无生产接线** evidence row：`source_anchor` 指向 `StreamModelDslBuilder.of():94` + grep evidence（`nop-stream/**/src/main/**/*.java` 对 `DslModelParser`/`StreamModelDslBuilder.of` 零命中）；`disposition: residual-risk`；`runtime_wiring: partial`（test wired, production unwired）；`required_lane: in-process`；`positive_proof: none`（无生产实跑）；`rejection_proof: none`；在 `declared_guarantee` 中注明"XDSL 入口路径当前仅 test-only invocation；无 runtime loader/dispatcher/bean 在 `main/` 代码中加载 `.stream.xml`；demo `fraud-detection.stream.xml` 无 Java driver class"。
- [x] 产出 **demo dangling transforms** evidence row：`source_anchor` 指向 `fraud-detection.stream.xml` 中 split-map/sum-reduce transforms（无 edge 连接）；`disposition: residual-risk`（latent build failure）；注明"demo 含 zero-upstream transforms (split-map/sum-reduce)，`buildTransforms()` 在 build 时对它们调 `requireSingleInput(upstreamIds)` 发现 `found 0` 会抛 `IllegalArgumentException`——demo 当前无 Java driver class 故从未触发此 build failure，但若被加载执行则会 fail-fast"。
- [x] 产出 **checkpoint config unused 字段** evidence row：`source_anchor` 指向 `CheckpointConfigModel` 中 storageConfig/storageType/barrierAlignmentTimeout/maxConsecutiveCheckpointFailures/jobId/pipelineId + `StreamModelDslBuilder.applyCheckpointConfig():124`（只应用部分字段）；`disposition: residual-risk`（parsed but not applied to env）；注明 unused 字段清单。
- [x] 全 evidence 文件回归校验 + corpus 交叉标注核对。

Exit Criteria:

- [x] ≥3 条 disposition evidence row（production-wiring gap + demo dangling + checkpoint unused fields），格式校验 exit 0
- [x] **无静默跳过（Rule #24）**：production-wiring gap 不得被静默当作 `wired`——须显式标 `partial`/`unwired` 并注明"test-only invocation"
- [x] `node ai-dev/tools/check-nop-stream-audit-manifest.mjs evidence --strict` exit 0，且校验器实际解析到行（非空过）；finding_id 交叉标注合法（ID 在 frozen corpus 内或 `none`）
- [x] `No owner-doc update required`
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **审计计划（无生产代码变更）**：本计划产出为 evidence rows + 矩阵文本，不改 nop-stream 生产代码。`./mvnw test`/`compile` 不强制；改为以 evidence 校验器退出码 + in-process 实跑证据引用为 closure 依据。但若审计中发现 confirmed live defect，按 roadmap 规则指派 remediation plan。

- [x] supported XDSL transform 节点各有 source-to-sink evidence row（in-process lane 实跑或如实标注缺覆盖）
- [x] XDSL → Java/graph trace + topology/stable-identity 等价性已验证（runtime_wiring 经实跑/manual-trace 裁定）
- [x] unsupported XDSL 节点各有 `fail-fast`/`non-goal`/`unverified` 裁定（无静默放行）
- [x] production-wiring gap（test-only invocation）显式裁定为 `residual-risk`
- [x] demo dangling transforms + checkpoint config unused fields 有显式 disposition
- [x] 支持/拒绝节点矩阵显式成文
- [x] 所有 evidence row 经 `check-nop-stream-audit-manifest.mjs evidence --strict` exit 0，且**非空过**
- [x] 不存在被静默降级到 deferred 的 in-scope 审计项
- [x] 审计发现的任何 confirmed live defect 已指派 active/successor remediation plan
- [x] `No owner-doc update required`（不改 `docs-for-ai/`）
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 验证（a）source-to-sink row 的 `positive_proof` 确为 in-process 实跑测试名（非组件 unit 充数），（b）`disposition: e2e-proved` 的 row 其 `positive_proof` 均为真实 `ClassName#method`，（c）`runtime_wiring=wired` 确经接线验证，（d）unsupported 节点无静默放行，（e）production-wiring gap 如实标 `partial`

## Deferred But Adjudicated

（执行中如出现延期项，须写明 Classification / Why Not Blocking Closure / Successor Required。预期场景：某 supported transform 的 in-process 实跑因 CEP/window 依赖未就绪——此类应标 `disposition: unverified`/`component-only` 而非 deferred，因为 disposition 是本计划合法终态。）

## Non-Blocking Follow-ups

- XDSL 入口的 `residual-risk`（test-only invocation）如需升级为 production-supported，由独立 remediation plan 添加 runtime loader/bean。
- Delta overlay 行为（`x:extends` / `_delta/default/`）属 Stage 8（Delta StreamModel entry audit）；本计划只验证非 Delta 的 XDSL 入口。
- corpus 中无直接 target XDSL 入口的 finding——如本审计发现新 finding（如 production-wiring gap），按 roadmap 规则指派 remediation plan 并在 evidence row 标注。

## Closure

Status Note: All 4 Phases and 13 Closure Gates verified PASS by independent closure audit. 30 evidence rows (EVID-S7-001..030) produced covering 13 supported transforms, 3 trace/equivalence rows, 11 unsupported-node rows, and 3 residual-risk dispositions. Anti-Hollow check confirmed: all 6 e2e-proved positive_proofs are real in-process tests (parse → build → execute → sink assertion); 3 unverified fail-fast rows honestly lack rejection tests; production-wiring gap honestly classified partial (zero main/ hits for DslModelParser/parseFromResource/.stream.xml); demo dangling transforms (split-map, sum-reduce) confirmed zero-edge with latent requireSingleInput fail-fast; checkpoint unused fields (storageConfig/storageType/barrierAlignmentTimeout/maxConsecutiveCheckpointFailures/jobId/pipelineId) confirmed parsed-but-not-applied. Manifest validator exit 0. No silent no-ops or disposition inflation found.
Completed: 2026-08-08

Closure Audit Evidence:

- Reviewer / Agent: independent closure-audit sub-agent (fresh read-only audit; not the implementer)
- Audit Session: closure-audit of plan 2026-08-08-0010-2 (ses_022b1ebb2ffe3sNt3vYFLDeVVt)
- Evidence:
  - Phase 1 (13 rows): 6 e2e-proved (source/map/filter/keyBy/reduce/sink — real in-process tests verified at TestStreamModelDslBuilderE2E:69, TestStreamModelDeltaExtends:104, TestAdvancedPipelineE2E:79), 7 component-only (flatMap/window/aggregate/process/cep/custom/timestampsAndWatermarks — honest gap, TestAdvancedTransforms methods verified real). PASS.
  - Phase 2 (3 rows): topology component-only (TestDagTopologyConsistency confirmed build-only, no execute()); stable-identity component-only (no XDSL-id→StreamNode trace test; XDSL id is streamRegistry key only, Transformation.getId() is unrelated AtomicInteger); checkpoint-applied component-only. PASS.
  - Phase 3 (11 rows): 7 fail-fast with real rejection_proof tests (union/sideOutput/streams/sideInputs/environments/schemas/coders); 3 unverified (requirements/checkpointParticipants/onStart-onEnd-onError — no rejection test exists in TestStreamModelDslBuilderFailFast, honest); 1 fail-fast manual-trace (unknown type, unreachable via valid XDSL parse). PASS.
  - Phase 4 (3 rows): production-wiring partial (grep nop-stream/**/src/main/**/*.java = 0 hits for DslModelParser/parseFromResource/.stream.xml); demo dangling transforms (split-map/sum-reduce zero-edge, requireSingleInput :292-306 would throw IllegalArgumentException "found 0"); checkpoint unused fields (6 fields in _CheckpointConfigModel, 0 references in StreamModelDslBuilder.applyCheckpointConfig). PASS.
  - `node ai-dev/tools/check-nop-stream-audit-manifest.mjs evidence --strict` exit 0 (parsed 30 EVID-S7 rows, non-empty).
  - `node ai-dev/tools/check-plan-checklist.mjs <plan> --strict` exit 0 after Closure section filled.
  - `./mvnw test -pl nop-stream/nop-stream-flow -am -T 1C` → BUILD SUCCESS, 51 tests 0 failures (all cited test classes green).
- Follow-up:
  - (1) add rejection tests for requirements/checkpointParticipants/onStart-onEnd/onError to close EVID-S7-024/025/026 unverified gap (test-effectiveness remediation, Stage 17 lane)
  - (2) add `<flatMap>` in-process e2e fixture to close EVID-S7-004 component-only gap (test-effectiveness remediation)
  - (3) production-wiring upgrade (runtime loader/bean in main/) if XDSL entry becomes production-supported — independent remediation plan
  - (4) Delta overlay behavior (`x:extends` / `_delta/default/`) — Stage 8
