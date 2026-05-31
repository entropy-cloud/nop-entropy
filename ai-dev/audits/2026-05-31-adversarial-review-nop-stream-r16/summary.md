# 对抗性审查汇总 — nop-stream Round 16

## 基本信息

- **审核模块**: nop-stream（流处理引擎）
- **审核日期**: 2026-05-31
- **审查类型**: 开放式对抗性审查
- **审查范围**: Round 16 — 全模块，聚焦 connector/TwoPhaseCommitSinkFunction、CEP/NFA/SharedBuffer、runtime/execution/transport/cluster、core/operators/graph/datastream
- **去重范围**: 15 轮对抗性审查 + 5 轮深度审计

## 发现摘要

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0      | 1    | TwoPhaseCommitSinkFunction saveState() 并发崩溃 |
| P1      | 10   | late-data API + timer 语义 + 窗口计数 + checkpoint ID + CEP 正确性 + 集群注册 + 内存泄漏 + 2PC 线程安全 |
| P2      | 9    | watermark + operator chain + checkpoint 存储 + 生命周期 + 恢复 + registry + buffer + 序列化 + CEP 验证 |
| P3      | 1    | Javadoc 错误 |

## 最关键发现

1. **AR-1** (P0): `TwoPhaseCommitSinkFunction.saveState()` 无锁遍历 `synchronizedMap`，checkpoint 线程与 commit 线程并发时 `ConcurrentModificationException` 崩溃。

2. **AR-2** (P1): `WindowedStreamImpl.allowedLateness()` setter 完全无效 — 三个聚合方法均未将值传递给 `WindowAggregationOperator`。late-data handling API 是死代码。

3. **AR-3** (P1): `HeapInternalTimerService.advanceWatermark()` 先移除同时间戳所有 timer 再触发，导致回调中 `deleteEventTimeTimer()` 无效。标准 trigger 自删除模式被破坏。

4. **AR-4** (P1): `WindowAggregationOperator` 合并路径在多目标时将当前元素重复加入每个合并目标的 accumulator。

5. **AR-5** (P1): `CheckpointIDCounter` 恢复后不更新，新 checkpoint ID 从 0 开始，覆写旧文件。与 `LocalFileCheckpointStorage` 按文件名排序形成级联。

6. **AR-6** (P1): `CepOperator.onEventTime()` 在只剩 1 个 partial match 时清空全部状态，但不验证是否为 start state。有效 match 被丢弃。

7. **AR-7** (P1): `DeweyNumber.increase()` int 溢出，高吞吐分支模式下版本号静默损坏。

8. **AR-8** (P1): `Lockable.release()` 在 refCounter 已为 0 时返回 true，静默双重释放 SharedBuffer 条目。

9. **AR-9** (P1): `JdbcClusterRegistry.registerNode()` 设置 `lease_expire_at=0`，新节点注册后约 5 秒内对 `getActiveNodes()` 不可见。

10. **AR-10** (P1): `CheckpointCoordinator.checkpointSuccessMap` 无限增长，长期运行作业内存泄漏。

## 级联风险

AR-5 (checkpoint ID 倒退) + AR-15 (storage 按文件名排序) = 恢复到旧 epoch 状态。
AR-1 (saveState CME) + AR-11 (setPendingCommits 接受任意 Map) = 恢复后 2PC 并发安全完全丧失。
AR-6 (清空非 start state) + AR-7 (DeweyNumber 溢出) + AR-8 (Lockable 双重释放) = CEP 子系统级联正确性失效。

<ADVERSARIAL_RESULT>issues</ADVERSARIAL_RESULT>
