# 2312 nop-stream R5：恢复面正确性（ST-11 + KR-7 族 + CC-11 + ST-03）

> Plan Status: completed
> Last Reviewed: 2026-10-05
> Source: ai-dev/audits/2026-10/2026-10-05-0523-deep-audit-nop-stream-quality-r5/02-state-backend-serialization.md（ST-11/ST-12/ST-03）、04-concurrency-resource.md（CC-11 降级裁定与遮蔽关系）、05-keyed-routing-window.md（KR-7 族）、对抗审查 AR-13 跨报告合并裁定；summary.md 修复归属 2312
> Related: 2311（已完成——ST-01/ST-13/ST-02）
> Review: 2026-10-05 独立子代理对抗性审查：4 Major + 6 Minor 全部吸收（F1 setKeyType 改恢复头派生；F2 VoidNamespace 还原分支为 Non-Goal 显式 carve-out；F5 泛型 @DataBean 运行时门裁定+验证；F6 form 携带 valueClassName 再水合；F7 deferred-to-open 纪律；行号修正）。

## Purpose

闭合 checkpoint **恢复面**的四个已确认缺陷：ProcessOperator timer 不持久化（ST-11）；join/over-window 工作缓冲无 JSON 往返形式——durable 默认下写侧必抛、降级配置下恢复侧静默清空（KR-7 族 = KR-7 + ST-12 + AR-13）；恢复合并不推进 insertionSeq（CC-11，被 ST-12 遮蔽的相邻缺陷）；rescale 重建丢弃快照信封 keyType 头（ST-03）。

## Current Baseline

- **ST-11（P1）**：`ProcessOperator`（core）的 `HeapInternalTimerService` 从不快照——全库 `snapshotTimers`/`restoreTimers` 仅 WindowOperator 消费；`setKeyType` 主代码唯一调用在 `WindowOperator:552`。恢复后 keyed ProcessFunction 的全部 event-time/processing-time timer 静默丢失。库内正确先例齐备：`WindowOperator` transient `restoredTimerSnapshot` + restoreState 捕获 + open() 延迟应用（:676-684/:576-578）+ `TimerStateKeys.WINDOW_INTERNAL_TIMERS="internal-timers"`；`CheckpointSerDe` 已有 TimerSnapshot 双向转换（serialize :851-876，deserialize :900-921 按 @type 分派）。
- **KR-7 族（P1）**：`PerKeyOrderedBuffer`（core common/buffer）无 JSON 往返形式；`EquiJoinOperator.snapshotState:144`/`OverWindowOperator.snapshotState` 直接 `putOperatorState(BUFFER_STATE, buffer)`。durable 默认（only-data-bean=true）下 persist 序列化必抛 `ERR_JSON_ONLY_DATA_BEAN_IS_SERIALIZABLE`；降级配置下 JSON 往返产出 `{}`，恢复侧 `instanceof PerKeyOrderedBuffer` 不成立 → 工作视图静默空（`putAll` 不执行），keyed mirror 仍在但匹配空转。`JoinSideRecord`（core operators/join）未标 `@DataBean`——写侧抛错的直接根因之一。恢复侧 `buffer.putAll(restored)`（EquiJoin:163-165、OverWindow:144-146）即 CC-11 触发点；**restore 先于 open**，此时 transient buffer 为 **null**（非空），任何直接 putAll 在内存路径 NPE——必须采用 WindowOperator :676-684/:576-579 的 deferred-to-open 纪律。
- **CC-11（P2，遮蔽）**：`PerKeyOrderedBuffer.putAll`（:56-62）不推进 `insertionSeq`，合并后新 `add` 分配的 (ts,seq) 可与合并条目碰撞静默覆盖。生产路径 restore-before-open 使碰撞当前不可达（04 维度复核降级裁定），但 KR-7 恢复管道修复后必须同步修，否则转化为静默数据丢失。
- **ST-03（P2）**：`RescaleStateAssembler.buildRescaledTaskState`（runtime execution，:181 起）合并快照条目后回填 `states` 丢弃信封 `keyType` 头（对照 `redistributePools` 保留 envelope）；对 rescale 产物再跑 reshard/rescale 时键重材料化失效，误分桶条目被 range 过滤静默丢弃。
- 2311 已提供：`MemoryKeyedStateBackend.getSnapshotKeyType()`（有效键类型）可供 setKeyType 使用。
- 测试基线：2311 收口后 core 1679 / cep / runtime 1233 / flow 全绿。

## Goals

- keyed ProcessFunction 的 timer 跨 checkpoint/savepoint 存活（event-time + processing-time），恢复后按原注册触发。
- join/over-window 工作缓冲在 durable 默认配置下可写出、经 JSON 往返后恢复为等价活对象（keys/values/insertionSeq 全保真）；in-memory 路径行为不变。
- putAll 合并后 insertionSeq 单调推进，(ts,seq) 不碰撞。
- rescale 产物信封保留 keyType（及 key-group ownership 已有字段）供二次 reshard/rescale 消费。

## Non-Goals

- 不修 KR-1/2/3/FLOW-5 路由一致性（2313）；不修 CC-01/02 并发活性（2314）；不修窗口 rescale operator-state 重分布（WIN-2 Phase 2，2316 只做 fail-fast）。
- 不改 `TimerSnapshot`/`PaneTrackingSnapshot` 既有协议**语义**；显式 carve-out：`TimerSnapshot.deserializeNamespace` **新增** VoidNamespace 字符串还原分支（additive、向后兼容——旧 form 无该值不受影响；当前 JSON 通道把 VoidNamespace 退化为字符串，恢复后 timer 与新注册不去重）。
- 不接线 `OverWindowOperator.rebuildViewFromKeyedState()`；但修正其两处失实注释（:47 "rebuilt from these entries on restore"、:159-162 区）与 AR-13 的注释失实登记。
- OverWindow `rebuildViewFromKeyedState()`（:257，零调用方）维持现状不改 scoping——传输协议修复后它降级为 legacy 兜底 API，登记 Follow-up。

## Scope

### In Scope

- core：`ProcessOperator`（timer 快照/恢复）、`TimerStateKeys`（新常量）、`PerKeyOrderedBuffer`（toSerializableForm/fromSerializableForm/putAll seq 推进）、`JoinSideRecord`（@DataBean）、`EquiJoinOperator`（restore 消费适配）、相关测试。
- cep：CepOperator 无改动（其台账传输已在 2311 覆盖键冻结；timer 台账恢复语义 CEP-7 归 2316）。
- runtime：`CheckpointSerDe`（serializeOperatorStates/deserialize 双向 PerKeyOrderedBuffer 分支）、`OverWindowOperator`（restore 消费适配）、`RescaleStateAssembler`（keyType 头保留）、相关测试。

### Out Of Scope

- RocksDB 后端行为；DSL/xdef；窗口 rescale 重分布。

## Execution Plan

### Phase 1 - ST-11：ProcessOperator timer 持久化

Status: completed
Targets: `core/.../operators/ProcessOperator.java`、`core/.../operators/TimerStateKeys.java`

- Item Types: `Fix`

- [x] `TimerStateKeys` 新增 `PROCESS_INTERNAL_TIMERS = "process-internal-timers"`
- [x] F1 修复（setKeyType 数据源）：`MemoryKeyedStateBackend` 新增包内 `noteRestoredKeyType(Class)`——由 `MemoryStateSerDe.restoreState` 在头覆盖生效时回写 effectiveKeyType；`ProcessOperator.open()` 在 keyed 后端创建块**之后**（applyPendingRestoreState 之后）执行 `internalTimerService.setKeyType(...)`，数据源 `keyedStateBackend instanceof MemoryKeyedStateBackend ? getSnapshotKeyType() : 跳过`（接口无访问器，不做接口扩展）
- [x] F2 修复：`TimerSnapshot.deserializeNamespace` 新增 `"VoidNamespace" → VoidNamespace.INSTANCE` 还原分支（对照 MemoryStateSerDe:751-754 先例）
- [x] `ProcessOperator`：transient `restoredTimerSnapshot` 字段；`snapshotState` 覆写（super 后 putOperatorState，timer service null 跳过并注释——WindowOperator 同口径）；`restoreState` 覆写（super 后 instanceof 捕获，**不直接应用**）；`open()` 在 setKeyType 之后延迟 `restoreTimers`（deferred-to-open 纪律）
- [x] 回归钉子：ProcessFunction 注册 event-time + processing-time timer → snapshotState → 经 `CheckpointSerDe.serializeTaskStateSnapshot/deserializeTaskStateSnapshot` 真实 JSON 往返 → 新算子 restoreState + open → timer 按原注册触发；**含非 String key 用例**（验证 setKeyType 接线，模板 TestTimerCheckpointRestoreE2E:229-270）（回退必红：移除 snapshotState 覆写即红；移除 setKeyType 接线则非 String 用例红）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 回归钉子回退必红验证记录进日志
- [x] `./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime` 全绿
- [x] 接线验证（Rule #23）：CheckpointSerDe TimerSnapshot 双向分支对 PROCESS_INTERNAL_TIMERS 生效（往返测试覆盖）
- [x] 无静默跳过：timer service null 跳过与 WindowOperator 口径一致并注释
- [x] No owner-doc update required（内部恢复语义修正）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - KR-7 族 + CC-11：工作缓冲 JSON 往返 + seq 单调

Status: completed
Targets: `core/.../common/buffer/PerKeyOrderedBuffer.java`、`core/.../model/JoinSideRecord.java`、`core/.../operators/join/EquiJoinOperator.java`、`runtime/.../operators/windowing/OverWindowOperator.java`、`runtime/.../checkpoint/storage/CheckpointSerDe.java`

- Item Types: `Fix`

- [x] `JoinSideRecord` 标注 `@DataBean`（写侧 ERR_JSON_ONLY_DATA_BEAN 抛错的直接根因之一）。裁定：only-data-bean 门是**运行时注解检查**（JsonSerializer.writeBean→beanModel.isDataBean），泛型类不碍运行时；PaneState javadoc 所记冲突属编译期 codegen 面，JoinSideRecord 非 codegen 生成——标注后以 Phase 2 钉子实证；若实证失败则回退为擦除泛型方案并记录
- [x] `PerKeyOrderedBuffer` 新增 `toSerializableForm()`/`fromSerializableForm(Map, Class<K>, Class<V>)`：insertionSeq + 每 key 条目（key、keyClassName、items[ts,seq,value]、valueClassName）全保真；**key 借 keyClassName、value 借 valueClassName 经 parseBeanFromText 再水合**（F6：Nop JSON 不回水合 @DataBean，否则恢复后 CCE）；form 结构与 CepOperator 台账信封（keyClass 先例）同风格；对畸形 form 类型化报错（有意偏离 TimerSnapshot 返回 null 的先例，显式声明）
- [x] `CheckpointSerDe.serializeOperatorStates` 新增 `instanceof PerKeyOrderedBuffer` 分支 → `toSerializableForm()`（置于 else 透传前）；`deserializeOperatorState` 新增 `@type` 分支 → 反射加载两个 className → `fromSerializableForm`（参照 TimerSnapshot 既有两分支）
- [x] `putAll` 推进 `insertionSeq`：遍历合并 tree 的 `lastKey()[1]` 取最大 seq，`insertionSeq = Math.max(insertionSeq, maxSeq + 1)`（add 为后置自增，条目 seq=自增前值，故 +1 防撞）；空合并不变（CC-11）
- [x] `EquiJoinOperator`/`OverWindowOperator` restore 采用 **deferred-to-open**：restoreState 仅 instanceof 捕获到 transient 字段（此刻 buffer 为 null）；open() 在 buffer 创建后应用（捕获 → `buffer.putAll(restored)`，等价替换且 seq 已由 putAll 保证单调）；删除/保留 Map 兼容入口由实现裁定并注释（SerDe 双向分支落地后 Map 分支为死代码，倾向删除）
- [x] `OverWindowOperator` 两处失实注释修正（:47/:159-162 区，AR-13 登记面）
- [x] 回归钉子（E2E 级，Rule #22）：join 算子缓冲若干条（含同 ts 多条验证 seq）→ snapshotState → `CheckpointSerDe` 真实 JSON 往返（durable 默认配置，不再抛 only-data-bean）→ restore + open → emitMatches 产出与不重启一致、后续 add 不与合并条目碰撞（CC-11 钉）——回退必红：移除 SerDe 双向分支即红；移除 putAll seq 推进则碰撞钉红

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 回归钉子回退必红验证记录进日志（含 durable 默认不再抛、往返保真两项）
- [x] putAll seq 碰撞单元钉（合并后 add 不覆盖）回退必红
- [x] `./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime` 全绿
- [x] 端到端验证（Rule #22）：join checkpoint E2E（runtime/flow 既有套件）全绿
- [x] 无静默跳过：fromSerializableForm 对畸形 form 类型化报错
- [x] No owner-doc update required 或已同步
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - ST-03：rescale 信封 keyType 保留

Status: completed
Targets: `runtime/.../execution/RescaleStateAssembler.java`

- Item Types: `Fix`

- [x] `buildRescaledTaskState` 合并信封时保留源快照的 `keyType` 头（与 key-group ownership 字段同路回填），并登记测试
- [x] 回归钉子：rescale 产物再经 reshard/rescale 过滤时键重材料化仍生效（对照 ST-03 场景：误分桶条目不再被静默丢弃）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 回归钉子回退必红验证记录进日志
- [x] `./mvnw test -pl nop-stream/nop-stream-runtime` 全绿
- [x] No owner-doc update required（内部元数据修复）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 所有 in-scope confirmed live defects（ST-11/KR-7 族/CC-11/ST-03）已修复
- [x] 所有 in-scope confirmed contract drifts 已收敛
- [x] 行为/契约结果已达成：恢复面钉子证明（timer 触发、缓冲往返保真、seq 单调、信封头保留）
- [x] 必要 focused verification 已完成（回退必红矩阵）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [x] 受影响的 owner docs 已同步，或明确写明 No owner-doc update required
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 验证（a）ProcessOperator timer 恢复在 open() 后真实重挂并可触发，（b）缓冲往返经真实 CheckpointSerDe JSON 序列化非 mock，（c）无空方法体/静默跳过
- [x] `./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime` 通过
- [x] checkstyle / 代码规范检查通过
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2312-nop-stream-r5-restore-correctness.md --strict` 退出码为 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream-core --severity high` 退出码为 0

## Deferred But Adjudicated

### OverWindow rebuildViewFromKeyedState 接线

- Classification: `watch-only residual`
- Why Not Blocking Closure: 传输协议修复后恢复直接从 operator-state 得到活 buffer；rebuild 是针对 legacy 快照的兜底 API，当前零调用方且 scoping 有限制——不接线不产生新的错误行为
- Successor Required: `no`
- Successor Path: 如后续支持 legacy 快照迁移再启用

## Non-Blocking Follow-ups

- EquiJoin 侧 rebuild 能力对齐（当前依赖传输协议，无 keyed-mirror 兜底）。

## Closure

Status Note: R5 恢复面四缺陷（ST-11 timer 丢失 / KR-7 族缓冲传输 / CC-11 seq 碰撞 / ST-03 信封丢头）全部修复；8 个回归钉子（core 3 + runtime 2 + process-timer 1 + envelope 1 + 畸形 form 1）经 6 探针回退必红矩阵验证；core+runtime 全量绿（runtime 1236 / 0F）。执行偏差（VoidNamespace 序列化分支、payload 嵌套泛型边界、双序恢复兼容）均已在代码注释与本日志披露。独立 closure audit APPROVE。
Completed: 2026-10-05

Closure Audit Evidence:

- Reviewer / Agent: 独立 fresh-session closure auditor（general-purpose 子代理，2026-10-05）
- Evidence:
  - Phase 1：TestProcessOperatorTimerCheckpointRestore 经真实 CheckpointSerDe JSON 往返断言 timer 存活 + Long 键重材料化；探针 snapshot/remove 与 setKeyType/remove 均 RED→绿（_tmp/plan2312-p1-mutation.log）
  - Phase 2：TestPerKeyOrderedBufferForm（往返再水合 + putAll seq 碰撞 + 畸形 form fail-fast）+ TestEquiJoinBufferJsonRoundTrip（durable 默认 JSON 往返 + 续 join）；探针 serialize 分支/deserialize 分支/putAll seq 均 RED→绿（_tmp/plan2312-p2-mutation.log）
  - Phase 3：TestRescaleEnvelopeKeyTypePreserved 直调 buildRescaledTaskState 断言信封 keyType 保留；探针 RED→绿（_tmp/plan2312-p3-mutation.log）
  - 全量：_tmp/plan2312-full.log core+runtime BUILD SUCCESS
  - Anti-Hollow：timer 恢复经真实 open() 延迟应用；缓冲往返经真实 CheckpointSerDe 非 mock；fromSerializableForm 畸形输入类型化抛错
  - `check-plan-checklist.mjs --strict` 退出码 0（completed 终态复跑）
  - Deferred 诚实性：OverWindow rebuild 接线为 legacy 兜底（传输修复后无新错误行为），EquiJoin rebuild 对齐为 follow-up

Follow-up:

- rebuild 接线（见 Deferred/Follow-ups）
