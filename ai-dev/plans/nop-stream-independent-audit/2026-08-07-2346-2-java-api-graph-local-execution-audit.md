# 3 Java API, Graph & LOCAL Execution Audit (nop-stream Independent Audit)

> Plan Status: completed
> Last Reviewed: 2026-08-07
> Draft Review: round 1 independent sub-agent review — 1 Blocker + 5 Majors found (#1 evidence path 子目录+错后缀→校验器空过; #2 compile chain 序错(GEP before generateLocal, 漏 PartitionedPlanGenerator/TaskExecutor, 误引 :534); #3 e2e-proved+manual-trace 漏洞; #4 uncovered-construct scope 歧义; #5 Exit Criteria "至少一条" vs "每条"). Round 2 fixes: evidence path→`*.evidence.md` 直系子文件+非空过校验; compile chain 按 live `execute():282-350` 实际序; 新增 e2e-proved 须真实测试名规则 + 覆盖缺口裁定规则; Exit Criteria 改"每条"; Stage 5 依赖澄清(仅依赖 Stage 4); Current Baseline :534 注明为 savepoint 路径. Round-2 独立 review verdict: **Consensus YES**（1 Blocker + 5 Majors 全部 RESOLVED；新增 1 Minor 已修）. 已 promoted active.
> Source: `ai-dev/backlog/nop-stream-independent-audit-roadmap.md` (Stage 6); frozen Stage-4 outputs (`source-manifest.md`, `evidence-schema.md`, `finding-corpus.md`, `ai-dev/tools/check-nop-stream-audit-manifest.mjs`); live repo baseline of `nop-stream-core` datastream/environment/graph/jobgraph/execution surfaces.
> Mission: nop-stream-independent-audit
> Work Item: 6. Java API, graph and LOCAL execution audit
> Related: Execution order `{2}` of this DRAFT_PLANS round. Roadmap 依赖表中 Stage 6 **仅**依赖 Stage 4（`A4 --> A6`，无 `A5 --> A6` 边）——本计划只用 in-process lane（始终可用、无需外部 provision），故**不阻塞于** Stage 5；Stage 5 的 lane 规则仅在分类 `environment_class` 时被惰性引用（in-process 已是 frozen 词表值，无需等 Stage 5 资格认定）。直接后继 Stage 4。Hard prerequisite for Stages 7 (XDSL), 9 (checkpoint), 11 (window), 12 (CEP), 15 (connectors)。On critical path。

## Purpose

独立验证 nop-stream 的 **Java 入口路径**是否实现其设计目标：从用户用 Java DataStream API 构建拓扑（`env.addSource()…map/keyBy/process…sink()`），经 graph/plan 编译（`StreamGraphGenerator → StreamGraph → JobGraphGenerator → GraphExecutionPlan`），在 **LOCAL 执行模式**下从 source 完整跑到 sink，产出正确结果。本审计只采信 **in-process lane**（单 JVM 全管线）以上的证据；不把组件级 unit 测试当作系统能力证明。每个被支持的 API 构造必须形成一条可复核的 source-to-sink evidence row；每个不支持的构造（two-input/side-output）必须有 fail-fast 证明或显式 non-goal 裁定。

本审计**发现**的任何 confirmed live defect 不在本计划内修复，而按 roadmap 规则（"A confirmed defect must be assigned to a new plan or an existing active plan"）指派给 active/successor remediation plan。

## Current Baseline

经 2026-08-07 live repo 核对（引用均与 frozen Stage-4 `source-manifest.md` 域 a/b/g + 实际源码一致）：

- **Java API 入口**：`nop-stream-core/src/main/java/io/nop/stream/core/environment/StreamExecutionEnvironment.java`——默认 `DeploymentMode.LOCAL`（`:79`），`execute()` 经 `StreamGraphGenerator`（`:279`/`:282`）构图，LOCAL 模式调 `provider.generateLocal(partitionedPlan)`（`:521`）；DISTRIBUTED 模式走 `executionDispatcher`（`:303/310/516`，属 Stage 13/14 范围，本计划 out-of-scope）。（注：`:534` 是 `buildJobGraph()` 即 savepoint 路径，非 `execute()` 主路径，不得用作 execute() 编译链 anchor。）
- **DataStream 公共 API 面**（`DataStream.java` 接口）：`keyBy(KeySelector)`→`KeyedStream`、`map(MapFunction)`、`filter(FilterFunction)`、`flatMap(FlatMapFunction)`、`process(ProcessFunction)`（有状态）、`assignTimestampsAndWatermarks`、`transform(operator,typeInfo)`（用户算子）、sink 族 `print/print(SinkFunction)/collect(CollectorFunction)/sink(SinkFunction)`。**支持的构造是 one-input 链 + keyBy 分区 + sink 终止**。
- **明确 absent 的构造**（确认 two-input/side-output 在 core DataStream 接口不存在）：无 `connect()`、无 `union()`、无 `getSideOutput()/OutputTag` 在 `DataStream.java`。Side-output 仅存在于 flow 模型 `StreamSideOutputModel`（`nop-stream-flow`，属 Stage 7 XDSL 范围）。`TwoInputStreamOperator`/`MultipleInputStreamOperator` 在 main source 无实现类（仅历史 Javadoc 残留引用，见 corpus M7-2-P2-3）。
- **graph/plan 编译链**：`StreamGraphGenerator`（`core/graph/`）→ `StreamGraph` → `JobGraphGenerator`（`core/jobgraph/`）→ partitioned plan → `GraphExecutionPlan`（`core/execution/`）→ `generateLocal` 执行。`Transformation → StreamNode → JobVertex → GraphExecutionPlan` 是稳定身份传播路径（`SingleOutputStreamOperatorImpl.java:46` Javadoc 述）。
- **历史 finding 交叉**（corpus）：M7-2-P2-5（DataStream API 把 `UnknownTypeInformation.INSTANCE<?>` 强转 `TypeInformation<R>`，6+ 入口）、M7-2-P2-3（公共 operator 接口 Javadoc 引用不存在类型 TwoInputStreamOperator 等）、M7-2-P0-1（CEP `forceNonParallel()` 历史 always-throws；当前实现已改为 `transformation.lockParallelismToOne()` `SingleOutputStreamOperatorImpl.java:53-57`，需 live 复验）。这些 finding 的最终 disposition 属 Stages 19-22，但本审计须据 live 行为标注其 evidence row 的 `finding_id`。
- **测试语料**（manifest 域 g）：core/runtime 含 in-process 集成测试（recovery/exactly-once/LOCAL barrier alignment 等），可用作 source-to-sink trace 的 in-process lane 证据来源。
- **真实 gap**：(1) 没有覆盖"支持的 DataStream 构造 → source-to-sink 实跑"的成套 evidence row；(2) 没有 graph/plan 编译 + 算子生命周期的可复核证据（open/initializeState/processElement/finish/snapshotState 是否在 LOCAL 路径被实际调用）；(3) 没有 fan-out/partition/parallelism 的独立证据（keyBy 分区、parallelism 传播、forceNonParallel 锁定）；(4) 没有 unsupported two-input/side-output 的 fail-fast 证明或显式 non-goal 裁定；(5) 没有 topology/stable-identity/recovery-inputs 的等价性判据成文。

## Goals

- 为**每个支持的 DataStream 构造**（source → map/filter/flatMap/process/keyBy → sink）产出一条 source-to-sink evidence row，`environment_class >= in-process`，正向 proof（in-process 实跑 sink 输出断言）+ 拒绝 proof（违规时 fail-fast / 回归断言），`disposition` 按 frozen 词表裁定。
- **`e2e-proved` 审计过程规则（本计划强制，补 frozen 校验器只查 lane 强度、不查 positive_proof 形态的缺口）**：`disposition: e2e-proved` 的 evidence row，其 `positive_proof` 必须是真实测试名（`ClassName#method`），**不得**是 `manual-trace:<...>` 或 `none`。若某构造只有 manual-trace（代码追踪）证据而无实跑测试，`disposition` 必须降级为 `component-only` 或 `unverified`，不得标 `e2e-proved`。`manual-trace` 仅可用于辅助说明 `runtime_wiring`，不可单独支撑 `e2e-proved`。
- **覆盖缺口裁定规则**：本计划是独立审计，**不新增 nop-stream 生产代码/生产测试**（产出为 evidence rows + 判据文本）。若某支持的构造在 repo 中无 in-process source-to-sink 测试可作正向 proof，其 evidence row `disposition` 标 `unverified`，并把"缺测试覆盖"作为发现按 roadmap 规则指派给 active/successor remediation plan（或在对应域 audit 中接续）——**不得**为凑 `e2e-proved` 而写新测试冒充既有覆盖，亦不得静默跳过该构造。
- 产出 **graph/plan 编译 + 算子生命周期**证据：`StreamGraphGenerator → JobGraphGenerator → GraphExecutionPlan` 链路在 LOCAL 路径确实被 `execute()` 调用（接线），算子 open/initializeState/processElement/finish 在 LOCAL 实跑中被实际触发（非仅类型存在）。
- 产出 **fan-out / partition / parallelism** 证据：keyBy 分区、parallelism 沿 Transformation→JobVertex 传播、`forceNonParallel`→`lockParallelismToOne` 在 LOCAL 实跑中生效。
- 为 **unsupported 构造**（two-input `connect`、`union`、side-output）产出 fail-fast 证明或显式 `non-goal` 裁定（引用 frozen `evidence-schema.md` non-goal 定义）。
- 冻结 **等价性判据**：topology 等价（Java 入口 vs 编译后 StreamGraph/JobGraph 节点/边一致）、stable identity（operator/vertex id 在 Transformation→StreamNode→JobVertex 传播不失真）、recovery inputs（LOCAL 模式恢复所需输入的判据，为 Stage 9 接续预留但不越界做 recovery 验证）。
- 所有 evidence row 经 `check-nop-stream-audit-manifest.mjs evidence` 校验通过；corpus finding 交叉标注。

## Non-Goals

- XDSL/Delta 入口行为（Stage 7 XDSL、Stage 8 Delta）。
- Checkpoint/recovery 语义验证（Stage 9）——本计划只冻结 recovery inputs 的等价性判据，不做 recovery 实跑。
- 远程/DISTRIBUTED 执行（Stage 13 控制面、Stage 14 数据面）。
- Connector source/sink 保证（Stage 15/16）——本计划只验证 sink 终止原语（print/collect/sink(SinkFunction)）在 LOCAL 路径连通，不验证具体外部 connector。
- Window/watermark/timer 结果语义（Stage 11）——本计划验证 `assignTimestampsAndWatermarks`/`keyBy` 构造连通与编译，不验证窗口结果正确性。
- CEP 匹配语义（Stage 12）。
- 修复本审计发现的 confirmed live defect（按 roadmap 规则指派 remediation plan）。

## Scope

### In Scope

- `ai-dev/audits/nop-stream-independent-audit/stage-6-java-api-graph-local.evidence.md`（domain evidence rows，manifest 域 a/b/g 范围）。**文件名必须是 `*.evidence.md` 且为 audit dir 直系子文件**——校验器 `check-nop-stream-audit-manifest.mjs` 的 `collectEvidenceRows()`（`:285-287`）用非递归 `readdirSync` 只扫 audit dir 直系 `*.evidence.md`；用其他路径/后缀会导致校验器读到 0 行、`evidence` 子命令空过（vacuous pass）。
- 等价性判据文档段（topology / stable identity / recovery inputs）——写入证据文件头部或 `evidence-schema.md` 增补（仅判据文本，不改字段/词表）。

### Out Of Scope

- 修复 confirmed live defect（指派 remediation plan）。
- DISTRIBUTED/远程执行路径（Stage 13/14）。
- XDSL/Delta/window/CEP/connector 语义（Stages 7/8/11/12/15/16）。
- 修改 frozen evidence-row 11 字段定义或 7 分类词表。

## Execution Plan

### Phase 1 - Java API Construction Surface Inventory & Source-to-Sink Evidence

Status: completed
Targets: `ai-dev/audits/nop-stream-independent-audit/stage-6-java-api-graph-local.evidence.md`

- Item Types: `Proof`

- [x] 枚举支持的 DataStream 构造并各产一条 evidence row（`source_anchor` 指向 `DataStream.java`/`DataStreamImpl.java`/`KeyedStreamImpl.java`/`SingleOutputStreamOperatorImpl.java` 的对应方法；`implementation_anchor` 指向实现）：map、filter、flatMap、process(有状态)、keyBy、assignTimestampsAndWatermarks、transform(用户算子)、sink 族(print/collect/sink(SinkFunction))。
- [x] 每条 source-to-sink evidence row 的 `positive_proof` 来自一条 **in-process lane 实跑**（`env.addSource()…<构造>…sink()` 在单 JVM 完整跑通，sink 输出断言通过）；`environment_class >= in-process`。
- [x] 每条 row 标注 `required_lane`（连通/wiring 类构造最低 `in-process`；纯 API 元数据可 `unit`）与 `finding_id`（交叉 corpus，如 map/filter 入口的 M7-2-P2-5 强转 finding）。

Exit Criteria:

- [x] evidence 文件存在，含 ≥8 条 supported-构造 evidence row（覆盖 map/filter/flatMap/process/keyBy/assignTimestampsAndWatermarks/transform/sink），格式经 `check-nop-stream-audit-manifest.mjs evidence` 校验 exit 0
- [x] **端到端验证（Rule #22）**：**每条** supported-构造 evidence row 的 `positive_proof` 是从 `env.addSource()` 到 sink 输出的 in-process 实跑测试名（`ClassName#method`），或该 row `disposition` 非 `e2e-proved`（`unverified`/`blocked`）并注明缺覆盖——不得"仅 1 条真 e2e + 其余用 component/unit 充数"（见 Goals 覆盖缺口裁定规则）
- [x] **接线验证（Rule #23）**：row 的 `runtime_wiring` 字段据 LOCAL 实跑裁定（`wired`/`partial`/`unwired`），不得仅凭方法存在标 `wired`
- [x] **无静默跳过**：任一构造无法在 in-process 实跑的，row `disposition` 标 `unverified`/`blocked`（不得 `e2e-proved`），并显式说明（Rule #24）
- [x] `No owner-doc update required`（证据文件是审计产出；不改 `docs-for-ai/`）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - Graph/Plan Compilation & Operator Lifecycle Audit

Status: completed
Targets: `ai-dev/audits/nop-stream-independent-audit/stage-6-java-api-graph-local.evidence.md`

- Item Types: `Proof`

- [x] 产出 graph/plan 编译链 evidence row，按 **live `execute()` 实际调用序**（`StreamExecutionEnvironment.java:279-350`）：`StreamGraphGenerator.generate(sinkList)`（`:282`→`StreamGraph`）→ `JobGraphGenerator.generate(streamGraph)`（`:285`→`JobGraph`）→ `PartitionedPlanGenerator.generate(jobGraph,fp)`（`:292`→`PartitionedPlan`）→ `generateDeploymentPlan(partitionedPlan)`（`:294`，LOCAL 模式经 `provider.generateLocal` `:521`→`DeploymentPlan`）→ `GraphExecutionPlan.build(jobGraph,deploymentPlan,barrierAlignment)`（`:317`）→ `TaskExecutor.submitTask`（`:346`）→ `awaitCompletion`（`:350`）。`source_anchor`/`implementation_anchor` 指向 `environment/StreamExecutionEnvironment.java:282,285,292,294,317,346` + `graph/StreamGraphGenerator.java` + `jobgraph/JobGraphGenerator.java` + `execution/GraphExecutionPlan.java`。**注意**：`:534` 是 `buildJobGraph()`（savepoint 路径），**非** `execute()` 主路径——不得用作 execute() 编译链 anchor；checkpoint-enabled 分支（`:296-301` `checkpointExecutorFactory.executeWithCheckpoint`）属 Stage 9 边界，本计划不验证该分支。
- [x] 产出算子生命周期 evidence row：open/initializeState/processElement/finish 在 LOCAL 实跑中被实际触发（`runtime_wiring=wired` 的依据是实跑断言或 manual-trace，非仅方法定义存在）；引用 corpus M7-2-P1-4（StreamOperator.initializeState(TaskStateSnapshot) 历史 never-called）与 M7-2-P1-5（finish() 历史 never-called）做 live 复验标注。

Exit Criteria:

- [x] ≥2 条 graph/plan + 生命周期 evidence row，格式校验 exit 0
- [x] **接线验证（Rule #23）**：编译链 row 的 `runtime_wiring` 经实跑/manual-trace 证明 `execute()` 确实调用 `StreamGraphGenerator`→…→`generateLocal`（非仅 import 存在）；算子生命周期 row 证明 open/processElement 等在 LOCAL 实跑被触发
- [x] **无静默跳过**：若某生命周期钩子（如 finish）在 LOCAL 实跑未被调用，row `disposition` 标 `unverified`/`residual-risk`（非 `e2e-proved`），并关联 corpus finding
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - Fan-Out, Partition & Parallelism Audit

Status: completed
Targets: `ai-dev/audits/nop-stream-independent-audit/stage-6-java-api-graph-local.evidence.md`

- Item Types: `Proof`

- [x] 产出 fan-out/partition evidence row：keyBy 分区在 LOCAL 实跑中按 KeySelector 路由（`source_anchor`→`DataStream.keyBy`/`KeyedStreamImpl`）；parallelism 沿 Transformation→StreamNode→JobVertex 传播不失真。
- [x] 产出 `forceNonParallel`→`lockParallelismToOne` evidence row：live 复验 M7-2-P0-1（历史 always-throws）——当前 `SingleOutputStreamOperatorImpl.java:53-57` 调 `transformation.lockParallelismToOne()`，在 LOCAL 实跑中验证 parallelism 被锁为 1 且不抛（或在越界 override 时 fail-fast）。

Exit Criteria:

- [x] ≥2 条 fan-out/partition/parallelism evidence row，格式校验 exit 0
- [x] **端到端验证**：partition row 的 `positive_proof` 来自 in-process 实跑（多 subtask + keyBy 路由断言），非纯 unit
- [x] M7-2-P0-1（forceNonParallel）的 live 复验结果写入 row（`finding_id` 标注 + `disposition` 据 live 行为裁定）
- [x] **无静默跳过**：parallelism 越界或锁定失效时显式标 `unverified`/`residual-risk`，非 `e2e-proved`
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - Unsupported Forms Fail-Fast Audit & Equivalence Criteria Freeze

Status: completed
Targets: `ai-dev/audits/nop-stream-independent-audit/stage-6-java-api-graph-local.evidence.md`, 等价性判据段

- Item Types: `Decision | Proof`

- [x] 为 unsupported 构造（two-input `connect`、`union`、core side-output/OutputTag）各产一条 evidence row：`disposition` 为 `non-goal`（引用 frozen 词表定义，注明 out-of-scope for current supported baseline）或 `fail-fast`（若代码在误用时抛异常）；`source_anchor` 指向 absent 的 API 位（如 `DataStream.java` 无 connect/union）+ corpus M7-2-P2-3（Javadoc 残留引用不存在类型）。
- [x] 冻结等价性判据文本（写入证据文件头部或 `evidence-schema.md` 增补，仅判据不改字段/词表）：(a) topology 等价——Java 入口构造与编译后 StreamGraph/JobGraph 的节点/边一一对应；(b) stable identity——operator/vertex id 在 Transformation→StreamNode→JobVertex→GraphExecutionPlan 传播不失真；(c) recovery inputs 判据——LOCAL 模式恢复所需输入的可观测判据（为 Stage 9 接续预留，本计划不做 recovery 实跑）。
- [x] 全 evidence 文件回归校验 + corpus 交叉标注核对。

Exit Criteria:

- [x] ≥3 条 unsupported-构造 evidence row（connect/union/side-output），`disposition` 为 `non-goal` 或 `fail-fast`，格式校验 exit 0
- [x] 等价性判据（topology / stable identity / recovery inputs）有显式文本
- [x] **无静默跳过（Rule #24）**：unsupported 构造不得被静默当作 supported；每个要么 `non-goal` 要么 `fail-fast`（误用时抛异常，非静默放行）
- [x] `node ai-dev/tools/check-nop-stream-audit-manifest.mjs evidence` exit 0，且校验器实际解析到本 stage 证据行（非 "0 evidence rows" 空过）；finding_id 交叉标注可被 `corpus` 子命令承认（ID 在 frozen corpus 内或 `none`）
- [x] `No owner-doc update required`（判据文本写入审计 schema/证据文件；不改 `docs-for-ai/`）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **审计计划（无生产代码变更）**：本计划产出为 evidence rows + 判据文本，不改 nop-stream 生产代码。`./mvnw test`/`compile` 不强制；改为以 evidence 校验器退出码 + in-process 实跑证据引用为 closure 依据。但若审计中发现 confirmed live defect，按 roadmap 规则指派 remediation plan（不在本计划内修复）。

- [x] supported DataStream 构造（map/filter/flatMap/process/keyBy/assignTimestampsAndWatermarks/transform/sink）各有 source-to-sink evidence row（in-process lane 实跑）
- [x] graph/plan 编译链 + 算子生命周期接线已验证（runtime_wiring 经实跑/manual-trace 裁定，非仅类型存在）
- [x] fan-out/partition/parallelism + forceNonParallel live 复验完成
- [x] unsupported 构造（connect/union/side-output）各有 `non-goal` 或 `fail-fast` 裁定（无静默放行）
- [x] 等价性判据（topology/stable-identity/recovery-inputs）显式成文
- [x] 所有 evidence row 经 `check-nop-stream-audit-manifest.mjs evidence` exit 0，且**非空过**——校验器实际解析到本 stage 产出的 ≥N 条 `@@EVIDENCE` 行（须用 `*.evidence.md` 直系子文件；确认校验器日志非 "0 evidence rows yet"）；corpus finding_id 交叉标注合法
- [x] 不存在被静默降级到 deferred 的 in-scope 审计项（每个构造有明确 disposition）
- [x] 审计发现的任何 confirmed live defect 已指派 active/successor remediation plan（不在本计划内吞掉）
- [x] `No owner-doc update required`（不改 `docs-for-ai/`）
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 验证（a）source-to-sink row 的 `positive_proof` 确为 in-process 实跑测试名（非组件 unit 充数，非 manual-trace），（b）`disposition: e2e-proved` 的 row 其 `positive_proof` 均为真实 `ClassName#method`（无 `e2e-proved`+`manual-trace`/`none` 组合），（c）`runtime_wiring=wired` 确经接线验证（非仅方法存在），（d）unsupported 构造无静默放行

## Deferred But Adjudicated

（执行中如出现延期项，须写明 Classification / Why Not Blocking Closure / Successor Required。预期场景：某构造的 in-process 实跑因依赖未就绪而暂缓——此类应标 `unverified`/`blocked` 而非 deferred，因为 disposition 是本计划合法终态并由 roadmap 规则承担其后果。）

## Non-Blocking Follow-ups

- 等价性判据中的 "recovery inputs" 仅冻结判据，recovery 实跑属 Stage 9；本计划不越界。
- 若后续 XDSL/Delta 审计（Stages 7/8）发现 Java 入口与 XDSL 入口的拓扑等价需补充构造，由对应 stage plan 触发增补 evidence row（successor）。
- DataStream API 的 `UnknownTypeInformation` 强转（M7-2-P2-5）若经本审计 live 复验仍为 defect，指派 remediation plan；其 disposition 由 Stages 19-22 最终裁定。

## Closure

Status Note: Stage 6 audit complete. Produced 16 evidence rows (9 supported-construct source-to-sink, 2 graph/plan+lifecycle, 2 fan-out/partition/parallelism, 3 unsupported-form non-goal) plus frozen equivalence criteria (topology/stable-identity/recovery-inputs) in `stage-6-java-api-graph-local.evidence.md`. No production code changed (audit-only). Each `e2e-proved` row's `positive_proof` is a real `env.execute()` source-to-sink test verified green; the two constructs lacking such a test (process, forceNonParallel) are honestly classified `component-only` with the coverage gap flagged for successor test-effectiveness work. Historical findings M7-2-P0-1 and M7-2-P1-5 revalidated as RESOLVED on the LOCAL path; M7-2-P2-3 revalidated as STALE; M7-2-P2-5 confirmed still-live (flagged for Stage 21). No confirmed in-scope live defect requires fixing in this plan.
Completed: 2026-08-08

Closure Audit Evidence:

- Reviewer / Agent: independent closure-audit subagent (session ses_022e2f9d8ffeKn5sh4RCotFV36, initial FAIL) + independent re-audit subagent (session ses_022ddbdccffe6G77js6JC3DLpe, PASS after fixes)
- Evidence:
  - Phase 1 Exit Criteria: PASS — 9 supported-construct rows; each `e2e-proved` positive_proof is a real in-process `env.execute()` test (TestDataStreamPipeline, TestKeyedStreamAggregation, TestAssignTimestampsAndWatermarks, TestEventTimeWindowE2E); process row honestly `component-only` (no execute() test). All 9 cited tests verified green via surefire (0 failures).
  - Phase 2 Exit Criteria: PASS — EVID-S6-010 (compilation chain) positive_proof re-pointed to TestDataStreamPipeline#testSourceMapFilterSink (calls execute()); EVID-S6-011 (lifecycle) proven by TestE2ESimplePipeline + live trace of operatorChain.open()/finish()/processElement in StreamTaskInvokable.java:345-532.
  - Phase 3 Exit Criteria: PASS — EVID-S6-012 (keyBy partition) in-process via TestKeyedStreamAggregation; EVID-S6-013 (forceNonParallel) `component-only`/unit (lock mechanism proven at graph-compilation level; no execute() source-to-sink test — honest classification). M7-2-P0-1 revalidated RESOLVED.
  - Phase 4 Exit Criteria: PASS — 3 unsupported-form rows (connect/union/side-output) all `non-goal`/`implementation_anchor: none`; equivalence criteria (topology/stable-identity/recovery-inputs) explicit in evidence file.
  - `node ai-dev/tools/check-nop-stream-audit-manifest.mjs evidence --strict` → exit 0, parsed 16 rows (non-vacuous — output `[PASS] evidence`, not "0 evidence rows yet").
  - Anti-Hollow check (re-audit): (a) all `e2e-proved` positive_proofs are real `ClassName#method` running `env.execute()` source-to-sink, (b) no `e2e-proved`+`manual-trace`/`none` combos, (c) `runtime_wiring=wired` rows traced through StreamExecutionEnvironment.execute():279-350, (d) no unsupported form silently passes.
  - Initial audit found 4 defects (1 CRITICAL: rejection-test-as-positive-proof for transform; 2 MODERATE: e2e-proved rows whose tests bypass execute(); 1 MINOR: non-existent test method). All 4 FIXED and confirmed by re-audit (ses_022ddbdccffe6G77js6JC3DLpe: "Re-audit PASS — all 4 defects fixed").

Follow-up:

- Coverage gaps (NOT confirmed live defects) for successor test-effectiveness remediation (roadmap item 17): (1) process(ProcessFunction) needs one env.execute() source-to-sink test; (2) forceNonParallel needs one env.execute() source-to-sink test exercising a locked vertex.
- Corpus findings M7-2-P2-5 (UnknownTypeInformation cast, still live) and M8-2-P2-18 (null operatorStateStore, recovery-scoped) flagged here; final disposition owned by Stages 19-22.
- Stale in-repo comment in TestEventTimeWindowE2E.java:43-46 (claims assignTimestampsAndWatermarks not handled by executor — outdated; it IS handled per StreamGraphGenerator.java:438) recorded as doc drift.
