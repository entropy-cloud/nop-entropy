# Checkpoint, State Backend & CEP State Correctness

> Plan Status: active
> Last Reviewed: 2026-08-04
> Draft Review: independent sub-agent review passed (no Blockers; 1 Major upsert-approach + Minors addressed; verdict YES). Session ses_036116ff3ffe081vHnTZrpQuCa.
> Source: `ai-dev/audits/nop-stream-production/2026-08-02-2107-multi-audit-nop-stream-production.md` (P1 incremental restore fail-fast gap, P1 incremental persist ref-count leak, P1 JdbcCheckpointStorage PostgreSQL upsert); `ai-dev/audits/nop-stream-production/2026-08-02-2107-open-audit-nop-stream-production.md` (AR-1 [P1] CEP SharedBufferAccessor stack desync)
> Related: Execution order `{2}` of 3 — depends on Plan {1}（恢复路径竞态修复后，checkpoint/state 正确性才能在稳定 recovery 下验证）。

## Purpose

收口检查点与状态后端四项状态正确性缺陷：增量 restore 跳过 `keyLayoutVersion` fail-fast（静默状态损坏）、增量 persist 在存储失败时永久泄漏 SST 段引用计数（磁盘单调增长）、JdbcCheckpointStorage 的 INSERT-then-UPDATE 在 PostgreSQL 上不可用（design §9.1 声称生产级）、CEP `SharedBufferAccessor.releaseNode` 并行栈失配（refcount 损坏或 over-release 崩溃）。这四项共同威胁 checkpoint 完整性与 CEP 匹配正确性。

## Current Baseline

经 2026-08-04 live repo 核对（引用与两份审计一致，已二次确认）：

- **P1 增量 restore 缺 fail-fast**：`RocksDBKeyedStateBackend.restoreState`（`:772-791`）。两个增量分支（`:775-777` marker 为 `IncrementalSnapshotResult`；`:779-787` marker 为 Map 经 BeanTool 重建）均直接 `restoreIncremental(result)` 后 `return`，未调用 `RocksDBKeyEncoder.verifyKeyLayoutVersion(snapshotData, true)`。该 helper 存在于 `RocksDBKeyEncoder.java:164`，文档要求增量路径强制 fail-fast；全量分支 `RocksDBSnapshotSerDe.restoreState`（`:416`）以 `incremental=false` 调用。`snapshotIncremental` 以 `keyLayoutVersion=2` 盖章。legacy v1-layout 增量 SST 在 v2 encoder 下会被按错误字节范围扫描 → 静默数据丢失。
- **P1 增量 persist 引用计数泄漏**：`CheckpointCoordinator.executeIncrementalPersistAsync`（`:581-624`）。`buildAndMaterializeSegments`（步骤1）在持久化 `CompletedCheckpoint`/`EpochManifest` **之前** 就对每个 SST handle 调用 `sharedStateRegistry.register(handle)`。存储失败时 `onCompletePersistFailure`（`:783-793`）不 unregister；`checkpointSegments.put`（`:624`，步骤4）仅成功分支执行，故后续 `gcSegmentsForCheckpoint(checkpointId)` 找不到条目直接返回 — 引用计数搁浅，物理文件直到 coordinator 重启重建 registry 才能回收。
- **P1 PostgreSQL upsert 不兼容**：`JdbcCheckpointStorage.storeCheckPoint`（`:96-119`）在单事务内 `INSERT`，捕获异常后若 `isDuplicateKeyException` 则在同一事务内 `UPDATE`。PostgreSQL 文档明确：事务内任何错误使事务进入 "aborted" 状态，后续语句全部失败；`isDuplicateKeyException`（`:665-680`）按类名/消息模式匹配，误分类风险叠加。design §9.3（`checkpoint-design.md:1225`）声称 `JdbcCheckpointStorage` 生产环境。同样模式出现在 `:324-348`、`:514-537`。**注**：平台 `IUpsertHandler` 仅 `MysqlUpsertHandler` 实现、API 过薄（`buildUpsert(tableName, columnNames)` 无 conflict-target/SET 列/WHERE），不满足三张复合键 checkpoint 表需求，故本 plan 采用 **`JdbcCheckpointStorage` 内联方言分支 SQL**（不扩展 `nop-dao` SPI、不跨模块）。
- **P1 CEP SharedBufferAccessor 栈失配**：`SharedBufferAccessor.releaseNode`（`:258-303`）。两个栈 `nodesToExamine`/`versionsToExamine` lockstep 推入（`:262-263` 初始化、`:289-290` 每条释放边）。已访问分支（`:267-270`）遵守"每次迭代弹 1 node + 1 version"不变量；**`curBufferNode == null` 分支（`:274-276`）`continue` 不弹 version**。此后每次迭代把错误 `DeweyNumber` 配对错误 `NodeId`，损坏无关匹配的 refcount 或漏释放边（内存泄漏），最终 `versionsToExamine.pop()`（`:278`）可能 `EmptyStackException`，或触发 `Lockable.release()` 的 over-release 守卫崩溃算子。`visited` 是每次 `releaseNode` 调用局部（`:261`），故 call N 的 `removeEntry`（`:297`）使 call N+1 在重叠子图上观察到 null — `followedByAny` + skip 策略必然产生。

## Goals

- 增量 restore 在两条分支上均调用 `verifyKeyLayoutVersion(snapshotData, true)`，legacy/absent 版本的 SST fail-fast 抛 `ERR_STREAM_STATE_ERROR`。
- 增量 persist 存储失败时回收已注册的 SST 段引用计数（unregister 零引用项），或将 register 推迟到两次存储写入成功之后。
- JdbcCheckpointStorage 采用方言感知的 upsert（PostgreSQL `ON CONFLICT` / MySQL `ON DUPLICATE KEY` / 通用 `MERGE`），消除事务内 INSERT-then-UPDATE 模式。
- CEP `releaseNode` null 分支在 `continue` 前弹出 version，恢复栈 lockstep 不变量。
- 每项配针对性回归测试（含 E2E / 数据库集成测试）。

## Non-Goals

- 控制面/运行时恢复竞态（Plan {1}）。
- SPI / 文档 drift、`_module` 标记、空心测试（Plan {3}）。
- 全部 P2 项（native handle 泄漏、retention 跨 pipeline、content-hash 校验、增量 native checkpoint 目录磁盘泄漏、JDBC retained-manifest override、error-handling 二层违规、doc/javadoc rot、低价值测试 — 均归 backlog）。

## Scope

### In Scope

- `RocksDBKeyedStateBackend.java`：增量 restore 两条分支补 `verifyKeyLayoutVersion(..., true)`。
- `CheckpointCoordinator.java`：`executeIncrementalPersistAsync` 失败分支 unregister 已注册段（或推迟 register）。
- `JdbcCheckpointStorage.java`：三处 INSERT-then-UPDATE 改为方言感知 upsert + PostgreSQL 集成测试。
- `SharedBufferAccessor.java`：`releaseNode` null 分支补 `versionsToExamine.pop()`。
- 每项的回归测试（含 CEP branching + skip 策略复现测试、PostgreSQL upsert 集成测试）。

### Out Of Scope

- RocksDB native handle 泄漏（P2，归 backlog）。
- 增量 native checkpoint 目录磁盘泄漏（P2，归 backlog）。
- `RocksDBIncrementalRestore` 段内容 hash 校验（P2，归 backlog）。
- `JdbcCheckpointStorage.loadRetainedEpochManifests` 未 override（P2，归 backlog）。
- CEP 状态类缺 `Serializable`（AR-3 [P2]，归 backlog）。

## Execution Plan

### Phase 1 - 增量 restore fail-fast 补齐

Status: planned
Targets: `nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/core/common/state/backend/rocksdb/RocksDBKeyedStateBackend.java`, `nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/core/common/state/backend/rocksdb/RocksDBKeyEncoder.java`

- Item Types: `Fix | Proof`

- [ ] 在 `restoreState`（`:772-791`）两条增量分支（marker 为 `IncrementalSnapshotResult`、marker 为 Map 经 BeanTool 重建）调用 `restoreIncremental(result)` **之前**，分别调用 `RocksDBKeyEncoder.verifyKeyLayoutVersion(snapshot.getStateData(), true)`

Exit Criteria:

- [ ] 两条增量分支在仓库中可观察到 `verifyKeyLayoutVersion(..., true)` 调用，位于 `restoreIncremental` 之前
- [ ] 新增回归测试：构造 `IncrementalSnapshotResult`（或 Map 形式）令 `keyLayoutVersion=1` 或 absent，**经 `restoreState` 入口**调用（非直接调 helper，以验证接线——现有 `TestRocksDBKeyGroupPrefixLayout.java:153,162` 直接调 helper，不覆盖 `restoreState` 接线），断言抛 `ERR_STREAM_STATE_ERROR`
- [ ] **无静默跳过**：legacy/absent 版本显式 fail-fast 抛异常，不静默按错误范围扫描
- [ ] `ai-dev/design/nop-stream/state-management-design.md` §5.3 已要求此 fail-fast（无需新增文档）；若实现与文档措辞需对齐则更新，否则 `No owner-doc update required`
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 增量 persist 引用计数泄漏修复

Status: planned
Targets: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java`

- Item Types: `Fix | Proof`

- [ ] 在 `executeIncrementalPersistAsync`（`:581-624`）的存储失败分支（`storeCheckPoint` 抛错 / `storeEpochManifest` 抛错），遍历 `segments` 调用 `sharedStateRegistry.unregister(seg.getPath())`（该 API 返回零引用 handle 列表，见 `SharedStateRegistry.java:38-43`），对返回的零引用 handle 调用 `segmentStore.discardSegment(...)` 物理回收 SST 文件；或改为将 register 推迟到两次存储写入成功之后（拆分 materialize 与 register）

Exit Criteria:

- [ ] 失败分支在仓库中可观察到 unregister 路径（或 register 已移到成功分支之后），失败不再永久搁浅引用计数
- [ ] 新增回归测试：触发增量 checkpoint 存储失败（mock `checkpointStorage.storeCheckPoint` 抛异常），断言 `sharedStateRegistry` 中对应段引用计数回落到失败前值，且零引用物理 SST 文件已被 `segmentStore.discardSegment` 回收（可由 `unregister` 返回的 handle 列表验证）
- [ ] **接线验证**：测试验证 `onCompletePersistFailure` 失败路径确实触发 unregister + 物理回收，而非仅靠 `gcSegmentsForCheckpoint`（其只读成功分支的 `checkpointSegments`）
- [ ] `No owner-doc update required`（纯内部引用计数/磁盘回收修正，checkpoint-design §9 契约不变）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - JdbcCheckpointStorage 方言感知 upsert

Status: planned
Targets: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/storage/JdbcCheckpointStorage.java`

- Item Types: `Fix | Proof`

- [ ] 将三处（`:96-119`、`:324-348`、`:514-537`）单事务 INSERT-then-UPDATE 改为**内联方言分支 upsert**（`JdbcCheckpointStorage` 内）：依据 `jdbcTemplate.getDialectForQuerySpace(querySpace)` 的数据库类型选择 PostgreSQL `INSERT ... ON CONFLICT (job_id,pipeline_id,checkpoint_id) DO UPDATE SET ...` / MySQL `ON DUPLICATE KEY UPDATE ...` / 通用先 INSERT 后捕获再 UPDATE（但分开事务/SAVEPOINT 隔离）；事务内不再出现"INSERT 抛错后同事务 UPDATE"模式。**不扩展 `nop-dao` IUpsertHandler SPI**（其 API 过薄、仅 MySQL 实现），避免跨模块变更
- [ ] （可选加固）用 SAVEPOINT 包裹 INSERT 以隔离失败，若 upsert 方言判断复杂

Exit Criteria:

- [ ] 三处 upsert 在仓库中可观察到方言分支或等价 upsert 构造，事务内不再出现"INSERT 抛错后同事务 UPDATE"模式
- [ ] 新增 PostgreSQL 集成测试：触发 duplicate-key 分支（重试/HA failover fencing 重叠/savepoint 重存同 id），断言 `storeCheckPoint` 在 PostgreSQL（H2 PostgreSQL 兼容模式或真实 PG）上成功完成而非事务 aborted
- [ ] **端到端验证**：一次 checkpoint store→load 往返在 duplicate-key 场景下成功（覆盖控制面 checkpoint 持久化路径）
- [ ] `ai-dev/design/nop-stream/checkpoint-design.md` §9.3（`ICheckpointStorage 接口`，生产环境 JDBC 声明所在）记录 upsert 方言矩阵（PostgreSQL ON CONFLICT / MySQL ON DUPLICATE KEY）作为 `JdbcCheckpointStorage` 实现约定
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - CEP SharedBufferAccessor 栈 lockstep 修复

Status: planned
Targets: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/sharedbuffer/SharedBufferAccessor.java`

- Item Types: `Fix | Proof`

- [ ] 在 `releaseNode`（`:274-276`）null 分支 `continue` 前补 `versionsToExamine.pop()`，恢复"每次迭代弹 1 node + 1 version"不变量

Exit Criteria:

- [ ] null 分支在仓库中可观察到 `versionsToExamine.pop()` 在 `continue` 之前
- [ ] 新增回归测试（`TestSharedBufferExtended` 或类似）：释放一个 node 后，再发起第二次 `releaseNode` 其遍历到达已被 `removeEntry`（`:297`）的条目（getEntry 返回 null），断言不抛 `EmptyStackException`、不触发 `Lockable over-release` 守卫、后续 refcount 正确（现有 `testReleaseNodeContinuesAfterNullIntermediateNode` **不** 覆盖此重叠释放场景，需新增）
- [ ] **端到端验证**：用 `followedByAny` + `SKIP_TO_FIRST`/`SKIP_PAST_LAST_EVENT` 的 branching pattern 跑一次完整匹配，断言 SharedBuffer 不出现 unbounded 增长或 over-release 崩溃（open-audit AR-1 建议的复现形态）
- [ ] **无静默跳过**：null 分支显式 pop（不再是吞掉 version 的静默 continue）
- [ ] `No owner-doc update required`（纯内部 NFA 遍历正确性修正，cep-design 契约不变）
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [ ] 四个 in-scope 确认 live defect（增量 restore fail-fast / 增量 persist ref-count 泄漏 / PostgreSQL upsert / CEP 栈失配）均已修复
- [ ] 每项均有针对性测试（含 PostgreSQL 集成测试与 CEP branching E2E）
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect
- [ ] 受影响的 owner docs 已同步，或明确写明 `No owner-doc update required`
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：closure audit 已验证 fail-fast / unregister / upsert / pop 在运行时确实生效，端到端路径连通，无静默跳过
- [ ] `./mvnw compile -pl nop-stream -am -T 1C`
- [ ] `./mvnw test -pl nop-stream -am -T 1C`
- [ ] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

（暂无；执行中如出现延期项，须写明 Classification / Why Not Blocking Closure / Successor Required。）

## Non-Blocking Follow-ups

- 本 plan 的 P2 邻接项（native handle 泄漏、增量 native 目录磁盘泄漏、段内容 hash 校验、JDBC retained-manifest override、CEP 状态类 Serializable）已归入 roadmap Follow-up Backlog，不阻塞 closure。

## Closure

Status Note: （关闭时填写）
Completed: YYYY-MM-DD

Closure Audit Evidence:

- Reviewer / Agent: （关闭时填写）
- Evidence: （关闭时填写，含每条 Exit Criterion / Closure Gate 验证结果、check-plan-checklist 与 scan-hollow 退出码）

Follow-up:

- （关闭时填写；confirmed live defect 不得出现在这里）
