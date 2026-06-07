# 对抗性审查汇总 — nop-stream Round N+2

## 基本信息

- **审核模块**: nop-stream（流处理引擎）
- **审核日期**: 2026-06-05
- **审查类型**: 开放式对抗性审查
- **重点区域**: Sink 算子 checkpoint 契约 + Window evictor 时间戳 + Window trigger 快照 + CDC connector 生命周期 + 分布式 barrier 路由 + CEP timer 持久化 + CheckpointCoordinator 并发
- **去重范围**: ~17 轮对抗性审查 + 深度审计（详见 01-open-findings.md）

## 发现摘要

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0      | 0    | —       |
| P1      | 6    | Checkpoint契约(1), 资源泄漏(1), 窗口正确性(2), 分布式协议(1), Barrier路由(1) |
| P2      | 9    | 状态持久化(2), 生命周期(2), 并发(2), 序列化(1), 连接器(1), 关闭安全(1) |
| P3      | 2    | API契约(1), 代码质量(1) |

## 最关键新发现

1. **AR-1** (P1): `StreamSinkOperator.processBarrier()` 无 try/catch — 快照失败直接挂死整个 checkpoint 流
2. **AR-2** (P1): `DebeziumCdcSourceFunction.run()` finally 块不清理资源 — cancel() 先于 run() 调用时永久泄漏 Debezium 引擎线程
3. **AR-3** (P1): `WindowOperator.emitWindowContents()` evictor 路径所有元素使用 watermark 时间戳而非事件时间戳
4. **AR-4** (P1): `WindowOperator.snapshotState()` 浅拷贝可变 SimpleAccumulator — checkpoint 后继续处理会静默损坏快照数据
5. **AR-6** (P1): `JobCoordinator.triggerCheckpoint()` 向所有 TaskManager 广播 barrier — 非 source 任务收到重复 barrier 注入
6. **AR-8** (P2): `CheckpointBarrierTracker.triggerCheckpoint()` barrier 被拒绝后遗留脏状态 — 永久禁用后续 checkpoint

## 评估结论

经过 ~17+2 轮审计，本轮发现集中在三个此前覆盖不足的交叉领域：

1. **Sink 算子的 checkpoint 覆盖契约**: 前期审查聚焦于 `AbstractStreamOperator` 的 barrier 处理，但未发现 `StreamSinkOperator` 完全覆盖了该方法且丢失了错误处理。这暴露了一个模式问题：子类覆盖 checkpoint 关键方法时缺乏框架级保障。

2. **Window 算子的状态正确性**: evictor 路径的时间戳丢失（AR-3）是一个确定性的逻辑错误，影响所有使用 evictor 的管道。与上一轮发现的 shallow-copy 问题（AR-4）一起，表明 WindowOperator 的状态管理在"持久化"和"元素元数据保持"两个维度都有缺陷。

3. **Connector 生命周期与框架契约的连锁**: DebeziumCdcSourceFunction 的三重问题（资源泄漏 + 状态不复位 + 非原子重入）与上一轮发现的 StreamSourceOperator 不调用 cancel() 形成连锁效应。

<ADVERSARIAL_RESULT>issues</ADVERSARIAL_RESULT>
