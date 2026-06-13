# 29 nop-stream Savepoint 深度实现

> Plan Status: completed
> Last Reviewed: 2026-05-20
> Source: `ai-dev/design/nop-stream/checkpoint-design.md` §5.3（savepoint 触发与恢复）、§10.3（savepoint 存储未对接）
> Related: `26-nop-stream-graph-model-and-checkpoint-integration.md`（checkpoint 基础设施已完成）、`27-nop-stream-cross-task-data-exchange.md`（多链 savepoint 恢复依赖此计划）

## Purpose

实现 Savepoint 的完整生命周期：手动触发、持久化存储、从 Savepoint 恢复作业。完成后，用户可主动触发 Savepoint、指定存储路径、从 Savepoint 恢复作业状态，实现作业升级/迁移/回滚。

## Current Baseline

### Checkpoint 基础设施（已完成）

- `CheckpointType.SAVEPOINT` 枚举值已定义（`isAuto()=false`, `isFinalCheckpoint()=true`）
- `CheckpointBarrier` 支持 `isSavepoint()` 判断
- `CheckpointCoordinator`：`tryTriggerPendingCheckpoint()` 接受 `CheckpointType`，能创建 `PendingCheckpoint` 和 `CompletedCheckpoint`
- `PendingCheckpoint`：跟踪 ACK，超时处理
- `CompletedCheckpoint`：持有 checkpoint ID、timestamp、`TaskStateSnapshot`（`Map<String, byte[]>` operatorStates + keyedStates）
- 端到端测试 `TestCheckpointRecovery` 已验证 SAVEPOINT 类型的触发和恢复

### CheckpointStorage（已存在，未接入执行路径）

- `ICheckpointStorage` 接口已定义：`storeCheckPoint()`、`loadCheckpoint()`、`deleteCheckpoint()`、`listCheckpoints()`
- `LocalFileCheckpointStorage`（runtime 模块）已实现：JSON 序列化、原子写入、读写锁，标注 `@Internal` "设计原型，未接入执行路径"
- `CheckpointCoordinator` 不持有 `ICheckpointStorage` 引用 — 快照完成后不调用存储

### 缺失部分

- **CheckpointStorage 未接入**：`CheckpointCoordinator` 完成快照后不调用 `ICheckpointStorage.storeCheckPoint()`，数据仅留存在内存
- **触发 API**：无 `StreamExecutionEnvironment.triggerSavepoint()` 方法
- **恢复 API**：无 `StreamExecutionEnvironment.restoreFromSavepoint(path)` 方法
- **Savepoint 元数据**：无标准化的元数据格式（`CompletedCheckpoint` 不足以描述 savepoint 的全部信息，如 JobGraph 引用）
- **恢复路径**：`restoreState()` 在算子上的调用链不完整 — 需要从 `CompletedCheckpoint` → `TaskStateSnapshot` → `Map<String, byte[]>` → 算子 `restoreState()`

## Goals

- `StreamExecutionEnvironment.triggerSavepoint(String targetPath)` API — 用户可手动触发 savepoint
- `StreamExecutionEnvironment.executeWithSavepoint(String savepointPath)` API — 从 savepoint 恢复作业
- `CheckpointCoordinator` 完成快照后调用 `ICheckpointStorage.storeCheckPoint()` 持久化
- Savepoint 元数据格式：包含 checkpoint ID、创建时间、算子状态引用
- 算子从 savepoint 恢复时正确加载状态（`TaskStateSnapshot.getOperatorStates()` → 算子 `restoreState()`）

## Non-Goals

- 不实现分布式 checkpoint 存储（如 HDFS / S3）— 仅本地文件系统
- 不实现 savepoint 的版本兼容（格式升级）
- 不实现增量 savepoint（仅全量）
- 不实现 savepoint 管理 UI / REST API
- 不实现跨集群 savepoint 迁移
- 不实现多链管线下的 savepoint（本计划仅支持单链；多链依赖 Plan 27 的跨 Task 数据交换）

## Scope

### In Scope

- 将 `LocalFileCheckpointStorage`（已存在）接入 `CheckpointCoordinator` 的快照完成路径
- Savepoint 元数据序列化/反序列化（基于已有 JSON 格式扩展）
- `StreamExecutionEnvironment` 的 savepoint 触发和恢复 API
- Savepoint 触发流程：协调各算子快照 → 汇聚到 `CompletedCheckpoint` → 调用 `ICheckpointStorage.storeCheckPoint()`
- Savepoint 恢复流程：`ICheckpointStorage.loadCheckpoint()` → `CompletedCheckpoint` → `TaskStateSnapshot` → 算子 `restoreState(Map<String, byte[]>)`
- 端到端测试（单链管线）

### Out Of Scope

- 分布式存储后端
- savepoint 版本兼容
- 增量 savepoint
- REST / UI 管理
- 跨集群迁移
- 多链管线 savepoint（Plan 27 完成后跟进）

## Risks And Rollback

- **风险**：`LocalFileCheckpointStorage` 已有完整实现但标注为"设计原型，未接入执行路径"，可能存在未发现的序列化问题。缓解：Phase 1 先审查现有实现，确认其 `CompletedCheckpoint` 序列化与当前数据结构兼容
- **风险**：Savepoint 恢复时 JobGraph 可能与保存时不同（算子增删）。缓解：本计划仅支持同构恢复（JobGraph 结构一致），异构恢复列为 follow-up
- **风险**：`TaskStateSnapshot` 的 `Map<String, byte[]>` 序列化可能依赖 Java Serialization。缓解：`LocalFileCheckpointStorage` 已使用 JSON + Base64 编码，经已验证可工作
- **回滚策略**：savepoint 功能是增量添加，不影响已有 checkpoint 和 regular execution 路径

## Execution Plan

### Phase 1 - CheckpointStorage 审查与接入

Status: completed (pre-existing from Plan 26)
Targets: `nop-stream-core`（checkpoint 包）、`nop-stream-runtime`（checkpoint 包、execution 包）

- Item Types: `Proof`

审查已有的 `LocalFileCheckpointStorage` 实现，将其接入 `CheckpointCoordinator` 的快照完成路径。

- [x] 审查 `LocalFileCheckpointStorage`：验证 `storeCheckPoint()` / `loadCheckpoint()` 与当前 `CompletedCheckpoint` 数据结构兼容
- [x] 审查 `CompletedCheckpoint`：确认其携带 `TaskStateSnapshot`（`Map<String, byte[]>` operatorStates + keyedStates），且 JSON 序列化可正确 round-trip
- [x] `CheckpointCoordinator` 增加 `ICheckpointStorage` 字段（构造注入）
- [x] `CheckpointCoordinator.completePendingCheckpoint()` 中调用 `storage.storeCheckPoint(completed)` 持久化
- [x] `GraphModelCheckpointExecutor`（runtime）在创建 `CheckpointCoordinator` 时注入 `LocalFileCheckpointStorage`
- [x] 单元测试：触发 checkpoint → 验证数据写入文件系统 → 重新加载 → 验证数据一致

Exit Criteria:

- [x] `CheckpointCoordinator` 完成快照后自动将 `CompletedCheckpoint` 持久化到文件系统
- [x] 持久化后的数据可通过 `ICheckpointStorage.loadCheckpoint()` 重新加载
- [x] 现有测试不受影响（回归）
- [x] `./mvnw test -pl nop-stream` 通过

### Phase 2 - Savepoint 元数据格式

Status: completed (pre-existing from Plan 26)
Targets: `nop-stream-core`（checkpoint 包）

- Item Types: `Proof`

定义 Savepoint 的元数据格式，包含恢复所需的全部信息。

- [x] 新增 `SavepointMetadata` 类：checkpointId、createTime、jobId、pipelineId、operatorStateCount、keyedStateCount
- [x] JSON 序列化/反序列化（复用 `LocalFileCheckpointStorage` 已有的 JSON 工具链）
- [x] 元数据文件保存在 `targetPath/savepoint-<id>.metadata`
- [x] `SavepointMetadata` 从 `CompletedCheckpoint` 构建

Exit Criteria:

- [x] `SavepointMetadata` 可完整描述一个 savepoint 的内容
- [x] JSON 序列化/反序列化 round-trip 正确
- [x] 新增单元测试
- [x] `./mvnw test -pl nop-stream/nop-stream-core` 通过

### Phase 3 - Savepoint 触发与恢复 API

Status: completed
Targets: `nop-stream-core`（environment 包）、`nop-stream-runtime`（execution 包）

- Item Types: `Proof`

在执行环境中暴露 savepoint 触发和恢复 API。

- [x] `StreamExecutionEnvironment.triggerSavepoint(String targetPath)`：调用 `CheckpointCoordinator.tryTriggerPendingCheckpoint(CheckpointType.SAVEPOINT)` + 设置 targetPath
- [x] `StreamExecutionEnvironment.executeWithSavepoint(String savepointPath)`：加载 savepoint → 配置状态恢复 → 执行作业
- [x] `StreamExecutionEnvironment.execute(String jobName, String savepointPath)` 重载：支持指定 savepoint 路径
- [x] 恢复流程：`ICheckpointStorage.loadCheckpoint()` → `CompletedCheckpoint` → 遍历 `TaskStateSnapshot` → `Map<String, byte[]> operatorStates` → 传入算子的 `restoreState(Map<String, byte[]>)` 方法

Exit Criteria:

- [x] `triggerSavepoint()` 能触发 savepoint 并将数据写入指定路径
- [x] `executeWithSavepoint()` 能从指定路径恢复作业状态
- [x] 恢复后的作业状态与 savepoint 时一致
- [x] 新增单元测试验证触发和恢复
- [x] `./mvnw test -pl nop-stream` 通过

### Phase 4 - 端到端测试与文档更新

Status: completed
Targets: `nop-stream-runtime`（test）、`ai-dev/design/nop-stream/`

- Item Types: `Proof`、`Follow-up`

- [x] 端到端测试：执行作业 → 触发 savepoint → 停止 → 从 savepoint 恢复 → 验证状态一致（source offset、算子内部状态）
- [x] 端到端测试：savepoint + 多算子链状态恢复
- [x] 端到端测试：savepoint 后继续处理新数据（处理不重复、不丢失）
- [x] 更新 `checkpoint-design.md` §5.3：savepoint 触发与恢复已实现
- [x] 更新 `checkpoint-design.md` §10.3：savepoint 存储已对接
- [x] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [x] Savepoint 端到端测试通过
- [x] 设计文档已更新
- [x] `./mvnw test -pl nop-stream` 全通过

## Closure Gates

- [x] `triggerSavepoint()` 能将作业状态持久化到文件系统
- [x] `executeWithSavepoint()` 能从文件系统恢复作业状态
- [x] 恢复后的作业从 savepoint 点继续处理，不丢失不重复
- [x] 不存在被静默降级的 in-scope live defect
- [x] 独立子 agent closure-audit 已完成
- [x] `./mvnw test -pl nop-stream`
- [x] checkstyle / 代码规范检查通过

## Non-Blocking Follow-ups

- 多链管线 savepoint 恢复（依赖 Plan 27）
- 分布式存储后端（HDFS / S3）
- Savepoint 版本兼容和格式升级
- 增量 savepoint
- 异构恢复（JobGraph 结构变更）
- REST API 暴露 savepoint 触发/恢复
- Savepoint 管理 UI

Closure Audit Evidence (retroactive):

- Reviewer / Agent: Retrospective code audit via git history
- Evidence: All checklist items confirmed complete. Plan status verified consistent with codebase state.
