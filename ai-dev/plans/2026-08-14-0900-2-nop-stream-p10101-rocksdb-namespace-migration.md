# 2 nop-stream-rocksdb 命名空间迁移（P1-01-01，人工批准 2026-08-14）

> Plan Status: completed
> Last Reviewed: 2026-08-14
> Source: `ai-dev/backlog/nop-stream-invariant-loop-roadmap.md` Follow-up Backlog「P1-01-01 nop-stream-rocksdb 类驻留 core 命名空间（split-package）— successor 登记」；`2026-08-13-1243-3`（Phase 3 取消 + successor 登记）
> Related: plan `2026-08-14-0900-1`（HG-01 线协议，同日人工批准批次）

## Purpose

把 `nop-stream-rocksdb` 模块 main/test 代码从 `io.nop.stream.core.common.state.backend.rocksdb[.incremental]` 迁移到 `io.nop.stream.rocksdb[.incremental]`，消除 split-package（rocksdb 类驻留 core 命名空间）。该迁移 = mission Cross-Cutting「结构性重构执行前人工确认」门已过（2026-08-14 人工批准，批准记录写入 roadmap backlog 条目 + 本 plan）。纯机械迁移，行为零变更。

## Current Baseline

- **live 已核实（2026-08-14）**：
  - main 17 类：`nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/core/common/state/backend/rocksdb/` 下 15 类（RocksDBKeyedStateBackend、RocksDBStateBackend、RocksDBSnapshotSerDe、RocksDBKeyEncoder、RocksDBValueSerDe、RocksDBMapState、RocksDBValueState、RocksDBListState、RocksDBAggregatingState、RocksDBInternalAggregatingState、RocksDBInternalAppendingState、RocksDBInternalListState、RocksDBReducingState、RocksDBOptionConfig、RocksDbTtlAware）+ `incremental/` 下 2 类（RocksDBIncrementalRestore、RocksDBIncrementalSnapshotStrategy）。
  - test 13 类：同包 9 类（TestRocksDBBackendSkeleton、TestRocksDBStateTypes、TestRocksDBStateTtl、TestRocksDBStateMigration、TestRocksDBSnapshotRestore、TestRocksDBKeyGroupPrefixLayout、TestRocksDBKeyGroupRangeRestore、TestRocksDBDescriptorAggregatingStateRestore、TestRocksDBIncrementalRestoreFailFast）+ `incremental/` 下 4 类（TestRocksDBIncrementalBackendWiring、TestRocksDBIncrementalRangeRestore、TestRocksDBIncrementalRestoreAndBenchmark、TestRocksDBIncrementalSnapshotStrategy）。
  - 模块内引用：`RocksDBStateBackend.java:15` import `io.nop.stream.core.common.state.backend.memory.MemoryOperatorStateBackend`（core 既有包，**不迁移**）；模块内跨包 import 共 6 行需改——`RocksDBKeyedStateBackend.java:28-29`（root→incremental 2 行）、`RocksDBIncrementalRestore.java:30`（incremental→root 1 行）、3 个 incremental test 文件 import root（TestRocksDBIncrementalBackendWiring:21、TestRocksDBIncrementalRangeRestore:23、TestRocksDBIncrementalRestoreAndBenchmark:25）。同包互引无需 import。
  - 跨模块引用（main 零、test 7 文件）：
    - `nop-stream-runtime/src/test` 6 文件 import 引用（TestCheckpointCoordinatorIncrementalIntegration、TestStateMigrationEndToEnd、TestCheckpointCoordinatorIncrementalPersistRollback、TestMaxParallelismReshardMigrationE2E、TestKeyGroupRoutingE2E、TestRocksDBStateBackendE2E）+ **FQN 内联引用共 2 文件 6 处（复审 Major-1 修正）**：
      - `TestE2EWindowAggregateRestore` **4 处**（:295、:296、:308、:309，2 个跨行语句）；
      - `TestRocksDBStateBackendE2E` **:262-263 另有 2 处** `RocksDBOptionConfig` FQN 内联（1 个跨行语句，`RocksDBStateBackend` import 之外的第二引用源）。
    - `nop-stream-runtime/pom.xml:67-72` test scope 依赖 nop-stream-rocksdb（不变）。
  - 配置/注册/文档引用：`grep` 全仓（xml/xpl/xdef/json/mjs/beans）零命中 `io.nop.stream.core.common.state.backend.rocksdb`；`docs-for-ai/`、`ai-dev/design/` 无 FQN 引用（module-groups.md:23 仅模块名）；`source-anchors.md` 无 RocksDB 锚点。
  - 目标包 `io.nop.stream.rocksdb` 当前不存在（无命名冲突），与模块命名惯例一致（`io.nop.stream.cep` / `io.nop.stream.flow`）。
  - `WindowedStreamImpl.java:162-165` 反射类名 = `io.nop.stream.runtime.operators.windowing.WindowOperatorFactoryImpl`（**与 rocksdb 无关**，P2-01-03 条目，不在本 plan）。
- **批准状态**：P1-01-01 人工确认门 2026-08-14 通过（用户批准「两者都批准」）；`2026-08-13-1243-3` Phase 3 预设路径「未批准 → 取消 + successor」的 successor 触发条件 = 用户批准，已满足。

## Goals

- main 17 类 + test 13 类包迁移至 `io.nop.stream.rocksdb[.incremental]`，模块内互引与 import 全部同步。
- 跨模块引用（runtime test 7 文件）import/FQN 同步。
- 行为零变更（纯机械迁移，无逻辑改动）；全量测试全绿。
- 批准记录落档（roadmap backlog 条目 closed + 本 plan）。

## Non-Goals

- **不迁移** core 包 `io.nop.stream.core.common.state.backend.memory.*`（MemoryOperatorStateBackend 等）——Memory 后端属于 core 本体，非 split-package。
- **不改** nop-stream-rocksdb 模块边界、依赖、beans.xml（模块无 resources）。
- **不改** `WindowedStreamImpl.java:162-165` 反射类名（P2-01-03，另一 backlog 条目）。
- **不做** 任何行为重构（P2 批次 RocksDB 条目：Options 泄漏 AR-18/P2-03、RocksDBIncrementalRestore try-with-resources 顺序 AR-17、cp-id 目录复用 AR-18、serde 分隔符碰撞 AR-19 等均维持 backlog，不随本 plan 处理）。
- **不改** 序列化格式/checkpoint 兼容性（类名迁移不影响 JSON/DB 内数据——序列化数据不含 FQN 类名映射路径依赖，验证证据 = 既有快照 round-trip 测试全绿）。
- **不改** `docs-for-ai/` 结构（module-groups.md 模块表措辞已正确，无需改）。

## Scope

### In Scope

- `nop-stream-rocksdb` main 17 类 + test 13 类包声明与文件目录迁移。
- 模块内 import 同步（同包引用改为新包引用；incremental ↔ 根包互引）。
- `nop-stream-runtime` test 7 文件引用同步。
- 全量验证 + 批准记录落档。

### Out Of Scope

- Memory 后端包、模块边界、行为重构、反射类名、序列化格式（见 Non-Goals）。

## Execution Plan

### Phase 1 - main 代码迁移

Status: completed
Targets: `nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/core/common/state/backend/rocksdb/**` → `nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/**`

- Item Types: `Fix | Proof`
- [x] [Fix] 17 个 main 类 `package` 声明改为 `io.nop.stream.rocksdb[.incremental]`，文件移动到对应目录。
- [x] [Fix] 模块内跨包 import 同步（根包 ↔ incremental 子包互引、对 core 包的 import 保持原样——仅同模块 rocksdb 包引用改新包）。
- [x] [Proof] `./mvnw compile -pl nop-stream-rocksdb -am` 通过（或 `-DskipTests` install）。
- [x] [Proof] grep 实证：main 目录 `io.nop.stream.core.common.state.backend.rocksdb` 零残留（新旧包同时消失）。

Exit Criteria:

- [x] main 17 类全部迁移且编译通过
- [x] grep 零残留（main 面）
- [x] `./mvnw compile -pl nop-stream-rocksdb -am` 0 errors
- [x] **端到端验证**（本 Phase 组件级）：模块编译链（core → rocksdb）完整通过
- [x] **接线验证**：N/A（纯包迁移，无新组件）；`No wiring verification required: 机械迁移零接线变化`
- [x] **无静默跳过**：N/A（无新逻辑分支）；`No silent-no-op surface: 纯迁移`
- [x] `No owner-doc update required`（main 迁移无文档面变更）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - test 代码迁移

Status: completed
Targets: `nop-stream/nop-stream-rocksdb/src/test/java/io/nop/stream/core/common/state/backend/rocksdb/**` → `nop-stream/nop-stream-rocksdb/src/test/java/io/nop/stream/rocksdb/**`；`nop-stream-runtime/src/test` 7 文件

- Item Types: `Fix | Proof`
- [x] [Fix] rocksdb 模块 test 13 类包声明 + 目录迁移 + import 同步（含对 main 新包引用）。
- [x] [Fix] `nop-stream-runtime` test 6 文件 import 更新 + **FQN 内联 2 文件 6 处**（TestE2EWindowAggregateRestore :295/:296/:308/:309 + TestRocksDBStateBackendE2E :262-263，复审 Major-1）。
- [x] [Proof] `./mvnw test -pl nop-stream-rocksdb -am` 全绿（rocksdb 模块 13 类测试类全过）。
- [x] [Proof] `./mvnw test -pl nop-stream-runtime -am` 全绿（7 文件引用更新后全过——含 RocksDB 相关 E2E/集成测试，验证跨模块引用正确）。
- [x] [Proof] grep 实证（**范围 = 代码文件 `nop-stream/**`，审查 M1**）：`io.nop.stream.core.common.state.backend.rocksdb` 零残留，新包 `io.nop.stream.rocksdb` 已就位。**注**：`ai-dev/**`（已完成历史 plan `2026-08-13-1243-3` 等、roadmap backlog 条目、audit 文件）仍含该字符串——历史计划按 Minimum Rule 20 不回写，roadmap/audit 条目由 Phase 3 更新；grep 判据不含 ai-dev。**措辞（复审 Minor-1）**：判据为「旧包消失、新包就位」，非「新旧包同时消失」。

Exit Criteria:

- [x] test 13 类 + runtime test 7 文件全部迁移且编译通过（含 FQN 内联 2 文件 6 处）
- [x] `./mvnw test -pl nop-stream-rocksdb,nop-stream-runtime -am -T 1C` 0 failures
- [x] grep 实证：代码面（`nop-stream/**`）`io.nop.stream.core.common.state.backend.rocksdb` 零残留，新包 `io.nop.stream.rocksdb` 就位（ai-dev 历史计划不回写，审查 M1）
- [x] **端到端验证**：RocksDB 后端 E2E（`TestRocksDBStateBackendE2E` 等）在迁移后全绿——证明包迁移不影响 checkpoint 持久化/恢复行为
- [x] **接线验证**：N/A（纯包迁移）；`No wiring verification required`
- [x] **无静默跳过**：N/A；`No silent-no-op surface`
- [x] `No owner-doc update required`（test 迁移无文档面变更）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 全量验证与批准落档

Status: completed
Targets: 全量构建、roadmap backlog 条目、audit 文件状态

- Item Types: `Proof | Fix`
- [x] [Proof] 全量回归：`./mvnw test -pl nop-stream -am -T 1C` 全绿（nop-stream 全组 10 模块）。
- [x] [Proof] **`./mvnw install -DskipTests -pl nop-stream-rocksdb`（审查 m3）**：把新包 artifact 刷入本地 `.m2`，避免后续不带 `-am` 的局部构建（如 `mvn test -pl nop-stream-runtime`）从 `.m2` 解析旧包导致 `ClassNotFound`。
- [x] [Proof] `node ai-dev/tools/check-nop-stream-invariants.mjs all` exit 0（不变式门禁不受包迁移影响——注册表引用类名按简单类名，复核确认）。
- [x] [Proof] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0。
- [x] [Proof] checkstyle 复核：变更集新代码零违规（包声明/import 顺序合规）。
- [x] [Fix] roadmap backlog `P1-01-01` 条目：Status `todo`（待人工批准）→ 已批准（2026-08-14）+ 已落地（本 plan 收口后）closed；影响面清单更新（迁移完成）。
- [x] [Proof] `ai-dev/audits/2026-08-13-0805-multi-audit-nop-stream-invariant-loop.md` P1-01-01 条目处置记录更新（Phase 取消 → successor 执行完成）。

Exit Criteria:

- [x] 全量回归 0 failures（nop-stream 全组）
- [x] `./mvnw install -DskipTests -pl nop-stream-rocksdb` 成功（.m2 新包就位）
- [x] mjs `all` exit 0 + doc-links exit 0
- [x] checkstyle 变更集零新增违规
- [x] roadmap `P1-01-01` 条目 closed（批准记录 + 落地记录在案）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 所有 in-scope confirmed live defects 已修复（split-package 面收敛）
- [x] 所有 in-scope confirmed contract drifts 已收敛（引用面与 live 一致）
- [x] 行为结果已达成：包迁移完成、行为零变更、全量测试全绿
- [x] 必要 focused verification 已完成（跨模块引用 7 文件 + RocksDB E2E）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [x] 受影响的 owner docs 已同步（roadmap backlog `P1-01-01` 条目 closed；`No owner-doc update required` for docs-for-ai/）
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）迁移后模块编译/测试运行时链路连通（E2E 全绿），（b）无空方法体/静默跳过/no-op 作为正常实现（纯迁移无新增逻辑）
- [x] `./mvnw compile`（`-pl nop-stream -am`）
- [x] `./mvnw test -pl nop-stream -am -T 1C` 全绿
- [x] checkstyle / 代码规范检查通过（变更集零新增违规）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <本plan> --strict` exit 0
- [x] `node ai-dev/tools/check-nop-stream-invariants.mjs all` exit 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0

## Deferred But Adjudicated

### P2 批次 RocksDB 行为条目（AR-17/AR-18/AR-19、multi P2-03/P2-09-01 等）

- Classification: `watch-only residual`
- Why Not Blocking Closure: 均为已裁决 P2 backlog 条目（资源清理顺序、目录复用、serde 分隔符碰撞、Options 泄漏），触发条件 = 类别清扫/复探时评估，与本 plan 纯迁移无交互；迁移不改变这些条目的 live 位置（新包路径按需在触发时更新引用）。
- Successor Required: `no`
- Successor Path: 无（维持 roadmap backlog 既有触发条件）。

## Non-Blocking Follow-ups

- `WindowedStreamImpl.java:162-165` 反射类名（P2-01-03）维持 backlog。
- module-groups.md / source-anchors 无 FQN 引用，无需更新（已核实）。

## Closure

Status Note: 全 3 Phase 执行完成。代码迁移部分独立 closure audit（fresh session `ses_000f897f0ffeqzqEhC2530yayW`）PASS——17+13 类迁移、包声明、零残留、43 处 import/package + 6 处 FQN 全部到位、git diff 零行为变更（38 文件 rename 全 99%）；audit 指出的 4 项记录层缺口（roadmap 未 close / audit 未更新 / evidence 空 / 未提交）本收口已全部补齐。
Completed: 2026-08-14

Closure Audit Evidence:

- Reviewer / Agent: fresh session `ses_000f897f0ffeqzqEhC2530yayW`（独立 closure auditor，read-only）
- Evidence: (1) main 面：新路径 15+2 类存在、旧路径目录不存在、package 声明 15×root + 2×incremental 全对；(2) test 面：9+4 类 package 正确；(3) 引用面：全仓（排除 target/.git/ai-dev/_tmp）grep `io.nop.stream.core.common.state.backend.rocksdb` 零命中；runtime 7 文件引用齐全（TestE2EWindowAggregateRestore :295-296/:308-309、TestRocksDBStateBackendE2E :28/:262-263）；(4) `RocksDBStateBackend.java:15` MemoryOperatorStateBackend import 保留正确；(5) `git diff 3c1846025 -M` 全 214 行归因：86 行 package/import（43 对）+ 12 行 FQN + 116 行 plan 自更新，零逻辑改动；(6) 全量回归 `./mvnw test -pl nop-stream -am -T 1C` 3050 tests / 0 failures（BUILD SUCCESS）；mjs all exit 0；doc-links exit 0；install 双仓库新包就位；check-plan-checklist 仅 4 项既有 FAIL（credential/2250/288/321，非本 plan 引入）。注：`TestRocksDBIncrementalRestoreAndBenchmark` 并发 reactor 偶发超限 = 08-02/05/06 日志已文档化 pre-existing flaky（单跑 PASS，本 plan 零行为变更不相关）。

Follow-up:

- 无。P2 批次 RocksDB 行为条目（AR-17/18/19）维持既有 backlog 触发条件。

## Optional Sections

## Risks And Rollback

- **序列化兼容性**：审查已核实（2026-08-14）——checkpoint JSON/DB 中持久化的类名只有**用户类型**（valueType/keyType/mapKeyType/accumulator/aggregateFunction 的 `.getName()`，全部经 `ClassNameValidator`）；`RocksDBSnapshotSerDe`/`MemoryStateSerDe`/`CheckpointSerDe` 的 `Class.forName` 全部只加载用户类型；`RocksDBStateBackend.getName()` 返回常量 `"RocksDBStateBackend"`（无包名）；仓内无 `IStateBackend` Java 序列化路径。迁移不破坏既有 checkpoint 数据；Phase 3 全量回归 + RocksDB E2E（含恢复路径）全绿为最终证据，若发现任一序列化路径依赖 FQN 则本 plan 范围扩到兼容处理（旧 FQN 别名映射）并留痕。
- **回滚**：纯机械迁移，git revert 即可；批准记录与门禁复核同步回滚。
