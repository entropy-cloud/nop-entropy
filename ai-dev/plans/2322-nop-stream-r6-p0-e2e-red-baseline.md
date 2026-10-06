# 2322 nop-stream R6 P0：E2E 红灯基线恢复（FLOW-5 守卫语义修正 + KR-3 合法迁移入口）

> Plan Status: active
> Last Reviewed: 2026-10-06
> Source: ai-dev/audits/2026-10/2026-10-06-0522-deep-audit-nop-stream-quality-r6/summary.md（P0-E2E-RED 族 + FLOW-03 + TE-03）
> Related: ai-dev/plans/2313-nop-stream-r5-routing-ownership-consistency.md（FLOW-5/KR-3 守卫原批次）、2316-nop-stream-r5-failfast-guards.md

## Adjudication（与 R6 审计原始修复方向的分歧裁定）

R6 审计 summary 对 P0 的原始建议是"fixture 按守卫语义改写（守卫行为正确）"。本计划经独立子 agent 对 live code 逐条核实后推翻该定性，理由（三重证据，全部可在仓库验证）：

1. `StreamModelDslBuilder.applyEdgePartition` 的 javadoc 明文设计是 "the implicit partition vertex takes the target's value"（分区顶点取目标并行度）——守卫行为与自家设计文档矛盾。
2. 守卫错误文案建议 "or a HASH edge"，但抛出点正在 HASH 分支内（R6 FLOW-03）——守卫与其自身建议矛盾。
3. owner doc `docs-for-ai/03-modules/nop-stream-user-guide.md:126` 已写着 "HASH 边目标的声明值自动同步到隐式 partition 顶点"（resize 语义）——owner doc 与 live 守卫互相矛盾，守卫是漂移方。

结论：缺陷本体是 R5 守卫语义偏离设计，而非 fixture 违规；fixture 不改声明。Phase 3 收口时须同步改写 R6 summary 的 P0 定性为本裁定。KR-3 面同理：守卫文案承诺的合法路径 "configure the state backend before keyBy" 在 DSL 入口不可达，修复是补齐该入口（显式 API），不是让守卫"识别迁移产物"（那会引入隐式容忍）。

## Purpose

恢复 `./mvnw test`（11 个 nop-stream 模块）全绿基线：修复 R5 守卫批次落地时引入的两个守卫×消费面集成缺口——(a) FLOW-5 守卫在 HASH 边分支的过严拒绝（偏离自身 javadoc 设计与 owner doc）；(b) KR-3 守卫承诺的合法迁移路径（"configure the state backend before keyBy"）在 DSL 入口不可达。守卫对意外漂移的 fail-fast 防护不得削弱。

## Current Baseline

- 审计基线 HEAD = 3b2493aef5；计划审查时 HEAD = 787ed59cc8（其间 4 个 commit 均为 nop-orm docs，`git diff -- nop-stream` 为空，代码无漂移）。主 agent 实测：`./mvnw install -DskipTests -pl <11 模块> -am` 成功；`./mvnw test -fn -pl <11 模块>` 下 nop-stream-fraud-example 4 个 E2E Error，其余 10 模块全绿。
- 红灯 1–3（TestPerTransformParallelismE2E / TestParallel2PcFileE2E / TestParallel2PcJdbcE2E）：死于 `nop.err.stream.edge-partition-unsupported`。fixture（`nop-stream/nop-stream-fraud-example/src/test/resources/_vfs/nop/stream/test/fraud-per-transform-parallelism.stream.xml` 等）声明 HASH 边 + keyExpr + keyed 阶段并行度 2/4 ≠ 上游 1；`StreamModelDslBuilder.applyEdgePartition`（nop-stream-flow，:1123-1132，HASH 分支内）比较 target 与 keyed transformation 并行度不等即抛。
- 执行面安全证据（独立审查已逐环节核实）：`keyed.setParallelism(target)` 经 `KeyedStreamImpl.setParallelism`→`Transformation.setParallelism` 真实生效；`StreamGraphGenerator.resolveParallelism`→`JobGraphGenerator.createJobVertex`（:451 取链首并行度）使 KeyBy 虚拟节点与下游成链后顶点并行度 = target；本地矩阵 `GraphExecutionPlan.buildPartitionMatrix`（:379-408）为全源×全目标 `new ResultPartition[srcP][tgtP]`（与 policy 无关），`EdgeAssembly.createWriterForEdge` 收集整行 → `HashPartitionRouter` 按目标全分区散列。跨并行度 HASH 散列能力已存在，守卫属过严。
- 红灯 4（TestS2OfflineReshardE2E）：死于 `nop.err.stream.invalid-arg` "keyBy maxParallelism drift: graph-frozen 128 != configured backend 256 (R5 KR-3)"。run2 经 `ScenarioTestSupport.buildEnv`：`StreamModelDslBuilder.of(model, resolver).build()`（builder :173-191 内部 ：175 创建 env，:186 `applyCheckpointConfig` 从不设 backend 实例，:189 `buildTransforms` 在 keyBy 处经 `DataStreamImpl.resolveKeyGroupMaxParallelism()` 冻结当时 M=默认 128），后置 `env.getCheckpointConfig().setStateBackend(new RocksDBStateBackend(..., 256))` → `validateKeyedRoutingParity` 拒绝。run1 M=128==默认不触发。`getCheckpointConfig()`/`setStateBackend` 均 public，在 ：175 之后 ：189 之前注入即可达——Phase 2 无需改 nop-stream-core 任何文件。
- 既有钉子（翻转/保留对象，审查已点名）：
  - `nop-stream-flow` `TestKeyedStageParallelismGuard.keyedStageParallelismMismatchFailsAtBuild`（钉 applyEdgePartition HASH 分支——将翻转；注意其 fixture `sinkFn` 注册的是 `MapFunction`（:51），翻转后 build 会走到 `resolveBean(..., SinkFunction.class)` 抛 `ERR_STREAM_BEAN_TYPE_MISMATCH`，须同时把 sinkFn 换成真实 SinkFunction）。
  - `nop-stream-flow` `TestPerTransformParallelismWiring.keyedStageMismatchFailsFast`（:227-269，钉 applyDeclaredParallelism KeyedStream 分支——按裁定保留拒绝，仅核对文案）。
  - `nop-stream-core` `TestKeyedRoutingParityGuard.testBackendDriftAfterKeyByFailsAtExecute`（KR-3 漂移拒绝钉子——必须保持通过；该测试在 nop-stream-core，两 Phase 均不改 core）。
  - 不受影响面（审查核实）：TestStreamModelEdgeContract（只钉 REBALANCE/BROADCAST）、`windowNodeParallelismFailsFast`（WI10 另一守卫，仍 fail-fast）、TestEquiJoinParallelismInvariant（join 内部 keyBy 无声明并行度）、TestMaxParallelismReshardMigrationE2E（纯低层 API）。
- 单测可观测路由注意：`env.getTransformations()` 是 package-private（StreamExecutionEnvironment.java:656）；flow 模块单测断言建图产物走 `buildJobGraph` → `JobEdge.getPartitioner()` → `KeySelectorPartitioner.getMaxParallelism()`（public）。

## Goals

- 4 个红灯 E2E 全部转绿，`./mvnw test -pl <11 模块>` 全绿（验收底线）。
- FLOW-5 守卫语义修正：HASH 边进入的 keyed 阶段允许目标并行度 ≠ 上游并行度并正确缩放（partition vertex 取目标值，与 javadoc/owner doc 对齐）；自相矛盾文案消除。
- KR-3 的合法迁移入口在 DSL 路径可达：显式"建图前配置后端"API，S2 场景改走该入口。
- KR-3 对"后置 setStateBackend 意外漂移"的 fail-fast 防护保持不变；`<keyBy>` 变换分支的 fail-fast 保持不变（防止下游 keyed 消费者宽度静默丢失的新形状）。

## Non-Goals

- 不重写 ForwardPartitionRouter / 执行面路由矩阵结构（能力已由 E2E 分布断言验证）。
- 不处理 R6 审计的其余 P1/P2/P3（归 2323–2325 及后续治理批次）。
- 不改变 checkpoint/rescale 状态重分布语义（KR-09 归 2324）。
- 不引入"静默容忍漂移"的回退：任何放宽都是显式语义（HASH 边两侧同写 resize / 显式 pre-build 配置）。
- 不修改 nop-stream-core 主代码（Phase 2 仅 flow builder + fraud-example 测试支撑）。
- 不修改 `docs/`、`docs-en/` 遗留目录下的 user-guide 副本。

## Scope

### In Scope

- `nop-stream-flow` `StreamModelDslBuilder.applyEdgePartition` HASH 分支守卫改 resize；`applyDeclaredParallelism` 保持拒绝但核对文案；相关单测翻转/新增。
- `nop-stream-flow` DSL 建图入口新增建图前 CheckpointConfig/StateBackend 配置 API。
- `nop-stream-fraud-example`：`ScenarioTestSupport.buildEnv` 翻转为后端 pre-build（其余调用方传 null 不受影响，审查已核实）；TestS2OfflineReshardE2E 经新入口注入后端；三个并行度 fixture 不改声明。
- `TestKeyedStageParallelismGuard` fixture 的 sinkFn bean 类型修正（MapFunction → SinkFunction）+ 断言翻转。
- owner doc：`docs-for-ai/03-modules/nop-stream-user-guide.md` 新增"跨 maxParallelism 恢复合法路径"小节（:126 既有 resize 描述已与修正后行为一致，无需改写）。

### Out Of Scope

- `nop-stream-runtime` 远程部署面、`nop-stream-core` 主代码。
- WindowOperator / RescaleStateAssembler / CheckpointBarrierTracker（2324）。
- MemoryStateSerDe 冻结协议（2323）。

## Execution Plan

### Phase 1 - FLOW-5 守卫语义修正（HASH 分支 resize 化）

Status: completed
Targets: `nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/StreamModelDslBuilder.java`、`nop-stream/nop-stream-flow/src/test/java/io/nop/stream/flow/builder/TestKeyedStageParallelismGuard.java`、`nop-stream-fraud-example`（E2E 验证）

- Item Types: `Fix`、`Proof`

- [x] 修改 `applyEdgePartition` HASH 分支：target 与 keyed transformation 并行度不等时不再抛异常，保留 `keyed.setParallelism(targetParallelism)`（partition vertex 取目标值，两侧同写、自洽）；删除 detail 中 "keyed-stage parallelism ... differs" 拒绝文案
- [x] `applyDeclaredParallelism` KeyedStream 分支保持 fail-fast 拒绝（裁定：`<keyBy parallelism=N>` resize 后下游 keyed 消费者未声明时按 env 默认盖章、无法成链，声明会在执行面静默丢失——该分支的拒绝防的是真静默通道，不能放开）；仅核对文案语义自洽（"declare equal parallelism"）
- [x] 修正 `TestKeyedStageParallelismGuard` fixture：sinkFn 从 `MapFunction` 换成真实 `SinkFunction`（否则翻转后 bean 类型不匹配先红）；断言从"不相等即抛"翻转为"resize 生效"——经 `buildJobGraph` 断言目标 JobEdge 的 partitioner/顶点并行度为声明值
- [x] 新增/扩展单测：HASH 边 srcP≠tgtP 建图后，目标顶点并行度 == 声明值 且 JobEdge partitioner 为 KeySelectorPartitioner（public 面断言，不用 package-private getTransformations）
- [x] 运行 `./mvnw test -pl nop-stream/nop-stream-flow`（含 -am 依赖就绪）全绿
- [x] 运行 `./mvnw test -pl nop-stream/nop-stream-fraud-example` 确认 TestPerTransformParallelismE2E / TestParallel2PcFileE2E / TestParallel2PcJdbcE2E 三个红灯转绿（TestS2OfflineReshardE2E 仍红，归 Phase 2）

Exit Criteria:

- [x] `applyEdgePartition` HASH 分支语义为 resize；grep 确认 `nop-stream-flow` main 内 HASH 分支不再存在 "keyed-stage parallelism ... differs" 自相矛盾拒绝文案（FORWARD 分支保留的拒绝文案含同词组，属计划保留项）；`applyDeclaredParallelism` 拒绝语义保留且有测试覆盖
- [x] `TestKeyedStageParallelismGuard` 翻转后通过（含 sinkFn 类型修正）；新增建图并行度断言存在且通过
- [x] **端到端验证**：TestPerTransformParallelismE2E 转绿，其 "declared map/sink parallelism 4 must materialize 4 subtasks" 分布断言通过（perThreadCounts ≥ 2 个 subtask 线程收到数据 + 计划面矩阵结构断言，与测试实际断言一致）
- [x] **接线验证**：分布断言证明 resize 后 HASH 路由真实散列到多个目标 subtask（≥2 线程非空断言存在且通过）
- [x] **无静默跳过**：`<keyBy>` 变换分支拒绝语义保留（TestPerTransformParallelismWiring.keyedStageMismatchFailsFast 通过）；非 HASH / 非 keyed 路径行为不变（既有测试通过佐证）
- [x] No owner-doc update required（user-guide :126 既有 resize 描述与修正后行为一致，可仓库验证；Phase 2 统一补跨 M 恢复小节）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - KR-3 合法迁移入口（DSL pre-build 后端配置）

Status: completed
Targets: `nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/StreamModelDslBuilder.java`、`nop-stream/nop-stream-fraud-example/src/test/java/io/nop/stream/fraud/scenario/ScenarioTestSupport.java`、TestS2OfflineReshardE2E、`docs-for-ai/03-modules/nop-stream-user-guide.md`

- Item Types: `Fix`（KR-3 错误文案承诺的合法路径在 DSL 路径不可达——已确认 contract gap）、`Proof`

- [x] DSL builder 新增显式 pre-build 配置 API（字段 + `build()` 内 env 创建后、`buildTransforms` 前注入 `checkpointConfig`），使 `resolveKeyGroupMaxParallelism()` 在 keyBy 冻结时读到目标 M；命名与现有 builder 链式风格一致
- [x] 新增单测：pre-build 配置的后端 M 被 keyBy 冻结——经 `buildJobGraph` 断言 JobEdge partitioner（KeySelectorPartitioner）maxParallelism == 配置值（public 面，不用 package-private API）
- [x] KR-3 默认防护不削弱：`nop-stream-core` 的 `TestKeyedRoutingParityGuard.testBackendDriftAfterKeyByFailsAtExecute` 保持通过（两 Phase 不改 core，运行确认）
- [x] `ScenarioTestSupport.buildEnv` 翻转：backend 参数经新 API 在 build 前注入，storage path 仍后置（其余调用方传 null，行为不变——审查已核实 TestParallel2PcFile/Jdbc、TestS2RecoveryAndRescaleE2E、ScenarioXdslPipelineResolver 均不受影响）
- [x] TestS2OfflineReshardE2E：run1/run2 均显式 pre-build 注入对应 M 的 RocksDB 后端；迁移→恢复→exactly-once 断言全部保留
- [x] 运行 `./mvnw test -pl nop-stream/nop-stream-fraud-example`：4 个红灯全绿
- [x] owner doc：`docs-for-ai/03-modules/nop-stream-user-guide.md` 新增"跨 maxParallelism 离线 reshard 恢复合法路径"小节（迁移工具 + pre-build 入口用法 + 默认漂移仍拒绝的边界）

Exit Criteria:

- [x] 新入口 API 存在且有单测（pre-build 配置的 M 经 JobEdge partitioner public 面断言）
- [x] KR-3 既有漂移拒绝钉子保持通过（测试名：`TestKeyedRoutingParityGuard.testBackendDriftAfterKeyByFailsAtExecute`，nop-stream-core）
- [x] **端到端验证**：TestS2OfflineReshardE2E 转绿——真实 checkpoint → 离线迁移 128→256 → 从迁移产物恢复 → 全输入 exactly-once 输出断言通过
- [x] **接线验证**：run2 恢复实际使用迁移产物（测试内 `totalKeyedEntries > 0` 与输出覆盖 fullExpected 断言通过）
- [x] **无静默跳过**：漂移拒绝路径仍显式抛错（钉子测试佐证）
- [x] owner doc 新增小节已落地且经 doc link checker
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 全量回归与收口

Status: completed
Targets: 11 个 nop-stream 模块

- Item Types: `Proof`、`Decision`（summary 定性改写）

- [x] `./mvnw install -DskipTests -pl <11 模块> -am` 成功
- [x] `./mvnw test -pl <11 模块>` 全绿（0 failures，0 errors；skipped 仅限既有门控类）
- [x] TestParallel2PcFileE2E / TestParallel2PcJdbcE2E 转绿确认（per-subtask 2PC 隔离断言通过）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] R6 审计 summary.md 的 P0 条目按本计划 Adjudication 改写定性（守卫语义偏离设计，fixture 不改）并补记修复归属 2322

Exit Criteria:

- [x] 上述五条命令/断言全部满足并记录输出摘要到日志
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] P0-E2E-RED 族全部子面闭合：(a) FLOW-5 fixture×3 转绿 + (b) KR-3×迁移入口×1 转绿
- [x] `./mvnw test -pl <11 个 nop-stream 模块>` 全绿（主验收）
- [x] KR-3 意外漂移防护未被削弱（core 钉子测试保持）+ `<keyBy>` 变换分支 fail-fast 保留（flow 钉子保持）
- [x] 必要 focused verification 完成（守卫 resize 语义单测 + pre-build 入口单测 + 分布断言）
- [x] 不存在被静默降级到 deferred 的 in-scope live defect
- [x] 受影响 owner docs 已同步（user-guide 跨 M 恢复小节新增）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证 (a) 新入口被 S2 测试真实调用（非仅编译存在），(b) resize 后分布断言证明路由散列真实生效，(c) 无空方法体/静默跳过
- [x] `./mvnw test -pl <11 模块>` 通过（兼作编译验证；flow 的下游消费者 nop-stream-runtime/nop-stream-sql 由 11 模块矩阵兜底）
- [x] 代码规范核对通过（import 分组 io.nop.* → third-party → java.* 等）

## Deferred But Adjudicated

### R6 P1/P2/P3 其余条目（87 条中除本计划外的全部）

- Classification: `out-of-scope improvement`（按 R5 先例进入后续批次 2323–2325 与治理轮次）
- Why Not Blocking Closure: 本计划只收口 CI 红线；其余缺陷不影响"全绿基线"成立，已有独立归属计划
- Successor Required: `yes`
- Successor Path: `ai-dev/plans/2323-nop-stream-r6-snapshot-freeze-protocol.md`（拟）、`ai-dev/plans/2324-nop-stream-r6-keyed-restore-routing.md`（拟）、`ai-dev/plans/2325-nop-stream-r6-failclosed-error-paths.md`（拟）

## Non-Blocking Follow-ups

- 若 FORWARD 分支（applyDeclaredParallelism）文案在收口核对中仍有语义瑕疵，随批修正（不阻塞）
- fraud-example 其余 skipped 门控类（multi-jvm）不在本计划范围

## Closure

Status Note: P0-E2E-RED 族 4 面全部闭合（FLOW-5 fixture×3 + KR-3×迁移入口×1），11 个 nop-stream 模块 `./mvnw test` 全绿（4056 tests / 0 failures / 0 errors / 28 skipped 门控，BUILD SUCCESS）；KR-3/KR-2 防护未削弱（core+flow 钉子保持），Anti-Hollow 三查通过。独立 closure audit 裁定 APPROVE WITH CONDITIONS，4 项条件（Phase 2 偏差补记、successor 落地、日志数字修正、按文件范围切分提交）已在收口前全部完成。

Completed: 2026-10-06

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，agent_e81f84b3-b081-48fe-81c3-588366c12b34）
- Audit Session: agent_e81f84b3-b081-48fe-81c3-588366c12b34
- Evidence:
  - Phase 1 Exit Criteria 7 条：6 PASS + 1 PARTIAL→已修（grep 断言措辞按 FORWARD 分支保留项改写）
  - Phase 2 Exit Criteria 7 条：全 PASS（withPreBuildEnvironment :173-182 + build() :201-205 前置执行；TestS2OfflineReshardE2E surefire 09:37:38 绿，全链路断言 :122-184）
  - Phase 3 Exit Criteria 2 条：PARTIAL→已修（4056/28 全量摘要补入日志）；其余 PASS
  - Closure Gates 10 条：全部就绪（4 E2E surefire 双源核实绿；28 skipped 均为既有门控类；防护钉子 git diff 为空）
  - `node ai-dev/tools/check-plan-checklist.mjs <plan> --strict` 退出码 0（收口核对时复跑）
  - Anti-Hollow：调用链 TestS2OfflineReshardE2E.run → buildEnv → withPreBuildEnvironment → build() 前置 configurer 确认连通；分布断言 perThreadCounts≥2 + 恰一次全量断言确认散列生效；三处主代码 diff 均为真实逻辑，remote null 为显式注释的保持旧行为
  - Deferred 项分类检查：仅含 R6 其余缺陷（out-of-scope，successor 2323/2324/2325 已落地为 draft 计划），无 in-scope live defect 被降级
  - 全量回归：`_tmp/regression-2322-test.log` BUILD SUCCESS，4056 tests / 28 skipped（core 1 + debezium 1 + runtime 10 + fraud 16 门控）

Follow-up:

- R6 其余缺陷由 successor 计划承接：2323（快照冻结族 ST-01/ST-02/REG-01）、2324（键控恢复族 KR-06/07/08/09，含 remote TM 穿参遗留）、2325（fail-closed 族 AR-01/CON-01/ST-03/TE-01）
