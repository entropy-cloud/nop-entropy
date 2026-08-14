# AR-22 Timer 键 JSON 恢复物化（P0 变体评估与修复）

> Plan Status: completed
> Last Reviewed: 2026-08-13
> Mission: nop-stream-invariant-loop
> Work Item: AR-22（roadmap Follow-up Backlog：Timer 键 JSON 恢复同机制受损，P0 变体评估）
> Source: `ai-dev/backlog/nop-stream-invariant-loop-roadmap.md` §AR-22（plan `2026-08-13-1243-1` Phase 1 类别清扫新发现）；`ai-dev/audits/2026-08-13-0805-multi-audit-nop-stream-invariant-loop.md` P2-INV-4（同机制键控面，已修复）；`ai-dev/audits/2026-08-13-0805-open-audit-nop-stream-invariant-loop.md` AR-01（P0 修复基线）
> Related: `2026-08-13-1243-1-nop-stream-engine-state-recovery-and-timer-fixes.md`（AR-01 Fix 基线与类别清扫登记）

## Purpose

把 AR-22 收口：先按 backlog 登记的「P0 变体评估」正式裁定严重级（对齐 AR-01 P0 先例：同机制 = JSON 持久化 round-trip 数值键类型漂移 → 静默状态丢失，本条目走 operator state 面 `internal-timers`，`deserializeKey` 修复不覆盖）；若裁定 P0/P1 → 以 `Fix` 落地（test-first 先红后绿 + round-trip 测试 + 作业级重启 E2E + 类别清扫 + 门禁复跑零命中）；若裁定 P2 → 按 backlog 维持并留痕（不立 I4 修复面）。无论裁定结果，评估结论与依据必须落档（backlog 条目状态流转）。

## Current Baseline

- **live 缺陷点（已核实）**：
  - `HeapInternalTimerService.TimerEntry.fromSerializableForm`（`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/HeapInternalTimerService.java:327-332`）：`(K) form.get("key")` 原样 cast，无 keyType 重物化。`toSerializableForm`（:314-320）直接存 `key` 原对象。
  - 持久化路径：`WindowOperator.snapshotState` → `result.putOperatorState("internal-timers", internalTimerService.snapshotTimers())`（`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperator.java:558-560`）→ `TimerSnapshot.toSerializableForm`（@type="TimerSnapshot" 自描述 map）→ `CheckpointSerDe` JSON 持久化（`serializeOperatorStates`）。
  - 恢复路径：`CheckpointSerDe.deserializeOperatorState`（`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/storage/CheckpointSerDe.java:468-489`，:474-475 `@type` 路由）→ `TimerSnapshot.fromSerializableForm`（HeapInternalTimerService.java:432-457）→ `TimerEntry.fromSerializableForm` 原样 cast → `WindowOperator.restoreState`（:594-596 捕获 `restoredTimerSnapshot`）→ `open()`（:489-492）`internalTimerService.restoreTimers(...)` 延迟应用。
  - 触发机制：timer 回调经 `setCurrentKey(entry.getKey())` 设置当前键 → 键控状态按 `TypedNamespaceAndKey`（类敏感 equals）查找 → Integer 键 vs live Long 键 miss → 窗口内容/触发结果静默丢失。Long 键 < 2^31 经 TextScanner JSON round-trip 成 Integer（与 AR-01 完全相同）；POJO 键恢复成 LinkedHashMap（同 P2-INV-4 机制）；String 键安全。
  - **双后端同受影响**：operator state 的 `internal-timers` 走共享 checkpoint JSON 路径（storageType local/jdbc），Memory 与 RocksDB 后端恢复时都经同一 `deserializeOperatorState`。
- **AR-01 修复基线（可复用机制）**：`MemoryStateSerDe.deserializeKey`（`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/memory/MemoryStateSerDe.java:836-858`）：按 backend 声明 `keyType` 重物化（`JsonTool.parseBeanFromText(json, keyType)`），失败抛 `ERR_STREAM_STATE_ERROR`（无静默跳过，guide #24）；`MemoryKeyedStateBackend.getKeyType()`（:391）/ `RocksDBKeyedStateBackend.getKeyType()`（`nop-stream-rocksdb/.../RocksDBKeyedStateBackend.java:352`，包私有，仅作参考不可作来源）均可提供 keyType。
- **修复方向候选（backlog 建议，实施时定稿；本 plan 声明两个候选落点，Phase 1/2 的测试断言层必须绑定所选落点，否则先红后绿死锁）**：候选 A = `HeapInternalTimerService` 层（service 持 keyType，`restoreTimers` 内重物化——核心单点，DTO 层 `TimerSnapshot.fromSerializableForm` 保持哑 cast 不变）；候选 B = `WindowOperator` 层（restore/open 时按 keyClass 重建 snapshot）。**关键约束：修复落点若为 A，Phase 1 复现断言必须写在 restore→fire 下游层（fired key 类型 + `TypedNamespaceAndKey` 命中语义），不能钉在 DTO 层（`fromSerializableForm` 输出 post-fix 仍为 Integer/LinkedHashMap）**。keyType 来源 = `WindowOperator.keyClass`（final 字段 :156，restoreState 与 open 任何时点可用；后端 keyType 即由它派生 `createKeyedStateBackend(keyClass)`，语义等价）。**注意**：生产路径 `restoreState` 时 `keyedStateBackend == null`（后端仅在 `open()` :421 创建），且 `IKeyedStateBackend` 接口无 `getKeyType()`、`RocksDBKeyedStateBackend.getKeyType()` 为包私有——均不可作为 keyType 来源。构造点涟漪：`HeapInternalTimerService` 构造点 = WindowOperator:464 + ProcessOperator:41 + 39 处测试调用点（全仓 41 处）——若候选 A 加必填构造参数将产生大 diff，优先 setter/可空参数。
- **现有测试覆盖（缺口）**：`TestHeapInternalTimerServiceSnapshotRestore`（core，纯内存 DTO 往返，未走 JSON 持久化路径 → 未捕获类型漂移）；`TestMemoryStateSerDeNumericKeyRestore`（键控面仅）；`TestTimerCheckpointRestoreE2E`（runtime，完整 operator 级 timer checkpoint→restore→open→fire→输出链路 + restore-before-open 延迟应用模式——天然扩展对象，但当前为 String 键 + 内存路径，无 JSON round-trip Long 键）；`TestE2ECheckpointAndRecovery.testLongKeyedStateJobRestartRestoresSameKeys`（AR-01 作业级重启先例，timer 面无对应）。
- **类别清扫范围（已核实）**：`restoreTimers`/`snapshotTimers`/`TimerSnapshot`/`internal-timers` 全仓调用点仅 `WindowOperator`（restore :490 / snapshot :559）+ `CheckpointSerDe` 路由 + `HeapInternalTimerService` 内部；`CepOperator` 使用自有 `cep-event-time-timers`（List<Long>，非键控）→ 不受影响。
- **backlog 状态**：`todo`（P0 变体评估）——评估本身即待办交付物。

## Goals

- 正式裁定 AR-22 严重级（P0/P1/P2，对齐 AR-01 同机制先例），结论 + 依据落档（backlog 条目状态流转）。
- 若 P0/P1：Timer 键 JSON 恢复按 keyType 重物化（Fix），机制对齐 `MemoryStateSerDe.deserializeKey`（无静默跳过，失败抛 `ERR_STREAM_STATE_ERROR`）。
- 测试：round-trip 单元测试（Long 键 <2^31 + POJO 键）+ 作业级重启 E2E（timer 触发后键控状态命中断言），test-first 先红后绿。
- 类别清扫：timer snapshot 面全调用点 + 双后端 operator-state 恢复路径一致，零遗留。
- 门禁复跑零命中（JUnit 门禁 + mjs `all`）+ `./mvnw test -pl nop-stream -am -T 1C` 全绿。

## Non-Goals

- 不处理 AR-20（`triggerAccumulators`/`paneTracking` 算子级 `key.toString()` 键，独立失败面，backlog 维持）。
- 不处理 P2-INV-6（NFAState PriorityQueue JSON 物化，CEP 面，backlog 维持）。
- 不触发 C3-RL-8（CEP 面 P2，触发条件 = I4/I5 类别清扫（CEP 面）或 per-state windowTimes 使用面扩展或复探——本 plan 类别清扫限于 timer/operator-state 面，CEP 面不在清扫范围；若执行中发现触及 CEP 面则按触发条件评估并留痕）。
- 不处理 `HG-01`（人工确认门，不裁决）。
- 不处理 P1-01-01（rocksdb 命名空间迁移，人工批准门未过）。
- 不处理 manifest 恢复 `latestCompletedCheckpoint` 回退边界（0132-2 Non-Blocking 复探登记，维持）。

## Scope

### In Scope

- `HeapInternalTimerService` TimerEntry/TimerSnapshot 恢复路径 keyType 重物化（核心修复）。
- `WindowOperator` restore→open 接线（`restoredTimerSnapshot` 应用路径，若修复需传递 keyType）。
- 测试：round-trip 单测 + 作业级重启 E2E + 先红后绿证据。
- 类别清扫：全仓 timer snapshot 调用点 grep + 双后端一致性。
- 文档：`ai-dev/design/nop-stream/checkpoint-design.md`（若 §2.1 timer state 节需同步重物化语义）、roadmap backlog AR-22 状态流转、adjudication-table（若裁定落档需要）、daily log。

### Out Of Scope

- 键控状态面（AR-01 已修复，不回改）。
- CEP timer 注册表（非键控，免疫）。
- 其余 P2 backlog 批次（08-12/08-13 open/multi audit 全部条目）。
- 门禁沉淀（本条目不引入新不变式门禁；如执行中发现新失败类 → I6 评估 Loop Rule）。

## Execution Plan

### Phase 1 - P0 变体评估 + 复现（test-first 先红）

Status: completed
Targets: 测试文件（`nop-stream-core/src/test/.../operators/TestHeapInternalTimerServiceSnapshotRestore.java` 落点 A；`nop-stream-runtime/src/test/.../TestTimerCheckpointRestoreE2E.java` 落点 B）；生产文件（`HeapInternalTimerService.java` / `WindowOperator.java`）本 Phase 只读参考，不修改

- Item Types: `Decision | Proof`

- [x] **评估裁定（Decision）**：**裁定结论 = P0**（与 AR-01 同机制同后果同触发面，走 operator state 面 `internal-timers`，`deserializeKey` 修复不覆盖）——按 AR-01 先例链四对比项落档：① 同机制（JSON round-trip 数值键类型漂移，`Long(123)` → `Integer(123)`，TextScanner parseInt 优先）；② 触发面（恢复后 timer 回调 `setCurrentKey(Integer)` 访问键控状态 miss——定时器窗口（EventTimeTrigger/ContinuousEventTimeTrigger/ProcessingTimeTrigger）作业重启场景必达，`TestE2EWindowAggregateRestore` 已证明重启路径可用）；③ 后果（静默状态丢失，无异常无日志，与 AR-01 相同）；④ 生产可达性（作业级重启 + 窗口定时器为既有功能，storageType=local 为既有配置）。裁定落档：adjudication-table.md 新增 §18 + roadmap backlog AR-22 条目状态流转。**执行中边界发现（已落档）**：非 @DataBean POJO 键在 JSON 持久化序列化点即抛 `ERR_JSON_ONLY_DATA_BEAN_IS_SERIALIZABLE`（JsonSerializer onlyForDataBean 守卫）→ checkpoint 响亮失败非静默丢失；静默漂移面 = Long 数值键 + @DataBean POJO 键（Fix 面覆盖）。**Phase 2/3 正常执行（P0 路径）。**
- [x] **复现测试（Proof，先红）**：**落点 A（`HeapInternalTimerService` 层）**——`TestHeapInternalTimerServiceSnapshotRestore.testLongTimerKeyJsonRoundTripFiresWithLongKey`：现有 2-arg 构造 API（无 keyType 机制），`snapshotTimers` → `toSerializableForm` → `JsonTool.serialize/parseMap` → `fromSerializableForm`（模拟 CheckpointSerDe storageType=local JSON 持久化）→ `restoreTimers` → `advanceWatermark` 触发 → recording `Triggerable` 捕获 fired timer → 断言 fired key 为 `Long`。**red 实测（2026-08-13）**：`ClassCastException: Integer cannot be cast to Long`（fired key = Integer(123)，类型漂移实证）；`TypedNamespaceAndKey.equals` 类敏感语义（Integer vs Long miss）在测试注释引用，未单独对 `TypedNamespaceAndKey` 断言（避免测试对象错位）。断言层绑定修复落点 A（restore→fire 下游层），非 DTO 层（post-fix DTO 仍输出 Integer）。
- [x] **POJO 键变体（Proof，先红）**：`TestHeapInternalTimerServiceSnapshotRestore.testPojoTimerKeyJsonRoundTripFiresWithPojoKey`——`@DataBean` static nested `PojoKey`（public 无参构造 + bean 属性 + equals/hashCode）经 JSON round-trip → restore→fire 断言键重物化为 `PojoKey`（非 LinkedHashMap）。**red 实测**：fired key = `LinkedHashMap {id=k1, seq=42}`（`assertEquals` 断言失败）。**执行中发现并落档**：测试 POJO 必须 `@DataBean` 标注——`JsonTool.serialize` 对非 @DataBean 类直接抛 `nop.err.core.json.only-data-bean-is-serializable`（red 首跑暴露，修正后二次 red 达成预期断言失败）；此发现同步进裁定（非 @DataBean POJO 键 = JsonTool 层 fail-fast，非静默丢失，Guide #24 合规）。
- [x] **双后端一致性（Proof）**：代码核查在案——operator state `internal-timers` 持久化走 `CheckpointSerDe.serializeOperatorStates`（TimerSnapshot → toSerializableForm）→ `JsonTool` JSON（`CheckpointSerDe.java:423-426`），恢复走 `deserializeOperatorState`（@type=TimerSnapshot → fromSerializableForm，`CheckpointSerDe.java:474-475`）——**storage 层路径，后端无关**；RocksDB 后端仅替换键控面 serde（RocksDBSnapshotSerDe = AR-01 面），timer 面 Memory/RocksDB 同路径同漂移（plan Current Baseline「双后端同受影响」实证成立）。跨后端 round-trip 测试按尽力项处理（落点 A 单测 + Phase 3 E2E 的 localStorage JSON 路径已覆盖共享路径，不做双后端独立 E2E）。

Exit Criteria:

- [x] AR-22 严重级裁定在案（backlog 条目状态 + 依据 + adjudication-table §18），**P0 → 进入 Phase 2**
- [x] 复现测试先红（pre-fix 失败：Long 键 CCE + POJO 键 LinkedHashMap 断言失败，缺陷 live 实证），测试命名/位置符合现有约定，断言层与修复落点 A 绑定
- [x] 双后端路径核查结论在案（timer 面共享 `deserializeOperatorState`，后端无关）
- [x] 文档裁定：本 Phase 不改代码，`No owner-doc update required`（backlog 落档 + adjudication-table §18 除外）

### Phase 2 - Fix + 类别清扫

Status: completed
Targets: `HeapInternalTimerService.java`、`WindowOperator.java`（如接线需要）、双后端恢复路径

- Item Types: `Fix | Proof`

- [x] **Fix（Fix）**：**落点 A 定稿并落地**——`HeapInternalTimerService` 新增 `keyType` 字段 + `setKeyType(Class<?>)`（可选 setter，41 处既有构造点零涟漪）+ `restoreTimers` 内 `rematerializeEntry` 重物化：机制对齐 `MemoryStateSerDe.deserializeKey`（`keyType != null && keyType != Object.class && !keyType.isInstance(key)` → `JsonTool.parseBeanFromText(JsonTool.serialize(key,false), keyType)`）；**null 键守卫**（`key == null → 原样返回`，对齐 deserializeKey `obj == null → return null`）；**重物化失败抛 `ERR_STREAM_STATE_ERROR`**（param 含键 JSON + keyType 名，无静默跳过 guide #24）。keyType 来源 = `WindowOperator.keyClass`（final 字段 :156，restoreState 与 open 任何时点可用）。
- [x] **接线（Proof）**：`WindowOperator.open()` :464-471——service 创建后立即 `internalTimerService.setKeyType(keyClass)`；延迟应用模式（:489-492 `restoredTimerSnapshot` 应用）无需改动（setKeyType 在 restoreTimers 之前执行，顺序实证：open 内 :464 创建 → :468 setKeyType → :493 restoreTimers）。**生产路径事实复核在案**：`restoreState` 时 `keyedStateBackend == null`（后端在 `open()` 才创建）→ 无法自 backend 取 keyType；`IKeyedStateBackend` 无 `getKeyType()`、RocksDB 版包私有——keyType 必须来自 `keyClass`，双后端通用（与 plan 基线一致）。**Object.class 边界留痕**：`keyType == Object.class` 守卫跳过重物化（与 AR-01 `deserializeKey` 同机制同限制）；生产路径 keyClass 为具体类型（E2E Long 键用例实证），无 Object.class 生产传参发现。**接线验证 = E2E 全链路**：restoreState → open（setKeyType）→ 延迟 restoreTimers（重物化）→ advanceWatermark → onEventTime `setCurrentKey(Long)` → `getWindowContents` 命中 → 输出 —— 运行时连通（测试绿为证，非仅类型系统）。
- [x] **类别清扫（Proof）**：grep 全仓 `restoreTimers`/`snapshotTimers`/`TimerSnapshot`/`fromSerializableForm`/`internal-timers` 调用点零遗漏（实证仅 WindowOperator :490/:559/:593 + CheckpointSerDe 路由 + HeapInternalTimerService 内部 + 测试）；双后端（Memory/RocksDB）operator-state 恢复路径一致性核查在案（共享 `deserializeOperatorState`，storage 层后端无关）；CepOperator timer 注册表免疫性复核在案（`registeredEventTimeTimers` = Set\<Long\> 时间戳，非键控，自有状态名 `EVENT_TIME_TIMERS_STATE_NAME`）。
- [x] **先红后绿（Proof）**：**red 证据**（2026-08-13 实测，pre-fix）——core：`testLongTimerKeyJsonRoundTripFiresWithLongKey` `ClassCastException: Integer cannot be cast to Long` + `testPojoTimerKeyJsonRoundTripFiresWithPojoKey` fired key = `LinkedHashMap {id=k1, seq=42}` 断言失败；runtime：`testLongTimerKeySurvivesJsonCheckpointRestoreAndFires` `expected: <1> but was: <0>`（JSON round-trip 恢复后无输出）。**green 证据**（post-fix）——core 11/11 + runtime 4/4（既有 String 键用例不改写）。
- [x] **无静默跳过复核（Proof）**：新增 `testTimerKeyRematerializationFailureFailsFast`（String 键 snapshot → Long 键 service + keyType 声明 → `assertThrows(StreamException)`，`ERR_STREAM_STATE_ERROR` 路径实测）；对照 guide #24 自检：重物化失败显式抛错（非吞异常非静默保留）。
- [x] 文档同步（Phase 1 裁定 P0）：`ai-dev/design/nop-stream/checkpoint-design.md` §2.1 timer state 行补重物化语义（AR-22 修复 + 边界发现）+ `ai-dev/logs/2026/08-13.md` 条目（Phase 1-2 执行记录）。

Exit Criteria:

- [x] Fix 落地 + 接线验证（restore→open→timer 触发→键控状态命中链路运行时连通，E2E 绿为证，非仅类型系统）
- [x] 类别清扫证据在案（grep 清单 + 双后端结论 + CEP 免疫）
- [x] 先红后绿证据在案（pre-fix 红：core CCE/断言失败 + runtime 无输出；post-fix 绿：core 11/11 + runtime 4/4）
- [x] 无静默跳过：重物化失败路径抛 `ERR_STREAM_STATE_ERROR`（fail-fast 用例在案）
- [x] 相关 owner doc 已同步（`checkpoint-design.md` §2.1）
- [x] `ai-dev/logs/` 对应日期条目已更新（2026/08-13.md Phase 1-2 条目）

### Phase 3 - 全量验证 + 收口

Status: completed
Targets: `nop-stream` 全模块 + 门禁 + E2E

- Item Types: `Proof | Follow-up`

- [x] **E2E（Proof）**：作业级重启级 E2E——`TestTimerCheckpointRestoreE2E.testLongTimerKeySurvivesJsonCheckpointRestoreAndFires`（新 Long 键 + JSON 持久化用例）：checkpoint 包含带 timer 的窗口（Long 键 123L < 2^31）→ **经 `CheckpointSerDe.serializeTaskStateSnapshot`/`deserializeTaskStateSnapshot` + `JsonTool` 的 storageType=local JSON 持久化路径**（仿 `testLongKeyedStateJobRestartRestoresSameKeys` 先例的存储层 round-trip）→ 重启（新 operator restoreState → open）→ timer 触发 → 断言键控状态命中 + 窗口结果正确输出（`"7"`）。**扩展方式**：新增并行嵌套类 `TestableLongKeyWindowOperator`（`WindowOperator<Long, ...>`），既有 String 键 3 用例不改写（Bug Fix Test Coverage Rule：prefer adding over rewriting）。**端到端验证（Anti-Hollow）**：checkpoint 持久化 → 恢复 → timer 触发 → 输出完整路径；**run2 零元素输入**（无任何元素经 processElement 路径触发窗口，Long 键 processElement 路径不可能掩蔽 timer 缺陷——输出只能来自恢复的 timer 回调）。先红后绿：pre-fix `expected: <1> but was: <0>`（静默丢失实证），post-fix 4/4 绿。
- [x] **门禁复跑（Proof）**：JUnit 门禁类全绿（`TestWiringExistenceInvariant` 10/0、`TestOutputContractInvariant` 10/0、`TestCheckpointIDCounterInvariant` 8/0、`TestSynchronizedCollectionInvariant` 12/0、`TestClusterRegistryConsistencyInvariant` 10/0、`TestWindowRoundTripInvariant` 9/0、`TestWindowOperatorMergingCleanupInvariant` 4/0、`TestCepReleaseSymmetryInvariant` 21/0）+ `node ai-dev/tools/check-nop-stream-invariants.mjs all` **exit 0**（pin 0）。**行号漂移重钉**：`WindowOperator.setKeyType` 接线（6 行）致 output-contract 注册表 WindowOperator 发射点 1192/2022 → **1198/2028 重钉**（先 V4/V5 红 → 重钉后 all 绿）。
- [x] **全量回归（Proof）**：`./mvnw test -pl nop-stream -am -T 1C` **2944 tests / 0 failures / 0 errors / 10 skipped**（core 1478 / runtime 830 / cep 327 / rocksdb 86 / 其余 0 失败；基线 2913 + 新增 4（core 3 + runtime 1）+ 后续批次既有增量），BUILD SUCCESS（复跑两遍稳定）。
- [x] **backlog/roadmap 状态流转（Follow-up）**：AR-22 条目标注修复完成（Status: `planned` → `done`，附裁定 + Fix + 先红后绿 + 类别清扫 + 边界发现完整记录）+ roadmap 新增 ✅ 收口条目（AR-22 P0，plan `2026-08-13-1615-1`）；`check-doc-links.mjs --strict` exit 0（0 errors）。
- [x] **独立 closure audit（Proof）**：fresh session 子 agent（`ses_005a80178ffeKcoAyqST2eRpY4`）按 guide closure 流程审计 **PASS**——逐项对照 live 代码 + 命令实测（core 11/11、runtime 4/4、mjs all exit 0、scan-hollow findings 与 `34aed42c1` 基线一致、doc-links 0 errors）+ Anti-Hollow 调用链追踪 + Deferred 分类检查（无静默降级）；evidence 已写入 plan Closure 段落。

Exit Criteria:

- [x] 作业级重启 E2E 通过（端到端完整路径：JSON 持久化 → 恢复 → timer 触发 → 输出；run2 零元素输入）
- [x] 门禁零命中（JUnit 全绿 + mjs `all` exit 0，output-contract 重钉在案）
- [x] `./mvnw test -pl nop-stream -am -T 1C` 全绿（2944/0/0/10）
- [x] backlog 状态流转（AR-22 → done）+ daily log 更新完成
- [x] 独立 closure audit PASS，evidence 在案

## Closure Gates

- [x] AR-22 严重级裁定在案（**P0**，Fix 落地，adjudication-table §18 + backlog 状态流转附依据）
- [x] Timer 键 JSON 恢复 keyType 重物化 Fix 落地（落点 A：`HeapInternalTimerService.setKeyType` + `restoreTimers` 内重物化），机制对齐 AR-01 `deserializeKey` 且无静默跳过（`ERR_STREAM_STATE_ERROR` fail-fast）
- [x] round-trip 单测 + 作业级重启级 E2E（timer 触发后键控状态命中）通过，先红后绿证据在案（red：CCE / LinkedHashMap 断言失败 / E2E 无输出；green：core 11/11 + runtime 4/4）
- [x] 类别清扫证据在案（timer snapshot 面全调用点 grep 零遗漏 + 双后端共享 `deserializeOperatorState` 一致 + CEP 免疫复核）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect（closure audit 分类检查 PASS）
- [x] 受影响的 owner docs 已同步到 live baseline（`checkpoint-design.md` §2.1 + adjudication-table §18 + roadmap AR-22）
- [x] 独立子 agent closure-audit 已完成并记录证据（`ses_005a80178ffeKcoAyqST2eRpY4`，plan Closure 段落）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan> --strict` exit 0（无未勾选项 + Closure Evidence 已写入）
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream --severity high` exit 0（或 findings 集与基线一致，按 Cycle 2 / I1 钉定判据 `34aed42c1`——audit 实测 findings 集与基线精确一致，touched 文件零命中）
- [x] **Anti-Hollow Check**：closure audit 已验证 restore→open→timer 触发→键控状态命中调用链运行时连通（代码追踪 + E2E 绿）；无空方法体/静默跳过/no-op 作为正常实现（`rematerializeEntry` 显式抛错）
- [x] `./mvnw compile`（`-pl nop-stream -am`，BUILD SUCCESS）
- [x] `./mvnw test`（`-pl nop-stream -am -T 1C`，2944/0/0/10 BUILD SUCCESS）
- [x] checkstyle / 代码规范检查通过（`-pl nop-stream -am`——模块既有基线 11340 条 pre-existing 违规为已知噪音（前序 plan 落档），本次变更集新代码行零违规（setKeyType/rematerializeEntry/接线行检查无命中）；mission 命令 `|| echo 'lint not configured'` 兜底语义生效）

## Deferred But Adjudicated

### C3-RL-8（CEP 面 P2，未触发）

- Classification: `watch-only residual`（backlog 既有裁决，adjudication-table.md §12.2）
- Why Not Blocking Closure: 本 plan 类别清扫限于 timer/operator-state 面，CEP 面不在清扫范围，触发条件未满足；如执行中触及 CEP 面则按触发条件评估并留痕。
- Successor Required: `no`

### AR-20 / P2-INV-6（独立失败面 P2，backlog 维持）

- Classification: `watch-only residual`（backlog 既有裁决）
- Why Not Blocking Closure: 独立失败面（算子级 `key.toString()` 键 / NFAState Queue 物化），本 plan 修复面不覆盖；既有触发条件维持。
- Successor Required: `no`

## Non-Blocking Follow-ups

- manifest 恢复 `latestCompletedCheckpoint` 回退边界复探（plan `2026-08-13-0132-2` 已登记，维持）。
- 窗口 reduce 自定义 POJO 累加器 JSON 序列化约束边界（plan `2026-08-13-0132-2` 已登记，维持）。

## Closure

Status Note: AR-22（P0）全 3 Phase 收口——裁定 P0（AR-01 同机制先例链，adjudication-table §18）+ Fix 落地（落点 A：`HeapInternalTimerService.setKeyType` + `restoreTimers` 内 `rematerializeEntry`，`WindowOperator.open()` 自 `keyClass` 注入）+ 先红后绿（core 3 + runtime E2E 1）+ 类别清扫零遗留 + 全量回归/门禁/文档全绿 + 独立 closure audit PASS。无 remaining plan-owned work。
Completed: 2026-08-13

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（general，fresh session）
- Audit Session: `ses_005a80178ffeKcoAyqST2eRpY4`
- Evidence:
  - Phase 1（裁定 + 先红）PASS：P0 裁定落档 adjudication-table §18（四对比项 + 双后端 + 边界发现）；Long/POJO 复现测试断言层绑定落点 A（restore→fire 层，非 DTO 层），red 实测（`ClassCastException: Integer cannot be cast to Long` / fired key = `LinkedHashMap {id=k1, seq=42}`）；双后端共享 `deserializeOperatorState` 核查在案
  - Phase 2（Fix + 类别清扫）PASS：`rematerializeEntry` 双表（event/processing）接入 + null 键/Object.class/`isInstance` 守卫 + `ERR_STREAM_STATE_ERROR` fail-fast（audit 逐行核对）；`WindowOperator.open()` 接线顺序实证（setKeyType :471 先于 restoreTimers :495-497）；类别清扫 grep 零遗漏 + CEP 免疫；`testTimerKeyRematerializationFailureFailsFast` 在案
  - Phase 3 PASS：E2E `testLongTimerKeySurvivesJsonCheckpointRestoreAndFires` 经 `CheckpointSerDe.serializeTaskStateSnapshot`/`deserializeTaskStateSnapshot` + `JsonTool` 真实 JSON 持久化 → 恢复 → timer 触发 → 输出（run2 零元素输入）；门禁 JUnit 全绿（8 类抽查 84 tests/0）+ mjs `all` exit 0（output-contract 1198/2028 重钉验证 live）；全量 `./mvnw test -pl nop-stream -am -T 1C` **2944 tests / 0 failures / 0 errors / 10 skipped**；backlog AR-22 → done + roadmap ✅ 收口条目
  - `node ai-dev/tools/check-plan-checklist.mjs <plan> --strict` 在本 Closure 写入 + 全部勾选后 exit 0（Closure Evidence 已写入）
  - Anti-Hollow 检查 PASS：调用链 `restoreState` → `open()`（setKeyType → 延迟 restoreTimers）→ `advanceWatermark` → `onEventTime` → `setCurrentKey` → `getWindowContents` → `emitWindowContents` 运行时连通（E2E 绿 + audit 代码追踪双重证据）；无空方法体/静默跳过/no-op（`rematerializeEntry` 显式抛错）；`scan-hollow-implementations.mjs --module nop-stream --severity high` findings 集与 Cycle 2 / I1 钉定判据 `34aed42c1` 基线**精确一致**（14 findings 全 pre-existing，touched 文件零命中）→ 门禁判据满足
  - Deferred 分类检查 PASS：C3-RL-8 / AR-20 / P2-INV-6 均既有 backlog watch-only residual（plan Non-Goals + Deferred 段声明），无 in-scope live defect 被静默降级；Non-Blocking Follow-ups 两项为 plan `2026-08-13-0132-2` 已登记维持项

Follow-up:

- 无 remaining plan-owned work
- Non-blocking（既有登记维持）：manifest 恢复 `latestCompletedCheckpoint` 回退边界复探 + 窗口 reduce 自定义 POJO 累加器 JSON 序列化约束边界（plan `2026-08-13-0132-2`）；非 @DataBean POJO 键 = JsonTool 层既有 fail-fast 守卫（非本 plan 面，落档 adjudication §18）
