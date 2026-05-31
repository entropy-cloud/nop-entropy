# 对抗性审查汇总 — nop-stream Round 13

## 基本信息

- **审核模块**: nop-stream（流处理引擎）
- **审核日期**: 2026-05-30
- **审查类型**: 开放式对抗性审查
- **审查范围**: Round 13 — 全模块聚焦任务生命周期、窗口状态序列化、2PC 参与者管理、传输层错误处理
- **去重范围**: 12 轮对抗性审查 + 4 轮深度审计

## Round 12 遗留问题确认

以下 Round 12 高优先级发现**仍未修复**：

| Round 12 编号 | 问题 | 当前状态 |
|---------------|------|---------|
| AR-1 | TwoPhaseCommitSinkFunction.saveState() 返回 null | ⚠️ 未修复 |
| AR-2 | ClassNameValidator `[L` 前缀 | ⚠️ 未修复 |
| AR-3 | RecordWriter.emitElement() 只写 partitions[0] | ⚠️ 未修复 |
| AR-5 | CountTrigger.onMerge() no-op 但 canMerge()=true | ⚠️ 未修复 |

## 发现摘要

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0      | 0    | —       |
| P1      | 6    | 任务生命周期(1) + 资源泄漏(1) + 2PC 参与者(1) + Barrier 丢失(1) + 序列化格式(1) + 窗口合并数据丢失(1) |
| P2      | 10   | 复合 key 碰撞(2) + 并发死锁(1) + 传输错误处理(1) + Checkpoint 清理(1) + 对象共享(1) + 回调时序(1) + 滑动窗口 DoS(1) + SharedBuffer EventId 复用(1) + 性能退化(1) |
| P3      | 1    | CEP 序列化兼容性(1) |

## 最关键发现

1. **AR-1** (P1): `SubtaskTask.cancel()` 只支持 CREATED→CANCELED。RUNNING 状态的任务无法停止，形成运行时可靠性的系统性风险。
2. **AR-3** (P1): `CheckpointCoordinator.notifyParticipantsFinishCommit` 使用列表索引跟踪失败参与者。`removeParticipant()` 后重试会操作错误参与者，可能在 2PC sink 中 commit/rollback 错误事务。
3. **AR-5** (P1): `WindowAggregationOperator` 反序列化使用 `#` 分隔符。String key 含 `#` 时 checkpoint 恢复损坏。
4. **AR-6** (P1): `WindowOperator.mergeWindowContents` 非累加器回退路径静默覆盖。Session window 合并 3+ 个非累加器窗口时只保留最后一个。
5. **AR-4** (P1): `InputGate.handleBarrierNonRecursive` 在 `barrierReceived[channelIndex]=true` 时静默丢弃新 barrier。重叠 checkpoint 场景下新 checkpoint 的 barrier 被吞噬。

## 评估结论

Round 13 在 12 轮对抗性审查和 4 轮深度审计之后仍然发现了 6 个 P1 和 10 个 P2 新问题。核心模式：

1. **任务生命周期管理**是一个之前审查完全未覆盖的维度。SubtaskTask 的状态机只允许 CREATED→RUNNING 和 CREATED→CANCELED，缺少 RUNNING→CANCELING 路径。
2. **窗口状态序列化和合并**的健壮性不足。四个独立问题（`#` 分隔符、非累加器覆盖、复合 key 碰撞、toString namespace）共同指向 WindowOperator 的 key 设计缺乏真实数据防御。
3. **2PC 参与者管理**的索引失效问题与 Round 12 的状态持久化缺失形成连锁风险。

<ADVERSARIAL_RESULT>issues</ADVERSARIAL_RESULT>
