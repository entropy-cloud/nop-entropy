# 13 WI6 union 多输入通路与内核约束修复

> Plan Status: completed
> Last Reviewed: 2026-10-02
> Source: `ai-dev/backlog/nop-stream-sql-roadmap.md`（WI6 行、§3.2 约束 1/2/4、R6/R7、Assumptions A1）
> Related: `ai-dev/plans/nop-stream-sql/11-wi8b-schemas-consumer.md`
> Owner: 仓库 owner（2026-10-02 执行指令委托）
>
> 审查修订记录：经独立子 agent 对抗性审查一轮修订（含想象性分析）——P1 平行边 matrix 键控裁定为 IdentityHashMap（不改 JobEdge 公共 equals，core+runtime 三处 Map 全列）；P2 EdgeConfig 一致性比较裁定为私有四字段值比较（EdgeConfig 无 equals，不扩公共面）；P3 HASH 边入 union 裁定为 validateEdgeDeclarations fail-fast（与 roadmap §3.1「union 之后 keyBy」一致）；P4 统一为 Flink 签名；P5 测试先例模块归属更正（runtime 非 core）；P6 孤立 union 用例预期码钉住；P7 remote 侧 matrix 纳入 P1 修复面；P8 Phase 1/2 补 owner-doc 裁定项。
> 执行期裁定（2026-10-02）：P2 语义修正——首轮实现把 declared-vs-undeclared 判为冲突，跑 runtime 全量时击穿既有 remote diamond 拓扑测试（TestRemotePlanTopicLegality：B 有 A→B 与 D→B 两条入边、仅前者声明配置，依赖首边继承）。修正为「undeclared 让位于 declared（文档化继承），仅 declared-vs-declared 四字段值不一致才 fail-fast」；约束 1 的「不静默」承诺由 declared 间冲突 fail-fast 承担，TestMultiEdgeGateConfigConsistency 用例同步改写。另录：本地 runner 同 JVM 仅首次 execute 交付（既有 reduce 管线复现，与 union 无关），E2E 拆为两个独立 init/destroy 测试类规避，遗留观察记 Non-Blocking Follow-ups。

## Purpose

打通 union 多输入通路：新增 `UnionTransformation` 与 `DataStream.union` API，`StreamGraphGenerator` 支持 union 分支，DSL builder 的 `<union>` 从 fail-fast 转为真实消费多上游。同步修掉两处内核缺陷（约束 1 首边 EdgeConfig 静默继承、约束 2 顶点对边去重塌缩），并把约束 4 的两输入上界显式落档。解锁 WI7/WI8d/WI13/WI17/WI21/WI22。

## Current Baseline

- `AdvancedTransforms.java:433-441`：`buildUnion` 直接抛 `ERR_STREAM_NOT_IMPLEMENTED`，文案自述「runtime only supports OneInputStreamOperator」。
- `OneInputTransformation.java:30,47`：单 `Transformation<IN> input` 字段；`Transformation.java:183` 的 `getInputs()` 抽象返回 `List<Transformation<?>>`。
- `StreamGraphGenerator.java:197-224`：dispatch 无 union 分支，未知 Transformation 类型抛 `ERR_STREAM_UNSUPPORTED`；`:410-413` `addUpstreamEdge` 只取 `getInputs().get(0)`。
- **约束 1**（`GraphExecutionPlan.java:562`）：`buildInputGate` 的 gate 级 `EdgeConfig` 只从 `inEdges.get(0)` 解析（`EdgeAssembly.resolveEdgeConfig`），多入边顶点的第 2+ 条边流控配置被静默丢弃。同构缺陷在 `RemoteGraphExecutionPlanBuilder.java:406`。
- **约束 2**（`JobGraphGenerator.java:582` 附近）：JobEdge 按 `sourceVertexId + "->" + targetVertexId` 去重（`createdEdges` Set），同一上游顶点到同一目标顶点的两条平行入边塌成一条。
- **约束 4**（`AbstractStreamOperator.java:428-444`）：`processWatermark(Watermark, int)` 内 `combinedWatermark == null` 时硬编码 `IndexedCombinedWatermarkStatus.forInputsCount(2)`；`processWatermark1/2` 是仅有的两个入口。运行时主路径不经过它——`InputGate` 已按 channel 做 min 合并（`InputGate.java:1181-1199`），`StreamTaskInvokable.java:1000-1001` 调单参 `headInput.processWatermark`；`processWatermark1/2` 仅被 wire 级测试直接调用。
- **约束 3 不在本 WI 范围**：数据元素丢弃 channelIndex（`InputGate.java:804-817` dispatchChannelElement 返回裸 element）——union 语义本身不需要输入溯源，两条输入合流后单算子无需区分来源；join 的二输入区分归 WI13（union 后 keyBy + process）。
- DSL 多入边已支持：`StreamModelDslBuilder.validateDag` 的 `upstreams.get(to).add(from)` 存 `Set<String>`，Kahn 拓扑按多入边算度（`:354-407`）。
- `stream.xdef:183`：`<union xdef:name="StreamUnionModel" xdef:ref="StreamTransformModel"/>` 已建模；`StreamUnionModel` 继承 `_StreamUnionModel`（transform 公共属性含 id/parallelism）。
- 序列化信封（`StreamElementCodec.java:60-95` + `ClassNameValidator.java:16-42`）：union 通路不引入新传输类型，数据元素仍是既有 `StreamRecord`，无白名单需求（A1 不触发）。
- **约束 2 修复的影响面（实测）**：`JobEdge.equals/hashCode` 基于「sourceVertex+targetVertex+partitionType」(`JobEdge.java:177-193`)；`edgePartitionMatrix` 为 `LinkedHashMap<JobEdge, ResultPartition[][]>`（core `GraphExecutionPlan.java:316/381`，runtime `RemoteGraphExecutionPlanBuilder.java:164-179/221`），全部访问点均为同实例 `get(edge)`；`edgeInputChannels`（runtime）同构。改为 IdentityHashMap 不影响任何既有单边拓扑（单边时两实例映射等价）。
- **EdgeConfig**（`execution/flow/EdgeConfig.java`）：4 字段（flowControlPolicy/queueCapacity/receiveWindow/packetSize）+ getter，无 equals/hashCode；`DefaultDeploymentPlanProvider`/`DeploymentPlanGenerator` 每边各 new 一个实例——多边一致性判定必须逐字段而非引用比较。
- 既有门禁：`check-nop-stream-invariants.mjs sync` 当前 exit 0；gate-inventory modules 映射只覆盖已登记类，新增类不在 catalog 中不触发（WI22 负责钉入新算子）。
- E2E 测试基建先例：`TestAdvancedPipelineE2E`（`.stream.xml → DslModelParser → StreamModelDslBuilder → env.execute → CollectingSinkFunction`，nop-stream-flow）；runtime 级先例 `TestE2EWindowOperatorWithCheckpoint` 在 **nop-stream-runtime**（非 core）；nop-stream-core 侧单测先例 `TestKeyedStreamAggregation`。
- 既有 union fail-fast 用例位置：`TestAdvancedTransforms.java:374-387`、`TestStreamModelDslBuilderFailFast.java:55-58`（孤立 `<union>` 用例：无边无源 → 新行为下走 union API 空输入校验，预期 `ERR_STREAM_INVALID_ARG`，码串钉住）。
- 测试资源目录：`nop-stream-flow/src/test/resources/_vfs/nop/stream/test/`（既有 test-reduce-pipeline.stream.xml + beans.xml）。

## Goals

- **core 新增**：`UnionTransformation<T>`（`List<Transformation<T>> inputs`，`getInputs()` 返回全部；outputType 取首输入或 Unknown）；`DataStream<T> union(DataStream<T>... streams)` API（Flink 同名签名，实现于 `DataStreamImpl`，校验非空数组、无 null 元素后构造 UnionTransformation 并注册 env，返回包它的新 DataStreamImpl）。
- **core 新增**：`StreamUnionOperator`（pass-through OneInputStreamOperator：record/watermark/barrier/status 全转发，无状态），union 顶点是真实执行顶点（方案裁定：真实顶点而非逻辑塌缩——下游 OneInputTransformation 单 input 字段无法承载多入边，真实顶点复用既有多 channel InputGate）。
- **StreamGraphGenerator**：`transformUnion` 分支——递归处理全部输入，建 union 节点，为每个输入建一条 StreamEdge；`registerStreams` 对重复 `sourceId->targetId` 键加序号后缀（self-union 不再互相覆盖）。
- **JobGraphGenerator 约束 2 修复**：按 StreamEdge 身份建 JobEdge（同顶点对多条平行边各建一条）；**平行边 matrix 键控裁定**——`GraphExecutionPlan.java:316/381` 与 `RemoteGraphExecutionPlanBuilder.java:178` 的 `Map<JobEdge, ResultPartition[][]>` 改用 `IdentityHashMap`（全部访问点均为同实例 put/get：JobEdge 实例在 JobGraphGenerator 创建后经 `jobGraph.getEdges()` 与 matrix 共享引用，改键控不改 JobEdge 公共 equals 契约，避免 public-contract 变更；runtime 侧 `edgeInputChannels` 同批处理）。
- **GraphExecutionPlan 约束 1 修复**：多入边顶点的 gate 配置不再取首边——所有入边解析出的 EdgeConfig 必须值一致，不一致 fail-fast（`ERR_STREAM_INVALID_ARG`，ARG_DETAIL 列出每边键与配置摘要）；**比较语义裁定**——`EdgeConfig` 无 equals（@DataBean 风格，仅 getter），一致性判定用私有四字段比较（flowControlPolicy + queueCapacity + receiveWindow + packetSize），不改 EdgeConfig 公共面；一致时任取其一（值相同，无静默）。`RemoteGraphExecutionPlanBuilder:406` 同步修（同构缺陷）。
- **约束 4 显式记录**：`AbstractStreamOperator` 的 `forInputsCount(2)` 上界在 javadoc 与 roadmap（FU-6 已登记）落档为「operator 级二输入水位合并上界；运行时主路径经 InputGate 按 channel 合并不受此限」，并给 `processWatermark(Watermark,int)` 补 index 越界 fail-fast（现依赖 `Guard.checkArgument`，已有）——本 WI 不参数化（R7 裁定：显式记录上界）。
- **builder 放行**：`AdvancedTransforms.buildUnion` 真实消费多上游（**按边声明顺序**合流——`upstreamIds` 是无序 Set，由 `StreamModelDslBuilder.buildTransforms` 把保序上游列表传入 AdvancedTransforms.build，或以 validateDag 的 edges 列表在 buildUnion 内排序，实现取改动最小者；每上游须 registry 中的 `DataStream` 实例且非 KeyedStream/WindowedStream 形态，否则 `ERR_STREAM_UPSTREAM_TYPE`；union 自身 `requireSingleInput` 豁免）。**HASH 边入 union 语义裁定**：`validateEdgeDeclarations` 对 `partition="HASH"` 且 `to` 为 `<union>` 元素的边 fail-fast（复用 `ERR_STREAM_EDGE_HASH_REDUNDANT` 或 `ERR_STREAM_INVALID_ARG`，ARG_DETAIL 指引「union 后 keyBy」）——与 roadmap §3.1「正确机制只有 union 之后 keyBy」一致；因此 `applyEdgePartition` 对 union 上游天然无 HASH 边可应用，保留逐上游调用以覆盖 registry 兜底路径。
- **端到端**：两源进单算子（union → map → sink）E2E 绿；self-union（同源两次入 union）拓扑绿且 sink 收到双份元素（约束 2 回归）。

## Non-Goals

- 不做 union 上的 keyBy/聚合语义变更（WI11/WI13 消费）；不做 DataStream API 之外的 union 连接器；不参数化 `AbstractStreamOperator` 水位输入数（FU-6）；不做 sideOutput（独立 runtime-API-gap）；不动 `InputGate` 数据元素的 channelIndex 保留（约束 3，join 归 WI13）；不动 CEP 非键入口的 parallelism 锁语义。

## Scope

### In Scope

- `nop-stream-core`：UnionTransformation、StreamUnionOperator（或等价 pass-through operator）、DataStream/DataStreamImpl union API、StreamGraphGenerator union 分支 + registerStreams 平行边键序号、JobGraphGenerator 约束 2 修复、GraphExecutionPlan 约束 1 修复、RemoteGraphExecutionPlanBuilder 同步修、AbstractStreamOperator 约束 4 javadoc 落档
- `nop-stream-flow`：AdvancedTransforms.buildUnion 真实实现、TestStreamModelDslBuilderFailFast 中 union fail-fast 用例改写、新增 builder 级 union 用例
- 测试：`nop-stream-core` 单测（UnionTransformation 图生成、约束 1/2 回归、self-union）+ `nop-stream-flow` E2E（两源进单算子）
- roadmap WI6 状态行与括注、`ai-dev/design/nop-stream/graph-model-design.md` 补 union 顶点模型注记（若该文档存在对应章节）；当日日志

### Out Of Scope

- WI7 的三个具名回归测试类（barrier 对齐/水位 min 合并/exactly-once checkpoint——归 WI7）；WI8d join 面；`InputGate` per-channel EdgeConfig（若一致性校验方案被 audit 推翻才升级）；`<sideOutput>` 解禁。

## Execution Plan

### Phase 1 - core：UnionTransformation 与 union API

Status: completed
Targets: `nop-stream/nop-stream-core`

- Item Types: `Feature`

- [x] 新增 `UnionTransformation<T>`（inputs 列表 + getInputs 全返回 + outputType 首输入）
- [x] `DataStream` 接口加 `union(DataStream<T>... streams)`；`DataStreamImpl` 实现（校验非空数组、无 null 元素；构造 UnionTransformation 并注册 env）
- [x] 新增 pass-through `StreamUnionOperator`（OneInputStreamOperator，转发 record/watermark/barrier/watermarkStatus；processBarrier 继承 AbstractStreamOperator 快照协议）
- [x] `StreamGraphGenerator.transformUnion` 分支 + `registerStreams` 重复键序号后缀
- [x] 新增单测 `TestUnionTransformation`：union API 构造（含空数组/null 元素 fail-fast）、StreamGraph 生成（union 节点 + N 条入边）、self-union 键序号

Exit Criteria:

- [x] `TestUnionTransformation` 全绿（5/5）：union API 两类非法输入（空数组/null 元素）fail-fast 且码串钉住；StreamGraph 含 union 节点与 N 条 StreamEdge
- [x] self-union 的 registerStreams 键不互相覆盖（`#2` 序号后缀，两条 stream 条目可区分）
- [x] **无静默跳过**：StreamUnionOperator 转发全实现（processElement/processWatermark/processWatermarkStatus），processBarrier 继承带快照协议的默认实现并注释说明
- [x] Owner-doc 裁定：本 Phase 仅新增 API 未改既有行为——`No owner-doc update required`（union 模型注记随 Phase 3 落 graph-model-design.md）
- [x] `./mvnw test -pl nop-stream/nop-stream-core -am` 绿（1663 测试）
- [x] ai-dev/logs/ 当日条目已更新

### Phase 2 - core：约束 1 与约束 2 修复

Status: completed
Targets: `GraphExecutionPlan`、`RemoteGraphExecutionPlanBuilder`、`JobGraphGenerator`

- Item Types: `Fix`

- [x] 约束 2：JobGraphGenerator 对 union 目标保留每声明边一条 JobEdge（**执行期精化**：非 union 目标维持顶点对去重——链内扇入若盲删去重会元素双写，保留去重是该场景的正确语义）；`edgePartitionMatrix`（core buildPartitionMatrix）与 runtime `partitionMatrix`/`edgeInputChannels` 改 `IdentityHashMap` 键控（同实例 put/get，不改 JobEdge 公共 equals）；remote topic 对平行边 `#序号` 消歧；deploymentPlan EdgeConfig 查找对平行边取同键结果
- [x] 约束 1：GraphExecutionPlan.buildInputGate 多入边逐边 resolveEdgeConfig，**四字段值比较**（flowControlPolicy/queueCapacity/receiveWindow/packetSize，EdgeConfig 无 equals）；declared-vs-declared 不一致抛 `ERR_STREAM_INVALID_ARG`（ARG_DETAIL 列出每边键与配置摘要）；declared-vs-undeclared 让位于 declared（执行期裁定，见审查修订记录）；RemoteGraphExecutionPlanBuilder 同步修
- [x] 约束 4：AbstractStreamOperator javadoc 显式记录二输入上界与运行时主路径不经此路的事实（代码零行为变更）
- [x] 新增回归 `TestJobGraphParallelEdges`（约束 2：self-union 两条 JobEdge 均存活，各自 matrix 的 ResultPartition 实例独立 + 单边零退化）与 `TestMultiEdgeGateConfigConsistency`（约束 1：同值异实例建 gate、双 undeclared 建 gate、declared 冲突 fail-fast、declared+undeclared 建 gate）

Exit Criteria:

- [x] self-union 拓扑的 JobGraph 含两条源→union JobEdge，且 matrix 两条目各自的 ResultPartition 实例相互独立（约束 2 修复生效，回归断言钉住）
- [x] 多入边顶点冲突 EdgeConfig 构建 DeploymentPlan 执行时 fail-fast，码串钉住；值一致（不同实例）的多边顶点正常执行（比较语义为逐字段，非引用）
- [x] RemoteGraphExecutionPlanBuilder 行为与本地 plan 一致（同构修复；runtime 全量 1185 绿含既有 TestRemotePlanTopicLegality diamond 拓扑回归）
- [x] Owner-doc 裁定：约束修复为内核缺陷修正——`ai-dev/design/nop-stream/graph-model-design.md` §3/§4.5 union 注记随 Phase 3 落
- [x] `./mvnw test -pl nop-stream/nop-stream-core -am` 绿（1663 测试，既有图生成/checkpoint 回归零退化）
- [x] ai-dev/logs/ 当日条目已更新

### Phase 3 - builder 放行与端到端

Status: completed
Targets: `nop-stream/nop-stream-flow`

- Item Types: `Feature`

- [x] `AdvancedTransforms.buildUnion` 真实实现：按边声明顺序收集上游 DataStream（重复边=重复合流，符合 no-silent-drop；无序 Set 的保序经 model.getEdges() 过滤）、逐上游校验 DataStream 且非 KeyedStream/WindowedStream 形态（拒绝抛 `ERR_STREAM_UPSTREAM_TYPE`）、逐上游 applyEdgePartition、调 union API、applyDeclaredParallelism
- [x] `validateEdgeDeclarations` 新增：`partition="HASH"` 且 `to` 为 `<union>` 元素的边 fail-fast `ERR_STREAM_EDGE_HASH_REDUNDANT`（ARG_DETAIL 指引「union 后 keyBy」）
- [x] 改写既有 union fail-fast 用例（TestAdvancedTransforms union 分支重写为 4 用例、TestStreamModelDslBuilderFailFast 孤儿 union → `ERR_STREAM_INVALID_ARG`）
- [x] 新增 `TestUnionPipelineE2E`（两源 → union → map → sink 全链绿，7 元素 multiset 断言）与 `TestSelfUnionPipelineE2E`（同源两次边入 union，sink 收到双份 10 元素）——**执行期发现**：本地 runner 同 JVM 仅首次 execute 交付（既有 reduce 管线复现，与 union 无关），拆两个独立 init/destroy 测试类规避
- [x] graph-model-design.md 补 union 顶点模型注记（§3 五类型 + §4.5 平行边裁定：真实执行顶点 + IdentityHashMap matrix 键控 + topic 消歧 + gate 配置一致性语义 + HASH 边禁入）；roadmap WI6 行同步在 Phase 4 执行

Exit Criteria:

- [x] **端到端验证**：两源进单算子（union→map→sink）从 `.stream.xml` 到 sink 输出完整跑通，输出断言精确（`[10,10,20,20,20,20,40]` multiset）
- [x] self-union 拓扑 E2E：sink 收到两份元素流（约束 2 在 E2E 层的回归证据）
- [x] 合法 union 经 DSL build + execute 绿；上游类型非法与 HASH 边入 union 两类 fail-fast 码串钉住
- [x] **接线验证**：union DSL 声明经 buildUnion 真实调用 DataStream.union 并出现在 env.execute 的执行图（E2E 输出断言为证，规则 #23）
- [x] `./mvnw test -pl nop-stream/nop-stream-flow -am` 绿（136 测试）
- [x] roadmap WI6 状态同步后 `parseRoadmapMarkdown` 复核 31 工作项 + 7 里程碑（Phase 4 已翻转：31 项 + 7 里程碑、13 done、无静默丢弃）
- [x] ai-dev/logs/ 当日条目已更新

### Phase 4 - 收口

Status: completed
Targets: plan 与 roadmap

- Item Types: `Proof`

- [x] 独立子 agent closure audit（不同 task_id）：union 链路逐环 wiring 实证、约束 1/2 回归实跑、约束 4 javadoc、门禁零退化——判定 PASS（6 项 Minor 不阻塞）；证据落 ai-dev/audits/nop-stream-sql/wi6-closure-audit.md
- [x] audit Minor 修复：M-1 remote 让位逻辑补齐（gateConfig null 提升后边 declared 值）、M-2 测试类 javadoc 与裁定语义对齐；修复后 TestRemotePlanTopicLegality 2/2 + core 定向 11/11 复验绿
- [x] audit 通过后 roadmap WI6 `todo` → `done`（括注单层无嵌套）；`parseRoadmapMarkdown` 复核 31 工作项 + 7 里程碑、13 done、无静默丢弃
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --severity high` core/runtime/flow 三模块退出码均 0
- [x] Plan Status → `completed`；check-plan-checklist --strict 退出码 0；check-doc-links --strict 退出码 0

Exit Criteria:

- [x] 独立 audit 证据落档两处（audit 文件 + plan Closure 段）
- [x] roadmap WI6 = done + 解析器 31 + 7 复核通过
- [x] check-plan-checklist --strict 退出码 0；check-doc-links --strict 退出码 0；scan-hollow 高危零发现

## Closure Gates

- [x] union 通路端到端连通：`DataStream.union` → UnionTransformation → StreamGraph union 节点 → JobGraph 平行边 → InputGate 多 channel → StreamUnionOperator → sink（TestUnionPipelineE2E/TestSelfUnionPipelineE2E 为证，规则 #22/#23；audit 逐环代码链实证）
- [x] 约束 1 修复有回归测试（TestMultiEdgeGateConfigConsistency 4 用例：同值异实例建 gate/双 undeclared 建 gate/declared 冲突 fail-fast/declared+undeclared 建 gate）
- [x] 约束 2 修复有回归测试（TestJobGraphParallelEdges：self-union 平行边存活 + partition 实例独立 + 单边零退化）；FU-5 已注记残余范围
- [x] 约束 4 上界显式记录（AbstractStreamOperator javadoc + roadmap FU-6 交叉引用）
- [x] DSL `<union>` 从 fail-fast 转为真实消费（builder 级 TestAdvancedTransforms 4 用例 + E2E 双层证据）
- [x] 既有测试零退化（core 1663 / flow 136 / runtime 1185 全绿）
- [x] 无静默跳过、无空壳实现（scan-hollow 三模块高危零发现）
- [x] 独立子 agent closure-audit 已完成并记录证据（不同 task_id，判定 PASS）
- [x] `./mvnw test -pl nop-stream/nop-stream-core -am` 绿
- [x] `./mvnw test -pl nop-stream/nop-stream-flow -am` 绿
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-stream-sql/13-wi6-union-multi-input.md --strict` 退出码 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Deferred But Adjudicated

### InputGate per-channel EdgeConfig

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 约束 1 的一致性校验方案消除了静默继承（多边配置不同即 fail-fast，用户有明确出路：统一各边配置）；per-channel 配置是能力增强而非缺陷残留
- Successor Required: `no`
- Successor Path: 如后续需要异构边流控，随 RemoteGraphExecutionPlanBuilder 的流控增强一并处理

## Non-Blocking Follow-ups

- FU-6（既有）：AbstractStreamOperator 水位合并固定两输入的参数化——本 WI 已显式记录上界，参数化仍留 FU-6。
- FU-5（既有）：self-join 多条平行边的命名与区分——约束 2 修复后平行边可存活，命名规范仍留 FU-5 评估。

## Closure

Status Note: union 多输入通路与内核约束修复落地——UnionTransformation/StreamUnionOperator/union API/图生成 union 分支全链连通（E2E 两源进单算子 + self-union 双份交付），约束 2（平行边去重塌缩）与约束 1（gate 首边配置静默继承）修复各有具名回归，约束 4 上界 javadoc 落档，builder `<union>` 放行且 HASH 边禁入。执行期两项裁定（declared-vs-undeclared 让位语义、双 E2E 测试类规避同 JVM 首跑限制）已在审查修订记录落档。独立 closure audit 判定 PASS（6 项 Minor，其中 M-1/M-2 已修复复验）。
Completed: 2026-10-02

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，与起草审查、执行均不同 task）
- Audit Session: 证据落档 ai-dev/audits/nop-stream-sql/wi6-closure-audit.md（判定 PASS）
- Evidence:
  - union 链路逐环接线：DataStreamImpl.union(:158-178) → env.addTransformation → transformUnion(:317-336) → JobGraphGenerator 平行边(:590-591) → IdentityHashMap matrix(:385/:186-187) → buildInputGate 多 channel(:554-593) → StreamUnionOperator 全转发(:46-58)
  - 定向测试：core 11 + flow 29 全绿；E2E 断言为精确 multiset
  - 全量回归：core 1663 / flow 136 / runtime 1185 BUILD SUCCESS
  - 工具门禁：invariants sync exit 0、scan-hollow core/runtime/flow 高危 0、doc-links --strict 0 errors、check-plan-checklist --strict exit 0
  - Minor 修复复验：TestRemotePlanTopicLegality 2/2 + core 定向 11/11
  - 生成物纪律：git 无 _gen/_ 前缀 diff
- `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-stream-sql/13-wi6-union-multi-input.md --strict` 退出码 0

Follow-up:

- no remaining plan-owned work；观察项——本地 runner 同 JVM 仅首次 execute 交付（既有平台限制，复现于 union 无关的 reduce 管线），已在此记录供 WI7/WI18 的 E2E 设计规避
