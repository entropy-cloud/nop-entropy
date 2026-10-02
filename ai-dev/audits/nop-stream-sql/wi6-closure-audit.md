# WI6 Closure Audit——13-wi6-union-multi-input.md

- Audit 日期：2026-10-02
- Auditor：独立子 agent（fresh session，与实现者不同 task）
- 裁定：**PASS**（可翻转 roadmap WI6 为 done）。全部 Exit Criteria 与 Closure Gates 经 live 代码与实跑测试核验通过；发现 6 项 Minor（均为文档/覆盖边角，非行为缺陷，不阻塞收口），见 §3。

## 1. Exit Criteria 逐条核验

### Phase 1（core：UnionTransformation 与 union API）— Status completed

| 项 | 裁定 | live 证据 |
|---|---|---|
| UnionTransformation 新增 | PASS | `nop-stream-core/.../transformation/UnionTransformation.java:30-63`（`List<Transformation<T>> inputs`、`getInputs()` 全返回 :60-62、outputType 取首输入 :46） |
| `DataStream.union(DataStream<T>...)` | PASS | 接口 `DataStream.java:157`；实现 `DataStreamImpl.java:158-178`（空数组 :159-163、null 元素 :167-171 各 fail-fast `ERR_STREAM_INVALID_ARG`；构造后 `environment.addTransformation` :176 并返回包裹它的 DataStreamImpl :177） |
| StreamUnionOperator pass-through | PASS | `StreamUnionOperator.java:46-58` processElement/processWatermark/processWatermarkStatus 全转发；:60-61 注明 processBarrier 继承 AbstractStreamOperator 快照协议（实测 `AbstractStreamOperator.java:407-432` 含 snapshotState + 失败 abort 路由，非空壳） |
| transformUnion + registerStreams 序号 | PASS | `StreamGraphGenerator.java:230-231` dispatch 分支；`:317-336` transformUnion（逐输入递归 transform :319-321、union 节点 + `addUnionID` :327-328、每输入一条 StreamEdge :332-335）；`registerStreams` :169-190 重复键仅命中时加 `#序号` 后缀（既有拓扑 id 形态不变） |
| TestUnionTransformation 5 用例 | PASS | 实跑 `Tests run: 5, Failures: 0`（surefire `io.nop.stream.core.transformation.TestUnionTransformation.txt`）；API 两类非法输入码断言 :62/:70、图生成 1 union 节点 + 2 边 :85-92、self-union `#2` 后缀 :113-114 |

### Phase 2（core：约束 1 与约束 2 修复）— Status completed

| 项 | 裁定 | live 证据 |
|---|---|---|
| 约束 2：union 目标保留平行边 | PASS | `JobGraphGenerator.java:590-591` `boolean unionTarget = streamGraph.isUnionNode(targetNodeId); if (unionTarget \|\| createdEdges.add(edgeKey))`——union 目标不进去重 Set，非 union 目标保留顶点对去重（链内扇入防双写，注释 :583-589 说明两分支语义） |
| IdentityHashMap 键控（core） | PASS | `GraphExecutionPlan.java:385` `buildPartitionMatrix` 用 `IdentityHashMap`；访问点 `buildInputGate:555` 与 fan-out 构建均取自同一 `jobGraph.getEdges()` 实例（同实例 put/get） |
| IdentityHashMap 键控（runtime） | PASS | `RemoteGraphExecutionPlanBuilder.java:186-187` `partitionMatrix` 与 `edgeInputChannels` 双 Map 均 `IdentityHashMap` |
| remote topic 平行边 `#序号` 消歧 | PASS | `RemoteGraphExecutionPlanBuilder.java:193-207`（仅重复对出现时加后缀，既有拓扑 topic 名不变） |
| 约束 1：buildInputGate 值一致性 | PASS | `GraphExecutionPlan.java:566-593`：逐边 `resolveEdgeConfig` + `sameGateConfig` 四字段值比较（:601-609，flowControlPolicy/queueCapacity/receiveWindow/packetSize）；declared 冲突 fail-fast `ERR_STREAM_INVALID_ARG`（:585-591，消息列每边键与配置摘要）；declared-vs-undeclared 让位（:581-583 `gateConfig == null && other != null → gateConfig = other`）——与执行期裁定一致 |
| 约束 1：remote 同步修 | PASS（见 Minor M-1） | `RemoteGraphExecutionPlanBuilder.java:427-441` 同构 declared-vs-declared 冲突 fail-fast + `sameGateConfig` :454-462；diamond 拓扑（首边 declared + 次边 undeclared）由 TestRemotePlanTopicLegality 2/2 回归护住 |
| 约束 4 javadoc | PASS | `AbstractStreamOperator.java:39-51`：显式记录 `forInputsCount(2)` 上界、FU-6 交叉引用、运行时主路径经 InputGate 按 channel 合并不受此限；代码零行为变更（`:438-439` 原样） |
| TestJobGraphParallelEdges | PASS | 实跑 2/2：平行边两 writer（:79-80）/union 两 channel（:88）/partition 实例独立 `assertNotSame`（:92-95）+ 单边零退化（:99-118） |
| TestMultiEdgeGateConfigConsistency | PASS | 实跑 4/4：同值异实例建 gate、双 undeclared 建 gate、declared 冲突 fail-fast（码串 + "conflicting flow-control configs" 消息断言 :121-125）、declared+undeclared 建 gate |

### Phase 3（builder 放行与端到端）— Status completed

| 项 | 裁定 | live 证据 |
|---|---|---|
| buildUnion 真实实现 | PASS | `AdvancedTransforms.java:444-487`：空上游 fail-fast（:446-452）；按 `model.getEdges()` 声明序收集（:458-470，重复边=重复合流注释 :453-457）；逐上游 DataStream 且非 Keyed/Windowed 校验 `ERR_STREAM_UPSTREAM_TYPE`（:475-481）；逐上游 `applyEdgePartition`（:482-483）；`merged.union(input)` 真实调 union API（:484）；`applyDeclaredParallelism`（:486）。dispatch 接线 `:114-115` |
| validateEdgeDeclarations HASH 禁入 union | PASS | `StreamModelDslBuilder.java:442-449`：HASH 且 to 为 StreamUnionModel → `ERR_STREAM_EDGE_HASH_REDUNDANT` + ARG_DETAIL 指引「union 后 keyBy」 |
| TestAdvancedTransforms 4 union 用例 | PASS | 实跑类 20/20（含 :375 registry 内 UnionTransformation+2 输入、:402 无上游、:414 KeyedStream 拒绝、:430 HASH 边拒绝） |
| FailFast 孤儿 union 改写 | PASS | `TestStreamModelDslBuilderFailFast.java:56-67` 钉 `nop.err.stream.invalid-arg`；实跑 7/7 |
| TestUnionPipelineE2E 判别性 | PASS | `TestUnionPipelineE2E.java:84-89`：排序 multiset 精确断言 `[10,10,20,20,20,20,40]` + size 7（src1 5 元素 + src2 2 元素 ×10），非「不抛异常」；实跑 1/1 |
| TestSelfUnionPipelineE2E 判别性 | PASS | `:80-81` 精确断言双份 10 元素 `[1,1,1,1,2,2,2,2,2,2]`；实跑 1/1 |
| 接线验证（规则 #23） | PASS | E2E 从 `.stream.xml`（`test-union-pipeline.stream.xml` 声明 `<union id="merge">` + 两条入边）经 DslModelParser → StreamModelDslBuilder.build → env.execute → sink 输出断言，全链真实执行 |
| flow 全量 136 绿 | PASS | 实跑 `Tests run: 136, Failures: 0` |
| roadmap WI6 翻转 | 未做（符合预期） | plan 明示 Phase 4 执行；roadmap `:241` 仍 `todo`，非缺陷 |

## 2. Anti-Hollow 接线链逐环核验（实读 + 实跑）

`DataStreamImpl.union`（:158-178）→ `environment.addTransformation`（:176）→ `StreamGraphGenerator.transform`（:230-231 dispatch）→ `transformUnion`（:317-336：union 节点 StreamUnionOperator 工厂 + N 条 StreamEdge + addUnionID）→ `JobGraphGenerator.createJobEdges`（:590-591 union 目标每声明边一条 JobEdge）→ `GraphExecutionPlan.buildPartitionMatrix`（:385 IdentityHashMap 每边独立 matrix）→ `buildInputGate`（:554-561 每边每 source subtask 一 channel；:566-593 gate 配置一致性）→ `StreamTaskInvokable`（`execution/task/StreamTaskInvokable.java:937` `inputGate.read()` → :970 dispatch 至链头）→ 链头 `StreamUnionOperator.processElement` 转发。union 节点为链边界由 `canChain` 规则 6 保证（`JobGraphGenerator.java:372-376` 多入边不可链入）。动态证明：TestUnionPipelineE2E/TestSelfUnionPipelineE2E 经 env.execute 全链交付精确 multiset。

无静默跳过：StreamUnionOperator 三转发方法全实现、processBarrier 继承带快照协议的默认实现并注释；buildUnion/transformUnion 无空方法体、无吞异常。scan-hollow core high 0 发现（实跑 exit 0）。

## 3. 发现的问题（均 Minor，不阻塞）

| # | 级别 | 位置与后果 |
|---|---|---|
| M-1 | Minor | `RemoteGraphExecutionPlanBuilder.buildRemoteInputGate`（:431-441）缺 core 侧的「首边 undeclared 让位后边 declared」更新（core 有 `GraphExecutionPlan.java:581-583`，remote 无）：当首条入边未声明、后边声明配置时，本地 gate 取 declared 值而 remote gate 静默保持默认（=WI6 前「取首边」行为残留）。仅该子情形存在本地/远程不一致；remote declared 冲突 fail-fast 主修复面完整。建议翻转 WI6 时登记 follow-up 或顺手对齐 |
| M-2 | Minor | `TestMultiEdgeGateConfigConsistency.java:36-40` 类 javadoc 仍写首轮语义「declared-vs-undeclared … is a conflict and fails the plan build」，与本类第 4 个用例及 `GraphExecutionPlan.java:566-571` 的让位裁定矛盾——执行期裁定后类注释未同步 |
| M-3 | Minor | remote 侧 declared 冲突 fail-fast 分支（`RemoteGraphExecutionPlanBuilder.java:434-440`）无直接回归测试：runtime 测试仅覆盖 diamond 继承（TestRemotePlanTopicLegality 2/2），镜像逻辑由 core 侧 TestMultiEdgeGateConfigConsistency 护住，remote 分支本身未被任何用例触发 |
| M-4 | Minor | doc-links 对 plan 13 自身 L29 的 `execution/flow/EdgeConfig.java` 报 1 条 BROKEN_LINK warning（0 error，strict exit 0）——反引号代码路径被当作相对链接解析 |
| M-5 | Minor（观察） | `graph-model-design.md:58`「五种类型」表述少于实际 dispatch 集（代码另有 SourceApiTransformation、TimestampsAndWatermarksTransformation 分支）——WI6 之前既有的简化表述，WI6 注记本身（UnionTransformation 行）与代码一致 |
| M-6 | Minor（观察） | roadmap `:293` FU-5 行前提「JobGraphGenerator 边去重下」因约束 2 修复部分过时（union 目标不再去重）；plan 的 Non-Blocking Follow-ups 已记录「平行边可存活、命名规范留 FU-5」。建议翻转 WI6 时顺手给 FU-5 括注 |

## 4. 实跑记录（本审计独立执行，非转抄）

- 定向 core：`TestUnionTransformation 5/5 + TestJobGraphParallelEdges 2/2 + TestMultiEdgeGateConfigConsistency 4/4`，exit 0
- 定向 flow：`TestUnionPipelineE2E 1/1 + TestSelfUnionPipelineE2E 1/1 + TestAdvancedTransforms 20/20 + TestStreamModelDslBuilderFailFast 7/7`（合计 29），exit 0
- 全量回归：core `1663 tests, 0 failures`（1 skip = TestEventTimeWindowE2E，既有）；flow `136 tests, 0 failures`；runtime `1185 tests, 0 failures`（10 skip = multijvm/Kafka/Pulsar 环境依赖，既有）。BUILD SUCCESS ×3
- 既有门禁零退化：TestPerTransformParallelismWiring 3/3、TestTwoPhaseCommitSinkFunction 12/12、TestE2ETwoPhaseCommitSink 3/3、TestE2EJdbcTwoPhaseCommitSink 4/4、TestTwoPhaseCommitSinkParallelismChangeRestoreE2E 4/4、TestRemotePlanTopicLegality 2/2 全绿
- 工具门禁：`check-nop-stream-invariants.mjs sync` exit 0（sync: OK）；`scan-hollow-implementations.mjs --module nop-stream/nop-stream-core --severity high` exit 0（0 findings）；`check-doc-links.mjs --strict` exit 0（0 errors，4 warnings：3 条 nop-bytecode 既有 + 1 条 M-4）
- `check-plan-checklist.mjs 13-wi6-union-multi-input.md --strict` exit 0（active plan 未勾项仅为 Phase 4/收口动作，warnings only）

## 5. Closure Gates 核验

- [x] union 通路端到端连通 —— §2 逐环代码 + TestUnionPipelineE2E/TestSelfUnionPipelineE2E 精确 multiset
- [x] 约束 1 回归双断言 —— TestMultiEdgeGateConfigConsistency 4/4（一致性通过 + 冲突 fail-fast）
- [x] 约束 2 回归 —— TestJobGraphParallelEdges 2/2 + TestSelfUnionPipelineE2E；FU-5 已在 plan Non-Blocking Follow-ups 更新（roadmap 括注建议随翻转补，见 M-6）
- [x] 约束 4 显式记录 —— AbstractStreamOperator.java:39-51 + roadmap :294 FU-6 行在档
- [x] DSL `<union>` 真实消费 —— buildUnion live 代码 + builder 级 4 用例 + E2E 双层
- [x] 既有测试零退化 —— core 1663 / flow 136 / runtime 1185 全绿
- [x] 无静默跳过、无空壳 —— §2 + scan-hollow 高危 0
- [x] 独立 closure-audit 完成并记录 —— 本文件即证据（不同 task_id）
- [x] core / flow 全量绿 —— 实跑 BUILD SUCCESS
- [x] check-plan-checklist --strict exit 0；check-doc-links --strict exit 0 —— 实跑

Phase 4 余下动作（audit 通过后由执行者完成，不属于本次审计缺陷）：roadmap WI6 `todo` → `done`（单层无圆括号括注）→ `parseRoadmapMarkdown` 复核 31+7 → plan Phase 4 勾选、Plan Status → completed、Closure 段落填写。

## 6. 最终判定

**PASS** —— 机制、测试、门禁、文档四面均达标，未发现 Blocker/Major。6 项 Minor 中 M-1（remote 让位语义缺角）建议作为翻转时的 follow-up 登记，其余为文档同步级。

- Reviewer / Agent: 独立子 agent（closure audit，2026-10-02）
- Evidence: §1 逐条 Exit Criterion、§2 接线链、§4 实跑记录、§5 Closure Gates
