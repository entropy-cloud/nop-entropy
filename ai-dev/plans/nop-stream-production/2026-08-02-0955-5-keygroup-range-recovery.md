# 5 KeyGroupRange 交集恢复 + RocksDB key-group 感知 restore + rescale

> Plan Status: active
> Last Reviewed: 2026-08-02
> Source: `ai-dev/backlog/nop-stream-production-roadmap.md` Stage 35；deferred item from `2026-08-02-0955-2-incremental-checkpoint-sst-sharing.md` "Key-Group range SST reading"（Successor Path: Stage 35）
> Mission: nop-stream-production
> Work Item: 35. KeyGroupRange 恢复 + RocksDB key-group 感知 restore
> Related: 前置 `2026-08-02-0955-4-key-group-model.md`（Stage 34 交付 KeyGroupRange + 可排序二进制前缀，**必须先完成**）；Stage 31 增量 checkpoint（`2026-08-02-0955-2`，已完成）

## Purpose

在 Stage 34 交付的 KeyGroup 模型与可排序二进制前缀基础上，实现 rescale（并行度变化）时的**局部状态恢复**：每个新 subtask 只恢复属于自己的 `KeyGroupRange` 区间，而非全量状态。同时激活 Stage 31 deferred 的「Key-group range SST 读取」——基于 Stage 34 的可排序前缀做 SST 文件 range 交集读取，替代全量扫描。交付并行度 4↔16 的 savepoint restore 验证。

**前置门禁**：Stage 34 必须先完成（KeyGroupRange 类、group→subtask 映射函数、RocksDB 可排序前缀键布局）。本 plan 在 Current Baseline 中假设 Stage 34 已 landing。

## Current Baseline

> 以下事实假设 Stage 34 已完成；若 Stage 34 实现细节与本描述冲突，以 live repo 为准并在执行时更新本节。

- **Stage 34 产出（前置依赖）**：`KeyGroup` / `KeyGroupRange`（交集/包含/分割操作）、稳定哈希、`maxParallelism`（默认 128）、`key → keyGroupId` 映射、group→subtask 映射函数、RocksDB 复合键以 `keyGroupId` 为可排序二进制首部前缀。
- **restore 现状**：`RocksDBKeyedStateBackend.restoreState`（`RocksDBKeyedStateBackend.java:624-627`）委托 `RocksDBSnapshotSerDe.restoreState`（`:401-450`）做**全量扫描重写**：`clearAllStates`（`:452-464`）逐 CF 全扫逐 key 删除，各 `restoreXxx`（`:479-725`）把快照里**每个** key 按当前 shard/group 重写。无 range/交集过滤——恢复即全量覆盖。
- **Memory restore**：`MemoryKeyedStateBackend.restoreState`（`:357-361`）→ `MemoryStateSerDe.restoreState`（`:104-153`），同样全量重建，无区间概念。
- **subtask 不拥有状态切片**：`TaskLocation`（`io.nop.stream.core.checkpoint.TaskLocation`）只知 `(jobId, pipelineId, vertexId, taskIndex)`，不知自己拥有哪些 key/groups。`TaskEpochSnapshot.shards: List<StateShard>`（`TaskEpochSnapshot.java:25`，getter `:44`，`addShard` `:52`）是规划中的 shard 归属记录，但 **`.addShard(` 生产代码从不调用，仅 `TestTaskEpochSnapshot.java` 使用**，运行时恒为空。
- **部署分配无 key-group 感知**：`DeploymentPlanGenerator.buildRoundRobinAssignment`（`:97-116`）纯 round-robin over `activeNodeIds`。`DeploymentAssignment.getNodeForSubtask`（`:63-69`）按 list index 取节点。两者都不记录 subtask→KeyGroupRange 归属。
- **增量 checkpoint SST（Stage 31）**：`RocksDBIncrementalSnapshotStrategy`（`:60-112`）产 SST 经 SHA-256 内容寻址 + `SharedStateRegistry` 引用计数；`RocksDBIncrementalRestore.reconstructRocksdbDir`（`:62-91`）把所有 SST + 非 SST 文件重建为一个可打开 RocksDB 目录。**但生产 `restoreState` 不走增量路径**——`RocksDBIncrementalRestore` 仅测试使用。Stage 31 deferred 明确「Key-group range SST 读取」需要本 Stage。
- **无 rescale 测试**：`TestStateShardRescale.java` 只验证单后端实例跨 `shardCount` 快照保留 key，不验证并行度变化或 per-subtask 切片。`parallelism=4→16/16→4` savepoint restore 测试不存在。
- 设计约束：`stateShardCount` 改变需显式迁移（`checkpoint-design.md:824`）；keyed state rescale 规则见 `checkpoint-design.md:817`（`stateShardCount` 不变时按 `ownerSubtask` 重新归属）。Stage 34 引入 maxParallelism 后，`maxParallelism` 不变、`parallelism` 可变是合法 rescale。

## Goals

- **KeyGroupRange 局部恢复**：rescale 时每个新 subtask `i` 只恢复 `KeyGroupRangeAssignment(parallelism, maxParallelism, i)` 区间内的 keyed state，其余 group 的 key 被跳过（而非全量加载后再丢弃）。
- **executor 级 restore dispatch 重构**：把 `GraphModelCheckpointExecutor.restoreTaskStatesFromSource`（`:904-932`）当前的严格 1:1 `TaskLocation` 查找，重构为按 KeyGroupRange 区间路由的 N:1 / 1:N 分发，使并行度变化（4→16 新 subtask 无旧 location、16→4 多旧 snapshot 合并）下状态能正确送达新 subtask。这是 Stage 35 的承重改动。
- **per-subtask 状态归属物化**：`TaskEpochSnapshot` 在生产 checkpoint 路径记录每个 subtask 实际拥有的 KeyGroupRange（`shards` 字段或等价区间记录），不再恒为空。
- **RocksDB SST range 交集读取（Stage 31 deferred）**：分两条路径——(a) 增量 checkpoint SST 路径：基于 Stage 34 可排序 `keyGroupId` 前缀做真正的 RocksDB range scan；(b) 全量 JSON snapshot 路径：快照已物化在内存，做 in-memory entry 过滤（只写回本 subtask KeyGroupRange 内的 key）。两条路径各自正确，不混淆。
- **rescale 端到端验证**：`parallelism=4 → savepoint → restore at parallelism=16`、`16 → 4`，所有 key 的聚合结果与无 rescale 的等价作业一致。
- group→subtask 映射接线进部署/恢复路径（Stage 34 只交付函数，本 plan 接线生产消费）。

## Non-Goals

- **跨 JVM restore**（Stage 40/42 数据面）——本 plan 仍为 local/embedded 执行，状态文件本地可达。
- **自动 StateShard→KeyGroup 存量迁移工具**（Stage 37）。
- **unaligned checkpoint + rescale 叠加**（Stage 47）。
- 改变 `maxParallelism` 本身（仅 `parallelism` 变化）；`maxParallelism` 变化仍属显式迁移。
- 增量 checkpoint 的跨 checkpoint SST 共享/引用计数（Stage 31 已完成）；本 plan 只消费其 SST 文件做 range 读取。
- Operator state 的 rescale（已有 `SPLIT_DISTRIBUTE/UNION/BROADCAST`，与本 keyed rescale 正交）。

## Scope

### In Scope

- restore 路径增加 KeyGroupRange 过滤：Memory 与 RocksDB 两后端。
- `TaskEpochSnapshot.shards` 生产填充（KeyGroupRange 归属）。
- group→subtask 映射接线进 checkpoint snapshot（记录归属）与 restore（消费归属）。
- RocksDB range scan restore（消费 Stage 34 可排序前缀）。
- `parallelism=4→16`、`16→4` savepoint restore E2E 测试（memory + rocksdb）。

### Out Of Scope

- 跨 JVM 状态文件传输（Stage 40）。
- `maxParallelism` 变化的迁移（Stage 37）。
- unaligned + rescale 交互（Stage 47）。

## Execution Plan

### Phase 1 - executor restore dispatch 重构 + group→subtask 归属接线 + TaskEpochSnapshot 物化

Status: planned
Targets: `GraphModelCheckpointExecutor.restoreTaskStatesFromSource`（`:904-932`，承重改动）；`TaskStateLookup`（`:899-902`）；`PartitionedPlan.java`；`DeploymentPlanGenerator.java`（`:97-116`）；`TaskEpochSnapshot.java`（`:25`）；checkpoint snapshot/manifest 路径

- Item Types: `Decision | Fix | Proof`

- [ ] **executor dispatch 重构（承重）**：`restoreTaskStatesFromSource`（`:904-932`）当前 `for subtask → stateLookup.lookup(TaskLocation(vertexId, taskIndex))` 是严格 1:1 查找——并行度 4→16 时新 subtask 4–15 无旧 location（lookup fail-fast）；16→4 时多个旧 snapshot 需合并。重构为按 KeyGroupRange 路由：新 subtask `i` 的 KeyGroupRange 与旧 plan 各 subtask 的 KeyGroupRange 求交，从所有相交的旧 `TaskStateSnapshot` 收集对应 group 的状态。`findTaskLocationInPlan`/`stateLookup` 改为区间感知（支持 1:N 新 subtask 读多旧 snapshot）
- [ ] checkpoint snapshot 时为每个 keyed subtask 计算其 `KeyGroupRange`（用 Stage 34 的 group→subtask 映射函数 + 当前 `parallelism`/`maxParallelism`），写入 `TaskEpochSnapshot`，生产路径真实填充（不再恒为空）
- [ ] **Decision（checkpoint schema）**：`TaskEpochSnapshot.shards` 当前是 `List<StateShard>`（标量 shard，无区间）。本 plan 裁定 schema：(a) 新增 `List<KeyGroupRange>` 字段记录区间归属（推荐，向后兼容），或 (b) 把 range 编码进 StateShard。决策需保证旧 checkpoint（`shards:[]`）JSON 反序列化不破坏，并在 owner-doc 记录
- [ ] restore 时从 `TaskEpochSnapshot`/`CompletedCheckpoint` 读取**旧** `parallelism` 下每个 subtask 的 KeyGroupRange 列表，确定新 `parallelism` 下本 subtask 需要读取哪些旧 group 区间（新旧 range 求交/并集，支持 16→4 多旧 snapshot 合并）
- [ ] group→subtask 映射稳定性验证：同一 key 在旧/新 `parallelism` 下归属的 group 不变（group 是 `maxParallelism` 上界的不变量），只有 group→subtask 归属随 `parallelism` 重算

Exit Criteria:

- [ ] **executor dispatch 接线验证（承重）**：`restoreTaskStatesFromSource` 在并行度变化下能正确路由状态——4→16 新 subtask 从旧 4 subtask snapshot 收集其 KeyGroupRange 内的 group；16→4 新 subtask 从多个旧 snapshot 合并其区间（集成测试断言）
- [ ] `TaskEpochSnapshot` 区间归属在生产 checkpoint 路径被非空填充——单测/集成测试断言其非空且内容正确
- [ ] **接线验证**：snapshot 写归属 → manifest 持久化 → restore 读归属 → executor dispatch 消费归属，链路完整连通（端到端测试断言 restore 侧能读到 snapshot 写入的归属）
- [ ] group→subtask 重算单测：`maxParallelism=128, oldParallelism=4, newParallelism=16`，新 subtask 5 的 KeyGroupRange 是旧 4 subtask range 的某个子区间并集；16→4 反向同理（合并多旧区间）
- [ ] **无静默跳过**：restore 时若归属信息缺失（旧 checkpoint 无 KeyGroupRange 记录），fail-fast 给出明确错误或明确 fallback 到全量恢复并记录（不可静默丢弃/静默全量）
- [ ] `ai-dev/design/nop-stream/checkpoint-design.md` §8.5（Rescale）更新：记录 executor dispatch 区间路由 + KeyGroupRange 归属接线 + scale-down 合并规则
- [ ] `./mvnw test -pl nop-stream/nop-stream-runtime -am` 通过（Phase 1 改 `GraphModelCheckpointExecutor`）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - RocksDB SST range 交集读取（Stage 31 deferred）

Status: planned
Targets: `RocksDBKeyedStateBackend.restoreState`（`:624-627`）；`RocksDBSnapshotSerDe.restoreState`（`:401-450`）/ 各 `restoreXxx`；`RocksDBIncrementalRestore`（`:62-91`）；`MemoryKeyedStateBackend.restoreState`（`:357-361`）/ `MemoryStateSerDe.restoreState`（`:104-153`）；Stage 34 键前缀

- Item Types: `Fix | Proof`

- [ ] 基于 Stage 34 的可排序 `keyGroupId` 二进制前缀，restore 时按本 subtask 的目标 KeyGroupRange 构造 RocksDB range scan（前缀 `[startGroup]` 到 `[endGroup]` 的字节区间），只读取本 subtask 拥有的 group，跳过其余 group
- [ ] 全量 JSON 快照 restore（`RocksDBSnapshotSerDe`）改为 **in-memory entry 过滤**：快照 `stateData` 已物化在内存，遍历 entry 时只写入落在本 subtask KeyGroupRange 内的 key（其余丢弃），而非全量重写。注意此路径非 RocksDB CF range scan（数据源是 JSON）
- [ ] **Memory 后端 in-memory 过滤**：`MemoryKeyedStateBackend.restoreState`（`:357-361`）/ `MemoryStateSerDe.restoreState`（`:104-153`）同样按目标 KeyGroupRange 过滤 entry（只写回区间内 key），与 RocksDB 全量路径对称
- [ ] 增量 checkpoint restore 路径接线：`RocksDBKeyedStateBackend.restoreState` 识别 `IncrementalSnapshotResult.MARKER_KEY`，对增量 checkpoint 走 `RocksDBIncrementalRestore` 重建 DB 目录后**真正 range scan**（消费 Stage 34 可排序前缀），替代当前无视 MARKER 的全量扫描 seam。**scale-down 合并**：16→4 时新 subtask 的 KeyGroupRange 跨多个旧 subtask 的 SST 集，需对每个相交旧 subtask 的 SST 集 range scan 后合并（非单 snapshot 内过滤）

Exit Criteria:

- [ ] range 读取单测：构造跨多个 group 的状态，restore 时只读取目标 KeyGroupRange 内的 key，其余 group 的 key 不出现在恢复后的 backend
- [ ] **接线验证**：range 读取在 restore 运行时确实被调用（端到端测试断言 restore 后 backend 只含本 subtask range 的 key；或计数器验证 range scan 被触发）
- [ ] 增量 checkpoint restore 接线：`restoreState` 对 `IncrementalSnapshotResult` 走增量 range 路径（非空壳）；既有 `TestRocksDBIncrementalRestoreAndBenchmark.java` 扩展验证 range 过滤
- [ ] **scale-down 多源合并测试**：16→4 场景下单新 subtask 从多个旧 subtask snapshot/SST 集合并其 KeyGroupRange 内状态（断言合并后 key 集正确、无丢失/重复）
- [ ] **端到端验证**：从 checkpoint（含增量）触发到 restore 完成的完整路径跑通，恢复后状态正确
- [ ] **无静默跳过**：range scan 的边界/空区间分支有处理（空 range 不静默返回错误数据，而是合法地恢复 0 entry 或抛异常）
- [ ] `./mvnw test -pl nop-stream/nop-stream-rocksdb -am` 通过
- [ ] `ai-dev/design/nop-stream/state-management-design.md` 记录 range restore 机制（两条路径：全量 JSON in-memory 过滤 + 增量 SST range scan；Stage 31 deferred 收口证据）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - rescale 端到端验证（parallelism 4↔16）+ executor 能力确认

Status: planned
Targets: `GraphModelCheckpointExecutor`（savepoint/restore 路径）；`nop-stream-runtime` recovery/E2E 测试目录

- Item Types: `Proof`

- [ ] **executor 能力确认/enabling**：核实 `GraphModelCheckpointExecutor` savepoint→restore 路径支持 keyed 作业在 parallelism>1 下运行、且允许跨 savepoint 改变 parallelism（Phase 1 的 dispatch 重构是 enabling 的核心）。若 `EmbeddedDistributedExecutor`/既有 E2E harness 不支持 parallelism>1 keyed savepoint/restore，则 Phase 1 dispatch 重构已提供该能力；本 item 验证而非新建独立 executor
- [ ] E2E 测试 A：`parallelism=4` 跑 keyed 作业 → savepoint → `parallelism=16` restore，断言所有 key 的聚合结果与 `parallelism=16` 从头跑的等价作业一致（无 key 丢失/重复）
- [ ] E2E 测试 B：`parallelism=16 → savepoint → parallelism=4` restore，同样断言结果等价
- [ ] 两测试覆盖 memory 与 rocksdb 两后端
- [ ] 断言 restore 后每个 subtask 只持有属于自己的 KeyGroupRange 的状态（切片正确性，非全量副本）

Exit Criteria:

- [ ] `parallelism=4→16` 与 `16→4` 两方向 E2E 测试均通过（memory + rocksdb）
- [ ] **端到端验证**：从 `env.addSource().keyBy()` 到 sink 输出，rescale 前后聚合结果一致；切片粒度正确（per-subtask range）
- [ ] **Anti-Hollow**：range restore + per-subtask slice 在运行时真实生效（不是"恢复全量然后假装切片"）——测试断言单个 subtask backend 的 key 数量 = 其 KeyGroupRange 内的 key 数量
- [ ] `TestStateShardRescale.java` 既有断言不回归
- [ ] `./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-rocksdb,nop-stream/nop-stream-runtime -am` 通过
- [ ] `ai-dev/design/nop-stream/checkpoint-design.md` §8.5 rescale 规则更新为 KeyGroupRange 语义
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [ ] KeyGroupRange 局部恢复落地（memory + rocksdb），有 focused test
- [ ] Stage 31 deferred「Key-group range SST reading」收口（range 读取交付，有真实消费者）
- [ ] `TaskEpochSnapshot.shards` 生产路径非空填充
- [ ] `parallelism=4→16`、`16→4` rescale E2E 通过
- [ ] restore 不存在静默全量 fallback（或 fallback 显式记录）；增量 checkpoint restore seam 修复
- [ ] Owner docs（`checkpoint-design.md` §8.5、`state-management-design.md`）同步到 live baseline
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：range restore + per-subtask slice 运行时真实生效；无空方法体/静默跳过
- [ ] `./mvnw compile -pl nop-stream/nop-stream-core,nop-stream/nop-stream-rocksdb,nop-stream/nop-stream-runtime -am`
- [ ] `./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-rocksdb,nop-stream/nop-stream-runtime -am`
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <本 plan> --strict` 退出码 0
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream --severity high` 退出码 0
- [ ] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

（执行中按需填写；预期 unaligned+rescale → Stage 47；跨 JVM restore → Stage 40）

## Non-Blocking Follow-ups

- `maxParallelism` 变化的显式迁移（Stage 37）
- 跨 JVM 状态文件传输（Stage 40）
- unaligned checkpoint + rescale 叠加（Stage 47）

## Closure

Status Note: (待 closure 时填写)
Completed: (待填写)

Closure Audit Evidence:
(待独立 closure-audit 后填写)
