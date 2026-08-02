# 31 — Incremental Checkpoint (SST Sharing)

> Plan Status: active
> Last Reviewed: 2026-08-02
> Source: `ai-dev/backlog/nop-stream-production-roadmap.md` Stage 31; G45 + G30(续); G33 deferred from Stage 19
> Related: Stage 30 (`2026-08-02-0955-1-rocksdb-state-backend`); Stage 19 (`2026-07-25-2300-1-checkpoint-concurrency` G33 deferral)
> **Blocked On: Stage 30 (`2026-08-02-0955-1`) — Phase 2+ 不可开始，直到 `RocksDBKeyedStateBackend`、`nop-stream-rocksdb` 模块、`rocksdbjni` 依赖全部落地。Phase 1（SharedStateRegistry）可独立先行。**

## Purpose

基于 RocksDB native checkpoint 实现 SST 文件内容寻址与跨 checkpoint 共享。引入 `SharedStateRegistry` 引用计数，使增量 checkpoint 只传输新增/变更的 SST 文件，subsumption 时清理不再引用的文件。

## Current Baseline

- Stage 30（前置）交付 `RocksDBKeyedStateBackend`（`nop-stream-rocksdb` 模块），全量扫描快照产出 `StateSnapshot`。Stage 30 接口假设：`RocksDBKeyedStateBackend` 暴露底层 `RocksDB` 实例引用（或等价的 checkpoint 能力）+ `incrementalCheckpointEnabled` 配置点。**如 Stage 30 实际接口与此假设不符，本 plan 需在 Stage 30 landing 后修订**
- `EpochManifest.segments`（`nop-stream-core/.../checkpoint/EpochManifest.java:34`）字段类型 `List<StateSegmentDescriptor>`，**构造器将 null 转为 `emptyList()`**——`CheckpointCoordinator.buildEpochManifest`（`:887-899`）传 null → runtime 为空列表
- `StateSegmentDescriptor`（`nop-stream-core/.../checkpoint/StateSegmentDescriptor.java:19-23`）类已存在但未使用——字段：`segmentType`/`path`/`codec`（默认 `"json"`）/`checksum`/`schemaVersion`
- `CheckpointSerDe.serializeEpochManifest`（`:185-197`）在 `segments` 非空时序列化（gate 为 `!isEmpty()` 非 `!= null`）；反序列化在 `:268-280`。当前永远为空
- `ICheckpointStorage`（`nop-stream-core/.../checkpoint/storage/ICheckpointStorage.java`）**无 segment 级 API**——每个 checkpoint 作为独立 blob 存储
- `CheckpointCoordinator.cleanupOldCheckpoints`（`:754-768`）按 `maxRetainedCheckpoints` 删除 checkpoint blob，**无引用计数 segment GC**；操作对象为 `CompletedCheckpoint`（非 `EpochManifest`）
- `CheckpointCoordinator` 构造器接收 `ICheckpointStorage`，无 `ISegmentStore` 参数
- Stage 18 异步两阶段 snapshot pipeline：段 1 ACK 线程 under monitor 构建 `EpochManifest`（`buildEpochManifest`，`:435`）→ 段 2 persist executor 做 I/O → 段 3a/3b 重新获取 monitor 完成/失败
- Stage 19 将 G33 `SharedStateRegistry` 显式延后至此 Stage
- `SharedStateRegistry` / `SharedStateHandle` / `StreamStateHandle` 在源码中**不存在**

## Goals

- 使用 RocksDB `Checkpoint` JNI API 创建物理一致性快照，SST 文件内容寻址（SHA-256）
- `SharedStateRegistry` 引用计数：多 checkpoint 共享同一 SST 文件，subsumption 时清理零引用文件
- `EpochManifest.segments` + `StateSegmentDescriptor` 从 dormant 状态激活——增量 manifest 携带 SST 引用
- 增量 checkpoint 显著快于全量（大状态下只传输新增 SST）

## Non-Goals

- Key-Group 级 SST range 读取（Stage 35）
- Unaligned checkpoint（Stage 43）
- 非 RocksDB 后端的增量快照（memory backend 无 SST 概念）
- 跨 JVM SST 文件传输（Stage 40）

## Scope

### In Scope

- 新建 `io.nop.stream.core.common.state.backend.rocksdb.incremental` package（`nop-stream-rocksdb` 模块内）
- `SharedStateRegistry` 接口 + 内存实现（引用计数）
- `SharedStateHandle` / `StreamStateHandle`（content-addressed SST 文件句柄）
- `RocksDBIncrementalSnapshotStrategy`：使用 `org.rocksdb.Checkpoint` JNI API 创建物理快照
- `ISegmentStore` side-channel 接口 + `LocalFileSegmentStore` 实现
- `CheckpointCoordinator` 接线：持有 registry + segment store；`buildEpochManifest` 填充 segments
- subsumption GC：ref-counted segment cleanup
- `checkpoint-design.md` 更新

### Out Of Scope

- RocksDB backend 本身（Stage 30 前置）
- Key-Group range SST 读取（Stage 35）
- JDBC segment 存储（`JdbcSegmentStore`——后续优化）
- 远程/对象存储 SST 共享（跨 JVM 后）

### Design Decisions

- **RocksDB Checkpoint API**：使用 `org.rocksdb.Checkpoint`（`new Checkpoint(db); cp.createCheckpoint(path, logSizeForFlush)`），**非** `RocksDB.checkpoint()`（不存在的方法）
- **非 SST 文件持久化**：RocksDB checkpoint 目录含 SST + WAL(`.log`) + `MANIFEST-*` + `OPTIONS-*` + `CURRENT` + `IDENTITY`。只有 SST 文件做内容寻址共享；非 SST 文件（WAL/MANIFEST 等）per-checkpoint 独立存储于 `{baseDir}/checkpoints/{checkpointId}/non-sst/`，restore 时完整恢复
- **内容寻址**：SST 文件以 SHA-256(content) 为唯一标识；相同内容跨 checkpoint 复用
- **Registry 生命周期**：`SharedStateRegistry` 由 `CheckpointCoordinator` 持有，job 级生命周期。registry 仅管理 handle + ref-count（内存操作），**不直接删除文件**；文件删除委托 `ISegmentStore.discardSegment`
- **ISegmentStore wiring**：`CheckpointCoordinator` 新增 `setSegmentStore(ISegmentStore)` setter（不修改构造器签名以保持向后兼容）。`incrementalCheckpointEnabled=true` 但 `segmentStore == null` 时抛 `UnsupportedOperationException`（非静默 fallback，遵守 Rule #24）。side-channel 方式（非侵入 `ICheckpointStorage`）的 rationale：`ICheckpointStorage` 的核心契约是 checkpoint-level blob 存储，segment 级操作是增量 checkpoint 专属关注点，混入会违反接口隔离
- **EpochManifest 构建 timing**：`buildEpochManifest` 当前在段 1（ACK 线程 under monitor）构建不可变 manifest。segments 计算涉及 RocksDB checkpoint 创建 + SHA-256（慢 I/O），不能在段 1 做。**Decision：重构为段 2（persist executor）计算 segments 后构建 manifest**。段 1 仅做 CAS + 标记 COMPLETED；段 2 做 checkpoint → SHA-256 → registry register → 构建 manifest（含 segments）→ 持久化。此重构在 `checkpoint-design.md` §2.2 async persist note 中文档化
- **Segment GC 数据源**：`CheckpointCoordinator` 维护内存 `Map<Long, List<StateSegmentDescriptor>>`（checkpointId → segments），在段 2 持久化成功后 under monitor 更新。`cleanupOldCheckpoints` 从此 map 获取 segment 列表调用 `registry.unregister`。`discardSegment` I/O **off-loaded 到 persist executor**（不在 monitor 下，避免 throughput 回退）
- **Task→Coordinator SST handle 数据流**：task 侧 `RocksDBKeyedStateBackend.snapshotState()` 在增量模式下产出 `IncrementalSnapshotResult`（含 SST content hashes + 非 SST 文件路径），经现有 ACK 机制携带到 coordinator（`TaskStateSnapshot.keyedStates` 中新增字段或专用 entry）。**Coordinator 从不直接操作 RocksDB 实例**——它在段 2 从 ACK 携带的 handle 列表调用 `registry.register`（去重）并构建 `EpochManifest.segments`。task 侧策略返回 raw handles，coordinator 侧做 registry 注册
- **Registry restart 恢复**：coordinator 启动/恢复时，从 `ICheckpointStorage.getLatestCheckpoints(jobId, maxRetained)` 加载 retained EpochManifests → 遍历每个 manifest 的 `segments` → 重新注册到 `SharedStateRegistry`（ref-count 重建）→ 重建 GC map。未在 retained manifest 中引用的 orphan segment 文件由启动时的一次性 cleanup 扫描处理（`segmentStore` 扫描 `shared-state/` 目录，删除不在 registry 中的文件）
- **Sync/incremental 互斥**：`incrementalCheckpointEnabled=true` 要求 `asyncSnapshotEnabled=true`。若两者冲突，coordinator 初始化时抛 `IllegalStateException`（增量 segments 计算涉及 RocksDB I/O + SHA-256，不能在 sync 路径的 monitor 下执行）
- **Dual ref-counting 统一**：`SharedStateRegistry` 是引用计数的唯一 source of truth（in-memory）。`ISegmentStore` **不做引用计数**——仅提供 `storeSegment`/`discardSegment`/`segmentExists`/`getSegmentPath`，由 registry 的 `discardUnreferenced` 返回的零引用 handle 驱动文件删除。store 侧无独立 ref-count，避免双重计数不一致

## Execution Plan

### Phase 1 — SharedStateRegistry + Content-Addressed SST Handles

Status: planned
Targets: `nop-stream-core/.../checkpoint/`（registry 接口 + impl 可放 core，无 RocksDB 依赖）

- Item Types: `Proof`

- [ ] `SharedStateHandle`：content-addressed SST 文件句柄（`contentHash`/`filePath`/`size`/`stateObjectId`）
- [ ] `StreamStateHandle`：逻辑状态引用（operatorId + stateName → 一组 SST handles）
- [ ] `SharedStateRegistry` 接口：`register(stateHandle)` 返回去重后的 canonical handle + increment count；`unregister(stateObjectId)` decrement count；返回 ref-count 降为 0 的 handle 列表供调用方 discard
- [ ] 内存实现 `SharedStateRegistryImpl`（`ConcurrentHashMap<contentHash, Entry<AtomicInteger, handle>>`）
- [ ] SST 文件 SHA-256 计算工具

Exit Criteria:

- [ ] `SharedStateRegistry` register 同一 contentHash 返回相同 handle，引用计数递增
- [ ] unregister 后引用计数递减；ref-count 降为 0 时 `unregister` 返回该 handle 供调用方 discard（registry 自身不做文件 I/O）
- [ ] 并发 register/unregister 不产生计数错误（多线程单元测试）
- [ ] **Test-Mandated Feature Rule**：registry 每个公共方法有对应单元测试
- [ ] `./mvnw test -pl nop-stream-core -am` 通过
- [ ] No owner-doc update required (internal infrastructure)

### Phase 2 — RocksDB Native Checkpoint + Incremental Snapshot Strategy

Status: planned（**blocked on Stage 30**）
Targets: `nop-stream-rocksdb/src/...`

- Item Types: `Proof`

- [ ] `RocksDBIncrementalSnapshotStrategy`：使用 `new Checkpoint(rocksDB); checkpoint.createCheckpoint(tempDir, logSizeForFlush)` 创建物理一致性快照（hard link SST 文件）
- [ ] 枚举 checkpoint 目录下 `.sst` 文件，计算 SHA-256，向 `SharedStateRegistry` 注册去重
- [ ] 非 SST 文件（WAL/MANIFEST/OPTIONS/CURRENT/IDENTITY）复制到 per-checkpoint 目录（`{checkpointDir}/non-sst/`）
- [ ] 产出 `IncrementalSnapshotResult`：引用的 SST handles 列表（来自 registry 去重后）+ 非 SST 文件路径 + metadata，不内联状态数据
- [ ] `RocksDBKeyedStateBackend` 增加配置项：`incrementalCheckpointEnabled`（默认 false）；启用时 `snapshotState()` 走增量策略

Exit Criteria:

- [ ] RocksDB `Checkpoint.createCheckpoint` 成功创建物理快照目录
- [ ] SST 文件 SHA-256 内容寻址正确
- [ ] **确定性去重测试**：disable auto-compaction + 手动 `flush` 后，连续两次 checkpoint（无状态变更）的 SST 集合相同，registry 去重生效（N+1 引用 N 的 SST handles 而非新建）
- [ ] 有状态变更时新增 SST 正确反映变更（新增 handle 不在之前 checkpoint 中）
- [ ] 非 SST 文件正确复制到 per-checkpoint 目录
- [ ] `incrementalCheckpointEnabled=false` 时退回 Stage 30 全量扫描路径（向后兼容）
- [ ] **接线验证**：`incrementalCheckpointEnabled=true` 时 `snapshotState()` 走增量策略而非全量扫描
- [ ] **Test-Mandated Feature Rule**：增量策略有独立测试（含确定性 setup）
- [ ] `./mvnw test -pl nop-stream-rocksdb -am` 通过

### Phase 3 — Storage Contract Extension + EpochManifest Segments Wiring

Status: planned（**blocked on Stage 30 + Phase 2**）
Targets: `nop-stream-core/.../checkpoint/storage/`, `nop-stream-core/.../checkpoint/EpochManifest.java`, `nop-stream-runtime/.../checkpoint/`

- Item Types: `Fix | Proof`

- [ ] 引入 `ISegmentStore` 接口：`storeSegment(Path sourceFile, String contentHash)`/`discardSegment(String contentHash)`/`segmentExists(String contentHash)`/`getSegmentPath(String contentHash)`（**不含 `referenceSegment`**——引用计数由 `SharedStateRegistry` 独占管理）
- [ ] `LocalFileSegmentStore` 实现：content-addressed 文件存储（`{baseDir}/shared-state/{contentHash-prefix}/{contentHash}.sst`），引用计数；`discardSegment` 真正删除文件
- [ ] 激活 `EpochManifest.segments`：增量 checkpoint 时填充 `List<StateSegmentDescriptor>`（segmentType=`rocksdb-sst`、path=contentHash、codec=`identity`、checksum=SHA-256、schemaVersion=1）
- [ ] **文档化 codec 值集**：`checkpoint-design.md` §2.6 记录 `codec` 取值（`json` | `identity`），restore 端按 `codec` 分支处理，未知值 fail-fast
- [ ] `CheckpointSerDe` segments 序列化路径（`:185-197`）验证非空 `segments` 正确 round-trip
- [ ] `CheckpointCoordinator` 新增 `setSegmentStore(ISegmentStore)` setter；`incrementalCheckpointEnabled=true` 但 `segmentStore == null` 时抛 `UnsupportedOperationException`

Exit Criteria:

- [ ] 增量 checkpoint 的 `EpochManifest` JSON 包含非空 `segments` 数组，每个 segment 有正确 `checksum`/`path`/`codec="identity"`
- [ ] `EpochManifest` serialize → deserialize round-trip 保留 segments 完整信息
- [ ] `LocalFileSegmentStore` 内容寻址正确：相同 contentHash 复用同一物理文件
- [ ] 非 segment-capable storage + `incrementalCheckpointEnabled=true` → `UnsupportedOperationException`（非静默 fallback）
- [ ] **Test-Mandated Feature Rule**：segment store 每个公共方法有测试
- [ ] `./mvnw test -pl nop-stream -am -T 1C` 通过

### Phase 4 — Coordinator Integration + End-to-End + Benchmark + Design Doc

Status: planned（**blocked on Phase 3**）
Targets: `nop-stream-runtime/.../checkpoint/CheckpointCoordinator.java`, `ai-dev/design/nop-stream/checkpoint-design.md`, `ai-dev/logs/`

- Item Types: `Proof | Follow-up`

- [ ] `CheckpointCoordinator` 持有 `SharedStateRegistry`（job 级生命周期）+ `ISegmentStore`
- [ ] **Config 校验**：`incrementalCheckpointEnabled=true` 但 `asyncSnapshotEnabled=false` → 初始化时抛 `IllegalStateException`
- [ ] **Registry restart 恢复**：coordinator 启动时从 retained EpochManifests 重建 registry ref-count + GC map；orphan segment 文件一次性 cleanup 扫描
- [ ] **重构 async persist pipeline**：段 1（ACK 线程 under monitor）捕获 `currentFingerprint` 到 local variable（保持 fingerprint-observation ordering）；段 2（persist executor）从 ACK 携带的 `IncrementalSnapshotResult` 提取 SST handles → `registry.register` 去重 → 构建 manifest → 持久化。在 `checkpoint-design.md` §2.2 文档化此 timing 变更
- [ ] **Segment GC**：`CheckpointCoordinator` 维护 `Map<Long, List<StateSegmentDescriptor>>`（段 2 持久化成功后 under monitor 更新）。`cleanupOldCheckpoints` 从 map 获取旧 checkpoint 的 segments → `registry.unregister` → 返回零引用 handles → off-load 到 persist executor 调用 `segmentStore.discardSegment`
- [ ] 端到端测试：RocksDB backend + `incrementalCheckpointEnabled=true` → source → keyed state → checkpoint N → 继续处理（少量变更）→ checkpoint N+1 → 验证 N+1 manifest segments 是 N 的子集 + 新增
- [ ] 端到端恢复测试：从增量 checkpoint manifest restore → RocksDB 从 SST + 非 SST 文件恢复 → 继续处理 → 结果正确
- [ ] **端到端向后兼容测试**：`incrementalCheckpointEnabled=false`（或 memory backend）端到端 checkpoint → restore 完整跑通，`EpochManifest.segments` 为空
- [ ] 增量 vs 全量对比基准：固定条件（1GB keyed state，≤64MB delta）下，增量 checkpoint latency ≤ 全量 latency × 0.5（≥2× 加速），测量比值记录在测试输出
- [ ] 更新 `checkpoint-design.md`：§9 存储与 Manifest 发布增加增量 checkpoint / segment 共享小节；§2.6 EpochManifest segments 激活说明（含 codec 值集）；§2.2 async persist timing 变更
- [ ] 更新 `ai-dev/logs/` 对应日期条目
- [ ] G33 gap 关闭追踪：`08-gap-analysis.md` G33 行标记 ✅ + 本 plan 路径

Exit Criteria:

- [ ] **端到端验证**：RocksDB 增量 checkpoint → restore → continue 完整跑通（从 `env.addSource()` 到 sink）
- [ ] **端到端向后兼容**：全量 checkpoint → restore 仍完整跑通（向后兼容未退化）
- [ ] **接线验证**：`CheckpointCoordinator` 在增量模式下确实调用 `SharedStateRegistry.register` 和填充 `EpochManifest.segments`（端到端测试中断言 manifest segments 非空）
- [ ] **Anti-Hollow Check**：`SharedStateRegistry` 在运行时被 coordinator 调用（非空壳）；subsumption 清理路径在运行时触发且删除物理文件（非 stub）
- [ ] 增量 vs 全量对比基准数据已记录（固定条件下，≥2× 加速）
- [ ] Registry restart 恢复：coordinator 重启后 ref-count 正确重建，orphan segment 文件被清理
- [ ] `checkpoint-design.md` 已更新（§2.2 + §2.6 + §9）
- [ ] `./mvnw test -pl nop-stream -am -T 1C` 通过
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [ ] `SharedStateRegistry` 引用计数正确（并发安全）
- [ ] RocksDB `Checkpoint` API → SST 内容寻址 → 跨 checkpoint 去重生效
- [ ] 非 SST 文件（WAL/MANIFEST/etc.）正确持久化与恢复
- [ ] `EpochManifest.segments` 激活，增量 manifest 可序列化/反序列化
- [ ] subsumption 清理零引用 SST 文件（物理删除，非 stub）
- [ ] 端到端增量 checkpoint → restore 路径完整跑通（Anti-Hollow）
- [ ] 全量 checkpoint 向后兼容（memory backend / `incrementalCheckpointEnabled=false`）端到端验证
- [ ] G33 gap 关闭追踪已更新
- [ ] `checkpoint-design.md` 已更新
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：组件间调用链在运行时确实连通
- [ ] `./mvnw compile -pl nop-stream -am`
- [ ] `./mvnw test -pl nop-stream -am -T 1C`
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <this-plan> --strict` 退出码 0
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream --severity high` 退出码 0
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### JDBC storage segment-level support

- Classification: `optimization candidate`
- Why Not Blocking Closure: Stage 31 以 LocalFile 存储为主验证增量 checkpoint 核心机制。JDBC segment 存储（BLOB per SST）为后续优化，不影响 LocalFile 路径的正确性验证。
- Successor Required: no
- Successor Path: (future optimization)

### Key-Group range SST reading

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: Key-group range 读取需要 binary composite key + key-group 前缀（Stage 34），与 SST 内容寻址共享是正交功能。
- Successor Required: yes
- Successor Path: Stage 35 (`35-keygroup-recovery`)

## Non-Blocking Follow-ups

- 跨 JVM SST 文件传输（Stage 40 数据面跨 JVM 后，segment store 需支持远程访问）
- RocksDB SST 文件手动 compaction 后的 segment 重新注册（优化项，非正确性门禁）

## Closure

Status Note: (待 closure audit 后填写)
Completed: (待定)

Closure Audit Evidence:

- Reviewer / Agent: (待定)
- Evidence: (待定)
