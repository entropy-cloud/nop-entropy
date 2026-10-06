# 2323 nop-stream R6 P1：快照冻结协议闭合（ST-01/ST-02/REG-01）

> Plan Status: draft
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

### Phase 1 - payload 叶子冻结与 operator-state 冻结

Status: planned
Targets: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/memory/MemoryStateSerDe.java`、MemoryOperatorStateBackend、EquiJoinOperator、OverWindowOperator

- Item Types: `Fix`、`Proof`

- [ ] writeListPayload/writeMapPayload：valueSer==null 分支叶子 defensiveCopy（保留 wrapper 元素类型两段式契约）
- [ ] MemoryOperatorStateBackend.snapshotState：对无类型快照通道的值统一类型保持冻结；EquiJoin/OverWindow snapshotState 改产出冻结视图
- [ ] serializeWithSerializer null 分支契约注释同步
- [ ] 回归钉子：快照后变异 list 元素 / map value 叶子 / buffer add，断言持久化 JSON 不变（三类）
- [ ] `./mvnw test -pl nop-stream/nop-stream-core` 全绿

Exit Criteria:

- [ ] 三类"快照后变异"钉子存在且通过（变异探针法验证回退必红：注释深拷贝行钉子必红）
- [ ] EquiJoin barrier 后 setMatched 不再泄入持久化视图（专钉或既有 2PC E2E 佐证）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - REG-01 seq 语义同步修复

Status: planned
Targets: `nop-stream/nop-stream-core` PerKeyOrderedBuffer

- Item Types: `Fix`、`Proof`

- [ ] putAll 消费 fromSerializableForm 产物的精确 insertionSeq（不再 lastKey()[1] 估算）
- [ ] 回归钉子：乱序事件时间（ts=10 后 ts=5）快照→恢复→再 add(ts=5) 不静默覆盖
- [ ] ST-02 恢复链路（toSerializableForm→putAll）端到端测试接线确认

Exit Criteria:

- [ ] **端到端验证**：EquiJoin 作业 checkpoint→恢复→继续处理输出正确（复用既有恢复 E2E 或新增）
- [ ] 乱序 seq 钉子变异探针回退必红
- [ ] **接线验证**：ST-02 冻结视图的恢复路径确实经 putAll（断言或代码追踪记录）
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [ ] ST-01/ST-02/REG-01 三缺陷闭合（变异探针回退必红）
- [ ] `./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime,nop-stream/nop-stream-fraud-example` 全绿
- [ ] R6 summary 对应条目补记修复归属
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：冻结逻辑在真实 checkpoint 管线中被调用（非仅单测直调）
- [ ] owner doc：`docs-for-ai/03-modules/nop-stream-user-guide.md` 状态快照语义小节核对（若描述行为不变则显式写 No owner-doc update required）
- [ ] `./mvnw test`（受影响模块）
- [ ] 代码规范核对通过

## Deferred But Adjudicated

### ST-09 族（RocksDB 侧对称捕获）等 R6 P2/P3 残余

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 不影响 Memory 冻结协议闭合；属治理批次
- Successor Required: `yes`
- Successor Path: 后续治理批次计划

## Closure

Status Note: （收口时填写）
Completed: （收口时填写）

Closure Audit Evidence: （收口时由独立子 agent 填写）

Follow-up:

- （收口时填写）
