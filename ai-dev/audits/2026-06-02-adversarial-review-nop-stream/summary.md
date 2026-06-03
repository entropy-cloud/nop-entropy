# 对抗性审查汇总 — nop-stream (2026-06-02)

## 基本信息

- **审核模块**: nop-stream（流处理引擎）
- **审核日期**: 2026-06-02
- **审查类型**: 开放式对抗性审查
- **审查范围**: 全模块，重点为 state backend、window operator、CEP engine、graph generation、execution engine
- **去重范围**: 16 轮对抗性审查 + 5 轮深度审计

## 发现摘要

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0      | 2    | 状态后端可变性损坏, 窗口 evictor 语义失效 |
| P1      | 9    | 检查点完整性, 图生成, 状态路由, CEP 定时器, 窗口命名空间, 并发安全 |
| P2      | 4    | CEP 溢出, 缓存一致性, 分区溢出, 任务跟踪 |
| P3      | 0    | — |

## 最关键新发现

1. **AR-1** (P0): `MemoryInternalAppendingState` 共享单例 `accumulator` 对可变累加器（`ListAccumulator`、`Histogram`）产生跨 key 引用别名——`resetLocal()` 清空存储中已有值，导致静默数据丢失。

2. **AR-3** (P0): `WindowOperator` evictor 路径对所有元素使用 `Long.MIN_VALUE` 时间戳包装 `TimestampedValue`，`TimeEvictor` 完全失效。

3. **AR-2** (P1): `StateSnapshot` + `MemoryStateSerDe` 快照存储活跃 state 的直接引用而非深拷贝，异步检查点场景下快照被后续处理静默修改。

4. **AR-4** (P1): `InputGate.checkBarrierAlignmentComplete()` 吞掉已对齐的 checkpoint barrier（finished channel 路径），导致检查点静默丢弃。

5. **AR-9** (P1): CEP per-state `windowTimes` 从不注册定时器，无事件时超时永不触发。

## 未修复已知问题

16 轮审查累计报告的 2 P0 + 12 P1 中，**仅 2 个已修复**（HeapInternalTimerService timer deletion、Lockable double release）。13 个关键问题仍在，包括两个 P0（TwoPhaseCommitSinkFunction CME、CheckpointBarrierTracker 死锁）。

## 优先修复建议

1. **[P0] AR-1**: 修复 MemoryInternalAppendingState 的共享累加器——每次 add() 创建新实例或深拷贝 getLocalValue()
2. **[P0] AR-3**: 修复 evictor 路径 TimestampedValue 包装——使用元素真实时间戳
3. **[P1] K-1/K-2**: 修复两个已知 P0（TwoPhaseCommitSinkFunction CME、CheckpointBarrierTracker 死锁）
4. **[P1] AR-9**: CepOperator 为 per-state windowTimes 注册定时器
5. **[P1] AR-10**: PartitionedPlanGenerator 使用类型检查替代类名子串匹配

<ADVERSARIAL_RESULT>issues</ADVERSARIAL_RESULT>
