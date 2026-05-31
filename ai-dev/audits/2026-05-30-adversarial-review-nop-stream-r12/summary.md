# 对抗性审查汇总 — nop-stream Round 12

## 基本信息

- **审核模块**: nop-stream（流处理引擎）
- **审核日期**: 2026-05-30
- **审查类型**: 开放式对抗性审查
- **审查范围**: Round 12 — 全模块聚焦 2PC 持久化链路、类名验证安全、watermark 传播、trigger 合并语义
- **去重范围**: 11 轮对抗性审查 + 4 轮深度审计

## Round 11 修复确认

Round 11 的全部高优先级发现（AR-1 信号量泄漏、AR-5 trigger state/activeWindowsPerKey/watermark 转发）已在当前代码中确认修复。

## 发现摘要

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0      | 1    | 2PC 状态持久化缺失 |
| P1      | 3    | 安全验证(1) + Watermark 广播(1) + Trigger 合并(1) |
| P2      | 4    | 整数溢出(1) + DAG 验证(1) + 反序列化(1) + 2PC 恢复(1) |

## 最关键发现

1. **AR-1** (P0): `TwoPhaseCommitSinkFunction.saveState()` 返回 null，`pendingCommits` 永不持久化。2PC sink 的 exactly-once 保证在 checkpoint/restore 链路上完全不工作。与 AR-8（restoreState 不恢复 pendingCommits）形成双重断裂。
2. **AR-2** (P1): `ClassNameValidator` 的 `[L` 前缀允许任意 `[Lcom.evil.Class;` 类名通过验证并在反序列化时实例化——defense-in-depth 缺失。
3. **AR-3** (P1): `RecordWriter.emitElement()` 在使用 `partitionRouter`（非 `partitioner`）时只写 `partitions[0]`，watermark/status 不广播到其他 channel。
4. **AR-5** (P1): `CountTrigger.onMerge()` 是 no-op 但 `canMerge()` 返回 true，Session Window 合并后计数器归零。

## 评估结论

Round 12 发现了一个系统性设计缺陷：`TwoPhaseCommitSinkFunction` 的 2PC 状态在 save/restore 链路上完全不持久化。这不是偶发的边界条件 bug，而是一个从未被实现的功能层——`pendingCommits` map 在内存中正确管理，但在 checkpoint 边界上被静默丢弃。之前的 11 轮审查和 4 轮深度审计都聚焦在 CheckpointCoordinator 的 2PC 协议正确性上（retryFailedCommits、shutdown abort 通知等），但遗漏了最基础的一环：sink function 自身的状态持久化。

<ADVERSARIAL_RESULT>issues</ADVERSARIAL_RESULT>
