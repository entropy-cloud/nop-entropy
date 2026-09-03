# 3 EpochManifest retention 落地（retention 路径同步裁剪 epoch manifests，双存储）

> Plan Status: active
> Mission: nop-stream-productization
> Work Item: item 33
> Last Reviewed: 2026-09-04
> Source: roadmap `ai-dev/backlog/nop-stream-productization-roadmap.md` item 33（来源 items 28+31 plan `2026-09-03-1951-3-remote-deploy-dataplane-stability.md` Phase 4 SOAK-3 复验发现，2026-09-04，runId `1788456304950-1`：137 个 `.epoch` 文件，maxRetained=5 不生效于 manifest 面）
> Related: `ai-dev/plans/nop-stream-productization/2026-09-03-1951-1-checkpoint-coordinator-structure-governance.md`（retention async executor 结构 F-A 的建立 plan）、`2026-09-03-1723-3-checkpoint-manifest-versioning-checksum.md`（manifest 字段面 Stage 51）

## Purpose

消除长运行作业的 manifest 无界累积：checkpoint retention 路径在裁剪 CompletedCheckpoint 的同时裁剪 epoch manifests（LocalFile `.epoch` 文件 + JDBC `stream_epoch_manifest` 行），使每个 `(jobId, pipelineId)` 的 manifest 数与 `maxRetainedCheckpoints` 同界。

## Current Baseline

（live 核对于 2026-09-04，行号为当日基线）

**双平面写入与单平面删除**：
- 每次持久化完成同时写两个平面：`{checkpointId}.checkpoint` 与 `{epochId}.epoch`（LocalFile）或 `stream_checkpoint` 与 `stream_epoch_manifest` 行（JDBC）。manifest 写入点：`CheckpointCoordinator.completePersistSynchronously`（:606）/`executePersistAsync`（:637）/`executeIncrementalPersistAsync`（:689），manifest 由 `buildEpochManifest`（:1548-1577）构造。
- retention 现状：`cleanupOldCheckpoints`（`nop-stream-runtime/.../checkpoint/CheckpointCoordinator.java:1340-1357`）只处理 checkpoint 平面——`getAllCheckpoints` 取全量、对超出 `maxRetained` 的尾部调 `deleteCheckpoint`（:1347）+ `gcSegmentsForCheckpoint`（:1351，Stage 31 segment 子suming GC）。**manifest 平面零清理**。
- retention 执行结构（plan 1951-1 F-A 已治理）：完成回调 `onCompletePersistSuccess`（:864-925）async 路径走 `scheduleRetentionCleanup`（:1271-1302，CAS in-flight 守卫 + trailing re-run 合并）提交到专用单线程 executor `checkpoint-retention-<jobId>`（:1304-1319）；sync-fallback（`asyncSnapshotEnabled=false`）在 monitor 内联执行（:900-908）。失败 WARN 留痕 + 下次完成自愈（:1354-1356）。
- `ICheckpointStorage`（core `nop-stream-core/.../checkpoint/storage/ICheckpointStorage.java`）有 `storeEpochManifest`（:51）/`loadLatestEpochManifest`（:53）/`loadRetainedEpochManifests`（default :62-69；LocalFile :557-591 与 JDBC :589-623 均已 override，keep-newest-N per `(jobId, pipelineId)`）——**无任何 manifest 删除面**（repo-wide grep `deleteEpochManifest|pruneManifest|trimManifest` 零 Java 命中）。唯一 manifest 删除入口是整作业 `deleteAllCheckpoints`（LocalFile 删整棵 `{jobId}` 树 :255-282；JDBC R-17 已同时删两表 :233-259）。
- 旋钮：`CheckpointConfig.maxRetainedCheckpoints`（默认 5；XDSL `maxRetainedCheckpoints` 默认 5；launch 侧 `OpsJobManager.submit` 缺省 3）。观测面治理配置（`StreamGovernanceConfig`）只管内存 history，刻意与 durable retention 解耦（javadoc :13-18）。

**restore 读集与裁剪目标的天然对齐**：
- `CheckpointCoordinator.restoreSharedStateRegistry()`（:1714-1745）经 `loadRetainedEpochManifests(jobId, pipelineId, maxRetainedCheckpoints)`（:1720）读回 retained 集、重注册 segments、重建 GC map、`cleanupOrphanSegments`（:1752-1782）。裁剪目标 = 保留 newest N（N=maxRetained）与 restore 读集同形，不会删掉恢复所需 manifest（前提：裁剪口径与 loadRetained 的 newest-N 排序口径一致，均按 epochId 降序）。

**Segment 联动（需裁定项）**：
- segment 生命周期由 SharedStateRegistry ref-count + `gcSegmentsForCheckpoint`（:1376-1412）管理；manifest 引用 segment（`segments: List<StateSegmentDescriptor>` 字段）。checkpoint 平面删除已触发 segment GC；manifest 裁剪新增的边界情形：被裁 manifest 若仍是某些 segment 的引用载体，须确认既有 GC/orphan 清理路径（LocalFile `cleanupOrphanSegments` 扫 `shared-state/*.sst`）覆盖，不产生"checkpoint 已删、manifest 已裁、segment 泄漏"的新通道。

**证据与既往记录**：
- SOAK-3 runId `1788456304950-1`：280s/interval 2s → 137 个 `.epoch` 文件（≈140 次完成），maxRetained=5；runbook §7（`distributed-runbook.md:133`）已记录缺陷归因；item 15 报告"retained manifests 有界"的正观察系 jam 早停假象（归因修正已落）。
- 既有审计建议：`ai-dev/audits/check/stream-runtime.md:71-88` 曾建议 manifest 清理（其行号引用已过期；其所述 JDBC deleteAll 不删 manifest 已被 R-17 修复——本项是独立的 retention 路径缺失）。
- 测试现状：`TestCheckpointRetentionAsync`（6 用例）的 in-memory `RetentionStorage.storeEpochManifest` 为 no-op（:211）——现有 retention 测试**观察不到** manifest 累积/裁剪；`TestCheckpointCoordinatorJdbcRetainedManifests`（3 checkpoint ref-count==3）、`TestEpochManifestPersistence`（store/load/篡改，无 retention 用例）、`TestJdbcCheckpointStorage.testLoadRetainedEpochManifestsMultiEpochNewestFirstCountBounded`（:533-561）。
- gated 面：`TestS2RestoreRescaleMultiJvmE2E`（multi-JVM 恢复）；演练装置 `ClusterSampleSources.fetchRetainedManifestCount`（`StabilityExerciseSupport:334-345`）已采样 manifest 计数——e2e 断言面现成。

**实现者清单**（接口扩展的波及面）：main 实现 `LocalFileCheckpointStorage`/`JdbcCheckpointStorage`；测试替身：`TestCheckpointRetentionAsync.RetentionStorage`、`TestAsyncSnapshotPipeline` 两处、`TestCheckpointCoordinatorIncrementalPersistRollback` 两处、`TestJobCoordinatorFailoverRestore` 两处、匿名实现 ×8（分布在 `TestCheckpointCoordinatorRaceCondition`/`TestCheckpointCoexistenceViaCoordinator`/`TestCheckpointCoordinator` ×2/`TestCheckpointCoordinatorPersistFailureLog`/`TestCheckpointHistory` 等 7 文件）。default 方法模式（items 28/31 `loadRetainedEpochManifests` 先例）可避免替身全量被迫迁移。

**真正剩余的 gap**：`ICheckpointStorage` 无 manifest 裁剪面；`cleanupOldCheckpoints` 不裁 manifest；双存储长运行 manifest 无界累积；retention 测试面观察不到 manifest。

## Goals

- `ICheckpointStorage` 新增 manifest 裁剪面（keep-newest-N per `(jobId, pipelineId)`，按 epochId 降序，与 `loadRetainedEpochManifests` 口径一致），LocalFile/JDBC 双实现。
- `CheckpointCoordinator` retention 路径（async executor 与 sync-fallback 两形态）同步裁剪 manifest；失败 WARN + 下轮自愈（沿既有容错语义）；async 路径段3a monitor 内零新增存储 I/O（沿 F-A 结构约束）；sync-fallback 沿 owner doc §2.2 D1(d) 既定内联语义，裁剪与既有 `deleteCheckpoint` 同点同风格内联。
- segment 联动裁定落档：manifest 裁剪不产生新的 segment 泄漏通道。
- 长运行有界性证明：真实完成路径（trigger→ACK→durable→retention）跑 N > maxRetained 轮后，双平面均 ≤ maxRetained（含 in-flight 余量），exactly-once/恢复行为不回退。

## Non-Goals

- 不改 `maxRetainedCheckpoints` 语义与默认值、不引入新的 retention 配置键。
- 不动 savepoint 面（`storeSavepoint`/`loadSavepoint` 独立生命周期保持）。
- 不做跨作业/全局清理、不改 `deleteAllCheckpoints`。
- 不处理 item 34（HA 接管 duplicate-key）。
- 不改观测面治理配置（`StreamGovernanceConfig` 与 durable retention 解耦保持）。

## Scope

### In Scope

- `nop-stream-core`：`ICheckpointStorage` 裁剪面（default 或 abstract，Phase 1 裁定）。
- `nop-stream-runtime`：`LocalFileCheckpointStorage`/`JdbcCheckpointStorage` 实现；`CheckpointCoordinator` retention 接线；相关测试（含 `TestCheckpointRetentionAsync.RetentionStorage` 扩展为记录 manifest）。
- gated 多 JVM / coordinator 级集成验证。
- owner docs：`ai-dev/design/nop-stream/checkpoint-design.md`（§9.2 retention 语义 + §9.3 接口方法表）、`ai-dev/design/nop-stream/distributed-runbook.md` §7（缺陷条目更新为已修复）。

### Out Of Scope

- `nop-stream-flow`/`nop-stream-fraud-example` 产品代码（演练装置如需断言增强仅限测试代码）。
- rocksdb segment store 内部逻辑（只裁定联动语义，不改 store 实现，除非联动裁定暴露缺陷——若暴露则按缺陷严重度就地修或路由 Follow-up）。

## Execution Plan

### Phase 1 - 裁剪语义与接口裁定（Decision 先行）

Status: planned
Targets: `ai-dev/design/nop-stream/checkpoint-design.md` §9.2/§9.3、接口签名裁定记录

- Item Types: `Decision`

- [ ] Decision D1（接口形态）：default 方法（返回被裁 epochId 集合或 void，替身默认 no-op 不被迫迁移——items 28/31 先例）vs abstract 全量迁移。约束：无论哪种，两个 main 实现必须真实实现；若 default no-op，须与"新功能不允许静默跳过"规则的边界写清（no-op 仅限测试替身域，生产实现缺失应可被测试识别——例如 coordinator 侧对 `getName()` 已知生产存储断言裁剪效果）。
- [ ] Decision D2（裁剪口径）：keep-newest-N per `(jobId, pipelineId)`，N=maxRetainedCheckpoints，按 epochId 降序——与 `loadRetainedEpochManifests` 排序口径逐字对齐（restore 读集 ⊆ 裁剪保留集）。epochId 与 checkpointId 关系核验锚点：`CheckpointCoordinator.java:1565-1566`（epochId := `completed.getCheckpointId()`）+ `CheckpointSerDe.java:461`（反序列化保 id）——生产仅此两处构造 `EpochManifest`，1:1 同源递增成立；结论写入裁定记录（如未来出现分离情形则按各自平面独立 newest-N）。
- [ ] Decision D2b（pipelineId 枚举口径）：`cleanupOldCheckpoints` 以 `getAllCheckpoints(jobId)`（跨该 job 全部 pipeline）为基准、删除时用 `old.getPipelineId()`，而 coordinator 持单 pipelineId——裁定裁剪的 pipelineId 枚举口径（仅 own pipelineId vs 从 allCheckpoints 归集 distinct pipelineId 逐个裁；当前生产单 pipeline `pipeline-0` 两解等价，裁定须与 Goals 的"每个 (jobId, pipelineId) 同界"承诺一致）。
- [ ] Decision D3（segment 联动）：核对被裁 manifest 引用的 segment 在既有 `gcSegmentsForCheckpoint`/`cleanupOrphanSegments`（LocalFile）/registry ref-count 语义下无新泄漏通道；若发现边界缺陷，就地修或按严重度路由（不得静默）。
- [ ] Decision D4（时序）：manifest 裁剪在 retention 一轮内 checkpoint 平面删除之后执行（同一轮、同一 executor；顺序保证"先删 checkpoint+GC segment、后裁 manifest"或论证顺序无关）。
- [ ] 裁定记录落 `checkpoint-design.md` §9.2（manifest retention 语义段）+ §9.3（方法表补 manifest 族方法含新裁剪面）。

Exit Criteria:

- [ ] D1/D2/D2b/D3/D4 五项裁定全部落档（含拒绝的替代方案与理由）
- [ ] `checkpoint-design.md` 更新后 `node ai-dev/tools/check-doc-links.mjs --strict` exit 0
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 双存储实现 + coordinator 接线

Status: planned
Targets: `nop-stream-core/.../checkpoint/storage/ICheckpointStorage.java`、`nop-stream-runtime/.../checkpoint/storage/LocalFileCheckpointStorage.java`、`JdbcCheckpointStorage.java`、`CheckpointCoordinator.java`、测试

- Item Types: `Fix | Proof`

- [ ] 接口裁剪面 + LocalFile 实现（列 `*.epoch`、按 epochId 降序保留 newest N、删除超出者；I/O 失败异常语义对齐既有 deleteCheckpoint 容错风格）。
- [ ] JDBC 实现（`DELETE FROM stream_epoch_manifest WHERE job_id=? AND pipeline_id=? AND epoch_id IN (读取到的超限旧 id)` 形态——**安全方向硬约束**：删除目标必须限定为读取时已观察到的超限旧 epochId，或数据库端原子单语句；**禁止"先 SELECT newest-N keep-set、后 DELETE NOT IN (keepSet)"的两步形态**——retention executor 与 persist executor 是不同线程池，读写窗口间新完成的 manifest（id 更大）不在 keepSet 会被误删，把泄漏缺陷换成恢复点倒退的正确性缺陷。另注：GENERIC 方言无 LIMIT、H2 2.x 子查询内 LIMIT 兼容性受限（保守起见），且该类既有 DELETE 均为无方言分支的平铺 SQL——两步读旧-删旧几乎是必然选择；删除行数可日志留痕）。
- [ ] `CheckpointCoordinator.cleanupOldCheckpoints` 扩展（或伴随私有方法）：按 D4 时序、D2b 口径调裁剪面；失败 WARN 不抛出（沿 :1354-1356 自愈语义）；**async 路径段3a monitor 内零新增存储 I/O**（裁剪只在 retention executor 内）；sync-fallback 沿 D1(d) 既定内联语义，裁剪与既有 deleteCheckpoint 同点同风格（不因本项把 sync-fallback I/O 挪出 monitor——那会破坏 owner doc §2.2 已 pin 的语义）。
- [ ] `TestCheckpointRetentionAsync.RetentionStorage` 扩展：真实记录 manifests（storeEpochManifest 不再 no-op），使 6 个既有 retention 用例的语义覆盖 manifest 面（最终一致/非阻塞/串行化/trailing re-run/失败自愈/sync-fallback 至少各有一条断言 manifest 也被裁）。
- [ ] focused 新用例：① LocalFile：写 8 个 manifest → 裁剪 → 目录只剩 newest N 个 `.epoch`（文件名断言）；② JDBC：等价行数断言（H2）；③ 保留集与 `loadRetainedEpochManifests` 一致性（裁后 load 返回集 == 裁剪保留集）；④ 裁剪失败（fake 抛异常）→ WARN + 下轮自愈收敛；⑤ in-flight 余量：retention 轮次间新完成的 manifest 允许短暂超 N，下一轮收敛（最终一致，不误删 newest）。
- [ ] **接线验证**：RecordingStorage 风格断言真实 coordinator 完成路径（trigger→ACK→persist→retention）确实调用了裁剪面（调用计数/参数断言），不只是接口与实现各自存在。

Exit Criteria:

- [ ] 双存储裁剪实现 + coordinator 接线落地，focused 用例 ①-⑤ 全绿，既有 retention 6 用例含 manifest 断言
- [ ] async 路径 monitor 内零新增存储 I/O（既有非阻塞用例保持绿）；sync-fallback 内联语义与 owner doc §2.2 D1(d) 一致（表述可判定：sync 路径裁剪调用点与 deleteCheckpoint 同栈）
- [ ] `checkpoint-design.md` §9.2/§9.3 与实现一致
- [ ] `./mvnw test -pl nop-stream -am -T 1C`（runtime 相关模块）绿
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 端到端有界性验证 + 回归 + runbook 收口

Status: planned
Targets: coordinator 级集成测试、gated 多 JVM 用例、`distributed-runbook.md` §7

- Item Types: `Proof | Fix`

- [ ] coordinator 级 e2e（**端到端验证**，真实 trigger→ACK→durable→retention 循环）：LocalFile 集成测试跑 > maxRetained 次 checkpoint 完成（如 maxRetained=3、完成 10 轮），断言 checkpoint 文件与 `.epoch` 文件均收敛 ≤ maxRetained（+in-flight 余量上界），restore（`loadRetainedEpochManifests`）仍成功且内容为 newest N。
- [ ] JDBC 侧等价断言（TestCheckpointCoordinatorJdbcRetainedManifests 扩展或新用例：多轮完成后 manifest 行数有界 + restore ref-count 语义不回退）。
- [ ] gated 多 JVM 有界性（`nop.stream.test.multi-jvm.enabled`）：沿 SOAK 短版或 `TestS2RestoreRescaleMultiJvmE2E` 形态跑多轮 checkpoint（复用 `fetchRetainedManifestCount` 采样）。**断言协议（防 flaky）**：主判据 = 停止负载后的收敛终态（manifest 计数 ≤ maxRetained）；运行中后段为辅助判据，余量上界放宽（如 ≤ maxRetained + 2×retention 轮耗时内可完成数，推导留档于测试注释），不以紧上界作硬断言；exactly-once/恢复判据不回退。
- [ ] 回归：`TestEpochManifestPersistence`（含 Stage 51 checksum/篡改用例）、`TestJdbcCheckpointStorage` 全量、gated 场景套件（C0-C3 面）不回退。
- [ ] runbook §7 缺陷条目更新为已修复（含新语义一句话：retention 一轮内双平面同裁）；如 §7 归因句仍引用"不清理"表述一并修正。

Exit Criteria:

- [ ] 双平面有界性在 coordinator 级 e2e 与 gated 多 JVM 两层均有留档证明
- [ ] exactly-once/恢复/fencing 既有 gated 判据零回退
- [ ] runbook §7 与 checkpoint-design.md 与 live 行为一致
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream-runtime --severity high` exit 0
- [ ] `node ai-dev/tools/check-nop-stream-invariants.mjs` exit 0；`check-doc-links.mjs --strict` exit 0；`check-plan-checklist.mjs <plan-file> --strict` exit 0
- [ ] 独立子 agent closure-audit 完成并写入证据（含 Anti-Hollow：完成路径→retention executor→裁剪面→文件/行消失全链验证）
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [ ] manifest 无界累积缺陷收口：长运行（N >> maxRetained 轮完成）后双存储 manifest 均 ≤ maxRetained（+in-flight 余量），e2e 留档
- [ ] restore 读集（loadRetainedEpochManifests）与裁剪保留集一致性有测试钉定
- [ ] segment 联动无新泄漏通道（D3 裁定 + 证据）
- [ ] retention 执行结构（async executor + sync-fallback）语义不回退：async 段3a monitor 内零新增 I/O；sync-fallback 沿 D1(d) 内联语义
- [ ] owner docs（checkpoint-design.md + distributed-runbook.md §7）同步
- [ ] `./mvnw test -pl nop-stream -am -T 1C` 全绿 + gated 启用态绿
- [ ] 四工具门禁 exit 0（hollow/invariants/doc-links/plan-checklist）
- [ ] 独立子 agent closure-audit 完成并写入证据

## Deferred But Adjudicated

（起草时无；执行中如出现按 Allowed classifications 分类并附理由）

## Non-Blocking Follow-ups

- （观察项）JDBC 裁剪的删除行数/时间指标如需观测，可后续接入 engine 层 metrics——非本计划 closure 必需

## Closure

Status Note: (待执行收口时填写)
Completed: (未完成)

Closure Audit Evidence:

- Reviewer / Agent: (待 closure audit)
- Evidence: (待 closure audit)

Follow-up:

- (待收口时裁定)
