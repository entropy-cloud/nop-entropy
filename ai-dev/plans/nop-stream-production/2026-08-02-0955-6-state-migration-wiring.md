# 6 状态迁移接线（StateMigrationFunction + checksum 不匹配→迁移主路径）

> Plan Status: active
> Last Reviewed: 2026-08-02
> Source: `ai-dev/backlog/nop-stream-production-roadmap.md` Stage 33；`ai-dev/design/nop-stream/checkpoint-design.md:766-801`（迁移四分支 + StateMigrationFunction 规约）
> Mission: nop-stream-production
> Work Item: 33. 状态迁移接线
> Related: 前置 `2026-07-26-1000-1-serializer-fingerprint-schema-compat.md`（Stage 29，已完成，交付 SerializerFingerprint + getState() fail-fast）；独立于 Stage 34/35

## Purpose

把 Stage 29 落地的「checksum 不匹配即 fail-fast」两态检查，演进为「不匹配时先查询已注册的 `StateMigrationFunction`，注册了则全量读旧值→转换→写新值，未注册才 fail-fast」的迁移机制。交付 `StateMigrationFunction` 接口、注册机制、触发接线，并以 Integer→Long 迁移作为验证用例。收口 Phase 2 状态后端生产化的最后一项。

## Current Baseline

- **Stage 29 已落地**：`SerializerFingerprint`（`io.nop.stream.core.checkpoint.SerializerFingerprint:32-50`，immutable，`schemaVersion` 恒为 `1`）+ `StateSchemaResolver`（`io.nop.stream.core.common.state.StateSchemaResolver`）。
- **checksum 生成**：`StateSchemaResolver.fromDescriptor`（`:67-94`）基于 `stateType` + class FQN 拼规范字符串，`computeSHA256`（`:154-170`）产 SHA-256 hex。`fingerprintsCompatible`（`:172-180`）= checksum 相等判断，**仅两态**。
- **getState() fail-fast 已存在**：
  - `MemoryKeyedStateBackend.verifySchemaCompatibility`（`MemoryKeyedStateBackend.java:283-301`）：比较 `fromDescriptor(currentDescriptor)` 与 `fromDescriptor(restoredDescriptor)` 两边 checksum，不等即抛 `ERR_STREAM_STATE_SCHEMA_MISMATCH`。注释 `:288` 明确「Stage 33 will extend this path to consult registered StateMigrationFunction」。
  - `RocksDBKeyedStateBackend` 同构逻辑（`RocksDBKeyedStateBackend.java:281-295`，`:284` 多一个 `restoredDescriptor == null` guard）。`restoredDescriptors` 由 `putRestoredDescriptor`（`:464`）在 `RocksDBSnapshotSerDe.restoreXxx`（`:489` 等多处）填充。
- **`StateMigrationFunction` 接口不存在**：全代码库搜索仅 3 处代码前向引用注释（`NopStreamErrors.java:189` javadoc、`MemoryKeyedStateBackend.java:288` javadoc、`TestStreamModelFingerprintRecoveryCompat.java:45` 注释）+ 设计文档规约（`checkpoint-design.md:791-801`），无接口/注册/触发实现。
- **设计规约**（`checkpoint-design.md:791-801`）：`StateMigrationFunction<Old,New>{ New migrate(Old); SerializerFingerprint sourceFingerprint(); SerializerFingerprint targetFingerprint(); }`，通过 `StreamComponents` 注册，恢复时 Coordinator 查找匹配迁移函数并执行全量读-写迁移。四分支逻辑（`:766-780`）：version 相等+checksum 不同→拒绝；version<current→要求显式 migration；version>current→拒绝（不支持降级）。
- **schemaVersion 恒为 1**：`SerializerFingerprint.java:36`。version-based 分支（lower→migrate / higher→reject）未激活，因 `schemaVersion=1` 恒等。
- **checksum 嵌入位置**：`MemoryStateSerDe.embedSchemaFingerprint`（`:681-685`）与 `RocksDBSnapshotSerDe.embedSchemaFingerprint`（`:122-130`）把 `schemaChecksum`+`schemaVersion` 写入 per-state JSON info。**增量 checkpoint（Stage 31）绕过 per-state JSON**——`RocksDBKeyedStateBackend.snapshotIncremental`（`:590-606`）只写 `MARKER_KEY`+`keyType`，SST 为不透明 raw bytes，per-state schemaChecksum 未嵌入。这是已知 seam。
- **迁移测试不存在**：nop-stream 无任何 migration 测试文件。
- **比对数据源**：当前 fail-fast 比较的是「恢复出的 descriptor 算的 checksum」vs「live descriptor 算的 checksum」，两者都从代码侧 type 信息独立计算，故旧 checkpoint（无 schemaChecksum 字段）也能检查（`checkpoint-design.md:807`）。

## Goals

- **`StateMigrationFunction` 接口**：按 `checkpoint-design.md:791-799` 规约定义（`migrate` + source/target fingerprint），位于 `common/state/`，`@Internal` 元数据层 API（不向算子业务代码强暴露，但允许高级用户注册）。
- **注册机制**：按 `checkpoint-design.md:801` 已指定的载体 `StreamComponents` 注册（不重新评估该选择），提供按 `(stateName, sourceFingerprint) → migrationFunction` 的查询。
- **触发接线**：`MemoryKeyedStateBackend.verifySchemaCompatibility` 与 `RocksDBKeyedStateBackend` 等价路径，在 checksum 不匹配时**先**查询注册的迁移函数：命中则执行全量读旧值→转换→写新值，未命中才 fail-fast。**执行点 = state-backend `getState()`（与 Stage 29 的比对时机决策一致），非设计文档原描述的 Coordinator**——此偏差在本 plan 的 doc 更新中显式记录为最终状态。
- **Integer→Long 迁移验证**：以 `ValueState` 的 Integer→Long 类型变更为 demo 用例，端到端验证：旧 schema checkpoint → 注册迁移 → restore → 状态被正确转换。
- **owner-doc 收口**：`state-management-design.md` §6.3 与 `checkpoint-design.md` 迁移段从"设计规约/前向引用"更新为"已落地最终状态"（含 Coordinator→backend 执行点偏差）。

### 迁移时机与幂等性约定（Decision，draft review 裁定）

> **迁移在 `initializeState`（算子 open、首条记录前）经首次 `getState()` 同步执行。**

- **时机假设**：迁移在算子初始化阶段、处理任何 element 之前完成。**不支持** element 处理中途懒触发 `getState()` 的迁移（若算子在 processing 中途首次 getState，迁移与 element 处理交错，违反 all-or-nothing 语义——本 plan 视为不支持用法）。
- **幂等性不变量**：迁移完成后，该 state 持有的 descriptor 更新为新 schema checksum；再次 `getState()` 比对时 checksum 已匹配，不重复迁移（Phase 2 exit criterion 验证此不变量）。
- **崩溃恢复**：迁移中途崩溃 → checkpoint 不可用，恢复从上一个成功 checkpoint 重跑（迁移全量扫描前不持久化"迁移中"标记；nop-stream 无迁移事务日志，接受此限制并记录）。

## Non-Goals

- **schemaVersion-based 四分支完整激活**：`schemaVersion` 当前恒为 1，version< / version> 分支无真实触发条件。本 plan 实现「checksum 不匹配 → 查迁移函数」的主路径；version-based branching 作为框架预留但不在本 plan 构造人为 version 差异验证（因 schemaVersion 无业务来源递增）。明确裁定：这不是降级，是 schemaVersion 缺乏递增来源的客观限制。
- **accumulator-state（Reducing/Aggregating）迁移的 E2E 验证**：`verifySchemaCompatibility` 对所有 `getXxxState` 重载统一接线（含 accumulator 类型），但 accumulator state 的存储值是 opaque `SimpleAccumulator`/ACC，`StateMigrationFunction<OldAcc,NewAcc>` 的正确性由用户 `AggregateFunction` 决定——错误迁移会产出**静默 corrupt**（非 no-op）。本 plan 仅对 `ValueState`（必要时 `MapState` 值）做 E2E 验证；accumulator 迁移接线落地但只补 1 个单测 surface 该风险，E2E 验证与语义保证为 follow-up。
- **element 处理中途懒触发 getState 的迁移**（见上方时机约定：不支持）。
- **增量 checkpoint per-state fingerprint 嵌入**（Stage 31 seam）：迁移机制基于 descriptor 比对（两侧均从 type 信息算 checksum），对 memory/rocksdb 全量路径均生效。增量路径的 schemaChecksum 嵌入缺漏**不影响**迁移触发（迁移不依赖持久化 checksum 字段），列为 follow-up。
- 跨 JVM 迁移编排（迁移仍为本地全量扫描）。
- Flink `TypeSerializerSnapshot` 递归 schema 检查移植（vision Non-Goal）。
- 自动推断迁移函数（必须显式注册）。

## Scope

### In Scope

- `StateMigrationFunction` 接口 + 注册点（`StreamComponents`，遵循 `checkpoint-design.md:801`）；`StateSchemaResolver` 查询辅助。
- `verifySchemaCompatibility`（memory + rocksdb）增加迁移函数查询分支。
- 迁移执行：全量遍历该 state 的所有 entry，读旧值→`migrate`→写新值。
- Integer→Long demo 迁移 + E2E 测试。

### Out Of Scope

- schemaVersion 人为递增验证（无业务来源）。
- 增量 checkpoint per-state checksum 嵌入（follow-up）。
- Map/List/Reducing/Aggregating 多类型迁移的逐一验证（demo 用 ValueState，其余类型走同路径，单测覆盖 1-2 个）。
- 跨 JVM 迁移、自动迁移推断。

## Execution Plan

### Phase 1 - StateMigrationFunction 接口 + 注册机制

Status: planned
Targets: `nop-stream-core/.../common/state/`（新建 `StateMigrationFunction.java`）；`StreamComponents` 注册点；`StateSchemaResolver`

- Item Types: `Decision | Fix | Proof`

- [ ] 定义 `StateMigrationFunction<Old, New>` 接口（按 `checkpoint-design.md:794-798`）：`New migrate(Old oldValue)`、`SerializerFingerprint sourceFingerprint()`、`SerializerFingerprint targetFingerprint()`。`@Internal`，包 `io.nop.stream.core.common.state`
- [ ] 注册点：按 `(stateName, sourceSchemaChecksum)`（或 source fingerprint）索引迁移函数。注册载体 = `StreamComponents`（**遵循 `checkpoint-design.md:801` 已指定选择，不重新评估**）
- [ ] `StateSchemaResolver` 增加迁移函数查询辅助：给定 `(stateName, currentFp, restoredFp)`，返回已注册的匹配迁移函数或 null。匹配规则：source = restoredFp、target = currentFp

Exit Criteria:

- [ ] `StateMigrationFunction` 接口存在，签名与 `checkpoint-design.md:794-798` 一致；`@Internal`
- [ ] 注册载体 = `StreamComponents`（与设计文档一致），决策记录于 plan + Phase 1 doc 更新；注册 → 查询链路单测：注册一个 demo 迁移函数后能按 (stateName, source/target checksum) 查回
- [ ] **无静默跳过**：查询无结果返回 null（供调用方 fail-fast），不抛裸异常也不静默返回默认迁移
- [ ] `ai-dev/design/nop-stream/checkpoint-design.md:791-801` 迁移段从"规约"更新为"已落地"（记录最终注册载体 `StreamComponents`、匹配规则、**执行点 = state-backend getState 而非 Coordinator** 的最终位置）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 迁移触发接线（verifySchemaCompatibility 扩展）

Status: planned
Targets: `MemoryKeyedStateBackend.verifySchemaCompatibility`（`:283-301`）；`RocksDBKeyedStateBackend` 等价路径（`:281-295`）；两后端的迁移执行

- Item Types: `Fix | Proof`

- [ ] `MemoryKeyedStateBackend.verifySchemaCompatibility`（`:283-301`）：checksum 不匹配时，调用 Phase 1 的迁移查询；命中迁移函数则执行全量读-写迁移（遍历该 state 所有 `TypedNamespaceAndKey` entry，读旧值→`migrate`→写回新类型值），并在迁移后更新该 state 持有的 descriptor 为新 schema；未命中则维持现有 `ERR_STREAM_STATE_SCHEMA_MISMATCH` fail-fast
- [ ] `RocksDBKeyedStateBackend` 等价路径（`:281-295`）同样扩展：命中迁移函数则全量扫描该 CF 的 entry，读旧值→`migrate`→写回（用新键编码）；未命中 fail-fast
- [ ] 迁移执行的事务/原子性：单后端单线程（mailbox 保证）下，迁移为同步全量扫描，迁移中途异常应使 backend 进入不可用态（不留下半旧半新数据）

Exit Criteria:

- [ ] **接线验证**：checksum 不匹配 + 已注册迁移函数 → 迁移执行（端到端测试断言 `migrate` 被调用、entry 被转换）；checksum 不匹配 + 未注册 → 仍抛 `ERR_STREAM_STATE_SCHEMA_MISMATCH`
- [ ] checksum 匹配 → 不触发迁移（既有兼容路径不回归，`TestStateSchemaCompatibility.java` 通过）
- [ ] memory + rocksdb 两后端迁移路径均有单测
- [ ] **accumulator-state 迁移风险 surface 测试**：对至少 1 个 accumulator 类型（Reducing 或 Aggregating）的迁移路径补单测，显式记录"迁移操作 opaque ACC、正确性由用户负责"的风险（仅 surface，不要求完整 E2E 保证——见 Non-Goals）
- [ ] **无静默跳过**：迁移函数存在但 `migrate` 抛异常时，异常向上传播（不被吞）；迁移后 descriptor 已更新（再次 getState 不重复迁移——幂等性不变量）
- [ ] **迁移时机文档化**：`state-management-design.md` §6.3 明确迁移在 initializeState 首次 getState 同步执行、不支持 processing 中途懒触发
- [ ] 更新 `TestStreamModelFingerprintRecoveryCompat.java:45` 注释：`StateMigrationFunction` 归属从 "Stage 29 deferred" 改为 "Stage 33 已落地"
- [ ] `TestStateSchemaFingerprintEndToEnd.java`、`TestStreamModelFingerprintRecoveryCompat.java` 既有断言不回归
- [ ] `ai-dev/design/nop-stream/state-management-design.md` §6.3 更新：迁移机制已落地（注册点、触发时机、全量扫描语义、Coordinator→backend 执行点偏差）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - Integer→Long 迁移 E2E 验证

Status: planned
Targets: `nop-stream-runtime` E2E 测试目录；demo migration function

- Item Types: `Proof`

- [ ] E2E 测试：作业用 `ValueState<Integer>` 跑一段，产 checkpoint/savepoint（schema checksum = Integer FQN）。改作业代码为 `ValueState<Long>`，注册 `Integer→Long` 迁移函数，restore → 断言所有 key 的值被正确转换为 Long（数值相等、类型为 Long）
- [ ] 对照测试：同上但不注册迁移函数，restore → 断言抛 `ERR_STREAM_STATE_SCHEMA_MISMATCH`（证明迁移是显式触发，非静默）
- [ ] memory + rocksdb 两后端各跑一次

Exit Criteria:

- [ ] Integer→Long 迁移 E2E 通过（memory + rocksdb）：迁移后值正确、类型正确
- [ ] **端到端验证**：从 `getState(ValueStateDescriptor<Integer>)` 产 checkpoint → 改 `ValueStateDescriptor<Long>` + 注册迁移 → restore → `getState` 返回正确 Long 值，完整链路
- [ ] 对照测试：未注册迁移时 fail-fast（证明非静默降级）
- [ ] **Anti-Hollow**：迁移函数在 restore 运行时被真实调用并转换数据（断言迁移前后 entry 类型变化），非空壳
- [ ] `./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-rocksdb,nop-stream/nop-stream-runtime -am` 通过
- [ ] `ai-dev/design/nop-stream/checkpoint-design.md` 迁移段最终状态记录（含 Integer→Long demo 引用）
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [ ] `StateMigrationFunction` 接口 + 注册 + 触发接线全部落地（memory + rocksdb）
- [ ] checksum 不匹配 → 查迁移函数 → 命中则迁移、未命中则 fail-fast 的主路径成立
- [ ] Integer→Long 迁移 E2E 通过；未注册时 fail-fast 对照通过
- [ ] 既有 Stage 29 fingerprint 测试不回归
- [ ] Owner docs（`state-management-design.md` §6.3、`checkpoint-design.md` 迁移段）从规约/前向引用更新为已落地最终状态
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：迁移函数运行时被真实调用并转换数据；无空方法体/静默跳过/吞异常
- [ ] `./mvnw compile -pl nop-stream/nop-stream-core,nop-stream/nop-stream-rocksdb,nop-stream/nop-stream-runtime -am`
- [ ] `./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-rocksdb,nop-stream/nop-stream-runtime -am`
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <本 plan> --strict` 退出码 0
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream --severity high` 退出码 0
- [ ] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### schemaVersion-based 四分支完整激活

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: `schemaVersion` 当前恒为 1，无业务来源递增（`SerializerFingerprint.java:36`）。version<current→migrate / version>current→reject 分支无真实触发条件，构造人为 version 差异验证无意义。本 plan 实现 checksum 不匹配→查迁移函数的主路径，已覆盖迁移核心价值。
- Successor Required: no
- Successor Path: (当 schemaVersion 获得递增来源时再激活四分支)
- **Provenance**: 此项继承自 Stage 29（`2026-07-26-1000-1` lines 215-220，`Successor Required: yes → Stage 33`）。本 plan 实现了迁移基础设施（接口/注册/触发），使四分支在 schemaVersion 有递增来源时可即时激活；version 分支本身因 schemaVersion≡1 客观不可触发，故再延后不构成 in-scope gap 降级。

### 增量 checkpoint per-state schemaChecksum 嵌入

- Classification: `optimization candidate`
- Why Not Blocking Closure: 迁移触发基于 descriptor 比对（两侧均从 type 信息算 checksum），对 memory/rocksdb 全量路径均生效。增量路径（Stage 31 `snapshotIncremental` 绕过 per-state JSON）的 checksum 缺漏不影响迁移触发——迁移不依赖持久化 checksum 字段。仅影响增量 checkpoint 的人工 inspect 与 version-based 分支（后者本就 deferred）。
- Successor Required: no
- Successor Path: (future: 增量路径补嵌 schemaChecksum，配合 schemaVersion 四分支)

## Non-Blocking Follow-ups

- Map/List/Reducing/Aggregating 多类型迁移的逐一 E2E 验证（本 plan demo 用 ValueState，其余类型走同迁移路径，单测覆盖即可）
- 跨 JVM 迁移编排（迁移仍为本地全量扫描）

## Closure

Status Note: (待 closure 时填写)
Completed: (待填写)

Closure Audit Evidence:
(待独立 closure-audit 后填写)
