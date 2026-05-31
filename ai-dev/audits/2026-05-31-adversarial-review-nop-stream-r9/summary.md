# 对抗性审查汇总 — nop-stream Round 15

## 基本信息

- **审核模块**: nop-stream（流处理引擎）
- **审核日期**: 2026-05-31
- **审查类型**: 开放式对抗性审查
- **审查范围**: Round 15 — 全模块，聚焦 checkpoint 生命周期、算子链 side-output、窗口合并 trigger 一致性、资源清理对称性
- **去重范围**: 14 轮对抗性审查 + 4 轮深度审计

## 发现摘要

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0      | 1    | CheckpointBarrierTracker 永久死锁 |
| P1      | 4    | barrier 静默丢弃 + trigger onMerge 遗漏 + side-output 丢失 + tracker index 错位 |
| P2      | 4    | InputGate 泄漏 + isFinished 语义 + MergingWindowSet 增长 + 跨 channel barrier ID |
| P3      | 0    | — |

## 最关键发现

1. **AR-1** (P0): `CheckpointBarrierTracker.triggerCheckpoint()` 当 source 拒绝 barrier 时（offerBarrier 返回 false），`operatorsToAck` 已设为非零但未重置。所有后续 checkpoint 调用命中 `operatorsToAck > 0` guard 永久返回 false。一次拒绝即可永久杀死该 task 的全部 checkpoint 能力。

2. **AR-3** (P1): `WindowAggregationOperator.processElementWithMerging()` 在 session window 合并时从不调用 `trigger.onMerge()`，而 `WindowOperator` 正确调用了。两个窗口算子对同一场景的 trigger 生命周期管理存在系统性不一致。

3. **AR-4** (P1): `ChainingOutput` 静默丢弃所有 side-output 记录（仅 WARN 日志）。算子链是默认优化，side output 是一级 API 特性。两者的交互导致 late data output 和 ProcessWindowFunction multi-output 在大多数部署中静默失效。

4. **AR-5** (P1): `registerTasksAndTrackers()` 为多链顶点的每个 chain 创建 tracker 并覆盖前一个，最终存活的 tracker 持有错误的 operator 列表，导致 checkpoint ACK 的 operator index 错位。

## 级联风险

AR-1（死锁）+ AR-2（barrier 静默丢弃）+ AR-9（跨 channel barrier ID 不检查）形成级联：
- 上游 channel 崩溃 → InputGate 丢弃对齐的 barrier（AR-2）
- 下游 source barrier 队列积压 → CheckpointBarrierTracker 死锁（AR-1）
- 并发 checkpoint 时 barrier ID 混合（AR-9）

<ADVERSARIAL_RESULT>issues</ADVERSARIAL_RESULT>
