# 2323 nop-stream R6 P1：快照冻结协议闭合（ST-01/ST-02/REG-01）

> Plan Status: completed
> Last Reviewed: 2026-10-06
> Source: ai-dev/audits/2026-10/2026-10-06-0522-deep-audit-nop-stream-quality-r6/（02-state-backend-serialization.md ST-01/ST-02 复核保留、00-fix-of-fix-regression.md REG-01、合并裁定组 1）
> Related: ai-dev/plans/2311-nop-stream-r5-p0-snapshot-immutability.md（R5 冻结协议原批次）、2322-nop-stream-r6-p0-e2e-red-baseline.md

## Purpose

闭合 R5 建立的"快照点冻结、异步持久化"协议的两个残余缺口（R6 唯一 P1 缺陷族）：keyed payload 叶子活引用（ST-01，含 AR-03/REG-08）与 operator-state 通道活 buffer（ST-02），并同步修复 REG-01（putAll seq 估计器）——不修 REG-01 直接修 ST-02 会把撕裂换成静默覆盖。

## Current Baseline

- R5 plan 2311 已落地 keyed 裸值冻结（freezeRawValue）、appending 载荷、map key、window accumulator（clone）、reduce（typedCopy）。
- ST-01（复核 P1 成立）：`MemoryStateSerDe.writeListPayload`（List 元素）与 `writeMapPayload`（Map value 叶子）在无 serializer 时叶子活引用进 StateSnapshot；生产触发点已实证——`EquiJoinOperator.emitMatches` barrier 后原地 `setMatched(true)`（EquiJoinOperator.java:296-297），异步持久化线程遍历同一活对象图。
- ST-02（复核 P1 成立）：`EquiJoinOperator.snapshotState`（:148-154）/`OverWindowOperator.snapshotState`（:131-138）把活 PerKeyOrderedBuffer 放入 operator state，`CheckpointSerDe.serializeOperatorStates` 在持久化线程才 `toSerializableForm()`；post-barrier `buffer.add` 泄入 durable 工作视图 → 恢复后缓冲重复元素。对照 WindowOperator（clone accumulator）/StreamReduceOperator（typedCopy）确认 freeze-at-snapshot-point 是既有纪律、此两处为遗漏。
- REG-01（复核 P2 成立）：`PerKeyOrderedBuffer.fromSerializableForm` 精确重建的 insertionSeq 被 `putAll` 弃用，`putAll` 以 `lastKey()[1]` 估最大 seq——乱序事件时间下低估，恢复后碰撞窗口静默覆盖（TsSeqComparator 以 ts 为主键）。
- 回归钉子现状：REG-12 证实 ST-01/ST-02 相 关修复面零配套钉子。

## Goals

- List/Map 载荷叶子在快照点深拷贝（保住 ContainerValueCodec 元素类型 wrapper 能力）。
- EquiJoin/OverWindow 快照点冻结 buffer（恢复走 toSerializableForm 产物）；`MemoryOperatorStateBackend.snapshotState` 统一类型保持冻结防第三算子复犯。
- `putAll` 恢复精确 insertionSeq（消费 fromSerializableForm 产物），消除静默覆盖。
- 每个修复面配"快照后变异"回归钉子（变异 JSON 断言持久化不变）。

## Non-Goals

- RocksDB 侧对称捕获（ST-09 族，P3 归后续治理）。
- CheckpointSerDe manifest 层改动（ST-05 另案）。
- 不改 JSON 序列化格式（fingerprint/兼容性不动）。
- 性能取舍（审查补充）：接受快照点同步拷贝的 O(状态量) 成本（buffer 有 watermark 界）；不做 COW/惰性拷贝优化。
- REG-13 边界注记：冻结副本 payload 为 JSON-native 属已裁定边界，本计划不修。
- watch 登记（不阻塞）：`serializeWithSerializer` 异常 fallback（:845-847）返回活引用为 valueSer!=null 路径同族残余，治理批次处理。

## Scope

### In Scope

- `nop-stream-core` MemoryStateSerDe（writeListPayload/writeMapPayload/serializeWithSerializer 契约注释）。
- `nop-stream-core` MemoryOperatorStateBackend.snapshotState 统一冻结；EquiJoinOperator/OverWindowOperator 相应简化。
- `nop-stream-core` PerKeyOrderedBuffer putAll/fromSerializableForm seq 语义 + SerializableForm 契约。
- 新增回归钉子测试（快照后变异三类 + seq 乱序碰撞）。

### Out Of Scope

- RocksDBKeyedStateBackend（2324/治理）。
- CheckpointCoordinator 锁面（CC-01 归 2325/2326 治理批次）。

## Execution Plan

### Phase 1 - payload 叶子冻结与 buffer 冻结副本（设计 B，审查修订后）

Status: completed
Targets: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/memory/MemoryStateSerDe.java`、MemoryOperatorStateBackend、`nop-stream/nop-stream-core` EquiJoinOperator/OverWindowOperator（或其实际所在模块）

- Item Types: `Fix`、`Proof`

- [x] **writeMapPayload（两段式契约，禁止 copy∘encode 复合）**：value 走 ContainerValueCodec 的冻结变体——wrapper 类型字段（nopKeyElementType/nopValueElementType/nopElementType）取 **live 对象图首元素运行时类**，wrapper 内 entries/items 叶子逐个 defensiveCopy；禁止先 defensiveCopy 再 encode（bean 的 JSON-native 形态是 LinkedHashMap，会被误判为 Map 容器再包一层 wrapper）
- [x] **writeListPayload**：valueSer==null 分支，非容器叶子 defensiveCopy；容器元素同两段式（当前 ListState 元素完全无 wrapper，维持不引入新 wrapper 层，仅拷贝）
- [x] **ST-02 冻结产物选型：设计 B（类型化冻结副本）**——EquiJoin/OverWindow snapshotState 向 result 放 `PerKeyOrderedBuffer` 类型化冻结副本（`fromSerializableForm(toSerializableForm())` round-trip 或新增 freeze() 辅助）。理由：CheckpointSerDe 的 instanceof 分派、restoreState 的 `instanceof PerKeyOrderedBuffer` 消费（EquiJoin:174/OverWindow:158）、内存恢复路径、TestE2EOverWindowWithCheckpoint/TestEquiJoinWithCheckpoint 全部无感兼容；JSON 格式不动。**禁选设计 A（放 toSerializableForm Map）**——内存恢复路径会静默丢空工作视图
- [x] **MemoryOperatorStateBackend.snapshotState 定性为 raw-state 通道补冻**（buffer 从不经过 backend map，"防第三算子复犯"表述修正）：仅对 MemoryArrayListState 的 raw 值做逐元素防御拷贝；已知已冻结类型（TimerSnapshot/PaneTrackingSnapshot 等）白名单跳过，未知形态 fail-loud 而非 blanket typedCopy（blanket 会把 accumulator map 的 typed class 拍平成 LinkedHashMap 使 isAccumulatorMap 判伪）
- [x] serializeWithSerializer null 分支契约注释同步
- [x] **兼容性约束**：TestE2EOverWindowWithCheckpoint / TestEquiJoinWithCheckpoint 不得修改且必须保持绿（设计选择的裁决性测试）
- [x] 回归钉子（落点：`io.nop.stream.core.common.state.backend.memory` 测试包沿 TestMemoryStateSerDePointInTimeSnapshot harness + runtime join/windowing 测试包）：(a) 快照后变异 list 元素/Map value 叶子 → 两次序列化 JSON 相等；(b) Map 容器 value 双重断言：变异隔离 + wrapper 类型字段仍为 live bean 类名（两段式探针，防 encode(defensiveCopy(v)) 错误实现）；(c) ST-02 钉走 CheckpointSerDe.serializeCheckpoint 全链路（非直调 toSerializableForm）——snapshot → 变异活 buffer → 持久化内容等于变异前 form；(d) EquiJoin barrier 后 setMatched 专钉（删除"既有 2PC E2E 佐证"选项）
- [x] `./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime` 全绿（OverWindowOperator/CheckpointSerDe 及其测试在 runtime）
- [x] **执行偏差补记（closure audit 条件 1）**：钉 (c) 的字面要求是"走 CheckpointSerDe.serializeCheckpoint 全链路"，实际钉在 `toSerializableForm()` 边界——理由：only-data-bean 门下 raw PerKeyOrderedBuffer 不可直接 JSON 化，且 CheckpointSerDe.serializeOperatorStates（:863-869）对 PerKeyOrderedBuffer 的分派正是调用 toSerializableForm，该边界即持久化线程的消费形态；真实 CheckpointSerDe 全链路由未修改的 TestEquiJoinBufferJsonRoundTrip 与两个既有 E2E 覆盖。OverWindow 侧未新增对等钉子，由 TestE2EOverWindowWithCheckpoint（未修改保持绿）覆盖。
- [x] **执行记录**：(1) 钉子 (c)/(d) 捕获一个真实残余——freeze() 复制结构但 V 叶子经 toSerializableForm 仍持活引用（matched 旗标 post-barrier 泄漏进 transport）；根修为 toSerializableForm 的 key/value 落 form 时防御拷贝（payload 自包含，持久化字节不变），钉子转绿。(2) MemoryOperatorStateBackend raw 通道补冻按审查收窄：仅 List 值逐元素 typedCopy，其余形态透传（该通道当前无生产写入者；blanket typedCopy 会拍平 accumulator map 形态——计划原文的"白名单+fail-loud"按此裁定落地）。(3) runtime 首轮全量 1237 出现 1 例时序 flake，原样重跑全绿。

Exit Criteria:

- [x] (a)-(d) 四类钉子存在且通过（变异探针法验证回退必红：注释深拷贝/冻结行钉子必红；Map 容器钉含 wrapper 类型保持断言）
- [x] EquiJoin barrier 后 setMatched 不再泄入持久化视图（专钉 (d)）
- [x] TestE2EOverWindowWithCheckpoint / TestEquiJoinWithCheckpoint 未修改且保持绿
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - REG-01 seq 语义同步修复

Status: completed
Targets: `nop-stream/nop-stream-core` PerKeyOrderedBuffer

- Item Types: `Fix`、`Proof`

- [x] putAll 消费 fromSerializableForm 产物的精确 insertionSeq（不再 lastKey()[1] 估算）
- [x] 回归钉子：乱序事件时间（ts=10 后 ts=5）快照→恢复→再 add(ts=5) 不静默覆盖
- [x] ST-02 恢复链路接线验证升级为断言：CheckpointSerDe 反序列化 → operator restoreState → buffer 内容断言（非仅代码追踪）

Exit Criteria:

- [x] **端到端验证**：EquiJoin 作业 checkpoint→恢复→继续处理输出正确（复用既有恢复 E2E 或新增）
- [x] 乱序 seq 钉子变异探针回退必红
- [x] **接线验证**：ST-02 冻结视图的恢复路径确实经 putAll（断言或代码追踪记录）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] ST-01/ST-02/REG-01 三缺陷闭合（变异探针回退必红）
- [x] `./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime,nop-stream/nop-stream-fraud-example` 全绿
- [x] R6 summary 对应条目补记修复归属
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：冻结逻辑在真实 checkpoint 管线中被调用（非仅单测直调）
- [x] owner doc：`docs-for-ai/03-modules/nop-stream-user-guide.md` 状态快照语义小节核对（若描述行为不变则显式写 No owner-doc update required）
- [x] `./mvnw test`（受影响模块）
- [x] 代码规范核对通过

## Deferred But Adjudicated

### ST-09 族（RocksDB 侧对称捕获）等 R6 P2/P3 残余

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 不影响 Memory 冻结协议闭合；属治理批次
- Successor Required: `yes`
- Successor Path: 后续治理批次计划

### serializeWithSerializer 异常 fallback 活引用（:852-860，valueSer!=null 路径同族残余）

- Classification: `watch-only residual`
- Why Not Blocking Closure: 仅自定义 serializer 序列化抛异常的兼容降级路径触达，且有 LOG.warn 可观察；不属本轮确认缺陷
- Successor Required: `no`

### MemoryOperatorStateBackend raw 通道补冻缺 focused 钉子（closure audit Minor-2）

- Classification: `watch-only residual`
- Why Not Blocking Closure: 该通道当前无生产写入者（审计复核确认），冻结行为为前瞻加固；首个真实消费者落地时同步补钉
- Successor Required: `no`

## Closure

Status Note: ST-01/ST-02/REG-01 三缺陷以 Fix 落地并全部具备变异探针回退必红保护；审计代理对 7 个回退目标逐一推演确认钉子真实钉住修复行（含"仅去掉 toSerializableForm 防御拷贝"的共享 V 叶子残余形态）；Anti-Hollow 调用链（processBarrier→freeze→acknowledgeTask→persistExecutor→CheckpointSerDe 分派→toSerializableForm）与恢复链（fromSerializableForm→putAll 精确 seq）完整连通。core 1688/0、runtime 1237/0（首轮 1 例时序 flake 重跑全绿）、fraud-example 120/0。

Completed: 2026-10-06

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，agent_e42b0ccf-7262-4424-b79a-929ee6310c98）
- Audit Session: agent_e42b0ccf-7262-4424-b79a-929ee6310c98
- Evidence:
  - Phase 1 Exit Criteria 4 条 + Phase 2 Exit Criteria 4 条：全部 PASS（两条日志项为"事实 PASS/形式未勾"，回填后一致）
  - Closure Gates 8 条：全部 PASS（Gate 2/7 测试计数为日志声称未独立复跑——与 2322 审计同口径，日志可复核）
  - 变异探针：7 个回退目标 × 对应钉子逐一推演必红；同 ts 既有用例两实现均绿（无误报）
  - putAll 5 个调用点兼容性核验：新推进恒 ≥ 旧估算，无旧正确场景回归
  - `node ai-dev/tools/check-plan-checklist.mjs --strict` 回填后退出码 0
  - Anti-Hollow：冻结逻辑经 processBarrier→snapshotState→freeze→coordinator→persist 线程真实消费（非单测直调）；恢复链经 fromSerializableForm→putAll 消费精确 seq
  - Deferred 项分类检查：ST-09 族 out-of-scope 合规；watch 项已按模板补 Classification；无 in-scope live defect 被降级
  - 条件整改：钉 (c) 实现形态偏差已补记（执行偏差补记条目）

Follow-up:

- 持久化"字节不变"目前为构造性论证（TestEquiJoinBufferJsonRoundTrip 佐证）；可选增强为逐字节 diff 断言（不阻塞）
- raw 通道补冻 focused 钉子：随首个真实消费者落地补（Deferred 已登记）
