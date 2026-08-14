# 1 恢复路径完整性修复：MapState 容器值 JSON 重物化 + ChannelState 静默丢弃日志（P1-21-01 + P1-09-01）

> Plan Status: completed
> Last Reviewed: 2026-08-13
> Mission: nop-stream-invariant-loop
> Source: `ai-dev/audits/2026-08-13-1930-multi-audit-nop-stream-invariant-loop.md` P1-21-01 / P1-09-01
> Related: `2026-08-13-1615-1-nop-stream-ar22-timer-key-json-restore-materialization.md`（同机制 AR-22 修复基线，键控/定时器键面）；`2026-08-13-1243-1-nop-stream-engine-state-recovery-and-timer-fixes.md`（AR-01 键控键面）

## Purpose

把两个 P1 恢复路径缺陷收口到已修复状态：(a) `MemoryStateSerDe` 容器值（MapState value = List/Map 等容器）经 JSON checkpoint 恢复后内层元素类型丢失，CEP 恢复即 CCE 或条件求值静默错误（P1-21-01，与 AR-01/AR-22 同族最新变体）；(b) `ChannelState.fromSerializableForm` 恢复路径对不可解码 in-flight 记录静默丢弃，且注释虚假承诺"codec 路径有日志"（P1-09-01，exactly-once 载体无诊断降级）。

## Current Baseline

- **P1-21-01 live 缺陷点（已核实）**：
  - `MemoryStateSerDe.snapshotMapState`（`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/memory/MemoryStateSerDe.java:575`，方法体 :551-583）：`pair.add(serializeWithSerializer(me.getValue(), valueSer))`，无自定义 serializer 时 valueSer=null → `List<Event>` 原样进 JSON（`@DataBean` 序列化无 `@type`，审计探针实证）。
  - `MemoryStateSerDe.restoreMapState`（:227）：`Object mv = deserializeValue(me.get(1), valueClass)`，valueClass=`List.class` → `deserializeValue`（:897-899）`type.isInstance(obj)` 短路（ArrayList 是 List 实例）→ 容器原样返回，**内层元素不重物化**（恢复后为 LinkedHashMap）。
  - `CepOperator.open()`（`nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java:274-276`）：`getMapState(new MapStateDescriptor<>(EVENT_QUEUE_STATE_NAME, Long.class, (Class) List.class))` 生产无条件创建。
  - **元素类型来源约束（已核实）**：恢复侧无法从任何既有来源得知容器内层元素类型——① valueClass 为 raw `List.class`（无泛型信息）；② JSON 无 `@type`（探针实证）；③ `CepOperator` 的 `inputSerializer` 生产为 null（`PatternStreamBuilder.java:139` 硬编码 null）。→ 修复必须**在快照侧建立元素类型来源**（见 Phase 1 Decision 项），否则 CEP exit criterion 不可达。
  - 恢复触发面：storageType=local（默认）→ LocalFileCheckpointStorage → CheckpointSerDe JSON round-trip → restoreMapState。**RocksDB 后端同样受损（已核实，审计"免疫"结论不成立）**：`deserializeMap`（`RocksDBValueSerDe.java:80`）全仓**零调用（死代码）**；`deserializeList` 仅用于 ListState 面（elementType=descriptor.getValueType()，恰为该状态元素类型）；MapState 容器值面 `RocksDBSnapshotSerDe.restoreMapState`（:555）`deserializeObject(me.get(1), List.class)` → `List.class.isInstance(ArrayList)` 短路 → 与 Memory 同缺陷；运行时 `RocksDBMapState.get()`（:171）`deserialize(bytes, List.class)` → `parseBeanFromText` 无元素类型 → 内层同样丢类型（RocksDB 在无 checkpoint 的运行时 put/get 即丢类型，比 Memory 更早受损）。
  - 全仓无测试对 CEP 事件队列做 JSON 存储层 round-trip（TestCepCheckpointRestoreE2E 直传内存 OperatorSnapshotResult）。
  - **既有修复基线**：AR-01（`deserializeKey` 键控键重物化）、AR-22（`HeapInternalTimerService.rematerializeEntry` timer 键重物化）均已落地，但均为"键"面；容器 value 内层元素重物化从未覆盖。
- **P1-09-01 live 缺陷点（已核实）**：
  - `ChannelState.fromSerializableForm`（`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/checkpoint/ChannelState.java:162-192`）：三条静默旁路——:166-168 非数字 channelIndex `continue`、:171-173 非 List `continue`、:176-178 非 Map `continue`；:183-187 catch Exception 空块（注释声称"observable via logging in the codec path"但 codec 零日志）。
  - `StreamElementCodec.decode`（`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/transport/StreamElementCodec.java:91-146`）：grep 全文件 `LOG.` 0 命中，仅抛 StreamException。
  - 违约对照：state 侧同类版本漂移 fail-fast（`ERR_STREAM_STATE_SCHEMA_MISMATCH`）；`docs-for-ai/02-core-guides/error-handling.md` per-element 隔离规则要求 `LOG.warn(..., e)` 且 throwable 作末参数。
  - unaligned checkpoint 恢复主路径调用（ChannelState 恢复 = exactly-once in-flight 记录唯一载体），decode 失败即静默丢记录 → exactly-once 静默降级为 at-least-once。
- **测试缺口**：`TestCheckpointSerDeChannelState`（:38-51）仅覆盖正常 round-trip，无畸形索引/损坏 payload 负面测试；CEP 侧无 JSON 存储层 round-trip 测试。

## Goals

- P1-21-01：容器值（List/Map 及嵌套）恢复时按元素类型递归重物化（元素类型来源在快照侧建立，见 Phase 1 Decision）；**Memory + RocksDB 双后端 MapState 容器值面统一修复**（RocksDB 同样受损，非免疫）；CEP `elementQueueState` JSON 存储层 round-trip 回归测试（先红后绿）。
- P1-09-01：ChannelState 恢复所有跳过路径（畸形索引 / 非 List / 非 Map / decode 失败）至少 `LOG.warn(..., e)` 留证（throwable 作末参数），删除/修正虚假注释；补两条负面测试钉住 skip 契约。
- 双后端一致性验证：同一 JSON checkpoint 快照 Memory/RocksDB 恢复行为一致（容器值面）。
- 无静默跳过（guide #24）：恢复路径任何跳过必须可观测。
- 门禁复跑零命中（JUnit 门禁 + mjs `all`）+ `./mvnw test -pl nop-stream -am -T 1C` 全绿。

## Non-Goals

- 不处理 P1-18-02（component-roadmap C5 状态表，独立 doc plan）。
- 不处理 AR-01 G52 liveness（独立 plan `2026-08-13-1930-2`）。
- 不处理 P2 批次（节点级 parallelism、serializeWithSerializer 降级、MapState 内层 key 兼容路径等，backlog 维持既有触发条件）。
- **RocksDB 侧**：仅覆盖 MapState 容器值恢复/运行时读取路径（与 Memory 同缺陷，一并修复）；其余 RocksDB 面（ListState 逐元素处理已正确、schema 指纹、迁移等）不动。
- 不引入新的不变式门禁（如执行中发现新失败类 → I6 评估 Loop Rule）。

## Scope

### In Scope

- `MemoryStateSerDe.snapshotMapState` / `deserializeValue` / `restoreMapState` 容器值面修复（List/Map 内层元素，含嵌套）。
- **元素类型来源（Decision）**：恢复侧无既有元素类型来源（valueClass=raw List.class、JSON 无 @type、inputSerializer 生产 null）→ 需在快照侧建立来源。候选（实施时裁定，测试断言必须绑定所选方案）：(A) 快照时记录元素类型（如 stateInfo 增加 `mapValueElementType` 字段；来源 = snapshot 侧 live 容器值首元素 class 或 descriptor serializer 类型——双后端 snapshot 路径均可达）；(B) snapshotMapState 序列化时以类型感知方式输出（如带 `@type` 的 JSON），恢复侧据此重物化。**不可选**：仅靠 restore 侧推断（无任何信息可推断）。
- RocksDB 侧 MapState 容器值面修复（`RocksDBSnapshotSerDe.restoreMapState` 短路 + `RocksDBMapState.get()` 运行时读取），与 Memory 同策略。
- CEP `elementQueueState` JSON 存储层 round-trip 回归测试（core 或 runtime 层，覆盖恢复后元素类型正确 + 条件求值匹配成立）。
- `ChannelState.fromSerializableForm` 三条旁路 + decode catch 日志化。
- `StreamElementCodec` 日志（如 decode 失败点补 LOG）。
- 两条负面测试（畸形索引 / 损坏 payload）。
- 双后端一致性验证（Memory vs RocksDB 同一快照）。
- 文档：`checkpoint-design.md`（若 §2.x 容器值/通道状态恢复语义需同步）、roadmap backlog 状态流转、daily log。

### Out Of Scope

- 键控键 / timer 键重物化（AR-01/AR-22 已修复，不回改）。
- unaligned 通道状态其余语义（epoch/fencing 面，已有 Stage 39/43 覆盖）。
- RocksDB ListState 逐元素处理（已正确）、RocksDB schema 指纹/迁移等其他面。
- 其余 P2 backlog 批次。
- 门禁沉淀（本 plan 不引入新门禁）。

## Execution Plan

### Phase 1 - MapState 容器值 JSON 恢复递归重物化（P1-21-01）

Status: completed
Targets: `nop-stream-core/.../MemoryStateSerDe.java`、`nop-stream-cep/.../CepOperator.java`（仅测试面）、`nop-stream-rocksdb/.../RocksDBValueSerDe.java`、`RocksDBSnapshotSerDe.java`、`RocksDBMapState.java`、`nop-stream-core/src/test`、`nop-stream-runtime/src/test`（E2E 面）

- Item Types: `Decision | Fix | Proof`

- [x] **Decision：元素类型来源方案**（先于 Fix 定稿，否则先红后绿死锁）：在 (A) 快照侧 stateInfo 记录 `mapValueElementType`（来源 = snapshot 侧 live 容器值首元素 class / descriptor serializer 类型）与 (B) 快照序列化时输出类型感知 JSON 之间裁定；**RocksDB 侧恢复/读取需同等可达**（同一快照格式双后端共享，选 A 则 RocksDB restoreMapState 读同字段；选 B 则 JSON 自带 @type）。裁定落档（plan 或 daily log），测试断言绑定所选方案。
- [x] 复现测试（先红）：核心层 round-trip 测试——`MapState<Long, List<Event>>`（Event 为 @DataBean）经 `serializeWithSerializer(null)` → JSON → `restoreMapState` 恢复后断言 `mv.get(0).getClass() == Event.class`；预期 pre-fix 为 LinkedHashMap（红）。
- [x] 复现测试（先红）：CEP 面 JSON 存储层 round-trip——`TestCepCheckpointRestoreE2E` 或新增测试经 CheckpointSerDe/JsonTool 完整 round-trip（storageType=local 路径），恢复后运行事件匹配，断言匹配成立且元素类型为 Event（pre-fix 红：LinkedHashMap → CCE 或求值错误）。
- [x] Fix：按 Decision 落点在快照/恢复侧实现容器值元素类型来源 + `deserializeValue` 容器值递归重物化（`type` 为 List/Collection/Map 时按元素类型重物化，含嵌套）；**无元素类型信息时的降级路径必须 LOG.warn（不静默损坏）**。
- [x] Fix：`restoreMapState` 对容器值路径调用新递归逻辑（valueClass=List.class 不再短路）。
- [x] Fix：RocksDB 侧同策略——`RocksDBSnapshotSerDe.restoreMapState`（:555）容器值路径 + `RocksDBMapState.get()`（:171）运行时读取（与 Memory 双端对齐，消除「Memory 修复后双后端不一致」风险）。
- [x] 验证（先红后绿）：核心层 + CEP 面 round-trip 测试全绿。
- [x] 类别清扫：grep 全仓 `restoreMapState` / `deserializeValue` / `snapshotMapState` 调用点，确认容器 value 面全部覆盖；`deserializeMap` 死代码确认（零调用，不引作修复依据）；双后端同快照断言（Memory/RocksDB 同一 JSON 快照恢复行为一致）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 核心层 round-trip 测试：恢复后 List 内层元素类型 == 原始元素类型（@DataBean Event 实例，非 LinkedHashMap）。
- [x] CEP 面 JSON 存储层 round-trip 测试：恢复后事件队列元素类型正确且条件求值/匹配成立（post-fix 绿，pre-fix 红证据在案）。
- [x] **元素类型来源在案**：恢复路径有明确元素类型来源（快照侧记录或类型感知 JSON），非「仅靠 restore 侧推断」（无信息可推断，已核实）；无来源场景的降级路径 LOG.warn 且测试覆盖。
- [x] **端到端验证**：从 checkpoint 持久化（snapshotMapState → CheckpointSerDe JSON）到恢复（restoreMapState → CepOperator.open 消费 elementQueueState）完整路径已验证。
- [x] **接线验证**：CepOperator 生产创建 elementQueueState 路径（:274-276）与修复后恢复路径运行时连通（测试经真实 storage 层 round-trip 而非内存直传）。
- [x] **无静默跳过**：无元素类型信息时的降级路径必须 LOG.warn（如存在），不静默返回损坏容器。
- [x] 双后端一致性：同一 JSON 快照 Memory/RocksDB 恢复行为一致（容器值面）——RocksDB `restoreMapState`/`RocksDBMapState.get()` 修复后与 Memory 同策略（含运行时读取路径）。
- [x] 文档：`checkpoint-design.md` 若涉及容器值恢复语义更新；否则写 `No owner-doc update required`。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - ChannelState 恢复静默丢弃日志化（P1-09-01）

Status: completed
Targets: `nop-stream-core/.../checkpoint/ChannelState.java`、`nop-stream-core/.../execution/transport/StreamElementCodec.java`、`nop-stream-core/src/test`

- Item Types: `Fix | Proof`

- [x] Fix：`ChannelState.fromSerializableForm` 三条旁路（:166-168 畸形索引、:171-173 非 List、:176-178 非 Map）各补 `LOG.warn`（含 channelIndex/key 与原因）。
- [x] Fix：:183-187 decode catch 补 `LOG.warn(..., e)`（throwable 作末参数，per-element 隔离规则），修正/删除"observable via logging in the codec path"虚假注释；如 `StreamElementCodec.decode` 内部无日志点，在 ChannelState catch 处记录。
- [x] 负面测试（两条）：畸形 channelIndex 键（如 "abc"）→ 恢复不失败且日志留证；损坏 payload（不可解码的 Map）→ 该条跳过、其余记录正常恢复、日志留证。
- [x] 验证：`TestCheckpointSerDeChannelState` 既有测试 + 新负面测试全绿。
- [x] 类别清扫：grep 全仓其他 `fromSerializableForm` / 恢复路径 catch 空块同形态（对照 state 侧 `ERR_STREAM_STATE_SCHEMA_MISMATCH` fail-fast 面），确认无同类静默。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 三条旁路 + decode catch 全部有 `LOG.warn`（含 throwable 或原因字段）。
- [x] 虚假注释已删除/修正（不再声称不存在的 codec 日志）。
- [x] 两条负面测试存在且验证 skip 契约（不 fail 整个恢复 + 可观测日志）。
- [x] 既有 `TestCheckpointSerDeChannelState` 正常路径测试未回退。
- [x] **无静默跳过**：恢复路径任何跳过均有日志；与 state 侧 fail-fast 面差异在文档中说明（best-effort 跳过 vs 版本漂移 fail-fast 的分工）。
- [x] 文档：`checkpoint-design.md` 若涉及 unaligned 通道状态恢复语义更新；否则写 `No owner-doc update required`。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见本 guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [x] P1-21-01 已修复（容器值递归重物化 + 元素类型来源在案 + CEP JSON round-trip 回归测试绿）。
- [x] P1-09-01 已修复（恢复路径跳过日志化 + 两条负面测试）。
- [x] 双后端一致性验证通过（容器值面，Memory + RocksDB 同策略）。
- [x] 类别清扫完成，无同类静默路径遗留。
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift。
- [x] 受影响的 owner docs 已同步到 live baseline，或明确写明 No owner-doc update required。
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据。
- [x] **Anti-Hollow Check**：closure audit 已验证（a）snapshot → JSON 持久化 → restore → CEP 消费调用链运行时连通，（b）无空方法体/静默跳过/no-op 作为正常实现。
- [x] `./mvnw test -pl nop-stream -am -T 1C`（全绿）
- [x] `./mvnw compile -pl nop-stream -am`（或对应模块）
- [x] mjs `all` exit 0（JUnit 门禁 + 注册表）
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### MapState 内层 key 在无 mapKeyType 记录的兼容路径不重物化（P2-15-01b）

- Classification: `watch-only residual`（既有 backlog 条目）
- Why Not Blocking Closure: 独立于本 plan 的容器 value 修复面（legacy 快照 keyTypeName==null 兼容路径，触发需旧版快照 + 非内置 key 类型）；已登记 backlog，触发条件维持。
- Successor Required: `no`

### serializeWithSerializer 静默降级（P2-09-02c）

- Classification: `watch-only residual`（既有 backlog 条目）
- Why Not Blocking Closure: 独立缺陷面（自定义 IStreamSerializer 失败降级），默认 JsonToolSerializer 不受影响，本 plan 修复面不涉及。
- Successor Required: `no`

## Non-Blocking Follow-ups

- P2 批次全部维持 backlog 既有触发条件（本 plan 类别清扫中发现的新同族项自动登记）。
- 若 Phase 1 类别清扫发现其他容器值消费点（非 MapState 面），登记 backlog。

## Closure

Status Note: 两 Phase 全部 item + Exit Criteria 勾选；独立 closure audit PASS；全量回归绿（2956 tests / 0 failures / 0 errors）；mjs all + doc-links + repo checkstyle.xml + scan-hollow 全 exit 0；roadmap ✅ 收口条目 + daily log 落档。
Completed: 2026-08-13

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session `ses_0045deb63ffeFt0ukxLrAGD5TT`），VERDICT = PASS
- Evidence:
  - **Phase 1 Exit Criteria（9/9 PASS，live code + test 证据）**：
    - 核心层 round-trip：`TestMemoryStateSerDeContainerValueRestore` 5 用例（List<Event> / Map<String,Event> / 嵌套 / List<Long> 标量重定类型 / legacy raw 降级不崩）——pre-fix 红（4 CCE）+ post-fix 绿。
    - CEP 面 JSON 存储层 round-trip：`TestCepCheckpointRestoreE2E.testE2EElementQueueSurvivesJsonStorageLayerRoundTrip`——pre-fix 红（生产路径 `onEventTime processEvent` CCE LinkedHashMap→DataBeanEvent）+ post-fix 绿（匹配 start42->end 成立）。
    - 元素类型来源在案：快照侧建立（Decision B，`ContainerValueCodec` per-level 首元素 class 包装器，双后端同格式）；无来源 legacy 降级 `LOG.warn`（`decode` :147）且测试覆盖（legacy raw 用例）。
    - 端到端验证：snapshotMapState → JsonTool JSON → restoreMapState → CepOperator.open 消费 elementQueueState 完整链路（CEP 测试经真实 storage 层 round-trip 非内存直传）。
    - 接线验证：`CepOperator.open()` :274-275 `getMapState(EVENT_QUEUE_STATE_NAME, Long.class, List.class)` 返回 restoreMapState 放置的状态（closure audit 追踪 `CepOperator.restoreState` → `applyPendingRestoreState` → `MemoryKeyedStateBackend.restoreState` → `restoreMapState` → `deserializeValue` 容器门 → `ContainerValueCodec.decode` 运行时连通）。
    - 无静默跳过：四类降级/失败路径 `LOG.warn` 实证（ContainerValueCodec :147/:197/:221/:225/:257）。
    - 双后端一致性：`TestRocksDBSnapshotRestore.testContainerValueSnapshotRestoresIdenticallyAcrossBackends`（同一 JSON 快照 Memory/RocksDB 恢复一致）+ `testMapStateContainerValueRuntimePutGet`（RocksDB 运行时 put/get 面）+ `testMapStateContainerValueJsonRoundTrip`。
    - 文档：`checkpoint-design.md` §2.5 keyed state 行更新。
    - daily log：`ai-dev/logs/2026/08-13.md` 本 plan 条目。
  - **Phase 2 Exit Criteria（7/7 PASS）**：四类跳过（NFE :171-176 / 非 List :179-183 / 非 Map :186-190 / decode :195-205 throwable 末参数）全 `LOG.warn`；虚假注释删除；3 条负面测试（ListAppender 实证日志 + skip 契约不 fail 恢复 + throwable proxy 断言）；既有 3 用例未回退（6/6 绿）；类别清扫零同类静默（CheckpointSerDe 恢复路径已 LOG.warn 实证；core/runtime main 零空 catch 块）；`checkpoint-design.md` §2.11.4 分工说明；daily log 条目。
  - **Closure Gates（实测）**：`./mvnw test -pl nop-stream -am -T 1C` BUILD SUCCESS **2956 tests / 0 failures / 0 errors**（surefire 聚合，closure audit 复跑实证）；`./mvnw clean install -DskipTests -pl nop-stream -am -T 1C` SUCCESS；`./mvnw compile -pl nop-stream -am -q` exit 0；mjs `all` exit 0；`check-doc-links.mjs --strict` exit 0（0 errors，3 条 warning 为无关历史 plan 既有断链）；repo `checkstyle.xml`（真实门禁）4 模块 0 违规（默认 Sun 检查 11K+ 条 = 仓库级既有债，本次变更行零新增命中）；`scan-hollow-implementations.mjs` core/cep/rocksdb/runtime 4 模块 exit 0 零发现；`check-plan-checklist.mjs <plan> --strict` exit 0。
  - **Anti-Hollow Check**：closure audit 追踪 CEP 恢复调用链运行时连通（restoreState → pendingRestoreState → open → applyPendingRestoreState → backend.restoreState → restoreMapState → deserializeValue → ContainerValueCodec.decode → open() getMapState 消费）；`ContainerValueCodec` 与全部 touched 方法无空方法体/静默 no-op（hollow scan 0 findings）。
  - **Deferred 分类检查**：P2-15-01b（legacy 快照无 keyType 的内层 key 路径）与 P2-09-02c（自定义 IStreamSerializer 失败降级）均为独立触发面的 watch-only residual，非本 plan 容器值元素类型面的静默降级（closure audit §9 逐条复核 PASS）。
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2026-08-13-1930-1-nop-stream-restore-path-serde-channel-fixes.md --strict` 退出码为 0（Closure Evidence 已写入后复核）。

Follow-up:

- 无 remaining plan-owned work。Non-Blocking：CEP SharedBuffer/NFAState 族非 @DataBean 无法整快照 JSON 持久化（序列化点 `ERR_JSON_ONLY_DATA_BEAN_IS_SERIALIZABLE`）维持 backlog P2-INV-6/P2-TST-9 既有触发条件；ValueState 等其他容器值面（非 MapState）JSON 恢复降级 warn 已可观测（`deserializeValue` 容器门通用覆盖），编码侧未包装 = out-of-scope（本 plan 范围为 MapState 容器值面）。
