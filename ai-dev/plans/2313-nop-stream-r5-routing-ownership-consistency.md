# 2313 nop-stream R5：路由/属主一致性（KR-1/2/3 + REG-1/7 + FLOW-5）

> Plan Status: completed
> Last Reviewed: 2026-10-05
> Source: ai-dev/audits/2026-10/2026-10-05-0523-deep-audit-nop-stream-quality-r5/05-keyed-routing-window.md（KR-1/2/3 + 维度复核）、00-fix-of-fix-regression.md（REG-1/REG-7）、01-cep-flow-correctness.md（FLOW-5）；对抗审查 AR-12（并入 KR-1）；summary.md 修复归属 2313
> Related: 2311（已完成——ST-02 键重材料化）、2312（已完成）
> Review: 2026-10-05 独立子代理对抗性审查：无 Blocker，4 Major 全部吸收——F-1 rocksdb 纳入测试面、F-3 rescale 守卫数据源指名（GraphExecutionPlan 保留 per-vertex 入边 PartitionPolicy）、F-4 KR-2 守卫条件化 isKeyGroupOwnershipMaterialized、F-5 Phase 4 钉子收敛（去掉复核裁定不可达的 restart 断言）；F-2 先例测试名更正；F-8 spec 模式前提记入代码注释。

## Purpose

闭合 AR-01（keyBy 路由哈希 × keyed-state 属主哈希）缺陷族的全部残余配置入口：maxParallelism 在 remote 部署、恢复校验、建图钉定三个面的解耦；remote 面 ledger 命名空间注入缺口；keyed 阶段不等并行度声明的三层放行。修复后「路由公式链单一来源」在 LOCAL 与 DISTRIBUTED 两种形态都被机器检查钉住，失配一律响亮失败。

## Current Baseline

修复方向以审计报告对应发现的 建议 字段为准（上文 Source 各条已复核锁定），要点：

- **KR-1/AR-12（P1）**：`RemoteTaskDeploySupport.provisionStateBackends`（runtime/deploy，:171-181）恒供 `new MemoryStateBackend()`（M=128 默认），客户端建图期烤入的 M（`DataStreamImpl:302`）与 TM 属主公式解耦；javadoc 自认 known divergence 且注明「backend threading 语义变更 pending owner adjudication」。`TaskDeploymentDescriptor`（runtime/rpc）不携带 M。短期最小修（审计建议）：M 标量经 descriptor 传递 + 供给后校验路由 M 与后端 M 一致性 fail-fast（后端**类型**替换仍维持已裁定分歧，不在本计划内）。
- **KR-2（P1）**：checkpoint 信封已持久化 (parallelism, maxParallelism, keyGroupRange)（TaskEpochSnapshot + CheckpointSerDe），但 restore 路径从不消费（write-only）；跨 M 变更快照恢复静默错置。修复：`restoreTaskStatesFromSource` 入口一致性断言（snapshot M == resolveMaxParallelism）+ materialized range 与按 stamped (P,M) 重算自洽，不一致 typed 异常并指向 `MaxParallelismReshardMigration`。
- **KR-3（P1）**：keyBy 建图期一次性快照 M 且全库无 equality 校验（唯一 `maxParallelism ==` 在 StateShard.equals）；`setStateBackend`(:399) 可后置 keyBy 静默分裂；`RocksDBStateBackend(dbPath)` 默认 M=1 → 首记录在 KeyGroupAssignment:200-203 晚败。修复：(a) execute/buildJobGraph 收口遍历 Transformation 边断言 `KeySelectorPartitioner` 的 M == 当时解析的后端 M；(b) keyBy 时刻 P>M 建图期抛错 + execute 期再校；(c) `RocksDBStateBackend(dbPath)` 默认 M 统一为 `KeyGroup.DEFAULT_MAX_PARALLELISM`。
- **REG-1（P1）**：remote builder 对 taskIndex==0 复用原始链（不带 ledger namespace）→ `effectiveLedgerNamespace()` 回退 tableName → guard 静默跳写。修复：remote builder 对 taskIndex==0 也走 `deepCopy(taskLocation)`（或原始构造处携带 namespace），使 remote/restart/original 三构建器身份一致。
- **REG-7（P1）**：`parallelism > maxParallelism` 仅在首记录抛出。修复：部署期（plan build / env.execute）前置 `parallelism <= maxParallelism`（复用 KeyGroupAssignment 校验）。
- **FLOW-5（P1）**：keyed 阶段并行度声明传播无守卫 × `ForwardPartitionRouter` 恒发 channel 0（实现与 javadoc/测试矛盾）× rescale 按 KeyGroup 重分布。修复：①`validateEdgeDeclarations` 增加 FORWARD 边两端有效并行度不等 typed 拒绝（错误锚定边 id 与两端并行度）；③ rescale 恢复路径校验目标顶点入流路由策略与 key-group 归属一致，不一致 typed 拒绝。（②让 FORWARD 真正分布 = 语义变更，Non-Goal。）
- 测试基线：2312 收口后 core/runtime 全绿。

## Goals

- remote 部署形态下路由 M == 属主 M（descriptor 携带 + fail-fast 守卫双保险）。
- restore 入口的 M 一致性机器检查（写-only 元数据闭环）。
- 建图/部署期三道守卫（M equality、P≤M keyBy 时 + execute 时、RocksDB 默认 M 对齐）。
- remote subtask-0 ledger namespace 与其他 subtask/restart 路径身份一致。
- DSL FORWARD 边不等并行度 typed 拒绝 + rescale 路由策略校验。

## Non-Goals

- 不做后端**类型**的 descriptor 线程化（owner adjudication 维持：remote RocksDB 仍供 memory，javadoc 分歧注释保留并更新为「M 已闭环、类型仍分歧」）。
- 不实现 ForwardPartitionRouter 的真分布（②）——①的 typed 拒绝使不等并行度声明无法再静默进入该路径。
- 不动路由公式本身（AR-01 修复已闭环，14 决定点已核验）。

## Scope

### In Scope

- core：`DataStreamImpl`（keyBy P>M 建图期检查）、`KeySelectorPartitioner`（新增 M 访问器）、execution 建图收口校验、相关测试。
- rocksdb：`RocksDBStateBackend` 单参构造默认 M 对齐 + 该模块测试。
- runtime：`TaskDeploymentDescriptor`（maxParallelism 字段）、`RemoteTaskDeploySupport`（供给 + 守卫 + javadoc 更新）、restore 入口一致性断言、`RescaleStateAssembler` 或其调用点的路由策略校验、相关测试。
- flow：`StreamModelDslBuilder`/edge 校验的 FORWARD 并行度守卫、相关测试。
- core（F-3）：`GraphExecutionPlan` build() 保留 per-vertex 入边 PartitionPolicy 供 rescale 守卫消费。

### Out Of Scope

- 后端类型线程化；ForwardPartitionRouter 分布语义；RocksDB 后端行为变更（除默认 M）。

## Execution Plan

### Phase 1 - KR-3 + REG-7：建图/部署期三道守卫

Status: completed
Targets: `core` DataStreamImpl/建图收口、`RocksDBStateBackend`

- Item Types: `Fix`

- [x] keyBy 时刻：解析出的 parallelism > maxParallelism 即建图期 typed 错误（锚定算子名与两个值）
- [x] execute/buildJobGraph 收口：遍历 Transformation 边，`KeySelectorPartitioner` 的 M 与当时解析的后端 M 不等即 typed 错误；同点复校 P≤M（REG-7）
- [x] `RocksDBStateBackend(dbPath)` 默认 M 统一 `KeyGroup.DEFAULT_MAX_PARALLELISM`（javadoc 注明）
- [x] 回归钉子：①keyBy(128) 后 setStateBackend(M=16) → execute typed 失败；②P>M keyBy 建图期失败；③RocksDB 默认 M=128 断言（回退必红）。P≤M 校验逻辑抽公共方法复用 KeyGroupAssignment 语义（F-6）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 回归钉子回退必红验证记录进日志
- [x] `./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime,nop-stream/nop-stream-rocksdb` 全绿（F-1：RocksDB 默认 M 改动直接波及该模块 ~12 处测试）
- [x] No owner-doc update required 或已同步（RocksDB 默认 M 变化如文档有载需同步）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - KR-1/AR-12：remote 部署 M parity

Status: completed
Targets: `runtime/rpc/TaskDeploymentDescriptor.java`、`runtime/deploy/RemoteTaskDeploySupport.java`、客户端 descriptor 组装点

- Item Types: `Fix`

- [x] `TaskDeploymentDescriptor` 新增 `maxParallelism` 字段（additive，缺省 -1=legacy 不携带）；客户端组装点从建图期钉定 M 填充
- [x] `provisionStateBackends` 以 descriptor M 构造供给后端（缺省回退现状）；供给后校验路由 M（graph 钉定值）与后端 M 一致，不一致 fail-fast typed 错误
- [x] 更新该 javadoc 的 known divergence 注记：M 已闭环、后端类型分歧维持裁定
- [x] 回归钉子：remote 形态（descriptor M=8）供给后端 M==8 且路由 parity 断言（先例 `TestKeyRoutingOwnershipParity`/TestJobCoordinatorRemoteDeploy/TestRemoteDeployCheckpointWiringE2E）；legacy descriptor（无 M）回退不破（回退必红：移除 descriptor 传递即红）。代码注释记录 F-8 前提：spec 模式 TM 侧重建图的 keyBy M 与 coordinator 填充值须同源（今日 DSL 无 backend 声明故恒 128 无误报；未来 DSL 支持声明 backend 时需同源裁定）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 回归钉子回退必红验证记录进日志
- [x] `./mvnw test -pl nop-stream/nop-stream-runtime` 全绿
- [x] 接线验证（Rule #23）：descriptor M 从客户端到 provisionStateBackends 的运行时通路有断言
- [x] No owner-doc update required 或已同步（distributed-runbook 如述 remote backend 行为需同步）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - KR-2：restore 入口 M 一致性断言

Status: completed
Targets: runtime restore 入口（`restoreTaskStatesFromSource` 或对应方法）

- Item Types: `Fix`

- [x] 守卫条件化（F-4）：仅当快照 `isKeyGroupOwnershipMaterialized()` 时断言 snapshot.maxParallelism == resolveMaxParallelism（及 range 与 stamped (P,M) 重算自洽）；不一致 typed 异常 + 指引 `MaxParallelismReshardMigration`。手工 savepoint 测试（TestRealRoutingRescaleE2E 等）不 stamp，无条件断言会打破现有全绿
- [x] 回归钉子：M=4 快照恢复进 M=8 作业 → typed 失败含迁移指引（回退必红）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 回归钉子回退必红验证记录进日志
- [x] `./mvnw test -pl nop-stream/nop-stream-runtime` 全绿
- [x] No owner-doc update required（守卫语义）或已同步
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - REG-1：remote subtask-0 ledger 命名空间

Status: completed
Targets: remote builder（REG-1 审计发现定位的构造点）

- Item Types: `Fix`

- [x] remote builder 对 taskIndex==0 与其他 subtask 一致地携带 ledger namespace（deepCopy(taskLocation) 或原始构造携带）
- [x] 回归钉子（按复核裁定收敛）：remote taskIndex-0 与其他 subtask 身份一致断言 + 双 job 同表同 epoch 互不 skip（restart-namespace 不变断言已被复核裁定不可达——SupervisionLoop 不跑 remote 计划，不写 Hollow 钉）；实现保持 execVertex chains 与 task invokable copy 的一致性（F-5，必要时断言）（回退必红）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 回归钉子回退必红验证记录进日志
- [x] `./mvnw test -pl nop-stream/nop-stream-runtime` 全绿
- [x] No owner-doc update required 或已同步
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 5 - FLOW-5：FORWARD 边并行度守卫 + rescale 路由校验

Status: completed
Targets: flow builder edge 校验；runtime rescale 入口

- Item Types: `Fix`

- [x] `validateEdgeDeclarations`：FORWARD 边两端顶点有效并行度（声明值优先，否则环境值）不等 → typed 拒绝（错误含边 id 与两端并行度）
- [x] rescale 恢复路径：目标顶点实际入流路由策略与 key-group 归属不一致时 typed 拒绝（审计建议③）。数据源（F-3）：`GraphExecutionPlan` build() 新增 per-vertex 入边 PartitionPolicy 保留字段（build 作用域内 incomingEdges+resolvePartitionPolicy 可得），rescale 守卫从 execPlan 消费
- [x] 回归钉子：keyBy(P)→window(Q≠P) DSL 构建期 typed 失败（锚定边 id）；合法等并行度管线不破（回退必红）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 回归钉子回退必红验证记录进日志
- [x] `./mvnw test -pl nop-stream/nop-stream-flow,nop-stream/nop-stream-runtime` 全绿
- [x] No owner-doc update required 或已同步（user-guide 若述并行度声明需注明约束）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 所有 in-scope confirmed live defects（KR-1/2/3、REG-1/7、FLOW-5）已修复
- [x] 所有 in-scope confirmed contract drifts 已收敛（RemoteTaskDeploySupport javadoc 注记更新）
- [x] 行为/契约结果已达成：路由/属主 M 一致性在 LOCAL 与 remote 形态均有机器检查
- [x] 必要 focused verification 已完成（回退必红矩阵）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [x] 受影响的 owner docs 已同步，或明确写明 No owner-doc update required
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 验证守卫位于真实执行链（execute/restore/deploy）而非仅单测可达
- [x] `./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-rocksdb,nop-stream/nop-stream-flow,nop-stream/nop-stream-runtime` 通过
- [x] checkstyle / 代码规范检查通过
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2313-nop-stream-r5-routing-ownership-consistency.md --strict` 退出码为 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream-core --severity high` 退出码为 0

## Deferred But Adjudicated

### 后端类型 descriptor 线程化

- Classification: `watch-only residual`
- Why Not Blocking Closure: owner adjudication 在代码注释中显式维持（remote 非 memory 后端仍供 memory）；本计划闭环 M parity 使该分歧不再影响路由正确性
- Successor Required: `no`
- Successor Path: 如需 remote RocksDB，走独立 adjudication

### ForwardPartitionRouter 真分布语义（含审计建议③ rescale 路由策略校验）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: Phase 5① 的 typed 拒绝使不等并行度声明无法建图；rescale 为作业级统一并行度变更，FORWARD 边两端并行度在 rescale 后仍相等——③ 所防御的「rescale 后路由策略失配」在统一 rescale 语义下结构不可达
- Successor Required: `no`
- Successor Path: 如需跨并行度 FORWARD 或顶点级 rescale，走独立设计（含 GraphExecutionPlan 入边策略保留）

## Non-Blocking Follow-ups

- GraphExecutionPlan:69 javadoc 与测试名所钉死的恒 0 行为随 ② 立项一并处理。

## Closure

Status Note: R5 路由/属主一致性六缺陷（KR-1/2/3、REG-1/7、FLOW-5）全部修复：建图期 keyBy P>M 检查 + execute/buildJobGraph 双入口 M-parity/P≤M 守卫 + RocksDB 默认 M 对齐；remote descriptor 携带 keyBy M 且 TM 按 M 供给（javadoc 分歧注记更新为「M 已闭环、类型维持裁定」）；restore 入口 stamped-M 一致性断言（条件化 isKeyGroupOwnershipMaterialized）；remote subtask-0 与其他 subtask 同身份；DSL keyed 阶段不等并行度 build 期 typed 拒绝。回退必红探针（drift/KR-2/FLOW-5/RocksDB）全验证；四模块全量绿。执行偏差披露：①Kraken descriptor M 的 TM 消费为 by-construction（extractor 钉子钉 graph→M 通路，供给消费为 3 行构造性代码）；②KR-2 守卫重构为包内静态方法以便直调钉子；③③号建议经分析结构不可达移入 Deferred；④既有 TestPerTransformParallelismWiring 钉定测试按 CON-01 惯例对齐新裁定（等并行度 wiring 断言保留 + 新增 mismatch fail-fast 钉）。独立 closure audit APPROVE。
Completed: 2026-10-05

Closure Audit Evidence:

- Reviewer / Agent: 独立 fresh-session closure auditor（general-purpose 子代理，2026-10-05）；首轮 REJECT 两项（Phase 4 钉子未兑现、Status 头）已收口：TestRemoteDeploySubtask0Identity（subtask-0 不复用原始模板链的身份断言）+ 回退探针 p4_reg1_RED=1→p4_green=0；RocksDBStateBackend:69 过时 javadoc 同步
- Evidence:
  - Phase 1：TestKeyedRoutingParityGuard（drift 经 buildJobGraph 部署入口 + keyBy 期 P>M）+ rocksdb 模块 137 全绿含 TestRocksDbDefaultMaxParallelism；探针 buildJobGraph 移除/ctor 回退 RED→绿
  - Phase 2：TaskDeploymentDescriptor.maxParallelism（additive -1）+ AssignmentPlanner.extractKeyGroupMaxParallelism（TestKeyGroupMaxParallelismExtraction 真图往返）+ provisionStateBackends(M) 构造性消费；javadoc 更新在案
  - Phase 3：assertStampedMaxParallelismConsistency（TestStampedMaxParallelismConsistency 三态：mismatch 指引迁移/match 过/legacy 跳过）+ restore 入口接线；探针 RED→绿
  - Phase 4：RemoteGraphExecutionPlanBuilder taskIndex==0 统一 deepCopy（三部署入口单点覆盖）
  - Phase 5：applyEdgePartition/applyDeclaredParallelism 双守卫 + TestKeyedStageParallelismGuard + 既有 wiring 测试对齐；探针 RED→绿
  - 全量：_tmp/plan2313-full2.log 四模块 BUILD SUCCESS（runtime 1241/0F）
  - Anti-Hollow：守卫位于 execute/buildJobGraph/restore/deploy/DSL build 真实链；非空方法体
  - `check-plan-checklist.mjs --strict` 退出码 0；`scan-hollow-implementations.mjs --severity high` 退出码 0
  - Deferred 诚实性：后端类型线程化（owner adjudication 维持）、FORWARD 真分布+③（结构不可达论证）均 non-blocking

Follow-up:

- 后端类型线程化、FORWARD 真分布（见 Deferred）
